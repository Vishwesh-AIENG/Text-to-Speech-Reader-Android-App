package com.app.ttsreader.data

import android.content.Context
import androidx.room.withTransaction
import com.app.ttsreader.data.local.AppDatabase
import com.app.ttsreader.data.local.ScanRecord
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level access to scan history backed by Room.
 *
 * Keeps only the 10 most recent entries to limit storage.
 */
class HistoryRepository(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val dao = database.scanDao()

    val recentScans: Flow<List<ScanRecord>> = dao.getRecentScans()

    suspend fun saveScan(
        recognizedText: String,
        translatedText: String,
        sourceLanguageCode: String,
        targetLanguageCode: String
    ) {
        // Insert + trim must be atomic so the recentScans flow never observes
        // an intermediate state where the new row exists alongside 10 old rows.
        database.withTransaction {
            dao.insert(
                ScanRecord(
                    recognizedText = recognizedText,
                    translatedText = translatedText,
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode
                )
            )
            dao.trimOldEntries()
        }
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }
}
