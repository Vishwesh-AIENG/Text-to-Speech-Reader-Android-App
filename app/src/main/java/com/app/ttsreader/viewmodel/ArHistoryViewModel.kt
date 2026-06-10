package com.app.ttsreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.data.local.AppDatabase
import com.app.ttsreader.data.local.ArHistoryEntity
import com.app.ttsreader.tts.SpeechController
import com.app.ttsreader.utils.LanguageUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViewModel for the AR translation history screen.
 *
 * Reads from [ArHistoryDao.observeAll] (newest first, favorites pinned) and
 * exposes commands for favoriting, deleting, and pronouncing a stored
 * translation via the shared [SpeechController].
 *
 * The SpeechController is created per-VM (same pattern as [DyslexiaViewModel]);
 * shutdown happens in [onCleared].
 */
class ArHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).arHistoryDao()
    private val speech = SpeechController(application)

    val entries: StateFlow<List<ArHistoryEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(entry: ArHistoryEntity) {
        viewModelScope.launch { dao.setFavorite(entry.id, !entry.isFavorite) }
    }

    fun delete(entry: ArHistoryEntity) {
        viewModelScope.launch { dao.deleteById(entry.id) }
    }

    fun clearNonFavorites() {
        viewModelScope.launch { dao.deleteAllNonFavorites() }
    }

    /** Speak the translated text in the target language. */
    fun pronounce(entry: ArHistoryEntity) {
        val targetLocale = LanguageUtils.findByCode(entry.targetLang)?.locale ?: Locale.getDefault()
        speech.setLanguage(targetLocale)
        speech.speak(entry.translatedText)
    }

    override fun onCleared() {
        speech.shutdown()
        super.onCleared()
    }
}
