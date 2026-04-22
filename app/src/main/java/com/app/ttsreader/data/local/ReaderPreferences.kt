package com.app.ttsreader.data.local

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** DataStore for E-Reader typography preferences (font size, reading theme). */
val Context.readerDataStore by preferencesDataStore(name = "reader_prefs")

object ReaderPrefsKeys {
    val FONT_SIZE = intPreferencesKey("font_size")
    val THEME     = stringPreferencesKey("theme")
}
