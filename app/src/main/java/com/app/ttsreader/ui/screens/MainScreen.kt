package com.app.ttsreader.ui.screens

import android.Manifest
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.R
import com.app.ttsreader.ui.components.CameraHintBanner
import com.app.ttsreader.ui.components.CameraPreview
import com.app.ttsreader.ui.components.ControlBar
import com.app.ttsreader.ui.components.ModelDownloadIndicator

import com.app.ttsreader.ui.components.OfflineBanner
import com.app.ttsreader.ui.components.PermissionHandler
import com.app.ttsreader.ui.components.RecognizedTextOverlay
import com.app.ttsreader.ui.components.SettingsButton
import com.app.ttsreader.review.InAppReviewManager
import com.app.ttsreader.ui.components.tap
import com.app.ttsreader.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Root screen composable with premium layout.
 *
 * ## Layer stack (Box, bottom-to-top z-order)
 * 1. [CameraPreview]              — full-screen live camera feed
 * 2. Top gradient scrim (black fade)
 * 3. Top bar                      — title, language chip, settings icon
 * 4. Bottom gradient scrim (black fade)
 * 5. Reading card + control bar   — [RecognizedTextOverlay] above [ControlBar]
 * 6. Banners                      — [OfflineBanner] OR [ModelDownloadIndicator]
 * 7. Snackbar host
 *
 * @param onOpenSettings Called when the user taps the settings icon.
 */
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    onOpenLanguages: () -> Unit = {},
    bottomNavPadding: Dp = 0.dp,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    // Stop camera + TTS when this screen is no longer active (user switched mode).
    // Everything restarts automatically via onPreviewViewReady when screen re-enters.
    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    // Static scrims — remembered once, never change
    val topScrimBrush = remember {
        Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
    }
    val bottomScrimBrush = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
    }

    val retryLabel   = stringResource(R.string.action_retry)
    val dismissLabel = stringResource(R.string.action_dismiss)

    // Haptic pulse when OCR first locks onto text
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        viewModel.ocrLockHaptic.collectLatest {
            haptic.tap()
        }
    }

    // In-App Review: prompt when TTS starts playing (after usage threshold is met).
    // isSpeaking transitions false → true indicate a fresh playback start.
    LaunchedEffect(uiState.isSpeaking) {
        if (uiState.isSpeaking && activity != null) {
            InAppReviewManager.maybeAskForReview(activity)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            val actionLabel = if (uiState.isErrorRetryable) retryLabel else dismissLabel
            val result = snackbarHostState.showSnackbar(
                message     = message,
                actionLabel = actionLabel,
                duration    = SnackbarDuration.Long
            )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    if (uiState.isErrorRetryable) {
                        viewModel.retryTranslation()
                    } else {
                        viewModel.dismissError()
                    }
                }
                SnackbarResult.Dismissed -> viewModel.dismissError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PermissionHandler(
            permission = Manifest.permission.CAMERA,
            onGranted = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Layer 1: full-screen camera preview
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onPreviewViewReady = { previewView ->
                            viewModel.startCamera(lifecycleOwner, previewView)
                        }
                    )

                    // Layer 2: Top gradient scrim (80dp fade from black to transparent)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(topScrimBrush)
                            .align(Alignment.TopStart)
                    )

                    // Layer 3: Top bar (title, language chip, torch, settings)
                    TopBar(
                        sourceLanguageName  = uiState.sourceLanguage.displayName,
                        targetLanguageName  = uiState.targetLanguage.displayName,
                        isTorchOn           = uiState.isTorchOn,
                        onLanguageChipClick = onOpenLanguages,
                        onSwapLanguages     = { viewModel.swapLanguages() },
                        onToggleTorch       = { viewModel.toggleTorch() },
                        onSettingsClick     = onOpenSettings,
                        modifier            = Modifier.align(Alignment.TopStart)
                    )

                    // Layer 4: Bottom gradient scrim (320dp fade from transparent to black)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(bottomScrimBrush)
                            .align(Alignment.BottomStart)
                    )

                    // Layer 5: Reading card + control bar (bottom, above nav bar)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = bottomNavPadding)
                            .align(Alignment.BottomCenter)
                    ) {
                        RecognizedTextOverlay(
                            recognizedText = uiState.recognizedText,
                            translatedText = uiState.translatedText,
                            isTranslating = uiState.isTranslating,
                            sentences = uiState.sentences,
                            currentSentenceIndex = uiState.currentSentenceIndex,
                            currentWordStart = uiState.currentWordStart,
                            currentWordEnd = uiState.currentWordEnd,
                            modifier = Modifier.fillMaxWidth()
                        )

                        ControlBar(
                            isSpeaking = uiState.isSpeaking,
                            canSpeak = uiState.ttsReady &&
                                      (uiState.translatedText.isNotBlank() ||
                                       uiState.recognizedText.isNotBlank()),
                            speechRate = uiState.speechRate,
                            pitch = uiState.pitch,
                            currentSentenceIndex = uiState.currentSentenceIndex,
                            totalSentences = uiState.totalSentences,
                            sleepTimerMinutes = uiState.sleepTimerMinutes,
                            sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                            onSpeak = { viewModel.speak() },
                            onStop = { viewModel.stopSpeaking() },
                            onPreviousSentence = { viewModel.previousSentence() },
                            onNextSentence = { viewModel.nextSentence() },
                            onSpeedChange = { viewModel.setSpeechRate(it) },
                            onPitchChange = { viewModel.setPitch(it) },
                            onSleepTimerChange = { viewModel.setSleepTimer(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Layer 6: Banners (top center)
                    OfflineBanner(
                        isOffline = uiState.isOffline,
                        modifier  = Modifier.align(Alignment.TopCenter)
                    )

                    // Lighting hint — fades out after 4 s
                    CameraHintBanner(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 110.dp)
                    )

                    ModelDownloadIndicator(
                        isDownloading = uiState.isModelDownloading && !uiState.isOffline,
                        modifier      = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        )
    }
}

/**
 * Premium top bar with app title, language chip, and settings icon.
 * Positioned over a gradient scrim for readability against the camera feed.
 */
@Composable
private fun TopBar(
    sourceLanguageName: String,
    targetLanguageName: String,
    isTorchOn: Boolean,
    onLanguageChipClick: () -> Unit,
    onSwapLanguages: () -> Unit,
    onToggleTorch: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App title (left)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.weight(1f))

        // Language chip — shows "EN → ES", tap to open Settings
        Surface(
            onClick = { haptic.tap(); onLanguageChipClick() },
            shape = MaterialTheme.shapes.medium,
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Text(
                text = String.format(
                    stringResource(R.string.top_bar_language_chip),
                    sourceLanguageName,
                    targetLanguageName
                ),
                style     = MaterialTheme.typography.labelSmall,
                color     = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Swap languages button — 48dp touch target
        IconButton(
            onClick  = { haptic.tap(); onSwapLanguages() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = stringResource(R.string.action_swap_languages),
                tint               = Color.White,
                modifier           = Modifier.size(20.dp)
            )
        }

        // Torch / flashlight toggle — 48dp touch target
        IconButton(
            onClick  = { haptic.tap(); onToggleTorch() },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector        = if (isTorchOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                contentDescription = stringResource(
                    if (isTorchOn) R.string.torch_on else R.string.torch_off
                ),
                tint               = if (isTorchOn) Color(0xFFFFD54F) else Color.White,
                modifier           = Modifier.size(20.dp)
            )
        }

        // Settings icon (right)
        SettingsButton(
            onClick  = onSettingsClick,
            modifier = Modifier
        )
    }
}
