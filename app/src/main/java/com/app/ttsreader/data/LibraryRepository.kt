package com.app.ttsreader.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.app.ttsreader.data.local.AppDatabase
import com.app.ttsreader.data.local.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Domain-level access to the E-Reader book library backed by Room.
 *
 * Handles SAF file import (content URI → internal storage copy) and
 * book lifecycle (insert, delete with file cleanup).
 */
class LibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).bookDao()

    val allBooks: Flow<List<BookEntity>> = dao.getAll()

    suspend fun importPdf(contentUri: Uri, contentResolver: ContentResolver): BookEntity =
        withContext(Dispatchers.IO) {
            val displayName = resolveDisplayName(contentResolver, contentUri)
            val booksDir = File(appContext.filesDir, "ereader_books").apply { mkdirs() }
            val uniqueFilename = "${System.currentTimeMillis()}_$displayName"
            val destFile = File(booksDir, uniqueFilename)

            contentResolver.openInputStream(contentUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot open input stream for $contentUri")

            // Strip known document extensions (.pdf, .doc, .docx, .txt)
            val title = displayName
                .removeSuffix(".pdf")
                .removeSuffix(".doc")
                .removeSuffix(".docx")
                .removeSuffix(".txt")

            val entity = BookEntity(
                title = title,
                filePath = "ereader_books/$uniqueFilename"
            )
            val id = dao.insert(entity)
            entity.copy(id = id)
        }

    suspend fun deleteBook(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { book ->
            val file = File(appContext.filesDir, book.filePath)
            file.delete()
        }
        dao.deleteById(id)
    }

    suspend fun getBook(id: Long): BookEntity? = dao.getById(id)

    suspend fun updateLastReadPage(id: Long, page: Int) =
        withContext(Dispatchers.IO) { dao.updateLastReadPage(id, page) }

    private fun resolveDisplayName(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return "unknown_${System.currentTimeMillis()}.pdf"
    }
}
