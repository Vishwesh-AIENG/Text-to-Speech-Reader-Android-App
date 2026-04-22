package com.app.ttsreader.camera

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.app.ttsreader.ocr.SpatialWord
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [ImageAnalysis.Analyzer] for the AR Magic Lens mode.
 *
 * Uses ML Kit **line-level** extraction ([TextAnalyzer.extractLines]) so each
 * [SpatialWord] carries ML Kit's own line geometry — more accurate bounding
 * boxes than manually re-grouping word-level elements in the ViewModel.
 *
 * ## Threading
 * All ML Kit callbacks intentionally run on ML Kit's internal background thread
 * (no `mainExecutor`). This keeps [onResult] and the ViewModel's heavy
 * `onFrameAnalyzed` processing off the UI thread, eliminating the primary cause
 * of jank that was present when the success listener was dispatched to main.
 * The [AtomicBoolean] gate still prevents concurrent ML Kit calls.
 *
 * ## Throttle
 * [throttleMs] (default 100 ms ≈ 10 fps) caps frame analysis rate.
 */
class ArLensAnalyzer(
    private val onResult: (
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) -> Unit,
    private val throttleMs: Long = 100L
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastTimestamp = 0L
    private val busy = AtomicBoolean(false)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastTimestamp < throttleMs || !busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastTimestamp = now

        val imgW   = imageProxy.width
        val imgH   = imageProxy.height
        val imgRot = imageProxy.imageInfo.rotationDegrees

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            busy.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imgRot)

        try {
            recognizer.process(inputImage)
                // No executor — callbacks run on ML Kit's background thread so the
                // heavy onFrameAnalyzed processing never touches the UI thread.
                .addOnSuccessListener { mlKitText ->
                    val lines = TextAnalyzer.extractLines(mlKitText)
                    onResult(lines, imgW, imgH, imgRot)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit failed: ${e.message}", e)
                }
                .addOnCompleteListener {
                    // imageProxy.close() is thread-safe; busy reset unblocks next frame.
                    imageProxy.close()
                    busy.set(false)
                }
        } catch (t: Throwable) {
            // Synchronous throw from process() would orphan the imageProxy; close it here.
            imageProxy.close()
            busy.set(false)
            throw t
        }
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val TAG = "ArLensAnalyzer"
    }
}
