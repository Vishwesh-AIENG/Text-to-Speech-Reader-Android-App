package com.app.ttsreader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.data.HistoryRepository
import com.app.ttsreader.data.local.ScanRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    private val _scans = MutableStateFlow<List<ScanRecord>>(emptyList())
    val scans: StateFlow<List<ScanRecord>> = _scans.asStateFlow()

    init {
        repository.recentScans
            .onEach { _scans.value = it }
            .launchIn(viewModelScope)
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
