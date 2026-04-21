package com.app.ttsreader.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.R
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.viewmodel.SettingsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.app.ttsreader.ui.components.BabelEnergyDivider
import com.app.ttsreader.ui.components.BabelWaveform
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.ui.theme.subtleNeonGlow
import com.app.ttsreader.utils.LanguageUtils
import com.app.ttsreader.viewmodel.BabelPhase
import com.app.ttsreader.viewmodel.BabelViewModel
import com.app.ttsreader.viewmodel.PickerTarget

/**
 * Babel Conversation Engine screen.
 *
 * ## Layout
 * The screen is split horizontally into three zones:
 * ```
 * ┌───────────────────────────────┐  ← Speaker A panel (rotated 180°)
 * │  Language badge               │
 * │  Received translation (dim)   │
 * │  Spoken text (bright)         │
 * │  [waveform when active]       │
 * ├─────── BabelEnergyDivider ────┤  ← animated neon sine wave
 * │  Language badge               │
 * │  Received translation (dim)   │
 * │  Spoken text (bright)         │
 * │  [waveform when active]       │  ← Speaker B panel
 * └───────────────────────────────┘
 * ```
 *
 * The top panel is rotated 180° so that Person A — sitting across a table —
 * can read their content upright.
 *
 * ## Ready state
 * Shows language selection chips in each panel and a centered START button.
 *
 * ## Active state
 * Shows conversation turns, live waveform, and per-panel mic buttons.
 *
 * @param onNavigateBack Called when the user presses Back to return to the Hub.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BabelScreen(
    onNavigateBack: () -> Unit,
    onOpenLanguages: () -> Unit = {},
    viewModel: BabelViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val rmsLevel      by remember { derivedStateOf { uiState.rmsLevel } }
    val sheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notDownloadedLang by remember { mutableStateOf<String?>(null) }

    // Stop recognition/TTS when this screen leaves composition.
    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    BackHandler {
        if (uiState.sessionActive) viewModel.stopSession()
        onNavigateBack()
    }

    // ── Microphone permission — auto-launch system dialog on first entry ───────
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    LaunchedEffect(micPermission.status) {
        if (!micPermission.status.isGranted && !micPermission.status.shouldShowRationale) {
            micPermission.launchPermissionRequest()
        }
    }

    when {
        micPermission.status.isGranted -> Unit   // fall through to main content below
        micPermission.status.shouldShowRationale -> {
            BabelMicRationale(onRequest = { micPermission.launchPermissionRequest() })
            return
        }
        else -> {
            BabelMicDenied()
            return
        }
    }

    // ── Derived display data ───────────────────────────────────────────────────
    val lastSpokenByTop    = uiState.turns.lastOrNull { it.isTopSpeaker }
    val lastSpokenByBottom = uiState.turns.lastOrNull { !it.isTopSpeaker }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        GlassBackground()

        Column(modifier = Modifier.fillMaxSize()) {

            // ── TOP PANEL — Person A (rotated 180°) ───────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Rotate entire content 180° so Person A reads it upright
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = 180f }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Language badge row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LanguageBadge(
                            language    = uiState.topLanguage,
                            isLocked    = uiState.sessionActive,
                            onClick     = { viewModel.openLanguagePicker(forTop = true) }
                        )
                        Spacer(Modifier.weight(1f))
                        // Mic button — manual trigger for Person A
                        if (uiState.sessionActive) {
                            MicButton(
                                isActive = uiState.isTopActive &&
                                           uiState.phase == BabelPhase.LISTENING,
                                onClick  = { viewModel.manualTrigger(isTop = true) }
                            )
                        }
                    }

                    // Conversation content
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Received translation (what other speaker said — shown dim)
                        if (lastSpokenByBottom != null && uiState.sessionActive) {
                            Text(
                                text           = lastSpokenByBottom.translatedText,
                                color          = HubColors.NeonGreenDim,
                                fontSize       = 13.sp,
                                lineHeight     = 18.sp,
                                maxLines       = 2,
                                overflow       = TextOverflow.Ellipsis
                            )
                        }
                        // Own spoken text (bright)
                        if (lastSpokenByTop != null && uiState.sessionActive) {
                            Text(
                                text       = lastSpokenByTop.spokenText,
                                color      = HubColors.NeonGreen,
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp,
                                maxLines   = 3,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                        // Ready / idle hint
                        if (!uiState.sessionActive) {
                            Text(
                                text     = stringResource(R.string.babel_ready),
                                color    = HubColors.NeonGreenDim,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Waveform (shown when this side is actively listening)
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                        val isListening = uiState.isTopActive &&
                                          uiState.phase == BabelPhase.LISTENING &&
                                          uiState.sessionActive
                        val isTranslating = uiState.isTopActive &&
                                            uiState.phase == BabelPhase.TRANSLATING &&
                                            uiState.sessionActive

                        if (isListening) {
                            BabelWaveform(
                                rmsLevel = rmsLevel,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (isTranslating) {
                            CircularProgressIndicator(
                                color    = HubColors.NeonGreen,
                                modifier = Modifier.size(24.dp).align(Alignment.CenterStart),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // ── ENERGY DIVIDER ────────────────────────────────────────────────
            Box(
                modifier        = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                BabelEnergyDivider(
                    isActive = uiState.sessionActive,
                    modifier = Modifier.fillMaxSize()
                )

                // END SESSION button centred on divider (only visible when session active)
                if (uiState.sessionActive) {
                    OutlinedButton(
                        onClick  = { viewModel.stopSession() },
                        shape    = RoundedCornerShape(50),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = HubColors.NeonGreen
                        ),
                        border   = BorderStroke(1.dp, HubColors.NeonGreenBorder),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.babel_stop_session),
                            modifier           = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text          = stringResource(R.string.babel_stop_session),
                            fontSize      = 10.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // ── BOTTOM PANEL — Person B ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Language badge row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LanguageBadge(
                            language = uiState.bottomLanguage,
                            isLocked = uiState.sessionActive,
                            onClick  = { viewModel.openLanguagePicker(forTop = false) }
                        )
                        Spacer(Modifier.weight(1f))
                        // Mic button — manual trigger for Person B
                        if (uiState.sessionActive) {
                            MicButton(
                                isActive = !uiState.isTopActive &&
                                           uiState.phase == BabelPhase.LISTENING,
                                onClick  = { viewModel.manualTrigger(isTop = false) }
                            )
                        }
                    }

                    // Conversation content
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Received translation (what top speaker said, in bottom's language)
                        if (lastSpokenByTop != null && uiState.sessionActive) {
                            Text(
                                text       = lastSpokenByTop.translatedText,
                                color      = HubColors.NeonGreenDim,
                                fontSize   = 13.sp,
                                lineHeight = 18.sp,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                        // Own spoken text (bright)
                        if (lastSpokenByBottom != null && uiState.sessionActive) {
                            Text(
                                text       = lastSpokenByBottom.spokenText,
                                color      = HubColors.NeonGreen,
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp,
                                maxLines   = 3,
                                overflow   = TextOverflow.Ellipsis
                            )
                        }
                        // Ready / idle hint
                        if (!uiState.sessionActive) {
                            Text(
                                text     = stringResource(R.string.babel_ready),
                                color    = HubColors.NeonGreenDim,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Waveform (shown when this side is actively listening)
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                        val isListening = !uiState.isTopActive &&
                                          uiState.phase == BabelPhase.LISTENING &&
                                          uiState.sessionActive
                        val isTranslating = !uiState.isTopActive &&
                                            uiState.phase == BabelPhase.TRANSLATING &&
                                            uiState.sessionActive

                        if (isListening) {
                            BabelWaveform(
                                rmsLevel = rmsLevel,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (isTranslating) {
                            CircularProgressIndicator(
                                color    = HubColors.NeonGreen,
                                modifier = Modifier.size(24.dp).align(Alignment.CenterStart),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // START button (bottom panel, only in ready state)
                if (!uiState.sessionActive) {
                    StartButton(
                        onClick  = { viewModel.startSession() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }

        // ── Error snackbar area ────────────────────────────────────────────────
        if (uiState.errorMessage != null) {
            Surface(
                modifier      = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                shape         = RoundedCornerShape(12.dp),
                color         = HubColors.NeonGreenFaint,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text      = uiState.errorMessage!!,
                        color     = HubColors.NeonGreen,
                        fontSize  = 13.sp,
                        modifier  = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick  = { viewModel.clearError() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = "Dismiss",
                            tint               = HubColors.NeonGreen,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── Language not-downloaded banner ────────────────────────────────────
        if (notDownloadedLang != null) {
            Surface(
                modifier      = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 88.dp),
                shape         = RoundedCornerShape(12.dp),
                color         = HubColors.NeonGreenFaint,
                tonalElevation = 0.dp,
                border        = BorderStroke(1.dp, HubColors.NeonGreenBorder)
            ) {
                Row(
                    modifier            = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text     = stringResource(R.string.language_not_downloaded_msg, notDownloadedLang!!),
                        color    = HubColors.NeonGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { notDownloadedLang = null; onOpenLanguages() }) {
                        Text(
                            text          = stringResource(R.string.go_to_languages),
                            color         = HubColors.NeonGreen,
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    IconButton(
                        onClick  = { notDownloadedLang = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = "Dismiss",
                            tint               = HubColors.NeonGreen.copy(alpha = 0.6f),
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Language picker bottom sheet ───────────────────────────────────────────
    if (uiState.languagePickerTarget != null) {
        val title = if (uiState.languagePickerTarget == PickerTarget.TOP)
            uiState.topLanguage.displayName else uiState.bottomLanguage.displayName

        ModalBottomSheet(
            onDismissRequest = { viewModel.closeLanguagePicker() },
            sheetState       = sheetState,
            containerColor   = HubColors.Black,
            tonalElevation   = 0.dp
        ) {
            Text(
                text          = stringResource(R.string.babel_select_language),
                color         = HubColors.NeonGreen,
                fontWeight    = FontWeight.Bold,
                fontSize      = 16.sp,
                letterSpacing = 1.sp,
                modifier      = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                items(LanguageUtils.SUPPORTED_LANGUAGES) { lang ->
                    val isSelected = lang.mlKitCode == title ||
                                     lang.displayName == title

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (lang.mlKitCode !in settingsState.downloadedModelCodes) {
                                    viewModel.closeLanguagePicker()
                                    notDownloadedLang = lang.displayName
                                } else {
                                    viewModel.selectLanguage(lang)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text      = lang.displayName,
                            color     = if (isSelected) HubColors.NeonGreen else HubColors.NeonGreenDim,
                            fontSize  = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier  = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector        = Icons.Default.Check,
                                contentDescription = null,
                                tint               = HubColors.NeonGreen,
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Microphone permission screens ─────────────────────────────────────────────

@Composable
private fun BabelMicRationale(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Default.Mic,
            contentDescription = null,
            tint               = HubColors.NeonGreen,
            modifier           = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text      = stringResource(R.string.babel_permission_required),
            color     = HubColors.NeonGreenDim,
            fontSize  = 15.sp,
            lineHeight = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors  = ButtonDefaults.buttonColors(
                containerColor = HubColors.NeonGreenFaint,
                contentColor   = HubColors.NeonGreen
            ),
            border  = BorderStroke(1.5.dp, HubColors.NeonGreen)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text          = stringResource(R.string.babel_grant_microphone),
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
    }
}

@Composable
private fun BabelMicDenied() {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Default.Mic,
            contentDescription = null,
            tint               = HubColors.NeonGreenDim.copy(alpha = 0.45f),
            modifier           = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text      = stringResource(R.string.permission_mic_denied),
            color     = HubColors.NeonGreenDim,
            fontSize  = 15.sp,
            lineHeight = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = HubColors.NeonGreenFaint,
                contentColor   = HubColors.NeonGreen
            ),
            border = BorderStroke(1.dp, HubColors.NeonGreenBorder)
        ) {
            Text(
                text          = stringResource(R.string.open_settings),
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
    }
}

// ── Private sub-composables ────────────────────────────────────────────────────

@Composable
private fun LanguageBadge(
    language: AppLanguage,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape         = RoundedCornerShape(20.dp),
        color         = HubColors.NeonGreenFaint,
        tonalElevation = 0.dp,
        modifier      = Modifier
            .subtleNeonGlow(cornerRadius = 20.dp, glowRadius = 4.dp, intensity = 0.08f)
            .border(
                BorderStroke(1.dp, HubColors.NeonGreenBorder),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = !isLocked, onClick = onClick)
    ) {
        Text(
            text          = language.displayName.uppercase(),
            color         = HubColors.NeonGreen,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier      = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MicButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "mic_pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.9f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micGlow"
    )

    Box(
        modifier         = Modifier
            .size(40.dp)
            .subtleNeonGlow(
                cornerRadius = 20.dp,
                glowRadius   = if (isActive) 8.dp else 3.dp,
                intensity    = if (isActive) glowAlpha * 0.3f else 0.07f
            )
            .background(
                color = if (isActive) HubColors.NeonGreenFaint else HubColors.Black,
                shape = CircleShape
            )
            .border(
                BorderStroke(1.dp, HubColors.NeonGreenBorder.copy(alpha = if (isActive) 0.9f else 0.4f)),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = Icons.Default.Mic,
            contentDescription = "Mic",
            tint               = HubColors.NeonGreen.copy(alpha = if (isActive) 1f else 0.5f),
            modifier           = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick  = onClick,
        shape    = RoundedCornerShape(50),
        colors   = ButtonDefaults.buttonColors(
            containerColor = HubColors.NeonGreenFaint,
            contentColor   = HubColors.NeonGreen
        ),
        border   = BorderStroke(1.5.dp, HubColors.NeonGreen),
        modifier = modifier
            .subtleNeonGlow(cornerRadius = 50.dp, glowRadius = 10.dp, intensity = 0.18f)
            .height(48.dp)
            .padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector        = Icons.Default.Mic,
            contentDescription = null,
            modifier           = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text          = stringResource(R.string.babel_start_session),
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 14.sp,
            letterSpacing = 1.5.sp
        )
    }
}
