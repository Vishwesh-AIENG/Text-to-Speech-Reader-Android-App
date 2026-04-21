package com.app.ttsreader.data

import android.content.Context
import com.app.ttsreader.data.local.SettingsDataStore
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.utils.LanguageUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain-level access to persisted user settings.
 *
 * Translates between raw [SettingsDataStore] key-value pairs and domain types
 * like [AppLanguage].
 *
 * ## Defaults
 * If a key has never been written (first launch), the Flow emits the default
 * defined in [LanguageUtils].
 *
 * ## Thread safety
 * DataStore handles concurrency internally — all calls are safe from any coroutine.
 */
class SettingsRepository(context: Context) {

    private val dataStore = SettingsDataStore(context.applicationContext)

    // ── Language ──────────────────────────────────────────────────────────────────

    val sourceLanguage: Flow<AppLanguage> = dataStore.sourceLanguageCode.map { code ->
        code?.let { LanguageUtils.findByCode(it) } ?: LanguageUtils.DEFAULT_SOURCE
    }

    val targetLanguage: Flow<AppLanguage> = dataStore.targetLanguageCode.map { code ->
        code?.let { LanguageUtils.findByCode(it) } ?: LanguageUtils.DEFAULT_TARGET
    }

    suspend fun setSourceLanguage(language: AppLanguage) {
        dataStore.setSourceLanguageCode(language.mlKitCode)
    }

    suspend fun setTargetLanguage(language: AppLanguage) {
        dataStore.setTargetLanguageCode(language.mlKitCode)
    }

    // ── Voice & Playback ────────────────────────────────────────────────────────

    val speechRate: Flow<Float> = dataStore.speechRate
    val pitch: Flow<Float> = dataStore.pitch
    val fontSize: Flow<Int> = dataStore.fontSize

    suspend fun setSpeechRate(rate: Float) { dataStore.setSpeechRate(rate) }
    suspend fun setPitch(pitch: Float) { dataStore.setPitch(pitch) }
    suspend fun setFontSize(size: Int) { dataStore.setFontSize(size) }

    // ── Onboarding ───────────────────────────────────────────────────────────────

    val onboardingShown: Flow<Boolean> = dataStore.onboardingShown
    suspend fun setOnboardingShown(shown: Boolean) { dataStore.setOnboardingShown(shown) }

    // ── Overlay style ────────────────────────────────────────────────────────────

    val useSdfOverlay: Flow<Boolean> = dataStore.useSdfOverlay
    suspend fun setUseSdfOverlay(use: Boolean) { dataStore.setUseSdfOverlay(use) }

}
