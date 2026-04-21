package com.app.ttsreader.gl

import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.app.ttsreader.camera.GyroSmoother
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for the full SDF overlay pipeline.
 *
 * ## Rendering pipeline
 *
 * ### Pass 1 — Box / Chrome program ([Shaders.BOX_FRAG])
 * Renders all non-text geometry in [Shaders.MODE_*]-selected styles:
 * - AR Lens: semi-transparent neon fill + pulsing border + ambient glow halo
 * - Indexing scanning: dim overlay + animated scan-line beam
 * - Indexing matches: dim overlay + heatmap fill + pulsing border + corner reticles
 *
 * ### Pass 2 — Glyph program ([Shaders.GLYPH_FRAG])
 * Renders translated-text characters from the SDF atlas as sharp,
 * anti-aliased neon-green glyphs positioned above each AR block.
 *
 * ## Gyroscope compensation (MODULE 4)
 * Between ML Kit frames (~100 ms apart), the device's gyroscope provides
 * orientation deltas at ~100 Hz.  Each draw call reads [gyroSmoother]'s
 * pixel-shift and adds it to every block's smoothed bounding box before
 * computing clip-space vertices.  When new ML Kit data arrives,
 * [updateArLensData] calls [GyroSmoother.captureBaseline] to reset the delta.
 *
 * ## Threading
 * - All `update*Data` methods must be called via [GLSurfaceView.queueEvent]
 *   (i.e. on the GL thread) — no additional locking is needed inside them.
 * - [gyroSmoother]'s pixel-shift fields are `@Volatile`; reading them from
 *   [onDrawFrame] without a lock is safe (worst case: one stale frame).
 */
class SdfOverlayRenderer(val gyroSmoother: GyroSmoother) : GLSurfaceView.Renderer {

    // ── Screen dimensions (updated in onSurfaceChanged) ───────────────────────

    @Volatile var screenWidth  = 0
    @Volatile var screenHeight = 0

    // ── Operating mode (set once from the composable factory block) ───────────

    @Volatile var mode: OverlayMode = OverlayMode.AR_LENS

    // ── Wall-clock origin for u_Time ──────────────────────────────────────────

    private var startTimeNanos = 0L

    // ── Snapshot data — written + read exclusively on the GL thread ───────────

    // AR Lens
    private var arBlocks:     List<ArBlockSnapshot> = emptyList()
    private var arImageWidth  = 1
    private var arImageHeight = 1

    // Indexing
    private var ixMatches:    List<IxMatchSnapshot> = emptyList()
    private var ixImageWidth  = 1
    private var ixImageHeight = 1
    private var ixIsScanning  = false

    // ── GL resources (created in onSurfaceCreated, released on context loss) ──

    private var atlas:        SdfAtlas?      = null
    private var boxProgram:   ShaderProgram? = null   // Pass 1 — all non-text geometry
    private var glyphProgram: ShaderProgram? = null   // Pass 2 — SDF atlas text

    // Box-program uniform / attrib locations
    private var boxAttrPos   = 0; private var boxAttrTex   = 0
    private var boxUniMode   = 0; private var boxUniAlpha  = 0
    private var boxUniTime   = 0; private var boxUniScore  = 0
    private var boxUniBorder = 0

    // Glyph-program uniform / attrib locations
    private var glyphAttrPos  = 0; private var glyphAttrTex  = 0
    private var glyphUniAtlas = 0; private var glyphUniAlpha = 0

    // Two dedicated VBOs — one per pass — so each pass's geometry is resident
    // on the GPU independently and neither overwrites the other's buffer.
    private val boxVboId   = IntArray(1)
    private val glyphVboId = IntArray(1)

    // Pre-allocated CPU-side vertex buffers (reused every upload)
    private lateinit var boxVertexBuffer:   FloatBuffer
    private lateinit var glyphVertexBuffer: FloatBuffer

    // ── VBO dirty tracking — skip re-upload when geometry hasn't changed ──────

    /**
     * Set to true whenever block/match data changes; cleared after each
     * successful VBO upload on the GL thread.  Only the geometry (vertex
     * positions) is re-uploaded on dirty frames — uniform writes (u_Time,
     * u_Alpha, etc.) happen every frame regardless, so animations continue
     * running without a full re-upload.
     */
    private var geometryDirty = true

    /** Cached render commands from the last geometry build. */
    private var cachedBoxCmds:   List<BoxCmd>   = emptyList()
    private var cachedGlyphCmds: List<GlyphCmd> = emptyList()

    // ── GLSurfaceView.Renderer callbacks ──────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        releaseGlResources()

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        startTimeNanos = System.nanoTime()

        // Atlas — infrastructure ready, Module 3 populates it with glyph SDFs
        atlas = SdfAtlas().also { it.create() }

        // ── Pass-1 box program ─────────────────────────────────────────────
        boxProgram = ShaderProgram(Shaders.QUAD_VERT, Shaders.BOX_FRAG).also { p ->
            boxAttrPos   = p.getAttribLocation("a_Position")
            boxAttrTex   = p.getAttribLocation("a_TexCoord")
            boxUniMode   = p.getUniformLocation("u_Mode")
            boxUniAlpha  = p.getUniformLocation("u_Alpha")
            boxUniTime   = p.getUniformLocation("u_Time")
            boxUniScore  = p.getUniformLocation("u_Score")
            boxUniBorder = p.getUniformLocation("u_BorderFrac")
        }

        // ── Pass-2 glyph program ───────────────────────────────────────────
        glyphProgram = ShaderProgram(Shaders.QUAD_VERT, Shaders.GLYPH_FRAG).also { p ->
            glyphAttrPos  = p.getAttribLocation("a_Position")
            glyphAttrTex  = p.getAttribLocation("a_TexCoord")
            glyphUniAtlas = p.getUniformLocation("u_Atlas")
            glyphUniAlpha = p.getUniformLocation("u_Alpha")
        }

        // Two dedicated VBOs — pre-allocated for MAX_QUADS quads each
        GLES20.glGenBuffers(1, boxVboId, 0)
        GLES20.glGenBuffers(1, glyphVboId, 0)
        boxVertexBuffer   = QuadGeometry.allocateVertexBuffer(MAX_QUADS)
        glyphVertexBuffer = QuadGeometry.allocateVertexBuffer(MAX_QUADS)

        // Force geometry rebuild on the next draw (new GL context)
        geometryDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth  = width
        screenHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val sw = screenWidth.toFloat()
        val sh = screenHeight.toFloat()
        if (sw <= 0f || sh <= 0f) return

        atlas?.uploadPending()

        val tSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000f

        // ── Rebuild geometry and re-upload VBOs only when data has changed ─
        if (geometryDirty) {
            val boxCmds   = mutableListOf<BoxCmd>()
            val glyphCmds = mutableListOf<GlyphCmd>()

            when (mode) {
                OverlayMode.AR_LENS  -> buildArLensCommands(boxCmds, glyphCmds, sw, sh)
                OverlayMode.INDEXING -> buildIndexingCommands(boxCmds, sw, sh)
            }

            cachedBoxCmds   = boxCmds
            cachedGlyphCmds = glyphCmds

            // Upload box geometry to its dedicated VBO
            if (boxCmds.isNotEmpty()) {
                boxVertexBuffer.clear()
                boxCmds.forEach { boxVertexBuffer.put(it.vertices) }
                boxVertexBuffer.flip()
                val bytes = boxCmds.size * QuadGeometry.FLOATS_PER_QUAD * Float.SIZE_BYTES
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, boxVboId[0])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bytes, boxVertexBuffer, GLES20.GL_DYNAMIC_DRAW)
            }

            // Upload glyph geometry to its dedicated VBO
            if (glyphCmds.isNotEmpty()) {
                glyphVertexBuffer.clear()
                glyphCmds.forEach { glyphVertexBuffer.put(it.vertices) }
                glyphVertexBuffer.flip()
                val bytes = glyphCmds.size * QuadGeometry.FLOATS_PER_QUAD * Float.SIZE_BYTES
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glyphVboId[0])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bytes, glyphVertexBuffer, GLES20.GL_DYNAMIC_DRAW)
            }

            geometryDirty = false
        }

        if (cachedBoxCmds.isEmpty() && cachedGlyphCmds.isEmpty()) return

        // Shared GL state for both passes
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // ── Pass 1: Box / chrome ───────────────────────────────────────────
        if (cachedBoxCmds.isNotEmpty()) {
            drawBoxPass(tSec)
        }

        // ── Pass 2: SDF glyph text ─────────────────────────────────────────
        if (cachedGlyphCmds.isNotEmpty()) {
            drawGlyphPass()
        }

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // ── Data update methods (call via queueEvent — runs on GL thread) ─────────

    fun updateArLensData(
        blocks:      List<ArBlockSnapshot>,
        imageWidth:  Int,
        imageHeight: Int
    ) {
        arBlocks      = blocks
        arImageWidth  = imageWidth.coerceAtLeast(1)
        arImageHeight = imageHeight.coerceAtLeast(1)
        geometryDirty = true
        // Capture gyro baseline so the shift resets for this new frame
        gyroSmoother.focalLengthPx = arImageWidth * 0.7f
        gyroSmoother.captureBaseline()
    }

    fun updateIndexingData(
        matches:     List<IxMatchSnapshot>,
        imageWidth:  Int,
        imageHeight: Int,
        isScanning:  Boolean
    ) {
        ixMatches     = matches
        ixImageWidth  = imageWidth.coerceAtLeast(1)
        ixImageHeight = imageHeight.coerceAtLeast(1)
        ixIsScanning  = isScanning
        geometryDirty = true
    }

    fun release() = releaseGlResources()

    // ── Pass 1: AR Lens command builder ───────────────────────────────────────

    private fun buildArLensCommands(
        boxCmds:   MutableList<BoxCmd>,
        glyphCmds: MutableList<GlyphCmd>,
        sw: Float, sh: Float
    ) {
        if (arBlocks.isEmpty()) return

        val imgW  = arImageWidth.toFloat()
        val imgH  = arImageHeight.toFloat()
        val scale = maxOf(sw / imgW, sh / imgH)
        val ox    = (sw - imgW * scale) / 2f
        val oy    = (sh - imgH * scale) / 2f

        // Gyro inter-frame compensation (MODULE 4)
        val gx = gyroSmoother.pixelShiftX
        val gy = gyroSmoother.pixelShiftY

        for (block in arBlocks) {
            if (block.displayAlpha < 0.01f) continue

            // Apply gyro shift to smoothed box (image space → screen space with shift)
            val box = block.smoothedBox
            val sL = box.left   * scale + ox + gx
            val sT = box.top    * scale + oy + gy
            val sR = box.right  * scale + ox + gx
            val sB = box.bottom * scale + oy + gy

            // ── Ambient glow halo (drawn first / underneath) ─────────────
            val boxW  = sR - sL
            val boxH  = sB - sT
            val glowPad = maxOf(boxW, boxH) * 0.55f
            boxCmds += BoxCmd(
                vertices   = QuadGeometry.screenRectToClipQuad(
                    sL - glowPad, sT - glowPad,
                    boxW + glowPad * 2f, boxH + glowPad * 2f, sw, sh
                ),
                mode        = Shaders.MODE_GLOW,
                alpha       = block.displayAlpha * 0.60f
            )

            // ── Block fill + pulsing neon border ─────────────────────────
            val fillVerts = if (block.cornerPoints != null && block.cornerPoints.size == 4) {
                QuadGeometry.cornerPointsToClipQuad(block.cornerPoints, imgW, imgH, sw, sh)
                    .also { applyGyroShiftToClipQuad(it, gx, gy, sw, sh) }
            } else {
                QuadGeometry.screenRectToClipQuad(sL, sT, boxW, boxH, sw, sh)
            }
            boxCmds += BoxCmd(
                vertices   = fillVerts,
                mode        = Shaders.MODE_AR_FILL,
                alpha       = block.displayAlpha,
                borderFrac  = BORDER_FRAC
            )

            // ── SDF glyph quads for translated text ───────────────────────
            // Threshold kept low (0.05) so text appears as soon as the box
            // fades in, rather than waiting for the box to reach 30% opacity.
            val text = block.translatedText
            if (text.isNotEmpty() && block.displayAlpha > 0.05f) {
                buildGlyphRow(
                    text       = text,
                    startX     = sL,
                    topY       = sT,
                    boxWidth   = boxW,
                    blockAlpha = block.displayAlpha,
                    sw = sw, sh = sh,
                    out        = glyphCmds
                )
            }
        }
    }

    // ── Pass 1: Indexing command builder ──────────────────────────────────────

    private fun buildIndexingCommands(
        boxCmds: MutableList<BoxCmd>,
        sw: Float, sh: Float
    ) {
        if (!ixIsScanning) return

        val imgW  = ixImageWidth.toFloat()
        val imgH  = ixImageHeight.toFloat()

        if (ixMatches.isEmpty()) {
            // ── Scanning state: dim + animated scan line ──────────────────
            boxCmds += BoxCmd(
                vertices = QuadGeometry.FULL_SCREEN_QUAD,
                mode     = Shaders.MODE_DIM,
                alpha    = 0.28f
            )
            boxCmds += BoxCmd(
                vertices = QuadGeometry.FULL_SCREEN_QUAD,
                mode     = Shaders.MODE_SCAN,
                alpha    = 1.0f
            )
        } else {
            // ── Match state: heavy dim + heatmap boxes + corner reticles ──
            boxCmds += BoxCmd(
                vertices = QuadGeometry.FULL_SCREEN_QUAD,
                mode     = Shaders.MODE_DIM,
                alpha    = 0.68f
            )

            val scale = maxOf(sw / imgW, sh / imgH)
            val ox    = (sw - imgW * scale) / 2f
            val oy    = (sh - imgH * scale) / 2f
            val pad   = 6f

            for (match in ixMatches.take(MAX_MATCHES)) {
                val b  = match.box
                val sL = b.left   * scale + ox - pad
                val sT = b.top    * scale + oy - pad
                val sR = b.right  * scale + ox + pad
                val sB = b.bottom * scale + oy + pad
                val bW = sR - sL
                val bH = sB - sT

                // Ambient glow
                val glowPad = maxOf(bW, bH) * 0.60f
                boxCmds += BoxCmd(
                    vertices  = QuadGeometry.screenRectToClipQuad(
                        sL - glowPad, sT - glowPad,
                        bW + glowPad * 2f, bH + glowPad * 2f, sw, sh
                    ),
                    mode  = Shaders.MODE_GLOW,
                    alpha = 0.55f,
                    score = match.score
                )

                // Heatmap fill + pulsing border
                val matchVerts = if (match.cornerPoints != null && match.cornerPoints.size == 4) {
                    QuadGeometry.cornerPointsToClipQuad(match.cornerPoints, imgW, imgH, sw, sh)
                } else {
                    QuadGeometry.screenRectToClipQuad(sL, sT, bW, bH, sw, sh)
                }
                boxCmds += BoxCmd(
                    vertices   = matchVerts,
                    mode        = Shaders.MODE_IX_FILL,
                    alpha       = 1.0f,
                    score       = match.score,
                    borderFrac  = BORDER_FRAC
                )

                // Corner reticles (8 thin line quads)
                appendCornerReticles(boxCmds, sL, sT, sR, sB, sw, sh)
            }
        }
    }

    // ── Glyph layout helper ───────────────────────────────────────────────────

    /**
     * Places a row of SDF glyph quads for [text] above (or inside) a block.
     * Queues SDF generation for any glyph not yet in the atlas.
     */
    private fun buildGlyphRow(
        text: String, startX: Float, topY: Float,
        boxWidth: Float, blockAlpha: Float,
        sw: Float, sh: Float,
        out: MutableList<GlyphCmd>
    ) {
        val glyphH  = GLYPH_HEIGHT_PX
        val glyphW  = glyphH * GLYPH_ASPECT
        val maxChars = (boxWidth / glyphW).toInt().coerceAtLeast(1)
        val display  = text.take(maxChars)

        // Place text just above the block; flip below if too close to top
        val textY = if (topY - glyphH - GLYPH_GAP_PX >= 0f)
            topY - glyphH - GLYPH_GAP_PX
        else
            topY + GLYPH_GAP_PX   // fall back: inside top of block

        // Center horizontally over the box
        val totalW  = display.length * glyphW
        val x0Base  = (startX + boxWidth / 2f - totalW / 2f).coerceAtLeast(0f)

        display.forEachIndexed { i, char ->
            val glyph = atlas?.getUvRect(char) ?: return@forEachIndexed   // queues SDF generation
            val x0 = x0Base + i * glyphW
            val x1 = x0 + glyphW
            val y0 = textY
            val y1 = textY + glyphH

            fun ndcX(x: Float) = (x / sw) * 2f - 1f
            fun ndcY(y: Float) = 1f - (y / sh) * 2f

            out += GlyphCmd(
                vertices = floatArrayOf(
                    ndcX(x0), ndcY(y0), glyph.uvRect.left,  glyph.uvRect.top,
                    ndcX(x1), ndcY(y0), glyph.uvRect.right, glyph.uvRect.top,
                    ndcX(x0), ndcY(y1), glyph.uvRect.left,  glyph.uvRect.bottom,
                    ndcX(x1), ndcY(y1), glyph.uvRect.right, glyph.uvRect.bottom
                ),
                alpha = blockAlpha
            )
        }
    }

    // ── Corner reticle helper ─────────────────────────────────────────────────

    /** Appends 8 thin line quads (2 per corner) to [out]. */
    private fun appendCornerReticles(
        out: MutableList<BoxCmd>,
        x0: Float, y0: Float, x1: Float, y1: Float,
        sw: Float, sh: Float
    ) {
        val cL = CORNER_LEN_PX
        val lH = LINE_WIDTH_PX / 2f   // half line width

        fun h(lx: Float, ly: Float, len: Float) =   // horizontal line quad
            QuadGeometry.screenRectToClipQuad(lx, ly - lH, len, LINE_WIDTH_PX, sw, sh)

        fun v(lx: Float, ly: Float, len: Float) =   // vertical line quad
            QuadGeometry.screenRectToClipQuad(lx - lH, ly, LINE_WIDTH_PX, len, sw, sh)

        val flat = BoxCmd(QuadGeometry.FULL_SCREEN_QUAD, Shaders.MODE_FLAT, 1.0f)

        // TL
        out += flat.copy(vertices = h(x0,      y0,      cL))
        out += flat.copy(vertices = v(x0,      y0,      cL))
        // TR
        out += flat.copy(vertices = h(x1 - cL, y0,      cL))
        out += flat.copy(vertices = v(x1,      y0,      cL))
        // BL
        out += flat.copy(vertices = h(x0,      y1,      cL))
        out += flat.copy(vertices = v(x0,      y1 - cL, cL))
        // BR
        out += flat.copy(vertices = h(x1 - cL, y1,      cL))
        out += flat.copy(vertices = v(x1,      y1 - cL, cL))
    }

    // ── Draw passes (bind pre-uploaded VBO, set uniforms, issue draw calls) ───

    private fun drawBoxPass(tSec: Float) {
        val cmds = cachedBoxCmds
        if (cmds.isEmpty()) return
        val prog = boxProgram ?: return

        prog.use()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, boxVboId[0])
        val stride = QuadGeometry.FLOATS_PER_VERTEX * Float.SIZE_BYTES
        GLES20.glEnableVertexAttribArray(boxAttrPos)
        GLES20.glEnableVertexAttribArray(boxAttrTex)
        GLES20.glVertexAttribPointer(boxAttrPos, 2, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glVertexAttribPointer(boxAttrTex, 2, GLES20.GL_FLOAT, false, stride, 2 * Float.SIZE_BYTES)

        GLES20.glUniform1f(boxUniTime, tSec)

        for ((i, cmd) in cmds.withIndex()) {
            GLES20.glUniform1f(boxUniMode,   cmd.mode)
            GLES20.glUniform1f(boxUniAlpha,  cmd.alpha)
            GLES20.glUniform1f(boxUniScore,  cmd.score)
            GLES20.glUniform1f(boxUniBorder, cmd.borderFrac)
            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                i * QuadGeometry.VERTICES_PER_QUAD,
                QuadGeometry.VERTICES_PER_QUAD
            )
        }

        GLES20.glDisableVertexAttribArray(boxAttrPos)
        GLES20.glDisableVertexAttribArray(boxAttrTex)
    }

    private fun drawGlyphPass() {
        val cmds  = cachedGlyphCmds
        if (cmds.isEmpty()) return
        val prog  = glyphProgram ?: return
        val atl   = atlas ?: return
        val texId = atl.textureId()
        if (texId == 0) return

        prog.use()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, glyphVboId[0])

        // Bind atlas texture to unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(glyphUniAtlas, 0)

        val stride = QuadGeometry.FLOATS_PER_VERTEX * Float.SIZE_BYTES
        GLES20.glEnableVertexAttribArray(glyphAttrPos)
        GLES20.glEnableVertexAttribArray(glyphAttrTex)
        GLES20.glVertexAttribPointer(glyphAttrPos, 2, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glVertexAttribPointer(glyphAttrTex, 2, GLES20.GL_FLOAT, false, stride, 2 * Float.SIZE_BYTES)

        for ((i, cmd) in cmds.withIndex()) {
            GLES20.glUniform1f(glyphUniAlpha, cmd.alpha)
            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                i * QuadGeometry.VERTICES_PER_QUAD,
                QuadGeometry.VERTICES_PER_QUAD
            )
        }

        GLES20.glDisableVertexAttribArray(glyphAttrPos)
        GLES20.glDisableVertexAttribArray(glyphAttrTex)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    // ── GL resource management ─────────────────────────────────────────────────

    private fun releaseGlResources() {
        boxProgram?.release();   boxProgram   = null
        glyphProgram?.release(); glyphProgram = null
        atlas?.release();        atlas        = null
        if (boxVboId[0] != 0) {
            GLES20.glDeleteBuffers(1, boxVboId, 0)
            boxVboId[0] = 0
        }
        if (glyphVboId[0] != 0) {
            GLES20.glDeleteBuffers(1, glyphVboId, 0)
            glyphVboId[0] = 0
        }
    }

    // ── Gyro shift application to clip-space quad ──────────────────────────────

    /**
     * Nudges the X/Y components of a 16-float clip-space quad (built from
     * [QuadGeometry.cornerPointsToClipQuad]) by the gyro pixel shift.
     * The shift is first converted from pixel-space to NDC-space.
     */
    private fun applyGyroShiftToClipQuad(
        verts: FloatArray, gx: Float, gy: Float, sw: Float, sh: Float
    ) {
        val ndcDx = gx / sw * 2f
        val ndcDy = -(gy / sh * 2f)   // NDC Y is inverted relative to screen Y
        // Each vertex: [ndcX, ndcY, u, v] — floats 0,1 are position
        for (i in 0 until QuadGeometry.VERTICES_PER_QUAD) {
            verts[i * QuadGeometry.FLOATS_PER_VERTEX + 0] += ndcDx
            verts[i * QuadGeometry.FLOATS_PER_VERTEX + 1] += ndcDy
        }
    }

    // ── Companion ──────────────────────────────────────────────────────────────

    companion object {
        private const val MAX_QUADS      = 256    // max quads per frame in VBO
        private const val MAX_MATCHES    = 12     // max highlighted Indexing matches
        private const val BORDER_FRAC    = 0.03f  // 3 % of UV space ≈ 2–4 px border
        private const val GLYPH_HEIGHT_PX = 22f   // translated text glyph height (px)
        private const val GLYPH_ASPECT   = 0.56f  // width / height ratio
        private const val GLYPH_GAP_PX   = 4f     // gap between glyph row and box
        private const val CORNER_LEN_PX  = 14f    // corner reticle arm length (px)
        private const val LINE_WIDTH_PX  = 3f     // corner reticle line width (px)
    }

    // ── Internal render command types ──────────────────────────────────────────

    private data class BoxCmd(
        val vertices:   FloatArray,
        val mode:       Float,
        val alpha:      Float,
        val score:      Float = 0f,
        val borderFrac: Float = 0f
    )

    private data class GlyphCmd(
        val vertices: FloatArray,
        val alpha:    Float
    )
}

// ── Overlay mode enum ─────────────────────────────────────────────────────────

enum class OverlayMode { AR_LENS, INDEXING }

// ── Snapshot types for cross-thread data transfer ─────────────────────────────

@Suppress("ArrayInDataClass")
data class ArBlockSnapshot(
    val smoothedBox:    RectF,
    val displayAlpha:   Float,
    val translatedText: String,
    val cornerPoints:   Array<Point>? = null
)

@Suppress("ArrayInDataClass")
data class IxMatchSnapshot(
    val box:          Rect,
    val score:        Float,
    val cornerPoints: Array<Point>? = null
)
