package com.app.ttsreader.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import com.app.ttsreader.camera.GyroSmoother

/**
 * Transparent [GLSurfaceView] that floats above the camera [PreviewView].
 *
 * ## Transparency setup
 * - [setEGLConfigChooser] requests an RGBA8888 framebuffer **with alpha**.
 * - [PixelFormat.TRANSLUCENT] on the holder's surface makes the window
 *   compositor treat black-with-zero-alpha as fully transparent.
 * - [setZOrderOnTop] places this surface above the camera's TextureView
 *   (CameraX [PreviewView] uses COMPATIBLE mode = TextureView).
 *
 * ## Render mode
 * [RENDERMODE_CONTINUOUSLY] is required because the fragment shader uses a
 * `u_Time` uniform for GPU-driven animations (breathing glow, scan line sweep,
 * heatmap pulse). Without continuous rendering the animations would freeze
 * between data updates.
 *
 * ## Gyroscope fusion (MODULE 4)
 * This view owns a [GyroSmoother] instance that is passed to [sdfRenderer].
 * The smoother runs on the device's `TYPE_GAME_ROTATION_VECTOR` sensor at
 * ~100 Hz, providing sub-frame camera-movement compensation that keeps overlay
 * quads spatially anchored between ML Kit frames.
 *
 * Lifecycle is managed automatically: [onPause] stops the sensor and the GL
 * thread; [onResume] restarts both.  The composable that hosts this view only
 * needs to forward the Activity lifecycle events — no additional wiring needed.
 */
class SdfOverlayView(context: Context) : GLSurfaceView(context) {

    /** Gyroscope sensor fusion — shared with [sdfRenderer]. */
    val gyroSmoother = GyroSmoother(context)

    val sdfRenderer = SdfOverlayRenderer(gyroSmoother)

    init {
        // OpenGL ES 2.0 — universally available on minSdk 24+
        setEGLContextClientVersion(2)

        // RGBA8888 with alpha channel for transparency compositing
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)

        // Make the surface transparent so the camera preview shows through
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)

        setRenderer(sdfRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Suspends the GL thread **and** the gyroscope sensor. */
    override fun onPause() {
        super.onPause()
        gyroSmoother.stop()
    }

    /** Resumes the GL thread **and** the gyroscope sensor. */
    override fun onResume() {
        super.onResume()
        gyroSmoother.start()
    }
}
