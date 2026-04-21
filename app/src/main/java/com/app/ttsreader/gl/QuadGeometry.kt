package com.app.ttsreader.gl

import android.graphics.Point
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Converts image-space bounding boxes to clip-space quad vertices for GL rendering.
 *
 * ## Coordinate mapping (FILL_CENTER)
 * CameraX [PreviewView] in FILL_CENTER mode scales the image so it fills the
 * screen completely, then centres it.  Out-of-screen pixels are cropped:
 * ```
 * scale   = max(screenW / imgW, screenH / imgH)
 * offsetX = (screenW − imgW × scale) / 2
 * offsetY = (screenH − imgH × scale) / 2
 * screenX = imgX × scale + offsetX
 * screenY = imgY × scale + offsetY
 * ```
 *
 * ## NDC conversion (OpenGL)
 * GL clip space is [−1, +1] with Y pointing **up**:
 * ```
 * ndcX = (screenX / screenW) * 2 − 1
 * ndcY = 1 − (screenY / screenH) * 2       // Y-axis flip
 * ```
 *
 * ## Vertex layout (GL_TRIANGLE_STRIP, 4 vertices per quad)
 * ```
 * 0(TL) ─── 1(TR)
 *   │  ╲      │
 *   │    ╲    │
 * 2(BL) ─── 3(BR)
 * ```
 * Each vertex: [ndcX, ndcY, u, v] — [FLOATS_PER_VERTEX] floats.
 * The strip draws triangle (0,1,2) then (1,2,3), covering the rectangle.
 */
object QuadGeometry {

    const val FLOATS_PER_VERTEX = 4                                 // x, y, u, v
    const val VERTICES_PER_QUAD = 4                                 // GL_TRIANGLE_STRIP
    const val FLOATS_PER_QUAD   = FLOATS_PER_VERTEX * VERTICES_PER_QUAD  // 16

    /**
     * Maps an axis-aligned image-space [box] to 4 clip-space vertices.
     *
     * @return 16-float array — [x,y,u,v] × [TL, TR, BL, BR]
     */
    fun rectToClipQuad(
        box:     RectF,
        imgW:    Float,
        imgH:    Float,
        screenW: Float,
        screenH: Float
    ): FloatArray {
        val (ndcLeft, ndcTop, ndcRight, ndcBottom) = imageRectToNdc(
            box.left, box.top, box.right, box.bottom,
            imgW, imgH, screenW, screenH
        )
        return floatArrayOf(
            ndcLeft,  ndcTop,    0f, 0f,   // TL
            ndcRight, ndcTop,    1f, 0f,   // TR
            ndcLeft,  ndcBottom, 0f, 1f,   // BL
            ndcRight, ndcBottom, 1f, 1f    // BR
        )
    }

    /**
     * Maps ML Kit [cornerPoints] (TL, TR, BR, BL order from the API) to 4
     * clip-space vertices for perspective-correct quad rendering (Module 4).
     */
    fun cornerPointsToClipQuad(
        corners: Array<Point>,
        imgW:    Float,
        imgH:    Float,
        screenW: Float,
        screenH: Float
    ): FloatArray {
        val scale   = maxOf(screenW / imgW, screenH / imgH)
        val offsetX = (screenW - imgW * scale) / 2f
        val offsetY = (screenH - imgH * scale) / 2f

        fun ndcX(px: Int) = ((px * scale + offsetX) / screenW) * 2f - 1f
        fun ndcY(py: Int) = 1f - ((py * scale + offsetY) / screenH) * 2f

        // ML Kit order: [0]=TL, [1]=TR, [2]=BR, [3]=BL
        val tl = corners[0]; val tr = corners[1]
        val br = corners[2]; val bl = corners[3]

        return floatArrayOf(
            ndcX(tl.x), ndcY(tl.y), 0f, 0f,   // TL
            ndcX(tr.x), ndcY(tr.y), 1f, 0f,   // TR
            ndcX(bl.x), ndcY(bl.y), 0f, 1f,   // BL
            ndcX(br.x), ndcY(br.y), 1f, 1f    // BR
        )
    }

    /**
     * Packs a list of quad vertex arrays into a single direct [FloatBuffer]
     * ready for a VBO upload.
     */
    fun packQuads(quads: List<FloatArray>): FloatBuffer {
        val total = quads.sumOf { it.size }
        return ByteBuffer
            .allocateDirect(total * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { quads.forEach { put(it) }; position(0) }
    }

    /**
     * Allocates a reusable direct [FloatBuffer] for [maxQuads] quads.
     * Call [FloatBuffer.clear] + [FloatBuffer.put] each frame, then
     * [FloatBuffer.flip] before uploading.
     */
    fun allocateVertexBuffer(maxQuads: Int): FloatBuffer =
        ByteBuffer
            .allocateDirect(maxQuads * FLOATS_PER_QUAD * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    /**
     * Converts a screen-space rect `(sx, sy, width, height)` directly to
     * 4 clip-space vertices.  Used for elements whose position is already
     * known in pixels (corner reticles, glyph quads, scan-line beam, etc.).
     *
     * @param sx      Screen X of the top-left corner (pixels, increasing right)
     * @param sy      Screen Y of the top-left corner (pixels, increasing down)
     * @param sw      Rect width in pixels
     * @param sh      Rect height in pixels
     * @param screenW Total screen width in pixels
     * @param screenH Total screen height in pixels
     */
    fun screenRectToClipQuad(
        sx: Float, sy: Float,
        sw: Float, sh: Float,
        screenW: Float, screenH: Float
    ): FloatArray {
        fun ndcX(x: Float) = (x / screenW) * 2f - 1f
        fun ndcY(y: Float) = 1f - (y / screenH) * 2f
        return floatArrayOf(
            ndcX(sx),      ndcY(sy),      0f, 0f,   // TL
            ndcX(sx + sw), ndcY(sy),      1f, 0f,   // TR
            ndcX(sx),      ndcY(sy + sh), 0f, 1f,   // BL
            ndcX(sx + sw), ndcY(sy + sh), 1f, 1f    // BR
        )
    }

    /**
     * A pre-built full-screen quad in NDC space.
     * Covers (-1,-1) → (+1,+1) with UV (0,0) → (1,1).
     * Used for dim overlays and the scan-line beam.
     */
    val FULL_SCREEN_QUAD = floatArrayOf(
        -1f,  1f, 0f, 0f,   // TL
         1f,  1f, 1f, 0f,   // TR
        -1f, -1f, 0f, 1f,   // BL
         1f, -1f, 1f, 1f    // BR
    )

    // ── Private helpers ────────────────────────────────────────────────────────

    private data class NdcRect(
        val left: Float, val top: Float, val right: Float, val bottom: Float
    )

    private fun imageRectToNdc(
        imgLeft: Float, imgTop: Float, imgRight: Float, imgBottom: Float,
        imgW: Float, imgH: Float, screenW: Float, screenH: Float
    ): NdcRect {
        val scale   = maxOf(screenW / imgW, screenH / imgH)
        val offsetX = (screenW - imgW * scale) / 2f
        val offsetY = (screenH - imgH * scale) / 2f

        fun sX(x: Float) = x * scale + offsetX
        fun sY(y: Float) = y * scale + offsetY

        return NdcRect(
            left   = (sX(imgLeft)   / screenW) * 2f - 1f,
            top    = 1f - (sY(imgTop)    / screenH) * 2f,
            right  = (sX(imgRight)  / screenW) * 2f - 1f,
            bottom = 1f - (sY(imgBottom) / screenH) * 2f
        )
    }
}
