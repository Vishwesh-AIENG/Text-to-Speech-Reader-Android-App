package com.app.ttsreader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Top-level extension property — creates exactly ONE DataStore per process.
 * This MUST be a top-level `val`, not inside a class, or Android will throw
 * `IllegalStateException: There are multiple DataStores active for the same file`.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_reader_settings")

/**
 * Thin wrapper around Jetpack [DataStore]<[Preferences]>.
 *
 * The underlying [dataStore] is a process-wide singleton via the top-level
 * extension property above. It is safe to create multiple [SettingsDataStore]
 * instances — they all read/write through the same DataStore file.
 */
class SettingsDataStore(private val context: Context) {

    // ── Reads (Flow) ─────────────────────────────────────────────────────────────

    val sourceLanguageCode: Flow<String?>
        get() = context.dataStore.data.map { it[KEY_SOURCE_LANGUAGE] }

    val targetLanguageCode: Flow<String?>
        get() = context.dataStore.data.map { it[KEY_TARGET_LANGUAGE] }

    val speechRate: Flow<Float>
        get() = context.dataStore.data.map { it[KEY_SPEECH_RATE] ?: 1.0f }

    val pitch: Flow<Float>
        get() = context.dataStore.data.map { it[KEY_PITCH] ?: 1.0f }

    val fontSize: Flow<Int>
        get() = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: 16 }

    val onboardingShown: Flow<Boolean>
        get() = context.dataStore.data.map { it[KEY_ONBOARDING_SHOWN] ?: false }

    val useSdfOverlay: Flow<Boolean>
        get() = context.dataStore.data.map { it[KEY_SDF_OVERLAY] ?: false }

    // ── Writes (suspend) ─────────────────────────────────────────────────────────

    suspend fun setSourceLanguageCode(code: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SOURCE_LANGUAGE] = code }
    }

    suspend fun setTargetLanguageCode(code: String) {
        context.dataStore.edit { prefs -> prefs[KEY_TARGET_LANGUAGE] = code }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_SPEECH_RATE] = rate }
    }

    suspend fun setPitch(pitch: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_PITCH] = pitch }
    }

    suspend fun setFontSize(size: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_FONT_SIZE] = size }
    }

    suspend fun setOnboardingShown(shown: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_SHOWN] = shown }
    }

    suspend fun setUseSdfOverlay(use: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SDF_OVERLAY] = use }
    }

    companion object {
        private val KEY_SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        private val KEY_TARGET_LANGUAGE = stringPreferencesKey("target_language")
        private val KEY_SPEECH_RATE    = floatPreferencesKey("speech_rate")
        private val KEY_PITCH          = floatPreferencesKey("pitch")
        private val KEY_FONT_SIZE        = intPreferencesKey("font_size")
        private val KEY_ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
        private val KEY_SDF_OVERLAY      = booleanPreferencesKey("sdf_overlay")

    }
}
