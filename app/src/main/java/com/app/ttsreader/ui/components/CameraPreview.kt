package com.app.ttsreader.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose wrapper around CameraX [PreviewView].
 *
 * Why AndroidView: PreviewView is a traditional Android View, not a Composable.
 * AndroidView bridges the two systems. The [factory] lambda runs exactly once
 * to create the View; [onPreviewViewReady] passes the reference out so the
 * ViewModel can bind the camera to it.
 *
 * ImplementationMode.COMPATIBLE: Uses TextureView internally, which works
 * correctly inside Compose's View hierarchy on all API levels.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewViewReady: (PreviewView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also { previewView ->
                onPreviewViewReady(previewView)
            }
        }
    )
}
