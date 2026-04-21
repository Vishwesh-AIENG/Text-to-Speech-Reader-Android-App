package com.app.ttsreader.ui.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.viewmodel.ArLensBlock

private val neonArgb = android.graphics.Color.argb(255, 57, 255, 20)

/**
 * Classic (Canvas-based) AR Lens overlay.
 *
 * Draws a neon-green filled + bordered rectangle for each stable [ArLensBlock],
 * then renders the translated (or original) text above the box using the native
 * Android canvas so no TextMeasurer / Compose text layout is needed on the hot
 * draw path.
 *
 * Coordinate mapping mirrors [com.app.ttsreader.gl.QuadGeometry] — FILL_CENTER:
 *   scale   = max(screenW / imgW, screenH / imgH)
 *   offsetX = (screenW − imgW × scale) / 2
 *   screenX = imgX × scale + offsetX
 */
@Composable
fun ClassicArLensOverlay(
    blocks:      List<ArLensBlock>,
    imageWidth:  Int,
    imageHeight: Int,
    modifier:    Modifier = Modifier
) {
    val visible = blocks.filter { it.displayAlpha > 0.05f }
    if (visible.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val sw   = constraints.maxWidth.toFloat()
        val sh   = constraints.maxHeight.toFloat()
        val imgW = imageWidth.toFloat().coerceAtLeast(1f)
        val imgH = imageHeight.toFloat().coerceAtLeast(1f)

        val scale = maxOf(sw / imgW, sh / imgH)
        val ox    = (sw - imgW * scale) / 2f
        val oy    = (sh - imgH * scale) / 2f

        val labelPaint = remember {
            AndroidPaint().apply {
                color          = neonArgb
                textSize       = 36f          // ~12 sp @ 3× density
                isAntiAlias    = true
                isFakeBoldText = true
                setShadowLayer(6f, 0f, 0f, neonArgb)
            }
        }
        val bgPaint = remember {
            AndroidPaint().apply {
                color       = android.graphics.Color.argb(160, 0, 0, 0)
                isAntiAlias = true
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            for (block in visible) {
                val b     = block.smoothedBox
                val sL    = b.left   * scale + ox
                val sT    = b.top    * scale + oy
                val sR    = b.right  * scale + ox
                val sB    = b.bottom * scale + oy
                val bW    = sR - sL
                val bH    = sB - sT
                val alpha = block.displayAlpha.coerceIn(0f, 1f)

                // Translucent fill
                drawRect(
                    color   = HubColors.NeonGreen.copy(alpha = 0.18f * alpha),
                    topLeft = Offset(sL, sT),
                    size    = Size(bW, bH)
                )
                // Neon border
                drawRect(
                    color   = HubColors.NeonGreen.copy(alpha = alpha),
                    topLeft = Offset(sL, sT),
                    size    = Size(bW, bH),
                    style   = Stroke(width = 2.5f)
                )

                // Text label above the box
                val label = block.translatedText.ifEmpty { block.originalText }
                if (label.isNotBlank()) {
                    val textY = (sT - 10f).coerceAtLeast(labelPaint.textSize + 4f)

                    // Dark background pill behind the text
                    val textW = labelPaint.measureText(label).coerceAtMost(bW + 16f)
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        sL, textY - labelPaint.textSize - 4f,
                        sL + textW + 10f, textY + 6f,
                        8f, 8f, bgPaint
                    )
                    drawContext.canvas.nativeCanvas.drawText(label, sL + 5f, textY, labelPaint)
                }
            }
        }
    }
}
