package com.app.ttsreader.ocr

import android.graphics.Point
import android.graphics.Rect

/**
 * A single word detected by the native C++/CRNN OCR engine.
 *
 * @param text       Recognized text string (CTC-decoded from CRNN output).
 * @param quad       8-float array: 4 corners in image space
 *                   `[x0,y0, x1,y1, x2,y2, x3,y3]` → TL, TR, BR, BL.
 *                   Matches the ML Kit `cornerPoints` convention.
 * @param confidence Recognition confidence `[0.0, 1.0]` — average softmax.
 */
data class SpatialWord(
    val text: String,
    val quad: FloatArray,
    val confidence: Float
) {
    init {
        require(quad.size == 8) { "quad must have exactly 8 floats (4 corners × 2)" }
    }

    /** Axis-aligned bounding box enclosing the 4-point quad. */
    fun toBoundingRect(): Rect {
        val xs = floatArrayOf(quad[0], quad[2], quad[4], quad[6])
        val ys = floatArrayOf(quad[1], quad[3], quad[5], quad[7])
        return Rect(
            xs.min().toInt(), ys.min().toInt(),
            xs.max().toInt(), ys.max().toInt()
        )
    }

    /** ML Kit-compatible corner-point array (TL, TR, BR, BL). */
    fun toCornerPoints(): Array<Point> = arrayOf(
        Point(quad[0].toInt(), quad[1].toInt()),
        Point(quad[2].toInt(), quad[3].toInt()),
        Point(quad[4].toInt(), quad[5].toInt()),
        Point(quad[6].toInt(), quad[7].toInt())
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpatialWord) return false
        return text == other.text &&
               quad.contentEquals(other.quad) &&
               confidence == other.confidence
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + quad.contentHashCode()
        result = 31 * result + confidence.hashCode()
        return result
    }
}
