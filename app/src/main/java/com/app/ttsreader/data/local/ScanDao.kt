package com.app.ttsreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    /** Returns the 10 most recent scans, newest first. */
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecentScans(): Flow<List<ScanRecord>>

    /** Inserts a new scan and trims old entries to keep only the 10 most recent. */
    @Insert
    suspend fun insert(record: ScanRecord)

    /** Deletes scans older than the 10 most recent. */
    @Query("DELETE FROM scan_history WHERE id NOT IN (SELECT id FROM scan_history ORDER BY timestamp DESC LIMIT 10)")
    suspend fun trimOldEntries()

    @Query("DELETE FROM scan_history")
    suspend fun deleteAll()
}
