package com.app.ttsreader.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Lightweight gyroscope-based inter-frame position smoother for the AR overlay.
 *
 * ## Problem
 * ML Kit text recognition runs at ~10 fps (100 ms throttle).  Between frames
 * the overlay positions are stale — any camera movement during that 100 ms
 * window makes the text boxes drift from the physical text they annotate.
 *
 * ## Solution
 * The device's `TYPE_GAME_ROTATION_VECTOR` sensor delivers orientation at
 * ~100 Hz with **no magnetic drift** (gyroscope-only fusion, well-suited for
 * short-term prediction).  Every time a new ML Kit frame arrives we capture a
 * baseline orientation.  On every GL frame (~60 Hz) we compute how far the
 * camera has rotated since that baseline and convert the angular delta to an
 * image-space pixel shift.  This shift is added to every block's smoothed
 * position before the quad is rendered — making the overlay feel anchored to
 * the physical world even between ML Kit frames.
 *
 * ## Accuracy
 * Focal-length estimate: `imageWidth × 0.7` (calibrated for ~70° horizontal
 * FOV, typical for Android rear cameras).  Sign convention for portrait hold:
 * - Yaw right (phone pans right) → image content shifts left → negative X shift
 * - Pitch up (phone tilts up) → image content shifts down → positive Y shift
 *   (image-space Y increases downward).
 *
 * ## Threading
 * [pixelShiftX] / [pixelShiftY] are `@Volatile` floats — written on the sensor
 * thread, read on the GL thread.  Small racy windows (a 64-bit double read
 * split across two 32-bit stores) are inconsequential for a visual effect.
 *
 * [captureBaseline] is `@Synchronized` and safe to call from any thread.
 */
class GyroSmoother(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    // Quaternion [qx, qy, qz, qw] — guarded by lock
    private val baselineQ = FloatArray(4)
    private val currentQ  = FloatArray(4)
    private val lock      = Any()
    private var hasBaseline = false

    /** Pixel shift X since last [captureBaseline]. Read on GL thread. */
    @Volatile var pixelShiftX = 0f
        private set

    /** Pixel shift Y since last [captureBaseline]. Read on GL thread. */
    @Volatile var pixelShiftY = 0f
        private set

    /** Approximate focal length in pixels.  Update whenever image dimensions change. */
    @Volatile var focalLengthPx = 800f

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun start() {
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Records the current orientation as the reference pose for the next ML Kit
     * frame.  Resets pixel shift to zero.  Call this each time a fresh frame of
     * block data arrives (from the GL thread via [GLSurfaceView.queueEvent]).
     */
    fun captureBaseline() {
        synchronized(lock) {
            System.arraycopy(currentQ, 0, baselineQ, 0, 4)
            hasBaseline   = true
            pixelShiftX = 0f
            pixelShiftY = 0f
        }
    }

    // ── SensorEventListener ───────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        // TYPE_GAME_ROTATION_VECTOR: values = [qx, qy, qz] or [qx, qy, qz, qw]
        val qx = event.values[0]
        val qy = event.values[1]
        val qz = event.values[2]
        val qw = if (event.values.size > 3) event.values[3]
                 else Math.sqrt(
                     (1.0 - qx * qx - qy * qy - qz * qz).coerceAtLeast(0.0)
                 ).toFloat()

        synchronized(lock) {
            currentQ[0] = qx; currentQ[1] = qy
            currentQ[2] = qz; currentQ[3] = qw

            if (!hasBaseline) return

            // ── Delta quaternion: baseline^{-1} × current ─────────────────
            // Unit quaternion inverse = conjugate: q^{-1} = (-qx, -qy, -qz, qw)
            val bx = -baselineQ[0]; val by = -baselineQ[1]
            val bz = -baselineQ[2]; val bw =  baselineQ[3]

            // Hamilton product (bx,by,bz,bw) × (qx,qy,qz,qw)
            val dqx = bw * qx + bx * qw + by * qz - bz * qy
            val dqy = bw * qy - bx * qz + by * qw + bz * qx
            // dqz and dqw not needed for small-angle pixel shift

            // Small-angle approximation: rotation ≈ 2 × imaginary quaternion part
            // Portrait phone, back camera (rotationDegrees=90):
            //   dqy → yaw (pan left/right) → horizontal image shift
            //   dqx → pitch (tilt up/down)  → vertical image shift (image Y down)
            val fl = focalLengthPx
            pixelShiftX = -dqy * 2f * fl   // yaw right → scene shifts left
            pixelShiftY =  dqx * 2f * fl   // pitch up  → scene shifts down (image Y ↑)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
