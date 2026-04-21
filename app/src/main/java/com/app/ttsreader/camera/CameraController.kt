package com.app.ttsreader.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors

class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /**
     * Binds CameraX Preview + ImageAnalysis use cases to the given lifecycle.
     * Must be called from a coroutine on the main thread — [viewModelScope] provides this.
     *
     * @param lifecycleOwner Lifecycle that scopes the camera session.
     * @param previewView    Surface for camera frames.
     * @param analyzer       Receives frames for OCR processing.
     */
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        cameraProvider = provider

        // Unbind all previous use cases before rebinding — prevents duplicate-binding crash
        provider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        // ResolutionSelector replaces the deprecated setTargetResolution() API (deprecated in 1.1).
        // RATIO_16_9 matches most phone sensors and gives a good 720p-class frame for OCR.
        // FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER: prefer resolutions at or below the target
        // to keep ML Kit processing fast; fall back higher only if no lower option exists.
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            // Drop frames when the analyzer is busy — avoids memory pressure
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }

    /** @return true if the device has a torch (flash). */
    fun hasTorch(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    /** Enables or disables the torch (flashlight). */
    fun enableTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    /**
     * Unbinds all use cases without shutting down the analysis executor.
     * Safe to call when a composable screen leaves composition — [startCamera]
     * can be called again to rebind when the screen re-enters.
     */
    fun pauseCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        // analysisExecutor is intentionally kept alive so startCamera() can reuse it.
    }

    /**
     * Unbinds all use cases and gracefully shuts down the analysis executor.
     * Called from [com.app.ttsreader.viewmodel.MainViewModel.onCleared].
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        analysisExecutor.shutdown() // graceful — lets any in-flight analysis complete
    }
}
