package com.app.ttsreader.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.ai.GemmaModelManager
import com.app.ttsreader.ai.GemmaModelState
import com.app.ttsreader.ai.GemmaSummarizer
import com.app.ttsreader.data.LibraryRepository
import com.app.ttsreader.data.local.BookEntity
import com.app.ttsreader.data.local.ReaderPrefsKeys
import com.app.ttsreader.data.local.readerDataStore
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.pdf.PdfTextExtractor
import com.app.ttsreader.translate.TranslationRepository
import com.app.ttsreader.tts.SpeechController
import com.app.ttsreader.tts.TtsState
import com.app.ttsreader.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

// ── Reader theme ──────────────────────────────────────────────────────────────

enum class ReaderTheme(val label: String) {
    DARK("Dark"),
    PARCHMENT("Paper"),
    LIGHT("Light");

    val bgColor: Color
        get() = when (this) {
            DARK      -> Color(0xFF0A1E30)   // dark navy — matches aurora base
            PARCHMENT -> Color(0xFFF5E6C8)
            LIGHT     -> Color(0xFFF8F8F8)
        }

    val textColor: Color
        get() = when (this) {
            DARK      -> Color.White
            PARCHMENT -> Color(0xFF3D2B1F)
            LIGHT     -> Color(0xFF1A1A1A)
        }

    val surfaceColor: Color
        get() = when (this) {
            DARK      -> Color.White.copy(alpha = 0.08f)   // glass surface
            PARCHMENT -> Color(0xFFEDD9B0)
            LIGHT     -> Color(0xFFEAEAEA)
        }

    val dimTextColor: Color  get() = textColor.copy(alpha = 0.60f)
    val borderColor: Color   get() = textColor.copy(alpha = 0.22f)

    val sentenceHighlight: Color
        get() = when (this) {
            DARK      -> Color.White.copy(alpha = 0.10f)
            PARCHMENT -> Color(0xFF8B6914).copy(alpha = 0.20f)
            LIGHT     -> Color(0xFF0066CC).copy(alpha = 0.13f)
        }

    val wordHighlight: Color
        get() = when (this) {
            DARK      -> Color(0xFF66FFD1).copy(alpha = 0.35f)  // cyan glass accent
            PARCHMENT -> Color(0xFF8B6914).copy(alpha = 0.42f)
            LIGHT     -> Color(0xFF0066CC).copy(alpha = 0.32f)
        }
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class ReaderUiState(
    // Book / page
    val book: BookEntity?          = null,
    val totalPages: Int            = 0,
    val currentPage: Int           = 0,
    val rawText: String            = "",
    val translatedText: String     = "",
    val isLoadingText: Boolean     = false,
    val isTranslating: Boolean     = false,
    // Display mode
    val showTranslation: Boolean   = true,
    val sourceLang: AppLanguage    = LanguageUtils.DEFAULT_SOURCE,
    val targetLang: AppLanguage    = LanguageUtils.DEFAULT_TARGET,
    val isPickingLanguage: Boolean = false,
    val error: String?             = null,
    // Step 3 — Typography
    val fontSize: Int                = 16,
    val readerTheme: ReaderTheme     = ReaderTheme.DARK,
    val showTypographyPanel: Boolean = false,
    // Step 4 — TTS
    val ttsState: TtsState           = TtsState.Initializing,
    val sentences: List<String>      = emptyList(),
    val currentSentenceIndex: Int    = -1,
    val currentWordStart: Int        = -1,
    val currentWordEnd: Int          = -1,
    val speechRate: Float            = 1.0f,
    // Step 5 — On-device Gemma AI Summarization
    val gemmaModelState: GemmaModelState     = GemmaModelState.NotDownloaded,
    val showGemmaDownloadPrompt: Boolean     = false,
    val isSummarizing: Boolean               = false,
    val summary: String                      = "",
    val summaryError: String?                = null,
    val showSummary: Boolean                 = false
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Manages PDF text extraction, JIT translation, typography preferences, and TTS
 * read-aloud with sentence/word highlighting.
 *
 * ## Typography persistence
 * Font size and reading theme are stored in a dedicated DataStore ("reader_prefs")
 * and reloaded on each ViewModel init via a single [first] suspend call.
 *
 * ## TTS pipeline
 * [readAloud] splits the currently visible text (original or translated) into
 * sentences and hands them to [SpeechController.speakSentences].  The controller
 * auto-advances sentences and emits [SpeechController.currentSentenceIndex] and
 * [SpeechController.currentWordRange] flows that are collected here and reflected
 * in [uiState] for the UI to render highlights.
 *
 * ## Thread safety
 * [rawCache] is accessed only inside [viewModelScope] coroutines.
 * [translationCache] / [translationPending] are [ConcurrentHashMap]s.
 * TTS state flows are collected on the default dispatcher and update [_uiState]
 * which is itself a [MutableStateFlow] (thread-safe value holder).
 */
class ReaderViewModel(
    application: Application,
    private val bookId: Long
) : AndroidViewModel(application) {

    private val translationRepo    = TranslationRepository()
    private val libraryRepo        = LibraryRepository(application)
    private val speechController   = SpeechController(application)

    private val rawCache: MutableMap<Int, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>) = size > 50
        }
    )
    private val translationCache: MutableMap<Int, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>) = size > 50
        }
    )
    private val translationPending = ConcurrentHashMap<Int, Boolean>()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        // Initialise on-device Gemma model manager (checks for existing model file)
        GemmaModelManager.init(getApplication())

        // Load persisted typography preferences, then load book
        viewModelScope.launch {
            val prefs = getApplication<Application>().readerDataStore.data.first()
            _uiState.value = _uiState.value.copy(
                fontSize    = prefs[ReaderPrefsKeys.FONT_SIZE] ?: 16,
                readerTheme = try {
                    ReaderTheme.valueOf(prefs[ReaderPrefsKeys.THEME] ?: ReaderTheme.DARK.name)
                } catch (_: IllegalArgumentException) {
                    ReaderTheme.DARK
                }
            )
            loadBook()
        }

        // Mirror GemmaModelManager state into uiState
        viewModelScope.launch {
            GemmaModelManager.state.collect { modelState ->
                _uiState.value = _uiState.value.copy(gemmaModelState = modelState)
            }
        }

        // Poll download progress every second while downloading
        viewModelScope.launch {
            while (true) {
                if (_uiState.value.gemmaModelState is GemmaModelState.Downloading) {
                    GemmaModelManager.pollProgress(getApplication())
                }
                delay(1_000L)
            }
        }

        // Observe TTS lifecycle state
        viewModelScope.launch {
            speechController.state.collect { tts ->
                _uiState.value = _uiState.value.copy(ttsState = tts)
                // When TTS finishes naturally, clear sentences to exit sentence-view mode
                if (tts is TtsState.Ready && _uiState.value.currentSentenceIndex < 0) {
                    _uiState.value = _uiState.value.copy(sentences = emptyList())
                }
            }
        }

        // Observe sentence-level progress
        viewModelScope.launch {
            speechController.currentSentenceIndex.collect { idx ->
                _uiState.value = _uiState.value.copy(currentSentenceIndex = idx)
            }
        }

        // Observe word-level progress (API 26+)
        viewModelScope.launch {
            speechController.currentWordRange.collect { (start, end) ->
                _uiState.value = _uiState.value.copy(currentWordStart = start, currentWordEnd = end)
            }
        }
    }

    // ── Page navigation ───────────────────────────────────────────────────────

    fun nextPage() {
        val s = _uiState.value
        if (s.currentPage < s.totalPages - 1) navigateToPage(s.currentPage + 1)
    }

    fun prevPage() {
        val s = _uiState.value
        if (s.currentPage > 0) navigateToPage(s.currentPage - 1)
    }

    fun navigateToPage(pageIndex: Int) {
        val state   = _uiState.value
        val clamped = pageIndex.coerceIn(0, (state.totalPages - 1).coerceAtLeast(0))
        if (clamped == state.currentPage && state.rawText.isNotEmpty()) return

        // Stop TTS before leaving the page
        stopReading()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentPage    = clamped,
                isLoadingText  = true,
                rawText        = "",
                translatedText = ""
            )

            val raw    = fetchRawText(clamped)
            val cached = translationCache[clamped]

            _uiState.value = _uiState.value.copy(
                rawText        = raw,
                translatedText = cached ?: "",
                isLoadingText  = false,
                isTranslating  = cached == null && raw.isNotEmpty()
            )

            libraryRepo.updateLastReadPage(bookId, clamped)

            if (cached == null && raw.isNotEmpty()) translatePage(clamped, raw)
            prefetchTranslation(clamped + 1)
            prefetchTranslation(clamped + 2)
        }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    fun setTargetLanguage(lang: AppLanguage) {
        stopReading()
        _uiState.value = _uiState.value.copy(
            targetLang        = lang,
            translatedText    = "",
            isPickingLanguage = false
        )
        translationCache.clear()
        translationPending.clear()
        val state = _uiState.value
        if (state.rawText.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(isTranslating = true)
            viewModelScope.launch { translatePage(state.currentPage, state.rawText) }
        }
    }

    fun toggleTranslation() {
        _uiState.value = _uiState.value.copy(showTranslation = !_uiState.value.showTranslation)
    }

    fun openLanguagePicker()  { _uiState.value = _uiState.value.copy(isPickingLanguage = true) }
    fun closeLanguagePicker() { _uiState.value = _uiState.value.copy(isPickingLanguage = false) }

    // ── Typography ────────────────────────────────────────────────────────────

    fun increaseFontSize() = setFontSize((_uiState.value.fontSize + 2).coerceAtMost(FONT_SIZE_MAX))
    fun decreaseFontSize() = setFontSize((_uiState.value.fontSize - 2).coerceAtLeast(FONT_SIZE_MIN))

    private fun setFontSize(size: Int) {
        _uiState.value = _uiState.value.copy(fontSize = size)
        viewModelScope.launch {
            getApplication<Application>().readerDataStore.edit { prefs ->
                prefs[ReaderPrefsKeys.FONT_SIZE] = size
            }
        }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        _uiState.value = _uiState.value.copy(readerTheme = theme)
        viewModelScope.launch {
            getApplication<Application>().readerDataStore.edit { prefs ->
                prefs[ReaderPrefsKeys.THEME] = theme.name
            }
        }
    }

    fun toggleTypographyPanel() {
        _uiState.value = _uiState.value.copy(
            showTypographyPanel = !_uiState.value.showTypographyPanel
        )
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

    /**
     * Starts reading the currently visible text aloud, sentence by sentence.
     * Reads the translation if [ReaderUiState.showTranslation] is true and a
     * translation is available, otherwise reads the original text.
     */
    fun readAloud() {
        val state = _uiState.value
        val (text, locale) = if (state.showTranslation && state.translatedText.isNotEmpty()) {
            state.translatedText to state.targetLang.locale
        } else {
            state.rawText to state.sourceLang.locale
        }
        if (text.isBlank()) return

        speechController.setLanguage(locale)
        val sents = splitIntoSentences(text)
        _uiState.value = _uiState.value.copy(sentences = sents)
        speechController.speakSentences(sents, 0)
    }

    /** Interrupts TTS playback and exits sentence-view mode. */
    fun stopReading() {
        speechController.stop()
        _uiState.value = _uiState.value.copy(
            sentences            = emptyList(),
            currentSentenceIndex = -1,
            currentWordStart     = -1,
            currentWordEnd       = -1
        )
    }

    fun nextSentence() {
        val state = _uiState.value
        val next  = state.currentSentenceIndex + 1
        if (next < state.sentences.size) speechController.speakSentences(state.sentences, next)
    }

    fun prevSentence() {
        val state = _uiState.value
        val prev  = (state.currentSentenceIndex - 1).coerceAtLeast(0)
        speechController.speakSentences(state.sentences, prev)
    }

    fun setSpeechRate(rate: Float) {
        speechController.setSpeechRate(rate)
        _uiState.value = _uiState.value.copy(speechRate = rate)
    }

    // ── AI Summarization (Step 5) ─────────────────────────────────────────────

    /**
     * Triggers on-device Gemma summarization of the current page.
     * The model must be downloaded first — check [ReaderUiState.gemmaModelState].
     */
    fun requestSummary() {
        if (_uiState.value.gemmaModelState !is GemmaModelState.Downloaded) return
        val state = _uiState.value
        // Prefer translated text if visible; fall back to original
        val text = if (state.showTranslation && state.translatedText.isNotEmpty())
            state.translatedText else state.rawText
        if (text.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isSummarizing = true,
            summary       = "",
            summaryError  = null,
            showSummary   = true
        )

        viewModelScope.launch {
            try {
                val result = GemmaSummarizer.summarize(
                    context        = getApplication(),
                    text           = text,
                    targetLanguage = state.targetLang.displayName
                )
                _uiState.value = _uiState.value.copy(
                    isSummarizing = false,
                    summary       = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSummarizing = false,
                    summaryError  = e.message ?: "Summarization failed"
                )
            }
        }
    }

    /** Shows the download confirmation prompt. Called when user taps ✨ and model is missing. */
    fun promptGemmaDownload() {
        _uiState.value = _uiState.value.copy(showGemmaDownloadPrompt = true)
    }

    /** User confirmed — start the background download (~1.3 GB). */
    fun confirmGemmaDownload() {
        _uiState.value = _uiState.value.copy(showGemmaDownloadPrompt = false)
        GemmaModelManager.startDownload(getApplication())
    }

    fun dismissGemmaDownloadPrompt() {
        _uiState.value = _uiState.value.copy(showGemmaDownloadPrompt = false)
    }

    fun dismissSummary() {
        _uiState.value = _uiState.value.copy(showSummary = false, summary = "", summaryError = null)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Called when ReaderScreen leaves composition.
     * Stops TTS playback; page and translation caches are retained so
     * reading resumes instantly if the user returns to the same book.
     */
    fun pause() {
        speechController.stop()
    }

    override fun onCleared() {
        speechController.shutdown()
        translationRepo.close()
        super.onCleared()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadBook() {
        viewModelScope.launch {
            val book  = libraryRepo.getBook(bookId) ?: run {
                _uiState.value = _uiState.value.copy(error = "Book not found")
                return@launch
            }
            val file  = File(getApplication<Application>().filesDir, book.filePath)
            val pages = PdfTextExtractor.pageCount(file)
            _uiState.value = _uiState.value.copy(book = book, totalPages = pages)
            navigateToPage(book.lastReadPage.coerceIn(0, (pages - 1).coerceAtLeast(0)))
        }
    }

    private suspend fun fetchRawText(pageIndex: Int): String {
        rawCache[pageIndex]?.let { return it }
        val book = _uiState.value.book ?: return ""
        val file = File(getApplication<Application>().filesDir, book.filePath)
        val text = PdfTextExtractor.extractPage(file, pageIndex)
        rawCache[pageIndex] = text
        return text
    }

    private suspend fun translatePage(pageIndex: Int, rawText: String) {
        if (translationPending[pageIndex] == true) return
        translationPending[pageIndex] = true
        val state = _uiState.value
        try {
            val translated = translateChunked(rawText, state.sourceLang.mlKitCode, state.targetLang.mlKitCode)
            translationCache[pageIndex] = translated
            if (_uiState.value.currentPage == pageIndex) {
                _uiState.value = _uiState.value.copy(translatedText = translated, isTranslating = false)
            }
        } catch (_: Exception) {
            if (_uiState.value.currentPage == pageIndex) {
                _uiState.value = _uiState.value.copy(isTranslating = false)
            }
        } finally {
            translationPending.remove(pageIndex)
        }
    }

    private suspend fun translateChunked(text: String, sourceLang: String, targetLang: String): String {
        if (sourceLang == targetLang) return text
        if (text.length <= MAX_CHUNK_CHARS) return translationRepo.translate(text, sourceLang, targetLang)

        val paragraphs = text.split("\n\n")
        val result     = StringBuilder()
        val chunk      = StringBuilder()
        for (para in paragraphs) {
            if (chunk.length + para.length + 2 > MAX_CHUNK_CHARS && chunk.isNotEmpty()) {
                result.append(translationRepo.translate(chunk.toString(), sourceLang, targetLang))
                result.append("\n\n")
                chunk.clear()
            }
            if (chunk.isNotEmpty()) chunk.append("\n\n")
            chunk.append(para)
        }
        if (chunk.isNotEmpty()) result.append(translationRepo.translate(chunk.toString(), sourceLang, targetLang))
        return result.toString().trim()
    }

    private fun prefetchTranslation(pageIndex: Int) {
        val state = _uiState.value
        if (pageIndex < 0 || pageIndex >= state.totalPages) return
        if (translationCache.containsKey(pageIndex) || translationPending[pageIndex] == true) return
        viewModelScope.launch(Dispatchers.IO) {
            val raw = fetchRawText(pageIndex)
            if (raw.isNotEmpty()) translatePage(pageIndex, raw)
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val sents = text.trim().split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }.map { it.trim() }
        return sents.ifEmpty { listOf(text.trim()) }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val application: Application,
        private val bookId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(application, bookId) as T
    }

    private companion object {
        const val MAX_CHUNK_CHARS = 4_000
        const val FONT_SIZE_MIN   = 12
        const val FONT_SIZE_MAX   = 28
    }
}
