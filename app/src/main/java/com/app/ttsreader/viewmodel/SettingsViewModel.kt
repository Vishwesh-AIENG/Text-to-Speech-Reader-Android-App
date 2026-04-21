package com.app.ttsreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.data.SettingsRepository
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.utils.LanguageUtils
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for the Settings screen.
 *
 * Owns:
 * - [SettingsRepository] for persisted preferences (language pair, dark mode)
 * - [RemoteModelManager] for listing / deleting downloaded ML Kit models
 *
 * Model management uses [RemoteModelManager] directly instead of creating a
 * separate [TranslationRepository], avoiding a duplicate ML Kit Translator
 * instance and its associated memory overhead.
 *
 * Language changes made here are persisted to DataStore immediately. The
 * [MainViewModel] observes the same DataStore flows and reacts on its own,
 * so there is no coupling between the two ViewModels.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val modelManager       = RemoteModelManager.getInstance()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        refreshDownloadedModels()
    }

    // ── Observe persisted settings ───────────────────────────────────────────────

    private fun observeSettings() {
        settingsRepository.sourceLanguage
            .onEach { lang -> _uiState.value = _uiState.value.copy(sourceLanguage = lang) }
            .launchIn(viewModelScope)

        settingsRepository.targetLanguage
            .onEach { lang -> _uiState.value = _uiState.value.copy(targetLanguage = lang) }
            .launchIn(viewModelScope)

        settingsRepository.speechRate
            .onEach { rate -> _uiState.value = _uiState.value.copy(speechRate = rate) }
            .launchIn(viewModelScope)

        settingsRepository.pitch
            .onEach { pitch -> _uiState.value = _uiState.value.copy(pitch = pitch) }
            .launchIn(viewModelScope)

        settingsRepository.fontSize
            .onEach { size -> _uiState.value = _uiState.value.copy(fontSize = size) }
            .launchIn(viewModelScope)

        settingsRepository.onboardingShown
            .onEach { shown -> _uiState.value = _uiState.value.copy(onboardingShown = shown) }
            .launchIn(viewModelScope)

        settingsRepository.useSdfOverlay
            .onEach { use -> _uiState.value = _uiState.value.copy(useSdfOverlay = use) }
            .launchIn(viewModelScope)
    }

    // ── Language persistence ─────────────────────────────────────────────────────

    fun setSourceLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setSourceLanguage(language) }
    }

    fun setTargetLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setTargetLanguage(language) }
    }

    // ── Voice & Playback ─────────────────────────────────────────────────────────

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { settingsRepository.setSpeechRate(rate) }
    }

    fun setPitch(pitch: Float) {
        viewModelScope.launch { settingsRepository.setPitch(pitch) }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingShown(true) }
    }

    fun toggleSdfOverlay() {
        viewModelScope.launch {
            settingsRepository.setUseSdfOverlay(!_uiState.value.useSdfOverlay)
        }
    }

    // ── Downloaded models ────────────────────────────────────────────────────────

    fun refreshDownloadedModels() {
        viewModelScope.launch {
            runCatching {
                val models = modelManager
                    .getDownloadedModels(TranslateRemoteModel::class.java)
                    .await()
                val codes = models.map { it.language }.toSet()
                _uiState.value = _uiState.value.copy(downloadedModelCodes = codes)
            }
        }
    }

    /**
     * Downloads the translation model for [languageCode] in the background.
     * Marks the code in [downloadingModelCodes] while the download is in progress.
     */
    fun downloadLanguage(languageCode: String) {
        if (languageCode in _uiState.value.downloadingModelCodes) return   // already in flight
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingModelCodes = _uiState.value.downloadingModelCodes + languageCode
            )
            runCatching {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
                    .setTargetLanguage(languageCode)
                    .build()
                val translator = Translation.getClient(options)
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                translator.close()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = "Download failed: ${e.message}"
                )
            }
            refreshDownloadedModels()
            _uiState.value = _uiState.value.copy(
                downloadingModelCodes = _uiState.value.downloadingModelCodes - languageCode
            )
        }
    }

    fun deleteModel(languageCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingModelCode = languageCode)
            runCatching {
                val model = TranslateRemoteModel.Builder(languageCode).build()
                modelManager.deleteDownloadedModel(model).await()
            }.onSuccess {
                refreshDownloadedModels()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete model: ${e.message}"
                )
            }
            _uiState.value = _uiState.value.copy(deletingModelCode = null)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
    }
}

/**
 * Immutable snapshot of the Settings screen state.
 */
data class SettingsUiState(
    val sourceLanguage: AppLanguage = LanguageUtils.DEFAULT_SOURCE,
    val targetLanguage: AppLanguage = LanguageUtils.DEFAULT_TARGET,
    val availableLanguages: List<AppLanguage> = LanguageUtils.SUPPORTED_LANGUAGES,
    val downloadedModelCodes: Set<String> = emptySet(),
    val downloadingModelCodes: Set<String> = emptySet(),
    val deletingModelCode: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val fontSize: Int = 16,
    val onboardingShown: Boolean = true,  // default true to avoid flash on existing installs
    val useSdfOverlay: Boolean = false,   // Classic (Canvas) by default; Beta = SDF/OpenGL
    val error: String? = null
)
