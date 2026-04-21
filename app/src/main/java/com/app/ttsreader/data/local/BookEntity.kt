package com.app.ttsreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single imported PDF book in the E-Reader library.
 *
 * [filePath] is stored relative to [Context.getFilesDir] (e.g. "ereader_books/123_doc.pdf")
 * so the path survives backup/restore cycles.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val targetLanguage: String? = null,
    val lastReadPage: Int = 0,
    val bookmarks: String = "[]",
    val dateAdded: Long = System.currentTimeMillis()
)
