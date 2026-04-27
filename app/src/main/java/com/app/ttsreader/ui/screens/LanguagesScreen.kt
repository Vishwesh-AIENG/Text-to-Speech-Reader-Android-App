package com.app.ttsreader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.ui.theme.subtleNeonGlow
import com.app.ttsreader.viewmodel.SettingsViewModel

/**
 * Full-screen language model manager.
 *
 * Lists every supported language with its current on-device status.
 * Languages can be downloaded or deleted from here. A progress indicator
 * is shown per-row while a download is in flight.
 *
 * This is the central place for model management — feature screens (Classic TTS,
 * AR Lens, Babel) deep-link here when the user selects a language whose model
 * isn't downloaded yet.
 */
@Composable
fun LanguagesScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // Refresh download status every time this screen opens
    LaunchedEffect(Unit) { settingsViewModel.refreshDownloadedModels() }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = HubColors.NeonGreen,
                        modifier           = Modifier.size(22.dp)
                    )
                }
                Text(
                    text          = "LANGUAGES",
                    color         = HubColors.NeonGreen,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 15.sp,
                    letterSpacing = 2.sp,
                    modifier      = Modifier
                        .weight(1f)
                        .subtleNeonGlow(glowRadius = 10.dp, intensity = 0.10f)
                )
            }

            // ── Subtitle ───────────────────────────────────────────────────────────
            Text(
                text      = "Downloaded models work fully offline. Download before heading somewhere without internet.",
                color     = HubColors.NeonGreenDim.copy(alpha = 0.70f),
                fontSize  = 12.sp,
                lineHeight = 17.sp,
                modifier  = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ── Error banner ───────────────────────────────────────────────────────
            if (uiState.error != null) {
                Surface(
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape         = RoundedCornerShape(8.dp),
                    color         = Color(0xFF2D0000).copy(alpha = 0.45f),
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text     = uiState.error!!,
                        color    = Color(0xFFFF8B8B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // ── Language list ──────────────────────────────────────────────────────
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding      = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                )
            ) {
                items(uiState.availableLanguages, key = { it.mlKitCode }) { language ->
                    val code         = language.mlKitCode
                    val isDownloaded = code in uiState.downloadedModelCodes
                    val isDownloading = code in uiState.downloadingModelCodes
                    val isDeleting   = code == uiState.deletingModelCode

                    LanguageRow(
                        name         = language.displayName,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading || isDeleting,
                        onDownload   = { settingsViewModel.downloadLanguage(code) },
                        onDelete     = { settingsViewModel.deleteModel(code) }
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    name: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                BorderStroke(1.dp, HubColors.NeonGreenBorder),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                isDownloading -> CircularProgressIndicator(
                    color       = HubColors.NeonGreen,
                    strokeWidth = 2.dp,
                    modifier    = Modifier.size(18.dp)
                )
                isDownloaded -> Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint               = HubColors.NeonGreen,
                    modifier           = Modifier.size(18.dp)
                )
                else -> Icon(
                    imageVector        = Icons.Default.CloudDownload,
                    contentDescription = "Not downloaded",
                    tint               = HubColors.NeonGreenDim.copy(alpha = 0.45f),
                    modifier           = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Language name
        Text(
            text       = name,
            color      = if (isDownloaded) HubColors.NeonGreen else HubColors.NeonGreenDim,
            fontSize   = 14.sp,
            fontWeight = if (isDownloaded) FontWeight.SemiBold else FontWeight.Normal,
            modifier   = Modifier.weight(1f)
        )

        // Action button
        when {
            isDownloading -> { /* spinner shown in status icon; no action button */ }
            isDownloaded -> {
                IconButton(
                    onClick  = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.Delete,
                        contentDescription = "Delete model",
                        tint               = HubColors.NeonGreenDim.copy(alpha = 0.40f),
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
            else -> {
                IconButton(
                    onClick  = onDownload,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Default.CloudDownload,
                        contentDescription = "Download model",
                        tint               = HubColors.NeonGreen,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
