package com.app.ttsreader.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.app.ttsreader.R

/**
 * Declarative permission handler using Accompanist.
 *
 * States handled:
 * 1. Granted                → renders [onGranted] content
 * 2. Should show rationale  → camera icon (primary) + explanation + "Grant" button (denied once)
 * 3. Never asked / denied   → auto-launches system dialog on first entry;
 *                             shows camera-blocked icon (error) + "Open Settings" if permanently denied
 *
 * The permission request is launched via [LaunchedEffect] keyed to
 * [permissionState.status]. This means it fires:
 *  - Once on the very first composition (status = never asked)
 *  - Again if the status changes (e.g. user revokes mid-session)
 * It does NOT re-fire on unrelated recompositions (e.g. theme change, scroll).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    permission: String,
    onGranted: @Composable () -> Unit
) {
    val permissionState = rememberPermissionState(permission)

    // Auto-launch the system dialog when the permission has never been asked.
    // Keyed to status so it re-evaluates only when status changes, not on every recompose.
    LaunchedEffect(permissionState.status) {
        if (!permissionState.status.isGranted && !permissionState.status.shouldShowRationale) {
            permissionState.launchPermissionRequest()
        }
    }

    when {
        permissionState.status.isGranted -> onGranted()

        permissionState.status.shouldShowRationale -> {
            // Denied once: show rationale and let user retry manually
            PermissionRationaleContent(
                message   = stringResource(R.string.permission_camera_rationale),
                onRequest = { permissionState.launchPermissionRequest() }
            )
        }

        else -> {
            // Permanently denied (or awaiting first-launch dialog triggered above)
            PermissionDeniedContent()
        }
    }
}

@Composable
private fun PermissionRationaleContent(
    message: String,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Filled.CameraAlt,
            contentDescription = stringResource(R.string.content_description_camera_icon),
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_grant_camera))
        }
    }
}

@Composable
private fun PermissionDeniedContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Filled.NoPhotography,
            contentDescription = stringResource(R.string.content_description_no_camera),
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text      = stringResource(R.string.permission_denied),
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(stringResource(R.string.open_settings))
        }
    }
}
