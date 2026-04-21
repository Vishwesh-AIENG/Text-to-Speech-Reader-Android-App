package com.app.ttsreader.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages ML Kit on-device translation.
 *
 * ## Translator caching
 * Creating a [Translator] instance is expensive (loads the model into memory).
 * We cache the instance and only recreate it when the language pair changes.
 * [downloadModelIfNeeded] is idempotent — calling it when the model is already
 * present returns immediately, so we gate the [DownloadingModel] state behind a
 * [modelReady] flag that resets only when the language pair changes.
 *
 * ## Threading
 * [translate] is a suspend function and safe to call from any coroutine context.
 * All ML Kit [Task] objects are converted to suspend calls via `.await()`
 * from `kotlinx-coroutines-play-services`.
 *
 * ## Error handling
 * [translate] throws on failure (network error during download, ML Kit error).
 * The caller ([com.app.ttsreader.viewmodel.MainViewModel]) wraps it in
 * `runCatching` and surfaces the error through [MainUiState].
 */
class TranslationRepository {

    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    private var translator: Translator? = null
    private var currentSourceLang: String = ""
    private var currentTargetLang: String = ""
    private var modelReady = false  // true once downloadModelIfNeeded has succeeded for the current pair

    /**
     * Translates [text] from [sourceLang] to [targetLang].
     *
     * If source == target, returns [text] unchanged without any ML Kit calls.
     * If the language pair differs from the cached translator, a new [Translator]
     * is created and the model is downloaded if not already present.
     *
     * @throws Exception on model download failure or translation failure.
     */
    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        if (sourceLang == targetLang) {
            _state.value = TranslationState.Idle
            return text
        }

        // Rebuild translator only when the language pair actually changes
        if (sourceLang != currentSourceLang || targetLang != currentTargetLang) {
            translator?.close()
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            translator = Translation.getClient(options)
            currentSourceLang = sourceLang
            currentTargetLang = targetLang
            modelReady = false
        }

        // Download the model only once per language pair
        if (!modelReady) {
            _state.value = TranslationState.DownloadingModel
            val conditions = DownloadConditions.Builder().build()
            try {
                translator!!.downloadModelIfNeeded(conditions).await()
                modelReady = true
            } catch (e: Exception) {
                _state.value = TranslationState.Idle   // don't leave spinner stuck
                throw e
            }
        }

        _state.value = TranslationState.Translating
        return try {
            translator!!.translate(text).await()
        } finally {
            // Always return to Idle, even if translate() throws
            _state.value = TranslationState.Idle
        }
    }

    /**
     * Returns the set of already-downloaded [TranslateLanguage] codes.
     * Used by the language selector UI (Step 7) to show a download indicator
     * next to languages whose models aren't yet cached on-device.
     */
    suspend fun getDownloadedModelCodes(): Set<String> {
        val models = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            .getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java)
            .await()
        return models.map { it.language }.toSet()
    }

    /**
     * Deletes the on-device model for the given language code to free storage.
     * Both source and target models are independent — only the target needs deleting
     * if you're keeping English as a fixed source.
     */
    suspend fun deleteModel(languageCode: String) {
        val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
        val model = com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(languageCode).build()
        modelManager.deleteDownloadedModel(model).await()
        // Reset model-ready flag if the deleted model is the current target
        if (languageCode == currentTargetLang || languageCode == currentSourceLang) {
            modelReady = false
        }
    }

    /**
     * Releases the active [Translator] instance.
     * Must be called from [com.app.ttsreader.viewmodel.MainViewModel.onCleared].
     */
    fun close() {
        translator?.close()
        translator = null
        _state.value = TranslationState.Idle
    }
}
