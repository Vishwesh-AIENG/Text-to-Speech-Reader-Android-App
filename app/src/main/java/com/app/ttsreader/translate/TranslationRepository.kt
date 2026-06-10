package com.app.ttsreader.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * Typed failure modes for translation. Lets callers (AR Lens) show specific UX
 * for the "model not downloaded" case instead of swallowing it as a generic error.
 */
sealed class TranslationError(message: String) : Exception(message) {
    /** The required ML Kit model for the language pair is not present on-device. */
    class ModelMissing(val languageCode: String) :
        TranslationError("Translation model not downloaded: $languageCode")

    /** Network / download failure (offline, ML Kit servers unreachable). */
    class Network(cause: Throwable) :
        TranslationError("Translation network error: ${cause.message}")

    /** Unclassified failure. */
    class Unknown(cause: Throwable) :
        TranslationError("Translation failed: ${cause.message}")
}

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
                throw classifyError(e)
            }
        }

        _state.value = TranslationState.Translating
        return try {
            translator!!.translate(text).await()
        } catch (e: Exception) {
            throw classifyError(e)
        } finally {
            // Always return to Idle, even if translate() throws
            _state.value = TranslationState.Idle
        }
    }

    /** Maps raw ML Kit / coroutine exceptions to typed [TranslationError]s. */
    private fun classifyError(e: Throwable): Throwable {
        if (e is TranslationError) return e
        // ML Kit raises MlKitException for missing model and IOException for network.
        val message = e.message?.lowercase() ?: ""
        return when {
            e is IOException || "network" in message || "internet" in message ->
                TranslationError.Network(e)
            "model" in message && ("missing" in message || "not found" in message) ->
                TranslationError.ModelMissing(currentTargetLang)
            else -> TranslationError.Unknown(e)
        }
    }

    /**
     * Checks whether the ML Kit model for the given language pair is available
     * locally. Returns `Result.success` if so, or `Result.failure(TranslationError.ModelMissing)`
     * naming the first missing side. Both source and target models must be present.
     *
     * Does NOT download; pairs with [downloadLanguage] in [com.app.ttsreader.viewmodel.SettingsViewModel].
     */
    suspend fun ensureModelDownloaded(sourceLang: String, targetLang: String): Result<Unit> {
        if (sourceLang == targetLang) return Result.success(Unit)
        return runCatching {
            val downloaded = getDownloadedModelCodes()
            // ML Kit always uses English as a pivot — at minimum the target model
            // is required. The source model is required if source != English.
            if (sourceLang != "en" && sourceLang !in downloaded) {
                throw TranslationError.ModelMissing(sourceLang)
            }
            if (targetLang != "en" && targetLang !in downloaded) {
                throw TranslationError.ModelMissing(targetLang)
            }
        }
    }

    /**
     * Returns the set of already-downloaded [TranslateLanguage] codes.
     * Used by the language selector UI (Step 7) to show a download indicator
     * next to languages whose models aren't yet cached on-device.
     */
    suspend fun getDownloadedModelCodes(): Set<String> {
        val models = RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
        return models.map { it.language }.toSet()
    }

    /**
     * Deletes the on-device model for the given language code to free storage.
     * Both source and target models are independent — only the target needs deleting
     * if you're keeping English as a fixed source.
     */
    suspend fun deleteModel(languageCode: String) {
        val modelManager = RemoteModelManager.getInstance()
        val model = TranslateRemoteModel.Builder(languageCode).build()
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
