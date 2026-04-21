package com.app.ttsreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single scanned text entry in the history.
 *
 * Stores the last 10 scans so users can quickly revisit previously scanned text.
 */
@Entity(tableName = "scan_history")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recognizedText: String,
    val translatedText: String,
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val timestamp: Long = System.currentTimeMillis()
)
