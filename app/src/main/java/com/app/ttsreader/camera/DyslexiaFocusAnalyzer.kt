package com.app.ttsreader.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.app.ttsreader.ocr.SpatialWord
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [ImageAnalysis.Analyzer] for the Dyslexia Focus mode.
 *
 * Passes [SpatialWord] detections to the ViewModel callback so the ViewModel
 * can apply EMA smoothing and derive the focus band position.
 *
 * ## Throttle
 * [throttleMs] (default 250 ms) caps frame analysis at ~4 fps — enough for
 * smooth focus band tracking without burning CPU on every camera frame.
 */
class DyslexiaFocusAnalyzer(
    context: Context,
    private val onResult: (
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) -> Unit,
    private val throttleMs: Long = 250L
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

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
                .addOnSuccessListener(mainExecutor) { mlKitText ->
                    onResult(TextAnalyzer.extractWords(mlKitText), imgW, imgH, imgRot)
                }
                .addOnFailureListener(mainExecutor) { e ->
                    Log.e(TAG, "ML Kit failed: ${e.message}", e)
                }
                .addOnCompleteListener(mainExecutor) {
                    imageProxy.close()
                    busy.set(false)
                }
        } catch (t: Throwable) {
            // Guarantee imageProxy is closed even if process() throws synchronously.
            imageProxy.close()
            busy.set(false)
            throw t
        }
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        private const val TAG = "DyslexiaAnalyzer"
    }
}
