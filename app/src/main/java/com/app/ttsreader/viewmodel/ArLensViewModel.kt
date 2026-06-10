package com.app.ttsreader.viewmodel

import android.app.Application
import android.graphics.RectF
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.ar.HungarianAssigner
import com.app.ttsreader.ar.TrackedBlockState
import com.app.ttsreader.camera.ArLensAnalyzer
import com.app.ttsreader.camera.CameraController
import com.app.ttsreader.data.local.AppDatabase
import com.app.ttsreader.data.local.ArHistoryDao
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.network.NetworkMonitor
import com.app.ttsreader.ocr.SpatialWord
import com.app.ttsreader.translate.TranslationError
import com.app.ttsreader.translate.TranslationRepository
import com.app.ttsreader.tts.SpeechController
import com.app.ttsreader.utils.FuzzyMatcher
import com.app.ttsreader.utils.LanguageUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ── Public domain model published to the UI ────────────────────────────────────

/**
 * One detected text region that is ready to be drawn.
 *
 * @param id            Stable monotonic id (decoupled from spatial position).
 * @param originalText  Raw OCR text.
 * @param translatedText Translation in the target language. Empty while pending.
 * @param smoothedBox   Position in **image space** (post-rotation, origin TL).
 *                      The overlay maps this to screen space.
 * @param displayAlpha  Target alpha in [0, 1]. The renderer interpolates toward this
 *                      at display refresh rate via its own `withFrameNanos` loop.
 */
data class ArLensBlock(
    val id: Long,
    val originalText: String,
    val translatedText: String,
    val smoothedBox: RectF,
    val displayAlpha: Float,
)

data class ArLensUiState(
    val blocks: List<ArLensBlock> = emptyList(),
    /** Sentinel `-1` until the first real frame has been processed. */
    val imageEffectiveWidth: Int  = -1,
    val imageEffectiveHeight: Int = -1,
    val isFrontCamera: Boolean    = false,
    val sourceLang: AppLanguage   = LanguageUtils.DEFAULT_SOURCE,
    val targetLang: AppLanguage   = LanguageUtils.DEFAULT_TARGET,
    val isPickingSource: Boolean  = false,
    val isPickingTarget: Boolean  = false,
    val isOffline: Boolean        = false,
    val missingModelLang: String? = null,
    val statusMessage: String     = "",
)

/**
 * Total renovation of the AR Magic Lens tracker.
 *
 * ## Tracker
 * - Hungarian (Jonker-Volgenant) cost-matrix matching replaces greedy NN.
 * - Per-block constant-velocity predictor — a missed frame extrapolates by
 *   `velocity·dt` instead of freezing.
 * - Monotonic [AtomicLong] ids; motion never mints a new id.
 *
 * ## Threading
 * All [trackedBlocks] mutations dispatch onto a single-permit dispatcher
 * ([trackerDispatcher]). No locks needed; no races possible.
 *
 * ## Translation
 * Cache keyed by `(src, tgt, text)`. In-flight jobs tracked in [inFlight] and
 * cancelled on language change. Fan-out bounded by [translationGate].
 *
 * ## Pause
 * Pause emits an EMPTY block list AND resets image dims to the `-1` sentinel
 * so the overlay refuses to render until a fresh frame replaces them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArLensViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArLensUiState())
    val uiState: StateFlow<ArLensUiState> = _uiState.asStateFlow()

    private val cameraController = CameraController(application)
    private val analyzer         = ArLensAnalyzer(
        onResult   = ::onFrameAnalyzed,
        throttleMs = ANALYZER_THROTTLE_MS,
    )
    private val translationRepo  = TranslationRepository()
    private val networkMonitor   = NetworkMonitor(application)
    private val speechController = SpeechController(application)
    private val historyDao: ArHistoryDao = AppDatabase.getInstance(application).arHistoryDao()

    /** Single-permit dispatcher — all tracker mutations confine here. */
    private val trackerDispatcher: CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(1)

    /** Owned exclusively by [trackerDispatcher]. */
    private val trackedBlocks = ArrayList<TrackedBlockState>(32)
    private val nextId = AtomicLong(1L)
    private var lastRotationDegrees = Int.MIN_VALUE
    private var lastFrameNs: Long = 0L

    /** Per-(src,tgt,text) translation cache. */
    private val translationCache = ConcurrentHashMap<String, String>()
    /** Keys already queued OR running — prevents the same text being re-enqueued every frame. */
    private val pendingKeys = ConcurrentHashMap.newKeySet<String>()
    /** Running translation coroutines — cancelled on language change. */
    private val inFlight = ConcurrentHashMap<String, Job>()
    /** Bounded fan-out. */
    private val translationGate = Semaphore(MAX_CONCURRENT_TRANSLATIONS)
    private val translationRequests = Channel<TranslationRequest>(Channel.UNLIMITED)

    /** Scratch buffers for the Hungarian solver — kept across frames so the
     *  per-frame call allocates nothing. */
    private val assignerScratch = HungarianAssigner.Scratch()
    private var costBuf  = FloatArray(64)
    private var assignBuf = IntArray(8)

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _uiState.value = _uiState.value.copy(isOffline = !isOnline)
            }
        }
        // Translation dispatcher — drains the channel and launches each request
        // as its own coroutine so the actual translate() call is cancellable.
        viewModelScope.launch(Dispatchers.IO) {
            for (req in translationRequests) {
                val key = cacheKey(req.sourceLang, req.targetLang, req.text)
                val job = launch {
                    translationGate.withPermit { handleTranslation(req) }
                }
                inFlight[key] = job
                job.invokeOnCompletion {
                    inFlight.remove(key, job)
                    pendingKeys.remove(key)
                }
            }
        }
    }

    // ── Camera lifecycle ───────────────────────────────────────────────────────

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                cameraController.startCamera(lifecycleOwner, previewView, analyzer)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(statusMessage = "Camera failed to start.")
            }
        }
    }

    /**
     * Called when ArLensScreen leaves composition. Image-space coordinates are
     * NOT portable across camera sessions — clear everything and emit empty
     * blocks + sentinel dims so the overlay refuses to render until the next
     * fresh frame arrives.
     */
    fun pause() {
        cameraController.pauseCamera()
        viewModelScope.launch(trackerDispatcher) {
            trackedBlocks.clear()
            lastRotationDegrees = Int.MIN_VALUE
            _uiState.value = _uiState.value.copy(
                blocks = emptyList(),
                imageEffectiveWidth = -1,
                imageEffectiveHeight = -1,
            )
        }
    }

    // ── Language selection ─────────────────────────────────────────────────────

    fun setSourceLanguage(lang: AppLanguage) {
        viewModelScope.launch(trackerDispatcher) {
            cancelAllTranslations()
            translationCache.clear()
            for (b in trackedBlocks) b.translatedText = ""
            _uiState.value = _uiState.value.copy(
                sourceLang = lang,
                isPickingSource = false,
                missingModelLang = null,
            )
            checkRequiredModels()
        }
    }

    fun setTargetLanguage(lang: AppLanguage) {
        viewModelScope.launch(trackerDispatcher) {
            cancelAllTranslations()
            translationCache.clear()
            for (b in trackedBlocks) b.translatedText = ""
            _uiState.value = _uiState.value.copy(
                targetLang = lang,
                isPickingTarget = false,
                missingModelLang = null,
            )
            checkRequiredModels()
        }
    }

    private fun cancelAllTranslations() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        pendingKeys.clear()
    }

    fun openSourcePicker() { _uiState.value = _uiState.value.copy(isPickingSource = true) }
    fun openTargetPicker() { _uiState.value = _uiState.value.copy(isPickingTarget = true) }
    fun closePicker()      { _uiState.value = _uiState.value.copy(isPickingSource = false, isPickingTarget = false) }

    private fun checkRequiredModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val src = _uiState.value.sourceLang.mlKitCode
            val tgt = _uiState.value.targetLang.mlKitCode
            translationRepo.ensureModelDownloaded(src, tgt)
                .onFailure { e ->
                    val missing = (e as? TranslationError.ModelMissing)?.languageCode
                    _uiState.value = _uiState.value.copy(missingModelLang = missing)
                }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(missingModelLang = null)
                }
        }
    }

    // ── Tap-to-pronounce ───────────────────────────────────────────────────────

    /**
     * Speak the translated text of [block] in the target language. Called by the
     * overlay when the user taps a bounding box.
     */
    fun speakBlock(block: ArLensBlock) {
        val locale = _uiState.value.targetLang.locale
        speechController.setLanguage(locale)
        val phrase = block.translatedText.ifEmpty { block.originalText }
        if (phrase.isNotBlank()) speechController.speak(phrase)
    }

    // ── Frame ingestion ────────────────────────────────────────────────────────

    /**
     * Receives a frame of detections from [ArLensAnalyzer] on its single-thread
     * executor; immediately hops onto [trackerDispatcher] so the heavy work is
     * confined and `trackedBlocks` needs no locks.
     */
    private fun onFrameAnalyzed(
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        isFrontCamera: Boolean,
    ) {
        viewModelScope.launch(trackerDispatcher) {
            processFrame(words, imageWidth, imageHeight, rotationDegrees, isFrontCamera)
        }
    }

    private fun processFrame(
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        isFrontCamera: Boolean,
    ) {
        val effectiveW = if (rotationDegrees == 90 || rotationDegrees == 270) imageHeight else imageWidth
        val effectiveH = if (rotationDegrees == 90 || rotationDegrees == 270) imageWidth  else imageHeight
        val minImageDim = min(effectiveW, effectiveH).toFloat().coerceAtLeast(1f)

        val nowNs = System.nanoTime()
        val dtMs = if (lastFrameNs == 0L) 0f else (nowNs - lastFrameNs) / 1_000_000f
        val rotationChanged = rotationDegrees != lastRotationDegrees && lastRotationDegrees != Int.MIN_VALUE
        lastFrameNs = nowNs
        lastRotationDegrees = rotationDegrees

        // Coordinate spaces aren't portable across rotation. Drop everything.
        if (rotationChanged) trackedBlocks.clear()

        // Build detections list — filter junk lines.
        val detections = ArrayList<Detection>(words.size)
        for (word in words) {
            if (word.text.isBlank()) continue
            val r = word.toBoundingRect()
            val w = (r.right - r.left).toFloat()
            val h = (r.bottom - r.top).toFloat()
            if (w < minImageDim * 0.02f || h < minImageDim * 0.008f) continue
            detections.add(
                Detection(
                    text = word.text,
                    cx = (r.left + r.right) / 2f,
                    cy = (r.top + r.bottom) / 2f,
                    w = w,
                    h = h,
                )
            )
        }

        // ── Predict tracker positions to "now" before matching ──────────────
        // This way, a fast pan doesn't bias matching against the right tracker.
        for (t in trackedBlocks) {
            t.cx = t.predictCx(dtMs)
            t.cy = t.predictCy(dtMs)
        }

        // ── Cost matrix [detections × trackers] + assignment ────────────────
        val nd = detections.size
        val nt = trackedBlocks.size
        val matched = BooleanArray(nt)
        val newBlocks = ArrayList<TrackedBlockState>(0)

        if (nd > 0 && nt > 0) {
            val needed = nd * nt
            if (costBuf.size < needed) costBuf = FloatArray(needed)
            if (assignBuf.size < nd) assignBuf = IntArray(nd)
            val distCap = minImageDim * MAX_MATCH_DIST_FRACTION
            val distNorm = minImageDim
            for (i in 0 until nd) {
                val d = detections[i]
                for (j in 0 until nt) {
                    val t = trackedBlocks[j]
                    val dx = d.cx - t.cx
                    val dy = d.cy - t.cy
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist > distCap) {
                        costBuf[i * nt + j] = HungarianAssigner.INF
                        continue
                    }
                    val iouVal = iou(d, t)
                    val textSim = FuzzyMatcher.score(d.text, t.sourceText)
                    val textLenDiff = abs(d.text.length - t.sourceText.length)
                    // Hard reject when text disagrees strongly on longer strings
                    val textReject = textLenDiff >= 3 && textSim < 0.4f
                    if (textReject) {
                        costBuf[i * nt + j] = HungarianAssigner.INF
                        continue
                    }
                    val cost = 0.5f * (1f - iouVal) +
                               0.3f * (dist / distNorm) +
                               0.2f * (1f - textSim)
                    costBuf[i * nt + j] = cost
                }
            }
            HungarianAssigner.solve(costBuf, nd, nt, assignBuf, assignerScratch)

            for (i in 0 until nd) {
                val j = assignBuf[i]
                if (j < 0) {
                    newBlocks.add(newBlock(detections[i], nowNs))
                } else {
                    matched[j] = true
                    updateMatched(trackedBlocks[j], detections[i], dtMs, minImageDim)
                }
            }
        } else if (nd > 0) {
            for (d in detections) newBlocks.add(newBlock(d, nowNs))
        }

        // ── Compact-evict unmatched trackers (in-place over the prefix) ────
        var write = 0
        for (j in 0 until nt) {
            val t = trackedBlocks[j]
            if (!matched[j]) {
                t.missedFrames++
                t.stableFrameCount = 0
                t.vx *= 0.85f
                t.vy *= 0.85f
                t.displayAlpha = max(0f, t.displayAlpha - 0.18f)
                val ageMs = (nowNs - t.lastSeenNs) / 1_000_000L
                val stale = t.missedFrames > MAX_MISSED_FRAMES || ageMs > STALE_THRESHOLD_MS
                if (stale && t.displayAlpha <= 0.02f) continue   // drop
            }
            if (write != j) trackedBlocks[write] = t
            write++
        }
        // Trim the tail produced by compaction, then append newly-created blocks.
        while (trackedBlocks.size > write) trackedBlocks.removeAt(trackedBlocks.size - 1)
        trackedBlocks.addAll(newBlocks)

        // ── Request translation for newly-stable blocks ─────────────────────
        val srcLang = _uiState.value.sourceLang.mlKitCode
        val tgtLang = _uiState.value.targetLang.mlKitCode
        for (t in trackedBlocks) {
            if (t.stableFrameCount < STABLE_FRAMES_REQUIRED) continue
            if (t.sourceText.isBlank()) continue
            val key = cacheKey(srcLang, tgtLang, t.sourceText)
            val cached = translationCache[key]
            if (cached != null) {
                t.translatedText = cached
            } else if (pendingKeys.add(key)) {
                // .add() returns false if key already present → already queued
                translationRequests.trySend(TranslationRequest(t.sourceText, srcLang, tgtLang))
            }
        }

        // ── Publish snapshot ────────────────────────────────────────────────
        val publish = ArrayList<ArLensBlock>(trackedBlocks.size)
        for (t in trackedBlocks) {
            if (t.displayAlpha <= 0.02f && t.stableFrameCount < STABLE_FRAMES_REQUIRED) continue
            publish.add(
                ArLensBlock(
                    id = t.id,
                    originalText = t.sourceText,
                    translatedText = t.translatedText,
                    smoothedBox = RectF(t.left, t.top, t.right, t.bottom),
                    displayAlpha = t.displayAlpha,
                )
            )
        }
        _uiState.value = _uiState.value.copy(
            blocks = publish,
            imageEffectiveWidth = effectiveW,
            imageEffectiveHeight = effectiveH,
            isFrontCamera = isFrontCamera,
        )
    }

    // ── Tracker primitives ────────────────────────────────────────────────────

    private fun newBlock(d: Detection, nowNs: Long): TrackedBlockState {
        val t = TrackedBlockState(
            id = nextId.getAndIncrement(),
            cx = d.cx, cy = d.cy, w = d.w, h = d.h,
            sourceText = d.text,
        )
        t.lastMeasuredCx = d.cx
        t.lastMeasuredCy = d.cy
        t.lastSeenNs = nowNs
        return t
    }

    private fun updateMatched(t: TrackedBlockState, d: Detection, dtMs: Float, minImageDim: Float) {
        // Innovation Δ = measured - predicted; we already moved t to predicted above.
        val dx = d.cx - t.cx
        val dy = d.cy - t.cy
        val disp = sqrt(dx * dx + dy * dy)

        // Adaptive gain: tight matches snap (alpha→1), noisy matches smooth (alpha→0.3).
        val processNoise = minImageDim * 0.02f
        val gain = disp / (disp + processNoise + 1e-6f)
        val alpha = (0.3f + 0.7f * gain).coerceIn(0.3f, 0.95f)

        // Velocity update from finite difference
        if (dtMs > 1f) {
            val newVx = (d.cx - t.lastMeasuredCx) / dtMs
            val newVy = (d.cy - t.lastMeasuredCy) / dtMs
            // Low-pass smooth velocity to avoid jitter
            t.vx = 0.4f * t.vx + 0.6f * newVx
            t.vy = 0.4f * t.vy + 0.6f * newVy
            // Reset velocity if the displacement is huge — guard against bad matches.
            if (disp > minImageDim * 0.06f) { t.vx = 0f; t.vy = 0f }
        }
        t.cx += alpha * dx
        t.cy += alpha * dy
        // Smooth dimensions slowly — text width/height don't change frame-to-frame
        t.w  = 0.7f * t.w + 0.3f * d.w
        t.h  = 0.7f * t.h + 0.3f * d.h

        t.lastMeasuredCx = d.cx
        t.lastMeasuredCy = d.cy
        t.lastSeenNs = System.nanoTime()
        t.missedFrames = 0

        // Stability: saturating counter on small per-frame displacements.
        val stableDisp = disp < minImageDim * 0.012f
        t.stableFrameCount = if (stableDisp) {
            min(STABLE_FRAMES_REQUIRED + 2, t.stableFrameCount + 1)
        } else {
            max(0, t.stableFrameCount - 1)
        }

        // Fade in once stable
        val targetAlpha = if (t.stableFrameCount >= STABLE_FRAMES_REQUIRED) 1f else 0.3f
        t.displayAlpha += (targetAlpha - t.displayAlpha) * 0.30f
    }

    private fun iou(d: Detection, t: TrackedBlockState): Float {
        val ax0 = d.cx - d.w * 0.5f; val ax1 = d.cx + d.w * 0.5f
        val ay0 = d.cy - d.h * 0.5f; val ay1 = d.cy + d.h * 0.5f
        val bx0 = t.left; val bx1 = t.right
        val by0 = t.top;  val by1 = t.bottom
        val ix0 = max(ax0, bx0); val ix1 = min(ax1, bx1)
        val iy0 = max(ay0, by0); val iy1 = min(ay1, by1)
        if (ix1 <= ix0 || iy1 <= iy0) return 0f
        val inter = (ix1 - ix0) * (iy1 - iy0)
        val union = d.w * d.h + (bx1 - bx0) * (by1 - by0) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private data class Detection(
        val text: String,
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
    )

    // ── Translation pipeline ──────────────────────────────────────────────────

    private data class TranslationRequest(
        val text: String,
        val sourceLang: String,
        val targetLang: String,
    )

    private suspend fun handleTranslation(req: TranslationRequest) {
        val key = cacheKey(req.sourceLang, req.targetLang, req.text)
        // Skip if a newer language pair has invalidated this request.
        val currentSrc = _uiState.value.sourceLang.mlKitCode
        val currentTgt = _uiState.value.targetLang.mlKitCode
        if (req.sourceLang != currentSrc || req.targetLang != currentTgt) {
            inFlight.remove(key)
            return
        }
        try {
            val translated = translationRepo.translate(
                text = req.text,
                sourceLang = req.sourceLang,
                targetLang = req.targetLang,
            )
            // Re-check language pair after suspend resume to avoid stale writes.
            val nowSrc = _uiState.value.sourceLang.mlKitCode
            val nowTgt = _uiState.value.targetLang.mlKitCode
            if (req.sourceLang == nowSrc && req.targetLang == nowTgt) {
                translationCache[key] = translated
                applyTranslation(req.text, req.sourceLang, req.targetLang, translated)
            }
        } catch (e: TranslationError.ModelMissing) {
            _uiState.value = _uiState.value.copy(missingModelLang = e.languageCode)
        } catch (_: Exception) {
            // Network / unknown — silently skip; user may retry on next stable frame.
        } finally {
            inFlight.remove(key)
        }
    }

    private fun applyTranslation(sourceText: String, src: String, tgt: String, translated: String) {
        viewModelScope.launch(trackerDispatcher) {
            for (t in trackedBlocks) {
                if (t.sourceText == sourceText) t.translatedText = translated
            }
            // Re-publish so the renderer picks up the translation immediately.
            val snapshot = trackedBlocks.map { t ->
                ArLensBlock(
                    id = t.id,
                    originalText = t.sourceText,
                    translatedText = t.translatedText,
                    smoothedBox = RectF(t.left, t.top, t.right, t.bottom),
                    displayAlpha = t.displayAlpha,
                )
            }
            _uiState.value = _uiState.value.copy(blocks = snapshot)
            // Upsert to history (off-thread)
            launch(Dispatchers.IO) {
                runCatching { historyDao.upsert(sourceText, translated, src, tgt) }
            }
        }
    }

    private fun cacheKey(src: String, tgt: String, text: String): String =
        "$src|$tgt|$text"

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        translationRequests.close()
        cancelAllTranslations()
        analyzer.close()
        cameraController.stopCamera()
        translationRepo.close()
        speechController.shutdown()
        super.onCleared()
    }

    private companion object {
        const val ANALYZER_THROTTLE_MS = 66L      // ~15 fps
        const val STABLE_FRAMES_REQUIRED = 3
        const val MAX_MISSED_FRAMES = 6
        const val STALE_THRESHOLD_MS = 1500L
        const val MAX_MATCH_DIST_FRACTION = 0.12f  // fraction of min(effW,effH)
        const val MAX_CONCURRENT_TRANSLATIONS = 3
    }
}
