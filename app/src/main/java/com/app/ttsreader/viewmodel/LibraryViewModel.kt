package com.app.ttsreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.data.LibraryRepository
import com.app.ttsreader.data.local.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LibraryRepository(application)

    val books: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.importPdf(uri, getApplication<Application>().contentResolver)
                _importError.value = null
            } catch (_: Exception) {
                _importError.value = "Failed to import PDF"
            }
        }
    }

    fun deleteBook(id: Long) {
        viewModelScope.launch {
            repository.deleteBook(id)
        }
    }

    fun clearError() {
        _importError.value = null
    }
}
