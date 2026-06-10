package com.app.ttsreader.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent history of translations seen in the AR Magic Lens mode.
 *
 * The (sourceText, sourceLang, targetLang) tuple is unique — if the same line
 * is re-seen, the existing row is updated (lastSeenAtMs bumped, seenCount++)
 * rather than appended.
 *
 * @param isFavorite User-toggled star; favorites survive the trim policy.
 * @param seenAtMs   Wall-clock of the first time this translation was captured.
 * @param lastSeenAtMs Wall-clock of the most recent capture.
 * @param seenCount  Total captures across all sessions.
 */
@Entity(
    tableName = "ar_history",
    indices = [Index(value = ["sourceText", "sourceLang", "targetLang"], unique = true)],
)
data class ArHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val isFavorite: Boolean = false,
    val seenAtMs: Long = System.currentTimeMillis(),
    val lastSeenAtMs: Long = System.currentTimeMillis(),
    val seenCount: Int = 1,
)
