package com.app.ttsreader.viewmodel

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.R
import com.app.ttsreader.camera.CameraController
import com.app.ttsreader.data.HistoryRepository
import com.app.ttsreader.camera.TextAnalyzer
import com.app.ttsreader.data.SettingsRepository
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.error.AppError
import com.app.ttsreader.error.ErrorMapper
import com.app.ttsreader.network.NetworkMonitor
import com.app.ttsreader.ocr.OcrResult
import com.app.ttsreader.ocr.TextRecognitionRepository
import com.app.ttsreader.translate.TranslationRepository
import com.app.ttsreader.translate.TranslationState
import com.app.ttsreader.tts.SpeechController
import com.app.ttsreader.tts.TtsState
import com.app.ttsreader.utils.LanguageUtils
import com.app.ttsreader.camera.BoundingBoxSmoother
import com.app.ttsreader.camera.SmoothedBox
import com.app.ttsreader.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.text.BreakIterator
import androidx.compose.runtime.Stable
import com.app.ttsreader.review.InAppReviewManager

/**
 * Central orchestrator for the entire TTS Reader screen.
 *
 * ## Resource ownership
 *   MainViewModel
 *     ├── CameraController          (ProcessCameraProvider + analysis executor)
 *     ├── TextAnalyzer              (ML Kit TextRecognizer)
 *     ├── TextRecognitionRepository (StateFlow<OcrResult>)
 *     ├── TranslationRepository     (ML Kit Translator + StateFlow<TranslationState>)
 *     ├── SpeechController          (Android TextToSpeech)
 *     ├── NetworkMonitor            (ConnectivityManager callbacks)
 *     └── SettingsRepository        (DataStore preferences)
 *
 * ## Flow pipelines
 *
 * 1. OCR display     — instant update on every frame result
 * 2. Translation     — debounced 800ms, retries with backoff on infrastructure failure
 * 3. Translation UI  — maps TranslationState to UI loading booleans
 * 4. TTS state       — maps TtsState to UI booleans + error
 * 5. TTS sentence    — tracks currentSentenceIndex for highlighting
 * 6. Network         — maps online/offline to isOffline flag
 * 7. Voice settings  — observes speechRate/pitch from DataStore
 *
 * ## Error strategy
 * All errors are classified via [ErrorMapper] into typed [AppError] values.
 * [MainUiState.error] carries the current error; [MainUiState.isErrorRetryable]
 * tells the UI whether to show "Retry" vs "Dismiss".
 * [retryTranslation] re-attempts translation with the currently recognised text.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrRepository         = TextRecognitionRepository()
    private val translationRepository = TranslationRepository()
    private val cameraController      = CameraController(application)
    private val textAnalyzer          = TextAnalyzer(
        context            = application,
        repository         = ocrRepository,
        throttleIntervalMs = 500L
    )
    private val speechController      = SpeechController(application)
    private val networkMonitor        = NetworkMonitor(application)
    private val settingsRepository    = SettingsRepository(application)
    private val historyRepository     = HistoryRepository(application)
    private val boundingBoxSmoother   = BoundingBoxSmoother()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Emits once when OCR first detects text (Empty → Success transition). */
    private val _ocrLockHaptic = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ocrLockHaptic: SharedFlow<Unit> = _ocrLockHaptic.asSharedFlow()

    private var hadTextPreviously = false
    private var sleepTimerJob: Job? = null
    /**
     * Cancellable one-shot job that force-expires persisted bounding boxes.
     *
     * ## Why this exists
     * [TextRecognitionRepository] uses a [StateFlow], which suppresses duplicate emissions
     * via value equality. [OcrResult.Empty] is a `data object`, so once the camera first
     * sees blank and emits [OcrResult.Empty], every subsequent blank frame is **silently
     * dropped** by the StateFlow — [observeOcrBoundingBoxes] is called exactly once.
     *
     * The [BoundingBoxSmoother] has an internal 500 ms persistence window, but that
     * window is only checked when [BoundingBoxSmoother.update] is called. Because the
     * StateFlow stops emitting, [update] is never called again, so boxes that should
     * have expired at T+500 ms remain frozen on screen indefinitely.
     *
     * The fix: on the first [OcrResult.Empty] (or [OcrResult.Error]) emission, schedule
     * a one-shot job that fires 50 ms after the persistence window to force-clear the
     * smoother. Cancel the job if a new [OcrResult.Success] arrives before it fires
     * (meaning the camera found text again and the boxes should stay).
     */
    private var boxExpiryJob: Job? = null

    // #region agent log
    private fun dbgLog(
        hypothesisId: String,
        location: String,
        message: String,
        data: JSONObject = JSONObject()
    ) {
        // NOTE: We send logs to the local debug ingest server so they appear in the
        // workspace log file `debug-863fc4.log` (host machine).
        //
        // Host bridging differs by runtime:
        // - Android Emulator: 10.0.2.2 -> host 127.0.0.1
        // - Genymotion:      10.0.3.2 -> host 127.0.0.1
        // - Physical device: requires host LAN IP (we can't know it here), so this
        //   likely won't work unless port-reversed. We still emit Logcat breadcrumbs.
        //
        // Gated on BuildConfig.DEBUG so release builds never open sockets or leak
        // telemetry to localhost:7761.
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = JSONObject()
                    .put("sessionId", "863fc4")
                    .put("runId", "pre-fix")
                    .put("hypothesisId", hypothesisId)
                    .put("location", location)
                    .put("message", message)
                    .put("data", data)
                    .put("timestamp", System.currentTimeMillis())

                val hosts = listOf("10.0.2.2", "10.0.3.2", "127.0.0.1")
                var sent = false
                var lastError: String? = null
                for (host in hosts) {
                    if (sent) break
                    try {
                        val url = URL("http://$host:7761/ingest/5fbd15c2-99fd-4a60-b00d-4e87f92f9ede")
                        (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            doOutput = true
                            connectTimeout = 400
                            readTimeout = 400
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("X-Debug-Session-Id", "863fc4")
                            outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                            inputStream.close() // force send
                            disconnect()
                        }
                        sent = true
                        Log.d("Dbg863fc4", "sent_to=$host msg=$message loc=$location")
                    } catch (e: Exception) {
                        lastError = "${e::class.java.simpleName}:${(e.message ?: "").take(80)}"
                    }
                }
                if (!sent) {
                    Log.w("Dbg863fc4", "send_failed hosts=$hosts err=$lastError loc=$location msg=$message")
                }
            } catch (_: Exception) {
                // never crash app for debug logging
            }
        }
    }
    // #endregion

    init {
        observeOcrResults()
        observeOcrBoundingBoxes()
        observeTranslationState()
        startTranslationPipeline()
        observeTtsState()
        observeSentenceIndex()
        observeWordRange()
        observeNetwork()
        observePersistedLanguages()
        observeVoiceSettings()
        refreshDownloadedModels()
    }

    // ── Pipeline 1: OCR display — 3-stage noise reduction ────────────────────────
    //
    //  Stage 1 — Lexical Sanitization  : strip rogue symbols and garbage tokens
    //  Stage 2 — Similarity Threshold  : drop updates ≥85% similar to current text
    //  Stage 3 — Temporal Debounce     : wait for the OCR signal to settle (500 ms)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeOcrResults() {
        ocrRepository.result
            // ── Stage 3: Temporal debounce ─────────────────────────────────────
            // StateFlow is conflated, so rapid camera-shake frames are already
            // collapsed to one value. Debounce then requires 500 ms of silence
            // before the settled result propagates — eliminates transient blur frames.
            .debounce(500L)

            // ── Stage 1: Lexical sanitization ──────────────────────────────────
            // Run the sanitizer on every Success before anything downstream sees it.
            // Garbage-only results are collapsed to Empty so the UI stays blank
            // rather than flashing noise.
            .map { result ->
                when (result) {
                    is OcrResult.Success -> {
                        val clean = sanitizeOcrText(result.fullText)
                        dbgLog(
                            hypothesisId = "A",
                            location = "MainViewModel.observeOcrResults:map",
                            message = "sanitizeOcrText applied to Success.fullText",
                            data = JSONObject()
                                .put("rawLen", result.fullText.length)
                                .put("cleanLen", clean.length)
                                .put("cleanBlank", clean.isBlank())
                        )
                        if (clean.isBlank()) OcrResult.Empty
                        else result.copy(fullText = clean)
                    }
                    else -> result
                }
            }

            .onEach { result ->
                when (result) {
                    is OcrResult.Success -> {
                        // ── Stage 2: Similarity threshold ──────────────────────
                        // If the incoming text is ≥85% similar to what is already
                        // on screen (word-set Jaccard), it is a micro-update from
                        // camera shake — drop it and keep the stable text.
                        val current = _uiState.value.recognizedText
                        if (isSimilarEnough(current, result.fullText)) return@onEach

                        if (!hadTextPreviously) _ocrLockHaptic.tryEmit(Unit)
                        hadTextPreviously = true

                        val textChanged = current != result.fullText
                        _uiState.value = _uiState.value.copy(
                            recognizedText      = result.fullText,
                            resumeSentenceIndex = if (textChanged) -1
                                                  else _uiState.value.resumeSentenceIndex
                        )

                        dbgLog(
                            hypothesisId = "C",
                            location = "MainViewModel.observeOcrResults:onEach",
                            message = "OCR Success applied to uiState.recognizedText",
                            data = JSONObject()
                                .put("recognizedLen", result.fullText.length)
                                .put("textChanged", textChanged)
                        )
                    }
                    is OcrResult.Empty -> {
                        hadTextPreviously = false
                        _uiState.value = _uiState.value.copy(
                            recognizedText = "",
                            translatedText = ""
                        )
                        dbgLog(
                            hypothesisId = "B",
                            location = "MainViewModel.observeOcrResults:onEach",
                            message = "OCR Empty cleared recognizedText/translatedText",
                            data = JSONObject()
                                .put("cleared", true)
                        )
                    }
                    is OcrResult.Error -> setError(AppError.OcrFailed(result.exception))
                }
            }
            .catch { e -> setError(AppError.OcrFailed(e)) }
            .launchIn(viewModelScope)
    }

    // ── Pipeline 1b: Bounding boxes — stabilised, no debounce ────────────────────
    //
    // This flow collects from the raw OCR source WITHOUT the 500 ms text debounce,
    // so the BoundingBoxSmoother receives every frame and can apply EMA frame-by-frame.
    // Text content (pipeline 1) and visual boxes are intentionally decoupled:
    //   • Text waits for a stable signal before updating the reading card.
    //   • Boxes track the live camera in real-time, just smoothed.

    private fun observeOcrBoundingBoxes() {
        ocrRepository.result
            .onEach { result ->
                when (result) {
                    is OcrResult.Success -> {
                        // Text detected — cancel any pending expiry and update smoother
                        boxExpiryJob?.cancel()
                        boxExpiryJob = null
                        val smoothed = boundingBoxSmoother.update(result.words)
                        _uiState.value = _uiState.value.copy(smoothedBoxes = smoothed)
                    }
                    else -> {
                        // No text / error:
                        // 1. Run the smoother once so the persistence timestamps are current.
                        val smoothed = boundingBoxSmoother.update(emptyList())
                        _uiState.value = _uiState.value.copy(smoothedBoxes = smoothed)

                        // 2. Schedule a guaranteed force-clear after the persistence window.
                        //    StateFlow won't re-emit OcrResult.Empty if the value hasn't
                        //    changed, so without this job the smoother would never be asked
                        //    to expire its tracked boxes and they'd stay on screen forever.
                        if (boxExpiryJob?.isActive != true) {
                            boxExpiryJob = viewModelScope.launch {
                                delay(BoundingBoxSmoother.PERSISTENCE_MS + 50L)
                                boundingBoxSmoother.clear()
                                _uiState.value = _uiState.value.copy(smoothedBoxes = emptyList())
                            }
                        }
                    }
                }
            }
            .catch { /* silently ignore — visual-only, not worth surfacing an error */ }
            .launchIn(viewModelScope)
    }

    /**
     * Stage 1 — Lexical sanitizer.
     *
     * Pass 1: strip rogue formatting characters globally ([]{} | \ ^ ~ `).
     * Pass 2: tokenize each line; keep a token only if it contains at least
     *         one Unicode letter OR is a multi-digit number (years, page refs, etc.).
     * Pass 3: drop lines that have no letters at all after token filtering.
     */
    private fun sanitizeOcrText(raw: String): String {
        val stripped = raw
            .replace(Regex("""[\[\]{}|\\^~`]"""), " ")   // rogue formatting chars
            .replace(Regex("""\s{2,}"""), " ")             // collapse runs of spaces

        return stripped.lines()
            .mapNotNull { line ->
                val tokens = line.trim()
                    .split(Regex("\\s+"))
                    .filter { token ->
                        token.isNotBlank() && (
                            token.any { it.isLetter() }          // has ≥1 letter
                            || token.matches(Regex("\\d{2,}"))   // or multi-digit number
                        )
                    }
                val clean = tokens.joinToString(" ")
                if (clean.none { it.isLetter() }) null else clean  // drop letter-free lines
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Stage 2 — Similarity gate (word-set Jaccard index).
     *
     * Returns true (→ drop the update) when [next] is ≥ [threshold] similar to
     * [prev], meaning it is a micro-variation rather than new content.
     *
     * Fast-path: if the two strings differ in length by >40% they cannot be
     * ≥85% similar — skip the Jaccard computation entirely.
     */
    // Cache of the previously-tokenized `prev` string so we avoid re-running
    // lowercase()+split(Regex)+filter{}+toSet() on the same value every frame.
    // Invalidated whenever the prev string identity changes.
    private var cachedPrevForSim: String? = null
    private var cachedPrevTokens: Set<String> = emptySet()
    private val whitespaceRegex = Regex("\\s+")

    private fun isSimilarEnough(prev: String, next: String, threshold: Float = 0.85f): Boolean {
        if (prev == next) return true
        if (prev.isBlank() || next.isBlank()) return false

        // Fast length divergence check
        val minLen = minOf(prev.length, next.length)
        val maxLen = maxOf(prev.length, next.length)
        if (minLen.toFloat() / maxLen < 0.60f) return false

        // Word-set Jaccard: |A ∩ B| / |A ∪ B|
        // Reuse the tokenized prev set across calls — it only changes when the
        // recognized text itself changes, which is much rarer than frame cadence.
        val prevWords = if (cachedPrevForSim == prev) {
            cachedPrevTokens
        } else {
            val computed = prev.lowercase().split(whitespaceRegex).filter { it.isNotBlank() }.toSet()
            cachedPrevForSim = prev
            cachedPrevTokens = computed
            computed
        }
        val nextWords = next.lowercase().split(whitespaceRegex).filter { it.isNotBlank() }.toSet()
        if (prevWords.isEmpty() || nextWords.isEmpty()) return false

        val intersection = prevWords.intersect(nextWords).size.toFloat()
        val union        = prevWords.union(nextWords).size.toFloat()
        return (intersection / union) >= threshold
    }

    // ── Pipeline 2: Debounced translation with pipeline restart ─────────────────

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startTranslationPipeline() {
        ocrRepository.result
            // Map every result to the cleaned text, or null if there's nothing translatable.
            // Null acts as a reset signal: when Empty fires and then Success returns with the
            // same text, distinctUntilChanged sees null→"text" as a change and re-fires.
            // Without this, after Empty clears translatedText the pipeline would see the same
            // fullText value as before and distinctUntilChanged would silently block it, leaving
            // the translation section blank even though the camera is back on text.
            .map { result ->
                when (result) {
                    is OcrResult.Success -> {
                        val clean = sanitizeOcrText(result.fullText).ifBlank { null }
                        dbgLog(
                            hypothesisId = "A",
                            location = "MainViewModel.startTranslationPipeline:map",
                            message = "Mapped OCR result to translatable text (nullable)",
                            data = JSONObject()
                                .put("resultType", "Success")
                                .put("rawLen", result.fullText.length)
                                .put("outIsNull", clean == null)
                                .put("outLen", clean?.length ?: 0)
                        )
                        clean
                    }
                    else -> {
                        dbgLog(
                            hypothesisId = "B",
                            location = "MainViewModel.startTranslationPipeline:map",
                            message = "Mapped non-Success OCR result to null reset",
                            data = JSONObject().put("resultType", result::class.java.simpleName)
                        )
                        null
                    }
                }
            }
            // Order matters: filterNotNull() must come BEFORE distinctUntilChanged()
            // so that OcrResult.Empty (mapped to null) resets the chain. If distinct
            // ran first, consecutive null→null would collapse and a later Success with
            // the same text as before would be silently swallowed.
            .filterNotNull()                // null values don't reach the debounce timer
            .distinctUntilChanged()
            .onEach { mapped ->
                dbgLog(
                    hypothesisId = "B",
                    location = "MainViewModel.startTranslationPipeline:distinct",
                    message = "Post-distinct emission",
                    data = JSONObject()
                        .put("len", mapped.length)
                )
            }
            .debounce(800L)
            .onEach { text -> translateText(text) }
            .retryWhen { cause, attempt ->
                // Restart the pipeline on infrastructure errors with exponential backoff.
                // CancellationException must NOT be retried — it means the scope is cancelled.
                if (cause is CancellationException) return@retryWhen false
                val backoffMs = (1000L * (attempt + 1)).coerceAtMost(5000L)
                delay(backoffMs)
                true
            }
            .catch { e ->
                // Only fires if retryWhen returns false (CancellationException or internal error).
                // CancellationException during scope cancellation is expected — don't surface it.
                if (e !is CancellationException) {
                    setError(ErrorMapper.fromTranslationException(e))
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun translateText(text: String) {
        dbgLog(
            hypothesisId = "D",
            location = "MainViewModel.translateText:entry",
            message = "translateText called",
            data = JSONObject().put("textLen", text.length)
        )
        if (text.isBlank()) {
            dbgLog(
                hypothesisId = "C",
                location = "MainViewModel.translateText:early",
                message = "translateText early-return: blank",
                data = JSONObject()
            )
            return
        }
        val source = _uiState.value.sourceLanguage
        val target = _uiState.value.targetLanguage
        if (source.mlKitCode == target.mlKitCode) {
            dbgLog(
                hypothesisId = "C",
                location = "MainViewModel.translateText:early",
                message = "translateText early-return: source==target",
                data = JSONObject()
                    .put("source", source.mlKitCode)
                    .put("target", target.mlKitCode)
            )
            return
        }

        runCatching {
            translationRepository.translate(
                text = text,
                sourceLang = source.mlKitCode,
                targetLang = target.mlKitCode
            )
        }.onSuccess { translated ->
            _uiState.value = _uiState.value.copy(translatedText = translated)
            dbgLog(
                hypothesisId = "D",
                location = "MainViewModel.translateText:success",
                message = "Translation succeeded; translatedText updated",
                data = JSONObject()
                    .put("translatedLen", translated.length)
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
            setError(ErrorMapper.fromTranslationException(e))
            dbgLog(
                hypothesisId = "D",
                location = "MainViewModel.translateText:failure",
                message = "Translation failed",
                data = JSONObject()
                    .put("exception", e::class.java.simpleName)
                    .put("msg", (e.message ?: "").take(160))
            )
        }
    }

    // ── Pipeline 3: Translation state → UI booleans ──────────────────────────────

    private fun observeTranslationState() {
        translationRepository.state
            .onEach { translState ->
                _uiState.value = _uiState.value.copy(
                    isModelDownloading = translState is TranslationState.DownloadingModel,
                    isTranslating      = translState is TranslationState.Translating
                )
                if (translState is TranslationState.Idle) {
                    refreshDownloadedModels()
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Pipeline 4: TTS state → UI booleans ──────────────────────────────────────

    private fun observeTtsState() {
        speechController.state
            .onEach { ttsState ->
                when (ttsState) {
                    is TtsState.Error -> setError(AppError.TtsInitFailed(Exception(ttsState.message)))
                    else -> _uiState.value = _uiState.value.copy(
                        isSpeaking = ttsState is TtsState.Speaking,
                        ttsReady   = ttsState is TtsState.Ready || ttsState is TtsState.Speaking
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Pipeline 5: TTS sentence index (for highlighting) ───────────────────────

    private fun observeSentenceIndex() {
        speechController.currentSentenceIndex
            .onEach { index ->
                _uiState.value = _uiState.value.copy(
                    currentSentenceIndex = index,
                    // Save resume point whenever a sentence is actively being spoken
                    resumeSentenceIndex = if (index >= 0) index
                        else _uiState.value.resumeSentenceIndex
                )
            }
            .launchIn(viewModelScope)
    }

    // ── Pipeline 5b: Word range (API 26+ word-level highlighting) ────────────────

    private fun observeWordRange() {
        speechController.currentWordRange
            .onEach { (start, end) ->
                _uiState.value = _uiState.value.copy(
                    currentWordStart = start,
                    currentWordEnd = end
                )
            }
            .launchIn(viewModelScope)
    }

    // ── Pipeline 6: Network connectivity ─────────────────────────────────────────

    private fun observeNetwork() {
        networkMonitor.isOnline
            .onEach { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
            .launchIn(viewModelScope)
    }

    // ── Pipeline 7: Voice settings (speech rate & pitch) ─────────────────────────

    private fun observeVoiceSettings() {
        settingsRepository.speechRate
            .distinctUntilChanged()
            .onEach { rate ->
                _uiState.value = _uiState.value.copy(speechRate = rate)
                speechController.setSpeechRate(rate)
            }
            .launchIn(viewModelScope)

        settingsRepository.pitch
            .distinctUntilChanged()
            .onEach { pitch ->
                _uiState.value = _uiState.value.copy(pitch = pitch)
                speechController.setPitch(pitch)
            }
            .launchIn(viewModelScope)
    }

    // ── Camera ───────────────────────────────────────────────────────────────────

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            runCatching {
                cameraController.startCamera(lifecycleOwner, previewView, textAnalyzer)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                setError(AppError.CameraError(e))
            }
        }
    }

    // ── Torch (flashlight) ────────────────────────────────────────────────────

    fun toggleTorch() {
        val newState = !_uiState.value.isTorchOn
        _uiState.value = _uiState.value.copy(isTorchOn = newState)
        cameraController.enableTorch(newState)
    }

    // ── TTS: Sentence-aware playback ────────────────────────────────────────────

    /**
     * Splits the text into sentences and begins playback.
     * Applies the target language locale to the TTS engine first.
     * Automatically sets [MainUiState.sentences] and [MainUiState.totalSentences].
     */
    fun speak() {
        val state = _uiState.value
        val textToSpeak = state.translatedText.ifBlank { state.recognizedText }
        if (textToSpeak.isBlank()) return

        val supported = speechController.setLanguage(state.targetLanguage.locale)
        if (!supported) {
            setError(AppError.TtsUnsupportedLanguage(state.targetLanguage.displayName))
            return
        }

        val sentences = splitIntoSentences(textToSpeak)

        // Resume from last position if sentences match (e.g. after phone call interrupt)
        val resumeIndex = if (state.resumeSentenceIndex >= 0 &&
            state.sentences == sentences &&
            state.resumeSentenceIndex < sentences.size
        ) {
            state.resumeSentenceIndex
        } else {
            0
        }

        _uiState.value = _uiState.value.copy(
            sentences = sentences,
            totalSentences = sentences.size,
            resumeSentenceIndex = -1  // consumed — clear it
        )
        speechController.speakSentences(sentences, resumeIndex)

        // Save to history and increment review counter when starting playback.
        if (resumeIndex == 0) {
            viewModelScope.launch {
                runCatching {
                    historyRepository.saveScan(
                        recognizedText = state.recognizedText,
                        translatedText = state.translatedText,
                        sourceLanguageCode = state.sourceLanguage.mlKitCode,
                        targetLanguageCode = state.targetLanguage.mlKitCode
                    )
                }
            }
            // Track usage for the in-app review prompt threshold.
            InAppReviewManager.incrementSpeakCount(getApplication())
        }
    }

    fun stopSpeaking() {
        speechController.stop()
    }

    /**
     * Plays the next sentence in the current text.
     * No-op if already at the last sentence or no sentences loaded.
     */
    fun nextSentence() {
        val state = _uiState.value
        val nextIndex = state.currentSentenceIndex + 1
        if (nextIndex < state.sentences.size) {
            val supported = speechController.setLanguage(state.targetLanguage.locale)
            if (supported) {
                speechController.speakSentences(state.sentences, nextIndex)
            }
        }
    }

    /**
     * Plays the previous sentence in the current text.
     * Clamps to index 0 if before the first sentence.
     */
    fun previousSentence() {
        val state = _uiState.value
        val prevIndex = (state.currentSentenceIndex - 1).coerceAtLeast(0)
        if (state.sentences.isNotEmpty()) {
            val supported = speechController.setLanguage(state.targetLanguage.locale)
            if (supported) {
                speechController.speakSentences(state.sentences, prevIndex)
            }
        }
    }

    /**
     * Adjusts the TTS speech rate.
     * Persists to DataStore for future sessions.
     *
     * @param rate Speed multiplier: 0.5f (half), 1.0f (normal), 2.0f (double), etc.
     */
    fun setSpeechRate(rate: Float) {
        _uiState.value = _uiState.value.copy(speechRate = rate)
        speechController.setSpeechRate(rate)
        viewModelScope.launch { settingsRepository.setSpeechRate(rate) }
    }

    /**
     * Adjusts the TTS voice pitch.
     * Persists to DataStore for future sessions.
     *
     * @param pitch Pitch multiplier: 0.5f (lower), 1.0f (normal), 2.0f (higher), etc.
     */
    fun setPitch(pitch: Float) {
        _uiState.value = _uiState.value.copy(pitch = pitch)
        speechController.setPitch(pitch)
        viewModelScope.launch { settingsRepository.setPitch(pitch) }
    }

    /**
     * Sets a sleep timer that automatically stops playback after N minutes.
     * Cancels any previous sleep timer.
     *
     * Uses [viewModelScope] to ensure the timer is cancelled if the app is killed
     * or the ViewModel is cleared, preventing CPU wake and memory leaks.
     *
     * @param minutes Duration in minutes. Pass 0 to disable the timer.
     */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = minutes,
            sleepTimerRemainingSeconds = minutes * 60
        )

        if (minutes <= 0) return

        sleepTimerJob = viewModelScope.launch {
            var remainingSeconds = minutes * 60
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds--
                _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = remainingSeconds)
            }
            // Timer finished — stop playback
            stopSpeaking()
            _uiState.value = _uiState.value.copy(
                sleepTimerMinutes = 0,
                sleepTimerRemainingSeconds = 0
            )
        }
    }

    /**
     * Cancels the active sleep timer, if any.
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = 0,
            sleepTimerRemainingSeconds = 0
        )
    }

    // ── Sentence splitting ──────────────────────────────────────────────────────

    /**
     * Splits text into sentences using [BreakIterator.getSentenceInstance].
     * Returns at least one element (the full text if splitting fails).
     */
    private fun splitIntoSentences(text: String): List<String> {
        return try {
            val iterator = BreakIterator.getSentenceInstance()
            iterator.setText(text)
            val sentences = mutableListOf<String>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                val sentence = text.substring(start, end).trim()
                if (sentence.isNotBlank()) {
                    sentences.add(sentence)
                }
                start = end
                end = iterator.next()
            }
            sentences.ifEmpty { listOf(text) }
        } catch (e: Exception) {
            // Fallback to full text if sentence splitting fails
            listOf(text)
        }
    }

    // ── Pipeline 8: Persisted language preferences ─────────────────────────────

    private fun observePersistedLanguages() {
        settingsRepository.sourceLanguage
            .distinctUntilChanged()
            .onEach { lang ->
                if (lang != _uiState.value.sourceLanguage) {
                    _uiState.value = _uiState.value.copy(sourceLanguage = lang, translatedText = "")
                    retranslateCurrentText()
                }
            }
            .launchIn(viewModelScope)

        settingsRepository.targetLanguage
            .distinctUntilChanged()
            .onEach { lang ->
                if (lang != _uiState.value.targetLanguage) {
                    _uiState.value = _uiState.value.copy(targetLanguage = lang, translatedText = "")
                    retranslateCurrentText()
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Language selection ────────────────────────────────────────────────────────

    fun setSourceLanguage(language: AppLanguage) {
        _uiState.value = _uiState.value.copy(sourceLanguage = language, translatedText = "")
        viewModelScope.launch { settingsRepository.setSourceLanguage(language) }
        retranslateCurrentText()
    }

    fun setTargetLanguage(language: AppLanguage) {
        _uiState.value = _uiState.value.copy(targetLanguage = language, translatedText = "")
        viewModelScope.launch { settingsRepository.setTargetLanguage(language) }
        retranslateCurrentText()
    }

    /**
     * Swaps source and target languages in a single atomic update.
     * Persists both to DataStore and triggers retranslation.
     */
    fun swapLanguages() {
        val state = _uiState.value
        val newSource = state.targetLanguage
        val newTarget = state.sourceLanguage
        _uiState.value = state.copy(
            sourceLanguage = newSource,
            targetLanguage = newTarget,
            translatedText = ""
        )
        viewModelScope.launch {
            settingsRepository.setSourceLanguage(newSource)
            settingsRepository.setTargetLanguage(newTarget)
        }
        retranslateCurrentText()
    }

    private fun retranslateCurrentText() {
        val text = _uiState.value.recognizedText
        if (text.isBlank()) return
        viewModelScope.launch { translateText(text) }
    }

    // ── Error handling ────────────────────────────────────────────────────────────

    /**
     * Retries the last failed translation using the currently recognised text.
     * Called from the "Retry" snackbar action in the UI.
     */
    fun retryTranslation() {
        dismissError()
        retranslateCurrentText()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(
            error = null,
            isErrorRetryable = false
        )
    }

    private fun setError(appError: AppError) {
        val message = ErrorMapper.toUserMessage(appError, getApplication())
        _uiState.value = _uiState.value.copy(
            error            = message,
            isErrorRetryable = appError.isRetryable
        )
    }

    // ── Downloaded models ────────────────────────────────────────────────────────

    private fun refreshDownloadedModels() {
        viewModelScope.launch {
            runCatching {
                val codes = translationRepository.getDownloadedModelCodes()
                _uiState.value = _uiState.value.copy(downloadedModelCodes = codes)
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    /**
     * Called when Classic TTS screen leaves composition.
     * Stops camera, silences TTS, and cancels in-flight jobs so nothing
     * runs in the background while another mode is active.
     * Everything restarts automatically when the screen re-enters composition.
     */
    fun pause() {
        cameraController.pauseCamera()
        speechController.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        boxExpiryJob?.cancel()
        boxExpiryJob = null
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        boxExpiryJob?.cancel()
        boxExpiryJob = null
        cameraController.stopCamera()
        textAnalyzer.close()
        translationRepository.close()
        ocrRepository.clear()
        boundingBoxSmoother.clear()
        speechController.shutdown()
    }
}

/**
 * Immutable snapshot of the entire screen's UI state.
 *
 * Annotated [@Stable] (not [@Immutable]) because [List] and [Set] fields are not
 * Compose-verifiable as structurally immutable. The object is always replaced
 * atomically via StateFlow.value so structural equality comparisons are correct.
 */
@Stable
data class MainUiState(
    // OCR
    val recognizedText: String = "",
    val smoothedBoxes: List<SmoothedBox> = emptyList(),

    // Translation
    val translatedText: String = "",
    val isTranslating: Boolean = false,
    val isModelDownloading: Boolean = false,

    // Language selection
    val sourceLanguage: AppLanguage = LanguageUtils.DEFAULT_SOURCE,
    val targetLanguage: AppLanguage = LanguageUtils.DEFAULT_TARGET,
    val availableLanguages: List<AppLanguage> = LanguageUtils.SUPPORTED_LANGUAGES,
    val downloadedModelCodes: Set<String> = emptySet(),

    // TTS
    val isSpeaking: Boolean = false,
    val ttsReady: Boolean = false,

    // Voice & Playback
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,

    // Sentence-aware playback
    val sentences: List<String> = emptyList(),
    val totalSentences: Int = 0,
    val currentSentenceIndex: Int = -1,

    // Word-level highlighting (API 26+ only, from onRangeStart)
    val currentWordStart: Int = -1,
    val currentWordEnd: Int = -1,

    // Resume playback (saved when TTS is interrupted, e.g. phone call)
    val resumeSentenceIndex: Int = -1,

    // Sleep timer
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0,

    // Camera
    val isTorchOn: Boolean = false,

    // Network
    val isOffline: Boolean = false,

    // Errors
    val error: String? = null,
    val isErrorRetryable: Boolean = false
)
