package com.app.ttsreader.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts plain text from PDF pages using Android's built-in [PdfRenderer] API
 * combined with ML Kit on-device text recognition (already bundled in the app).
 *
 * ## How it works
 * 1. [PdfRenderer] renders each PDF page to an [android.graphics.Bitmap] at
 *    150 % of the native page size for reliable OCR resolution.
 * 2. ML Kit [TextRecognition] runs the bundled offline OCR model on the bitmap.
 * 3. The extracted text is returned and cached by [ReaderViewModel].
 *
 * ## Advantages over Apache PDFBox
 * - Zero additional dependencies (both APIs are already in the project).
 * - Works for **text-based** and **scanned** PDFs alike.
 * - No initialisation required — [PdfRenderer] is a standard Android API (minSdk 21+).
 *
 * ## Limitations
 * - Render + OCR adds ~500 ms – 2 s per page depending on device; caching hides this.
 * - Complex multi-column layouts may merge columns; Step 3 can offer a raw-image pane.
 */
object PdfTextExtractor {

    /**
     * Returns the number of pages in [file], or 0 if the file cannot be opened.
     * Runs on [Dispatchers.IO].
     */
    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Renders page [pageIndex] (0-indexed) to a bitmap and runs ML Kit OCR on it.
     *
     * Returns an empty string if:
     * - [pageIndex] is out of range
     * - The file cannot be opened
     * - OCR finds no text (e.g. pure image/decorative page)
     *
     * Runs on [Dispatchers.IO] for rendering; the ML Kit `.await()` suspends without
     * blocking a thread. Never throws.
     */
    suspend fun extractPage(file: File, pageIndex: Int): String {
        return try {
            val bitmap = renderPageBitmap(file, pageIndex) ?: return ""
            val text = ocrBitmap(bitmap)
            bitmap.recycle()
            text
        } catch (_: Exception) {
            ""
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun renderPageBitmap(file: File, pageIndex: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                        renderer.openPage(pageIndex).use { page ->
                            // Render at 1.5× native resolution for reliable OCR
                            val w = (page.width  * RENDER_SCALE).toInt()
                            val h = (page.height * RENDER_SCALE).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            // White background — PdfRenderer uses transparent by default
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun ocrBitmap(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image  = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            result.text.trim()
        } finally {
            recognizer.close()
        }
    }

    private const val RENDER_SCALE = 1.5f
}
