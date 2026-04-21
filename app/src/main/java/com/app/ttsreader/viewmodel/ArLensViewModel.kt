package com.app.ttsreader.viewmodel

import android.app.Application
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.camera.ArLensAnalyzer
import com.app.ttsreader.camera.CameraController
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.network.NetworkMonitor
import com.app.ttsreader.ocr.SpatialWord
import com.app.ttsreader.translate.TranslationRepository
import com.app.ttsreader.utils.FuzzyMatcher
import com.app.ttsreader.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

// ── Domain model ──────────────────────────────────────────────────────────────

/**
 * A single text block that has been detected, stabilised, and translated.
 *
 * @param id            Stable tracking ID for this physical text occurrence.
 * @param originalText  Text as it appears on the physical object.
 * @param translatedText Translation in the target language. Empty while pending.
 * @param smoothedBox   EMA-smoothed bounding box in **image space** (portrait).
 * @param displayAlpha  Current render alpha [0, 1]. Animated towards 0 if the
 *                      block is unstable, towards 1 when stable with a translation.
 */
@Suppress("ArrayInDataClass")
data class ArLensBlock(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val smoothedBox: RectF,
    val displayAlpha: Float,
    val cornerPoints: Array<Point>? = null
)

// ── UI state ──────────────────────────────────────────────────────────────────

data class ArLensUiState(
    val blocks: List<ArLensBlock>   = emptyList(),
    val imageEffectiveWidth: Int    = 1,
    val imageEffectiveHeight: Int   = 1,
    val sourceLang: AppLanguage     = LanguageUtils.DEFAULT_SOURCE,
    val targetLang: AppLanguage     = LanguageUtils.DEFAULT_TARGET,
    val isPickingSource: Boolean    = false,
    val isPickingTarget: Boolean    = false,
    val isOffline: Boolean          = false,
    val statusMessage: String       = ""
)

/**
 * ViewModel for the AR Magic Lens mode.
 *
 * ## Pipeline
 * 1. Each [SpatialWord] from the native engine is spatially grouped into text lines.
 * 2. Each line-group is matched to an existing [TrackedBlock] via text similarity
 *    ([FuzzyMatcher.score] ≥ 0.75) or spatial proximity (< 60 px).
 * 3. Matched blocks receive EMA-smoothed position updates (adaptive α 0.15–0.50).
 * 4. A block is **stable** when all displacements in the window are < 15 px.
 * 5. Stable blocks trigger a one-shot translation request.
 * 6. [displayAlpha] lerps towards 1 when stable + translated, towards 0 otherwise.
 * 7. Blocks missing for > [MAX_MISSED_FRAMES] are evicted.
 */
class ArLensViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArLensUiState())
    val uiState: StateFlow<ArLensUiState> = _uiState.asStateFlow()

    private val cameraController   = CameraController(application)
    private val analyzer           = ArLensAnalyzer(
        onResult   = ::onFrameAnalyzed,
        throttleMs = 100L
    )
    private val translationRepo    = TranslationRepository()
    private val networkMonitor     = NetworkMonitor(application)

    private val translationCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
                size > 200
        }
    )
    private val translationPending = ConcurrentHashMap<String, Boolean>()
    private val trackedBlocks      = mutableMapOf<String, TrackedBlock>()

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.value = _uiState.value.copy(isOffline = !isOnline)
            }
        }
    }

    // ── Camera ─────────────────────────────────────────────────────────────────

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                cameraController.startCamera(lifecycleOwner, previewView, analyzer)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Camera failed to start.")
            }
        }
    }

    // ── Language selection ─────────────────────────────────────────────────────

    fun setSourceLanguage(lang: AppLanguage) {
        _uiState.value = _uiState.value.copy(sourceLang = lang, isPickingSource = false)
        translationCache.clear()
        translationPending.clear()
        trackedBlocks.values.forEach { it.translatedText = "" }
    }

    fun setTargetLanguage(lang: AppLanguage) {
        _uiState.value = _uiState.value.copy(targetLang = lang, isPickingTarget = false)
        translationCache.clear()
        translationPending.clear()
        trackedBlocks.values.forEach { it.translatedText = "" }
    }

    fun openSourcePicker() { _uiState.value = _uiState.value.copy(isPickingSource = true) }
    fun openTargetPicker() { _uiState.value = _uiState.value.copy(isPickingTarget = true) }
    fun closePicker()      { _uiState.value = _uiState.value.copy(isPickingSource = false, isPickingTarget = false) }

    // ── Line conversion ────────────────────────────────────────────────────────

    /**
     * Each incoming [SpatialWord] already represents a full ML Kit text line
     * (produced by [TextAnalyzer.extractLines]). No manual re-grouping is needed —
     * just convert directly to [NativeBlock] using ML Kit's own geometry.
     */
    private data class NativeBlock(
        val text: String,
        val box: RectF,
        val cornerPoints: Array<Point>?
    ) {
        val centerX: Float get() = (box.left + box.right)  / 2f
        val centerY: Float get() = (box.top  + box.bottom) / 2f
    }

    private fun groupWordsIntoBlocks(words: List<SpatialWord>): List<NativeBlock> =
        words.mapNotNull { word ->
            if (word.text.isBlank()) return@mapNotNull null
            val rect = word.toBoundingRect()
            NativeBlock(
                text         = word.text,
                box          = RectF(rect.left.toFloat(), rect.top.toFloat(),
                                     rect.right.toFloat(), rect.bottom.toFloat()),
                cornerPoints = word.toCornerPoints()
            )
        }.filter { it.box.width() >= 20f && it.box.height() >= 8f }

    // ── Frame analysis ─────────────────────────────────────────────────────────

    private fun onFrameAnalyzed(
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) {
        val effectiveW = if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
        val effectiveH = if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth  else imageHeight

        // Accept all detected regions regardless of text content.
        // When CRNN model is not loaded all texts are ""; filtering by length
        // would silently drop every detection. The stability classifier will
        // reject genuine noise (it won't hold stable for STABLE_FRAMES_REQUIRED frames).
        val newBlocks = groupWordsIntoBlocks(words)

        val matchedIds = mutableSetOf<String>()
        val nowMs = System.currentTimeMillis()

        for (block in newBlocks) {
            val rawCX = block.centerX
            val rawCY = block.centerY

            val bestMatch = trackedBlocks.values
                .filter { it.id !in matchedIds }
                .minByOrNull { tracked ->
                    val tCX = (tracked.smoothedLeft + tracked.smoothedRight)  / 2f
                    val tCY = (tracked.smoothedTop  + tracked.smoothedBottom) / 2f
                    val spatialDist = hypot((rawCX - tCX).toDouble(), (rawCY - tCY).toDouble()).toFloat()
                    val textSim     = FuzzyMatcher.score(block.text, tracked.originalText)
                    // Require meaningful text similarity — spatial proximity alone is not enough.
                    // This prevents old stale blocks being "refreshed" by nearby different text.
                    val textMatch   = textSim >= 0.75f
                    val spatialClose = spatialDist < SPATIAL_MATCH_PX && textSim >= TEXT_SIM_THRESHOLD
                    if (textMatch || spatialClose) spatialDist else Float.MAX_VALUE
                }

            if (bestMatch != null) {
                matchedIds.add(bestMatch.id)
                bestMatch.lastSeenTimeMs = nowMs   // ← timestamp reset on every real match

                val prevCX = (bestMatch.smoothedLeft + bestMatch.smoothedRight)  / 2f
                val prevCY = (bestMatch.smoothedTop  + bestMatch.smoothedBottom) / 2f
                val displacement = hypot(
                    (rawCX - prevCX).toDouble(),
                    (rawCY - prevCY).toDouble()
                ).toFloat()

                if (bestMatch.recentDisplacements.size >= STABILITY_WINDOW) {
                    bestMatch.recentDisplacements.removeFirst()
                }
                bestMatch.recentDisplacements.addLast(displacement)

                val avgDisp = if (bestMatch.recentDisplacements.isEmpty()) EMA_ALPHA
                              else bestMatch.recentDisplacements.average().toFloat()
                val adaptiveAlpha = when {
                    avgDisp < 8f  -> 0.50f
                    avgDisp > 30f -> 0.15f
                    else          -> EMA_ALPHA
                }
                bestMatch.smoothedLeft   += adaptiveAlpha * (block.box.left   - bestMatch.smoothedLeft)
                bestMatch.smoothedTop    += adaptiveAlpha * (block.box.top    - bestMatch.smoothedTop)
                bestMatch.smoothedRight  += adaptiveAlpha * (block.box.right  - bestMatch.smoothedRight)
                bestMatch.smoothedBottom += adaptiveAlpha * (block.box.bottom - bestMatch.smoothedBottom)
                bestMatch.cornerPoints   = block.cornerPoints
                bestMatch.missedFrames   = 0

                val isStable = bestMatch.recentDisplacements.size >= STABILITY_WINDOW &&
                               bestMatch.recentDisplacements.max() < MAX_DISPLACEMENT_PX

                if (isStable) bestMatch.stableFrameCount++ else bestMatch.stableFrameCount = 0

                val cachedTranslation = translationCache[bestMatch.originalText]
                if (cachedTranslation != null) {
                    bestMatch.translatedText = cachedTranslation
                } else if (isStable
                    && bestMatch.stableFrameCount >= STABLE_FRAMES_REQUIRED
                    && translationPending[bestMatch.originalText] != true
                ) {
                    requestTranslation(bestMatch.originalText)
                }

                // Show block as soon as it is stable — original text is visible
                // immediately; translated text replaces it once the request finishes.
                val targetAlpha = if (isStable) 1f else 0f
                bestMatch.displayAlpha += (targetAlpha - bestMatch.displayAlpha) * ALPHA_LERP_IN

            } else {
                val id = buildBlockId(block.text, rawCX, rawCY)
                trackedBlocks[id] = TrackedBlock(
                    id             = id,
                    originalText   = block.text,
                    smoothedLeft   = block.box.left,
                    smoothedTop    = block.box.top,
                    smoothedRight  = block.box.right,
                    smoothedBottom = block.box.bottom,
                    displayAlpha   = 0f,
                    cornerPoints   = block.cornerPoints
                )
                trackedBlocks[id]!!.recentDisplacements.addLast(Float.MAX_VALUE)
            }
        }

        val toRemove = mutableListOf<String>()
        for ((id, tracked) in trackedBlocks) {
            if (id !in matchedIds) {
                tracked.missedFrames++
                tracked.stableFrameCount = 0
                // Use the faster OUT lerp so unmatched blocks vanish immediately
                tracked.displayAlpha += (0f - tracked.displayAlpha) * ALPHA_LERP_OUT
                // Evict if: exceeded max missed frames OR stale by wall-clock time
                val staleByTime = (nowMs - tracked.lastSeenTimeMs) > STALE_THRESHOLD_MS
                if (tracked.missedFrames > MAX_MISSED_FRAMES || staleByTime) {
                    toRemove.add(id)
                }
            }
        }
        toRemove.forEach { trackedBlocks.remove(it) }

        val uiBlocks = trackedBlocks.values
            .filter  { it.displayAlpha > 0.05f }
            .map     { tracked ->
                ArLensBlock(
                    id             = tracked.id,
                    originalText   = tracked.originalText,
                    translatedText = tracked.translatedText,
                    smoothedBox    = RectF(
                        tracked.smoothedLeft,
                        tracked.smoothedTop,
                        tracked.smoothedRight,
                        tracked.smoothedBottom
                    ),
                    displayAlpha   = tracked.displayAlpha,
                    cornerPoints   = tracked.cornerPoints
                )
            }

        _uiState.value = _uiState.value.copy(
            blocks               = uiBlocks,
            imageEffectiveWidth  = effectiveW.coerceAtLeast(1),
            imageEffectiveHeight = effectiveH.coerceAtLeast(1)
        )
    }

    // ── Translation ────────────────────────────────────────────────────────────

    private fun requestTranslation(originalText: String) {
        translationPending[originalText] = true
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val translated = translationRepo.translate(
                    text       = originalText,
                    sourceLang = state.sourceLang.mlKitCode,
                    targetLang = state.targetLang.mlKitCode
                )
                translationCache[originalText] = translated
            } catch (e: Exception) {
                // Allow a retry on the next stable frame
                translationPending.remove(originalText)
            } finally {
                // Always clear the pending flag so the entry doesn't leak in the map
                // even after a successful translation (cache now serves the result).
                translationPending.remove(originalText)
            }
        }
    }

    private fun buildBlockId(text: String, cx: Float, cy: Float): String {
        val gridX = (cx / 100).toInt()
        val gridY = (cy / 100).toInt()
        return "${text.trim().hashCode()}_${gridX}_${gridY}"
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Called when ArLensScreen leaves composition.
     * Unbinds camera so no frames are analyzed while another mode is active.
     * Tracked blocks and translation caches are retained so the overlay
     * restores instantly if the user returns to the same session.
     */
    fun pause() {
        cameraController.pauseCamera()
    }

    override fun onCleared() {
        analyzer.close()
        cameraController.stopCamera()
        translationRepo.close()
        super.onCleared()
    }

    // ── Internal tracking state ────────────────────────────────────────────────

    private class TrackedBlock(
        val id: String,
        val originalText: String,
        var smoothedLeft:   Float,
        var smoothedTop:    Float,
        var smoothedRight:  Float,
        var smoothedBottom: Float,
        var displayAlpha:   Float = 0f,
        var missedFrames:   Int   = 0,
        var stableFrameCount: Int = 0,
        var translatedText: String = "",
        val recentDisplacements: ArrayDeque<Float> = ArrayDeque(),
        var cornerPoints: Array<Point>? = null,
        /** Wall-clock ms of the last frame where this block was actually matched. */
        var lastSeenTimeMs: Long = System.currentTimeMillis()
    )

    private companion object {
        const val EMA_ALPHA              = 0.35f   // EMA for position smoothing
        const val MAX_DISPLACEMENT_PX    = 25f     // max px jitter to be considered "stable"
        const val SPATIAL_MATCH_PX       = 50f     // reduced from 80 — tighter spatial match radius
        const val STABILITY_WINDOW       = 2       // frames in displacement history
        const val STABLE_FRAMES_REQUIRED = 2       // consecutive stable frames before showing
        const val MAX_MISSED_FRAMES      = 2       // reduced from 5 — evict after 2 missed frames (~200ms)
        const val ALPHA_LERP_IN          = 0.35f   // fade-in speed (unchanged)
        const val ALPHA_LERP_OUT         = 0.65f   // fade-out speed — 2× faster so blocks vanish quickly
        /** Hard expiry: blocks not matched in this many ms are force-evicted regardless of alpha. */
        const val STALE_THRESHOLD_MS     = 400L    // 400ms max lifetime after last match
        /** Minimum text similarity to allow a spatial-only match. */
        const val TEXT_SIM_THRESHOLD     = 0.55f   // blocks must be at least 55% text-similar to match
    }
}
