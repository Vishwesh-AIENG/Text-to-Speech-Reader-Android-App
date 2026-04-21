package com.app.ttsreader.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

/**
 * 1024×1024 GL_ALPHA texture atlas that stores SDF (Signed Distance Field)
 * bitmaps for individual glyphs.
 *
 * ## Layout
 * The atlas is a 16×16 grid of 64×64-pixel cells.  Each cell holds one glyph.
 * That gives 256 slots — enough for typical translation output without eviction.
 * When the atlas is full, slots are reused in FIFO order (oldest glyph overwritten).
 *
 * ## SDF encoding
 * - **0.0** (alpha = 0)   → pixel is well outside the glyph, distance ≥ SPREAD
 * - **0.5** (alpha = 127) → pixel is exactly on the glyph edge
 * - **1.0** (alpha = 255) → pixel is well inside the glyph, distance ≥ SPREAD
 *
 * The fragment shader samples `.a` and passes it to `smoothstep` to produce
 * crisp, anti-aliased edges at any scale.
 *
 * ## Threading
 * - [create] and [release] must be called on the **GL thread**.
 * - [ensureGlyph] may be called from any thread.
 * - [uploadPending] must be called from the **GL thread** (typically `onDrawFrame`).
 * - SDF generation runs on a dedicated [HandlerThread] to avoid blocking the GL thread.
 *
 * ## Usage
 * ```
 * // onSurfaceCreated (GL thread)
 * atlas.create()
 *
 * // onDrawFrame (GL thread) — before drawing text quads
 * atlas.uploadPending()
 * for (char in text) {
 *     val uv = atlas.getUvRect(char) ?: continue  // queues generation if absent
 *     // draw glyph quad using uv
 * }
 *
 * // When GLSurfaceView is destroyed (GL thread, via renderer.release())
 * atlas.release()
 * ```
 */
class SdfAtlas {

    // ── Constants ──────────────────────────────────────────────────────────────

    companion object {
        private const val ATLAS_SIZE  = 1024
        private const val CELL_SIZE   = 64
        private const val GRID_SIZE   = ATLAS_SIZE / CELL_SIZE   // 16
        private const val MAX_SLOTS   = GRID_SIZE * GRID_SIZE    // 256
        private const val SPREAD      = 8f                       // SDF spread in pixels
        private const val IN_PROGRESS = -1                       // sentinel for charToSlot
    }

    // ── GL state (GL thread only) ──────────────────────────────────────────────

    private var textureId = 0

    // ── Slot management (guarded by [lock]) ───────────────────────────────────

    private val lock         = Any()
    private val charToSlot   = HashMap<Char, Int>()         // char → slot or IN_PROGRESS
    private val slotToChar   = arrayOfNulls<Char>(MAX_SLOTS) // reverse map for eviction
    private var nextFreeSlot = 0                             // FIFO cursor, wraps mod MAX_SLOTS

    // ── Cross-thread upload queue ──────────────────────────────────────────────

    private val pendingUploads = ConcurrentLinkedQueue<PendingGlyph>()

    // ── Background generation thread ──────────────────────────────────────────

    private var glyphThread: HandlerThread? = null
    private var glyphHandler: Handler?      = null

    // ── Glyph info returned to callers ────────────────────────────────────────

    /** UV bounds of a glyph cell within the atlas texture ([0,1] × [0,1]). */
    data class GlyphInfo(val uvRect: RectF)

    // ── Internal upload record ────────────────────────────────────────────────

    private class PendingGlyph(
        val char:  Char,
        val slot:  Int,
        val alpha: ByteArray   // CELL_SIZE × CELL_SIZE 8-bit SDF
    )

    // ── Lifecycle (GL thread) ─────────────────────────────────────────────────

    /**
     * Creates the 1024×1024 GL_ALPHA atlas texture and starts the background
     * glyph-generation thread.  **Must be called on the GL thread.**
     */
    fun create() {
        // Create GL texture
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,     GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,     GLES20.GL_CLAMP_TO_EDGE)
        // Zero-initialise — prevents artefacts on unoccupied cells
        val zeros = ByteBuffer.allocateDirect(ATLAS_SIZE * ATLAS_SIZE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0,
            GLES20.GL_ALPHA, ATLAS_SIZE, ATLAS_SIZE, 0,
            GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE, zeros
        )

        // Start background thread
        val ht = HandlerThread("sdf-glyph-gen").also { it.start() }
        glyphThread  = ht
        glyphHandler = Handler(ht.looper)
    }

    /**
     * Drains the upload queue and pushes pending glyph bitmaps to the GPU.
     * **Must be called on the GL thread**, typically at the start of `onDrawFrame`.
     */
    fun uploadPending() {
        if (textureId == 0) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        while (true) {
            val p = pendingUploads.poll() ?: break
            val col = p.slot % GRID_SIZE
            val row = p.slot / GRID_SIZE
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D, 0,
                col * CELL_SIZE, row * CELL_SIZE,
                CELL_SIZE, CELL_SIZE,
                GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(p.alpha)
            )
            // Mark slot as ready
            synchronized(lock) { charToSlot[p.char] = p.slot }
        }
    }

    /**
     * Returns the [GlyphInfo] for [char] if it is already in the atlas.
     * If not, schedules SDF generation in the background and returns `null`.
     * Returns `null` while generation is in progress.
     *
     * **Thread-safe** — may be called from any thread.
     */
    fun getUvRect(char: Char): GlyphInfo? {
        val slot = synchronized(lock) { charToSlot[char] }
        if (slot != null && slot != IN_PROGRESS) return slotToUvRect(slot)
        if (slot == null) scheduleGeneration(char)
        return null   // null while in-progress too
    }

    /** Texture handle for binding in the shader. */
    fun textureId(): Int = textureId

    /**
     * Releases the GL texture and stops the background thread.
     * **Must be called on the GL thread.**
     */
    fun release() {
        glyphThread?.quit()
        glyphThread  = null
        glyphHandler = null
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        synchronized(lock) {
            charToSlot.clear()
            slotToChar.fill(null)
            nextFreeSlot = 0
        }
        pendingUploads.clear()
    }

    // ── Private: slot allocation ───────────────────────────────────────────────

    /**
     * Allocates a slot for [char] and schedules background SDF generation.
     * No-op if [char] already has an entry (ready or in-progress).
     */
    private fun scheduleGeneration(char: Char) {
        val handler = glyphHandler ?: return

        val slot = synchronized(lock) {
            if (char in charToSlot) return   // race: already scheduled
            // Claim next slot (FIFO wrap-around eviction)
            val s = nextFreeSlot
            nextFreeSlot = (nextFreeSlot + 1) % MAX_SLOTS
            // Evict the previous occupant of this slot (if any)
            slotToChar[s]?.let { evicted -> charToSlot.remove(evicted) }
            slotToChar[s] = char
            charToSlot[char] = IN_PROGRESS
            s
        }

        handler.post {
            val sdfData = generateSdf(char)
            pendingUploads.add(PendingGlyph(char, slot, sdfData))
        }
    }

    // ── Private: SDF generation (background thread) ───────────────────────────

    /**
     * Rasterises [char] onto a 64×64 bitmap, then computes a signed-distance
     * field via a brute-force neighbourhood scan (spread = [SPREAD] pixels).
     *
     * Runtime: ~0.5–1 ms on a mid-range device for a 64×64 glyph.
     */
    private fun generateSdf(char: Char): ByteArray {
        // ── Step 1: rasterise to binary ───────────────────────────────────────
        val bmp    = Bitmap.createBitmap(CELL_SIZE, CELL_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.WHITE
            textSize = (CELL_SIZE - 12).toFloat()   // leave 6px padding each side
            textAlign = Paint.Align.CENTER
        }
        val fm    = paint.fontMetrics
        val baseY = CELL_SIZE / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(char.toString(), CELL_SIZE / 2f, baseY, paint)

        val pixels = IntArray(CELL_SIZE * CELL_SIZE)
        bmp.getPixels(pixels, 0, CELL_SIZE, 0, 0, CELL_SIZE, CELL_SIZE)
        bmp.recycle()

        // Binary inside/outside — alpha channel > 50%
        val inside = BooleanArray(CELL_SIZE * CELL_SIZE) {
            (pixels[it] ushr 24) > 127
        }

        // ── Step 2: brute-force EDT with spread radius ────────────────────────
        val spreadInt = SPREAD.toInt()
        val spreadSq  = (spreadInt + 1) * (spreadInt + 1).toFloat()
        val result    = ByteArray(CELL_SIZE * CELL_SIZE)

        for (py in 0 until CELL_SIZE) {
            for (px in 0 until CELL_SIZE) {
                val idx   = py * CELL_SIZE + px
                val isIn  = inside[idx]
                var minSq = spreadSq

                val x0 = maxOf(0, px - spreadInt)
                val x1 = minOf(CELL_SIZE - 1, px + spreadInt)
                val y0 = maxOf(0, py - spreadInt)
                val y1 = minOf(CELL_SIZE - 1, py + spreadInt)

                for (qy in y0..y1) {
                    val dy = (qy - py).toFloat()
                    if (dy * dy >= minSq) continue          // early row skip
                    for (qx in x0..x1) {
                        if (inside[qy * CELL_SIZE + qx] != isIn) {
                            val dx = (qx - px).toFloat()
                            val dSq = dx * dx + dy * dy
                            if (dSq < minSq) minSq = dSq
                        }
                    }
                }

                val dist   = sqrt(minSq.toDouble()).toFloat().coerceAtMost(SPREAD)
                val signed = if (isIn) dist else -dist
                val norm   = (0.5f + 0.5f * signed / SPREAD).coerceIn(0f, 1f)
                result[idx] = (norm * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            }
        }

        return result
    }

    // ── Private: UV computation ────────────────────────────────────────────────

    private fun slotToUvRect(slot: Int): GlyphInfo {
        val col = slot % GRID_SIZE
        val row = slot / GRID_SIZE
        val u0  = col.toFloat() / GRID_SIZE
        val v0  = row.toFloat() / GRID_SIZE
        return GlyphInfo(RectF(u0, v0, u0 + 1f / GRID_SIZE, v0 + 1f / GRID_SIZE))
    }
}
