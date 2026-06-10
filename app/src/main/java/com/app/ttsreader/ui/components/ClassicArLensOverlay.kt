package com.app.ttsreader.ui.components

import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ttsreader.ar.LabelLayoutEngine
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.viewmodel.ArLensBlock
import kotlin.math.exp
import kotlin.math.max

private val neonArgb = android.graphics.Color.argb(255, 57, 255, 20)

/**
 * Renovated AR Lens overlay — single render path.
 *
 * Key invariants:
 * - **Sentinel image dims**: if [imageWidth] or [imageHeight] is `<= 0`, the
 *   overlay refuses to render. This prevents the "scale-explosion" artifact
 *   when stale tracker state survives a pause/resume.
 * - **Per-display-frame animation**: an internal `Map<Long, AnimState>` keyed
 *   by [ArLensBlock.id] holds smoothed pose + alpha. A `withFrameNanos` loop
 *   drives interpolation at display refresh, decoupled from ML Kit's 15 fps.
 * - **Label collision resolver**: each frame, [LabelLayoutEngine] places labels
 *   in priority order (above → below → inside box). Long labels truncate; any
 *   label that can't fit anywhere is culled (the box still draws).
 * - **Density-aware**: stroke `1.5.dp`, text `13.sp`, all gaps in dp.
 * - **Tap-to-pronounce**: tapping a bounding box invokes [onTapBlock].
 *
 * Coordinate mapping mirrors `PreviewView.ScaleType.FILL_CENTER`:
 *   scale   = max(screenW / imgW, screenH / imgH)
 *   offsetX = (screenW − imgW × scale) / 2
 *   screenX = imgX × scale + offsetX
 * Front-camera mode mirrors X — the box is drawn on the same side of the
 * preview as the actual text.
 */
@Composable
fun ClassicArLensOverlay(
    blocks: List<ArLensBlock>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = false,
    onTapBlock: (ArLensBlock) -> Unit = {},
) {
    // Refuse to draw against sentinel / not-yet-initialised image dims.
    if (imageWidth <= 0 || imageHeight <= 0) return
    if (blocks.isEmpty()) return

    val density = LocalDensity.current
    val animStates = remember { LinkedHashMap<Long, AnimState>() }

    // Sync target poses from the latest StateFlow emission into AnimState.
    // Remove stale entries; create new ones for first-seen ids.
    val blocksKey = blocks.map { it.id }   // identity changes when the set of ids changes
    LaunchedEffect(blocksKey, isFrontCamera) {
        // First-render snap: if a block is new, initialise smoothed=target so it doesn't
        // animate from (0,0). Otherwise keep prior smoothed values for continuity.
        val seen = HashSet<Long>(blocks.size)
        for (b in blocks) {
            seen.add(b.id)
            val existing = animStates[b.id]
            if (existing == null) {
                animStates[b.id] = AnimState(
                    targetCx = b.smoothedBox.centerX(),
                    targetCy = b.smoothedBox.centerY(),
                    targetW  = b.smoothedBox.width(),
                    targetH  = b.smoothedBox.height(),
                    targetAlpha = b.displayAlpha,
                    smoothedCx = b.smoothedBox.centerX(),
                    smoothedCy = b.smoothedBox.centerY(),
                    smoothedW  = b.smoothedBox.width(),
                    smoothedH  = b.smoothedBox.height(),
                    alpha = 0f,
                    text = b.translatedText.ifEmpty { b.originalText },
                )
            } else {
                existing.targetCx = b.smoothedBox.centerX()
                existing.targetCy = b.smoothedBox.centerY()
                existing.targetW  = b.smoothedBox.width()
                existing.targetH  = b.smoothedBox.height()
                existing.targetAlpha = b.displayAlpha
                existing.text = b.translatedText.ifEmpty { b.originalText }
            }
        }
        // Mark anyone NOT in the latest snapshot for fade-out then evict on alpha~0.
        val iter = animStates.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key !in seen) e.value.targetAlpha = 0f
        }
    }

    // Per-display-frame animator — runs while composed, advances smoothedCx/Cy/W/H
    // and alpha toward their targets via spring/tween-ish exponential lerp.
    var frameTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                val dtMs = if (prev == 0L) 16f else ((now - prev) / 1_000_000f).coerceIn(1f, 100f)
                prev = now

                // Critically-damped exponential lerp factors.
                val posTau = 80f          // ~80 ms half-life for position
                val alphaInTau = 60f      // fade in fast
                val alphaOutTau = 200f    // fade out slow

                val posK = 1f - exp(-dtMs / posTau)

                val toRemove = ArrayList<Long>(0)
                for ((id, s) in animStates) {
                    s.smoothedCx += (s.targetCx - s.smoothedCx) * posK
                    s.smoothedCy += (s.targetCy - s.smoothedCy) * posK
                    s.smoothedW  += (s.targetW  - s.smoothedW)  * posK
                    s.smoothedH  += (s.targetH  - s.smoothedH)  * posK

                    val tau = if (s.targetAlpha > s.alpha) alphaInTau else alphaOutTau
                    val aK = 1f - exp(-dtMs / tau)
                    s.alpha += (s.targetAlpha - s.alpha) * aK

                    // Evict fully faded ghosts.
                    if (s.targetAlpha <= 0f && s.alpha <= 0.01f) toRemove.add(id)
                }
                for (id in toRemove) animStates.remove(id)

                // Trigger one Compose recomposition per frame.
                frameTick = now
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.pointerInput(blocksKey, imageWidth, imageHeight, isFrontCamera) {
            // Tap detection — convert screen coords back to image space, hit-test
            // against every visible smoothed box, fire callback for the topmost hit.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val first = event.changes.firstOrNull() ?: continue
                    if (!first.pressed || first.previousPressed) continue
                    val tap = first.position
                    val (scale, ox, oy) = computeFillCenter(
                        sw = size.width.toFloat(),
                        sh = size.height.toFloat(),
                        imgW = imageWidth.toFloat(),
                        imgH = imageHeight.toFloat(),
                    )
                    val hit = hitTest(blocks, tap.x, tap.y, scale, ox, oy, size.width.toFloat(), isFrontCamera)
                    if (hit != null) {
                        first.consume()
                        onTapBlock(hit)
                    }
                }
            }
        },
    ) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val (scale, ox, oy) = remember(sw, sh, imageWidth, imageHeight) {
            computeFillCenter(sw, sh, imageWidth.toFloat(), imageHeight.toFloat())
        }

        // Density-aware paints — kept across recompositions.
        val labelPaint = remember(density) {
            AndroidPaint().apply {
                color = neonArgb
                textSize = with(density) { 13.sp.toPx() }
                isAntiAlias = true
                isFakeBoldText = true
                setShadowLayer(with(density) { 4.dp.toPx() }, 0f, 0f, neonArgb)
            }
        }
        val bgPaint = remember {
            AndroidPaint().apply {
                color = android.graphics.Color.argb(180, 0, 0, 0)
                isAntiAlias = true
            }
        }
        val strokePx = with(density) { 1.5.dp.toPx() }
        val padPx = with(density) { 6.dp.toPx() }
        val cornerPx = with(density) { 6.dp.toPx() }
        val gapPx = with(density) { 4.dp.toPx() }
        val minLabelMaxW = with(density) { 80.dp.toPx() }

        // Hoisted outside the Canvas lambda — `remember` doesn't work in DrawScope.
        val labelEngine = remember(sw, sh, gapPx) { LabelLayoutEngine(sw, sh, gapPx) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Force re-draw on every animator pulse.
            @Suppress("UNUSED_EXPRESSION")
            frameTick

            labelEngine.reset()

            // Z-sort by top→bottom, left→right for deterministic label placement.
            val sorted = animStates.values.sortedWith(
                compareBy({ it.smoothedCy - it.smoothedH * 0.5f }, { it.smoothedCx - it.smoothedW * 0.5f })
            )

            for (s in sorted) {
                if (s.alpha <= 0.02f) continue

                // Image-space box → screen-space box (with optional X-mirror)
                val imgL = s.smoothedCx - s.smoothedW * 0.5f
                val imgR = s.smoothedCx + s.smoothedW * 0.5f
                val imgT = s.smoothedCy - s.smoothedH * 0.5f
                val imgB = s.smoothedCy + s.smoothedH * 0.5f

                val sL0 = imgL * scale + ox
                val sR0 = imgR * scale + ox
                val sT  = imgT * scale + oy
                val sB  = imgB * scale + oy
                val sL = if (isFrontCamera) sw - sR0 else sL0
                val sR = if (isFrontCamera) sw - sL0 else sR0

                // Cull off-screen
                if (sR < 0f || sL > sw || sB < 0f || sT > sh) continue

                val bw = sR - sL
                val bh = sB - sT
                val a = s.alpha.coerceIn(0f, 1f)

                // Translucent fill
                drawRect(
                    color = HubColors.NeonGreen.copy(alpha = 0.18f * a),
                    topLeft = Offset(sL, sT),
                    size = Size(bw, bh),
                )
                // Neon border
                drawRect(
                    color = HubColors.NeonGreen.copy(alpha = a),
                    topLeft = Offset(sL, sT),
                    size = Size(bw, bh),
                    style = Stroke(width = strokePx),
                )

                // Label — measure, ellipsize, place via collision engine.
                val rawLabel = s.text
                if (rawLabel.isBlank()) continue
                labelPaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
                bgPaint.alpha = (a * 180f).toInt().coerceIn(0, 180)

                val maxLabelW = max(bw + padPx * 4f, minLabelMaxW)
                val ellipsized = ellipsize(rawLabel, labelPaint, maxLabelW)
                val textW = labelPaint.measureText(ellipsized)
                val fm = labelPaint.fontMetrics
                val textH = fm.descent - fm.ascent
                val pillW = textW + padPx * 2f
                val pillH = textH + padPx * 1.2f

                val boxRect = RectF(sL, sT, sR, sB)
                val slot = labelEngine.resolve(boxRect, pillW, pillH) ?: continue

                drawContext.canvas.nativeCanvas.drawRoundRect(
                    slot.left,
                    slot.top,
                    slot.right,
                    slot.bottom,
                    cornerPx,
                    cornerPx,
                    bgPaint,
                )
                // Text baseline = pill.top + padding + (textH - descent)
                val baseline = slot.top + padPx * 0.6f - fm.ascent
                drawContext.canvas.nativeCanvas.drawText(
                    ellipsized,
                    slot.left + padPx,
                    baseline,
                    labelPaint,
                )
            }
        }
    }
}

/**
 * Mutable per-block animation state, owned by the overlay's animator loop.
 * Public-by-internal so `LinkedHashMap` can hold these by reference.
 */
private class AnimState(
    var targetCx: Float,
    var targetCy: Float,
    var targetW:  Float,
    var targetH:  Float,
    var targetAlpha: Float,
    var smoothedCx: Float,
    var smoothedCy: Float,
    var smoothedW:  Float,
    var smoothedH:  Float,
    var alpha: Float,
    var text: String,
)

/** Returns (scale, offsetX, offsetY) for FILL_CENTER mapping. */
private fun computeFillCenter(sw: Float, sh: Float, imgW: Float, imgH: Float): FillCenter {
    val safeImgW = imgW.coerceAtLeast(1f)
    val safeImgH = imgH.coerceAtLeast(1f)
    val scale = max(sw / safeImgW, sh / safeImgH)
    val ox = (sw - safeImgW * scale) / 2f
    val oy = (sh - safeImgH * scale) / 2f
    return FillCenter(scale, ox, oy)
}

private data class FillCenter(val scale: Float, val ox: Float, val oy: Float)

private fun hitTest(
    blocks: List<ArLensBlock>,
    tapX: Float,
    tapY: Float,
    scale: Float,
    ox: Float,
    oy: Float,
    screenW: Float,
    isFrontCamera: Boolean,
): ArLensBlock? {
    // Iterate in reverse so the topmost (last-drawn) is hit first.
    for (i in blocks.indices.reversed()) {
        val b = blocks[i]
        val imgL = b.smoothedBox.left
        val imgR = b.smoothedBox.right
        val imgT = b.smoothedBox.top
        val imgB = b.smoothedBox.bottom

        val sL0 = imgL * scale + ox
        val sR0 = imgR * scale + ox
        val sT  = imgT * scale + oy
        val sB  = imgB * scale + oy
        val sL = if (isFrontCamera) screenW - sR0 else sL0
        val sR = if (isFrontCamera) screenW - sL0 else sR0
        if (tapX in sL..sR && tapY in sT..sB) return b
    }
    return null
}

/** Truncates [text] with ellipsis so [paint].measureText fits within [maxW]. */
private fun ellipsize(text: String, paint: AndroidPaint, maxW: Float): String {
    if (paint.measureText(text) <= maxW) return text
    val ellipsis = "…"
    val eW = paint.measureText(ellipsis)
    if (eW >= maxW) return ""
    var lo = 0
    var hi = text.length
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        val sub = text.substring(0, mid) + ellipsis
        if (paint.measureText(sub) <= maxW) lo = mid else hi = mid - 1
    }
    return text.substring(0, lo) + ellipsis
}
