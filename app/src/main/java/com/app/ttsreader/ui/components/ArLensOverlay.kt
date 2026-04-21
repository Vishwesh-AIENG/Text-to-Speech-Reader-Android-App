package com.app.ttsreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.app.ttsreader.gl.ArBlockSnapshot
import com.app.ttsreader.gl.OverlayMode
import com.app.ttsreader.gl.SdfOverlayView
import com.app.ttsreader.viewmodel.ArLensBlock

/**
 * Full-screen AR overlay — now backed by an OpenGL ES 2.0 [SdfOverlayView].
 *
 * The composable signature is unchanged so [ArLensScreen] needs zero edits.
 * Internally, the Compose Canvas has been replaced with an [AndroidView]
 * wrapping a transparent [GLSurfaceView]. The ViewModel's block data is
 * pushed to the GL thread via [GLSurfaceView.queueEvent] on every
 * recomposition triggered by the `StateFlow` collector.
 *
 * ## Module 1 — clear glass
 * The renderer currently clears to transparent every frame. Corner reticles,
 * scan lines, block erasure, and SDF text rendering will be added in
 * Modules 2–3.
 *
 * ## Lifecycle
 * A [DisposableEffect] forwards [Lifecycle.Event.ON_PAUSE] and
 * [Lifecycle.Event.ON_RESUME] to the [GLSurfaceView] so the GL thread
 * is suspended when the activity is backgrounded.
 */
@Composable
fun ArLensOverlay(
    blocks:      List<ArLensBlock>,
    imageWidth:  Int,
    imageHeight: Int,
    modifier:    Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Remember a single SdfOverlayView instance across recompositions
    val viewRef = remember { arrayOfNulls<SdfOverlayView>(1) }

    AndroidView(
        modifier = modifier,
        factory  = { context ->
            SdfOverlayView(context).also { view ->
                view.sdfRenderer.mode = OverlayMode.AR_LENS
                viewRef[0] = view
            }
        },
        update   = { view ->
            // Convert domain objects to lightweight snapshots for the GL thread.
            // Show original text immediately while translation is still in flight —
            // the translatedText replaces it once the request completes.
            val snapshots = blocks.map { block ->
                ArBlockSnapshot(
                    smoothedBox    = block.smoothedBox,
                    displayAlpha   = block.displayAlpha,
                    translatedText = block.translatedText.ifEmpty { block.originalText },
                    cornerPoints   = block.cornerPoints
                )
            }
            view.queueEvent {
                view.sdfRenderer.updateArLensData(snapshots, imageWidth, imageHeight)
            }
        }
    )

    // Forward lifecycle events to the GLSurfaceView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = viewRef[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
