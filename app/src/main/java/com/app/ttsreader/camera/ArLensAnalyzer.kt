package com.app.ttsreader.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.app.ttsreader.ocr.SpatialWord
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [ImageAnalysis.Analyzer] for the AR Magic Lens mode.
 *
 * Uses ML Kit **line-level** extraction ([TextAnalyzer.extractLines]).
 *
 * ## Threading
 * All ML Kit callbacks are dispatched onto a single-thread [Executors.newSingleThreadExecutor].
 * This means [onResult] is always invoked on the same background thread, so the
 * tracker's confined dispatcher gets a clean serialised stream of frames.
 *
 * ## Throttle + debouncer
 * [throttleMs] (default 66 ms ≈ 15 fps) caps frame rate.
 * A per-line debouncer drops a frame if every detected line is byte-identical
 * AND geometrically near-identical (IoU > 0.95) to the previous frame's lines —
 * common when the camera is stationary on static text. The tracker still gets
 * one frame per real motion change.
 */
class ArLensAnalyzer(
    private val onResult: (
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        isFrontCamera: Boolean,
    ) -> Unit,
    private val throttleMs: Long = 66L,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val callbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ArLensAnalyzer-callback").apply { isDaemon = true }
    }

    /**
     * Optional — set by [CameraController] when the front camera is selected so
     * the overlay can mirror coordinates. CameraController currently binds back
     * camera only, so this defaults to false; wiring is present for safety.
     */
    @Volatile
    var isFrontCamera: Boolean = false

    @Volatile
    private var lastTimestamp = 0L
    private val busy = AtomicBoolean(false)

    /** Last frame's lines, used by [shouldDebounce]. Owned by the callback thread. */
    private var lastLines: List<LineSnapshot> = emptyList()

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
                .addOnSuccessListener(callbackExecutor) { mlKitText ->
                    val lines = TextAnalyzer.extractLines(mlKitText)
                    if (!shouldDebounce(lines)) {
                        onResult(lines, imgW, imgH, imgRot, isFrontCamera)
                    }
                }
                .addOnFailureListener(callbackExecutor) { e ->
                    Log.e(TAG, "ML Kit failed: ${e.message}", e)
                }
                .addOnCompleteListener(callbackExecutor) {
                    imageProxy.close()
                    busy.set(false)
                }
        } catch (t: Throwable) {
            imageProxy.close()
            busy.set(false)
            throw t
        }
    }

    /**
     * Returns true if the new frame is geometrically + textually identical to
     * the previous one (and we should therefore skip publishing it).
     * Updates [lastLines] when returning false (so the next call sees a new baseline).
     */
    private fun shouldDebounce(lines: List<SpatialWord>): Boolean {
        // Snapshot incoming
        val snap = lines.map {
            val r = it.toBoundingRect()
            LineSnapshot(
                text = it.text,
                left = r.left.toFloat(),
                top = r.top.toFloat(),
                right = r.right.toFloat(),
                bottom = r.bottom.toFloat(),
            )
        }
        val prev = lastLines
        // Mismatched counts → publish
        if (snap.size != prev.size || snap.isEmpty()) {
            lastLines = snap
            return false
        }
        // Same count — require byte-identical text AND IoU > 0.95 on every line
        for (i in snap.indices) {
            val a = snap[i]
            val b = prev[i]
            if (a.text != b.text || iou(a, b) < 0.95f) {
                lastLines = snap
                return false
            }
        }
        return true   // identical → debounce
    }

    fun close() {
        recognizer.close()
        callbackExecutor.shutdown()
    }

    private data class LineSnapshot(
        val text: String,
        val left: Float, val top: Float, val right: Float, val bottom: Float,
    )

    private fun iou(a: LineSnapshot, b: LineSnapshot): Float {
        val ix0 = maxOf(a.left, b.left)
        val iy0 = maxOf(a.top, b.top)
        val ix1 = minOf(a.right, b.right)
        val iy1 = minOf(a.bottom, b.bottom)
        if (ix1 <= ix0 || iy1 <= iy0) return 0f
        val inter = (ix1 - ix0) * (iy1 - iy0)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - inter
        return if (union <= 0f) 0f else inter / union
    }

    companion object {
        private const val TAG = "ArLensAnalyzer"
        /** CameraSelector value the ViewModel can pass through to set lens facing. */
        val DEFAULT_LENS_FACING: Int = CameraSelector.LENS_FACING_BACK
    }
}
