package com.app.ttsreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArHistoryDao {

    /** Newest first, favorites pinned to top. */
    @Query("SELECT * FROM ar_history ORDER BY isFavorite DESC, lastSeenAtMs DESC LIMIT 200")
    fun observeAll(): Flow<List<ArHistoryEntity>>

    /**
     * Upsert by unique (sourceText, sourceLang, targetLang). If a row already
     * exists we bump lastSeenAtMs and seenCount; otherwise insert fresh.
     * The two-step approach (lookup → insert/update) keeps the favorite flag
     * and seenAtMs intact across captures.
     */
    suspend fun upsert(
        sourceText: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = findExisting(sourceText, sourceLang, targetLang)
        if (existing == null) {
            insert(
                ArHistoryEntity(
                    sourceText = sourceText,
                    translatedText = translatedText,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    seenAtMs = now,
                    lastSeenAtMs = now,
                    seenCount = 1,
                )
            )
        } else {
            update(
                existing.copy(
                    translatedText = translatedText,
                    lastSeenAtMs = now,
                    seenCount = existing.seenCount + 1,
                )
            )
        }
    }

    @Query("SELECT * FROM ar_history WHERE sourceText = :text AND sourceLang = :src AND targetLang = :tgt LIMIT 1")
    suspend fun findExisting(text: String, src: String, tgt: String): ArHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ArHistoryEntity): Long

    @Update
    suspend fun update(entity: ArHistoryEntity)

    @Query("UPDATE ar_history SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM ar_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ar_history WHERE isFavorite = 0")
    suspend fun deleteAllNonFavorites()
}
