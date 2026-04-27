package com.app.ttsreader.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.R
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.viewmodel.SettingsViewModel
import com.app.ttsreader.ui.components.ArLensOverlay
import com.app.ttsreader.ui.components.CameraHintBanner
import com.app.ttsreader.ui.components.ClassicArLensOverlay
import com.app.ttsreader.ui.components.CameraPreview
import com.app.ttsreader.ui.components.PermissionHandler
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.ui.theme.subtleNeonGlow
import com.app.ttsreader.utils.LanguageUtils
import com.app.ttsreader.viewmodel.ArLensViewModel

/**
 * AR Magic Lens screen — full-bleed camera with neon green AR overlay.
 *
 * ## Visual design
 * The screen deliberately avoids all Material3 chrome. The camera fills the
 * entire display edge-to-edge. A translucent neon-bordered top strip holds
 * the only controls: back, language pair, title, and BETA badge.
 *
 * The theme exactly matches the Hub: pure black surface, neon green (#39FF14)
 * accents, `subtleNeonGlow` on interactive elements, futuristic letter spacing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArLensScreen(
    onNavigateBack: () -> Unit,
    onOpenLanguages: () -> Unit = {},
    viewModel: ArLensViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState    = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notDownloadedLang by remember { mutableStateOf<String?>(null) }

    // Unbind camera when this screen leaves composition so no frames are
    // analyzed while another mode is active. Camera restarts automatically
    // via onPreviewViewReady when the screen re-enters composition.
    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    PermissionHandler(permission = Manifest.permission.CAMERA) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HubColors.Black)
        ) {

            // ── Full-bleed camera ─────────────────────────────────────────────
            val lifecycleOwner = LocalLifecycleOwner.current
            CameraPreview(
                modifier           = Modifier.fillMaxSize(),
                onPreviewViewReady = { previewView ->
                    viewModel.startCamera(lifecycleOwner, previewView)
                }
            )

            // ── AR overlay — Classic (Canvas) or Beta (SDF/OpenGL) ───────────
            if (settingsState.useSdfOverlay) {
                ArLensOverlay(
                    blocks      = uiState.blocks,
                    imageWidth  = uiState.imageEffectiveWidth,
                    imageHeight = uiState.imageEffectiveHeight,
                    modifier    = Modifier.fillMaxSize()
                )
            } else {
                ClassicArLensOverlay(
                    blocks      = uiState.blocks,
                    imageWidth  = uiState.imageEffectiveWidth,
                    imageHeight = uiState.imageEffectiveHeight,
                    modifier    = Modifier.fillMaxSize()
                )
            }

            // ── Top controls strip ────────────────────────────────────────────
            ArTopBar(
                sourceLangName  = uiState.sourceLang.displayName,
                targetLangName  = uiState.targetLang.displayName,
                useSdf          = settingsState.useSdfOverlay,
                onBack          = onNavigateBack,
                onPickSource    = { viewModel.openSourcePicker() },
                onPickTarget    = { viewModel.openTargetPicker() },
                onToggleOverlay = { settingsViewModel.toggleSdfOverlay() },
                modifier        = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )

            // Lighting hint — fades out after 4 s, sits below the top bar
            CameraHintBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
            )

            // ── Offline banner ────────────────────────────────────────────────
            if (uiState.isOffline) {
                ArOfflineBanner(
                    message  = stringResource(R.string.ar_lens_offline_banner),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }

            // ── Language not-downloaded banner ────────────────────────────────
            if (notDownloadedLang != null) {
                Surface(
                    modifier      = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 56.dp),
                    shape         = RoundedCornerShape(12.dp),
                    color         = HubColors.Black.copy(alpha = 0.92f),
                    border        = BorderStroke(1.dp, HubColors.NeonGreenBorder),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier            = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
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
    }

    // ── Language pickers (outside the camera Box so they overlay correctly) ──
    if (uiState.isPickingSource) {
        ArLanguagePicker(
            title      = stringResource(R.string.ar_lens_pick_source),
            selected   = uiState.sourceLang,
            sheetState = sheetState,
            onDismiss  = { viewModel.closePicker() },
            onSelect   = { lang ->
                if (lang.mlKitCode !in settingsState.downloadedModelCodes) {
                    viewModel.closePicker()
                    notDownloadedLang = lang.displayName
                } else {
                    viewModel.setSourceLanguage(lang)
                }
            }
        )
    }
    if (uiState.isPickingTarget) {
        ArLanguagePicker(
            title      = stringResource(R.string.ar_lens_pick_target),
            selected   = uiState.targetLang,
            sheetState = sheetState,
            onDismiss  = { viewModel.closePicker() },
            onSelect   = { lang ->
                if (lang.mlKitCode !in settingsState.downloadedModelCodes) {
                    viewModel.closePicker()
                    notDownloadedLang = lang.displayName
                } else {
                    viewModel.setTargetLanguage(lang)
                }
            }
        )
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun ArTopBar(
    sourceLangName:  String,
    targetLangName:  String,
    useSdf:          Boolean,
    onBack:          () -> Unit,
    onPickSource:    () -> Unit,
    onPickTarget:    () -> Unit,
    onToggleOverlay: () -> Unit,
    modifier:        Modifier = Modifier
) {
    // Pulsing glow on the bottom border of the top bar
    val transition = rememberInfiniteTransition(label = "topBarGlow")
    val borderAlpha by transition.animateFloat(
        initialValue  = 0.35f,
        targetValue   = 0.75f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HubColors.Black.copy(alpha = 0.72f))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button — circular neon ring
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .subtleNeonGlow(cornerRadius = 20.dp, glowRadius = 6.dp, intensity = 0.18f)
                    .clip(CircleShape)
                    .background(HubColors.TileSurface)
                    .border(1.dp, HubColors.NeonGreen.copy(alpha = 0.55f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_to_hub),
                    tint               = HubColors.NeonGreen,
                    modifier           = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Source language chip
            ArLangChip(
                name     = sourceLangName,
                onClick  = onPickSource,
                modifier = Modifier.weight(1f)
            )

            // Arrow divider
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint               = HubColors.NeonGreen.copy(alpha = 0.50f),
                modifier           = Modifier
                    .padding(horizontal = 4.dp)
                    .size(14.dp)
            )

            // Target language chip
            ArLangChip(
                name     = targetLangName,
                onClick  = onPickTarget,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // Title column + tappable overlay-mode badge
            ArTitleBadge(useSdf = useSdf, onToggle = onToggleOverlay)
        }

        // Glowing neon bottom border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind {
                    // 3-layer glow below the bar
                    drawRect(
                        color = HubColors.NeonGreen.copy(alpha = borderAlpha * 0.15f),
                        topLeft = Offset(0f, -6.dp.toPx()),
                        size    = Size(size.width, 6.dp.toPx())
                    )
                    drawRect(
                        color = HubColors.NeonGreen.copy(alpha = borderAlpha * 0.35f),
                        topLeft = Offset(0f, -2.dp.toPx()),
                        size    = Size(size.width, 2.dp.toPx())
                    )
                    drawRect(color = HubColors.NeonGreen.copy(alpha = borderAlpha))
                }
        )
    }
}

// ── Language chip ──────────────────────────────────────────────────────────────

@Composable
private fun ArLangChip(
    name:     String,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .subtleNeonGlow(cornerRadius = 8.dp, glowRadius = 5.dp, intensity = 0.14f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = HubColors.NeonGreenBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(HubColors.TileSurface)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text          = name,
            color         = HubColors.NeonGreen,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines      = 1,
            overflow      = TextOverflow.Ellipsis
        )
    }
}

// ── Title + BETA badge ─────────────────────────────────────────────────────────

@Composable
private fun ArTitleBadge(useSdf: Boolean, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val badgeAlpha by transition.animateFloat(
        initialValue  = 0.60f,
        targetValue   = 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text          = stringResource(R.string.ar_lens_title),
            color         = HubColors.NeonGreen,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 2.5.sp,
            textAlign     = TextAlign.Center
        )
        // Tappable mode badge — CLASSIC (default) or SDF (beta)
        Box(
            modifier = Modifier
                .subtleNeonGlow(cornerRadius = 3.dp, glowRadius = 4.dp, intensity = 0.20f)
                .clip(RoundedCornerShape(3.dp))
                .background(HubColors.NeonGreenFaint)
                .border(
                    1.dp,
                    HubColors.NeonGreen.copy(alpha = if (useSdf) badgeAlpha * 0.70f else 0.40f),
                    RoundedCornerShape(3.dp)
                )
                .clickable { onToggle() }
                .padding(horizontal = 5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text          = if (useSdf) stringResource(R.string.badge_beta) else "CLASSIC",
                color         = HubColors.NeonGreen.copy(alpha = if (useSdf) badgeAlpha else 0.70f),
                fontSize      = 7.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

// ── Offline banner ─────────────────────────────────────────────────────────────

@Composable
private fun ArOfflineBanner(
    message:  String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(HubColors.Black.copy(alpha = 0.80f))
            .border(
                width = 1.dp,
                color = HubColors.NeonGreen.copy(alpha = 0.35f)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.WifiOff,
            contentDescription = null,
            tint               = HubColors.NeonGreen.copy(alpha = 0.65f),
            modifier           = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text      = message,
            color     = HubColors.NeonGreen.copy(alpha = 0.75f),
            fontSize  = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── Language picker bottom sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArLanguagePicker(
    title:      String,
    selected:   AppLanguage,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss:  () -> Unit,
    onSelect:   (AppLanguage) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = HubColors.TileSurface,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .subtleNeonGlow(cornerRadius = 2.dp, glowRadius = 4.dp, intensity = 0.20f)
                    .size(width = 40.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HubColors.NeonGreen.copy(alpha = 0.45f))
            )
        }
    ) {
        // Sheet title
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Neon accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .subtleNeonGlow(cornerRadius = 1.dp, glowRadius = 4.dp, intensity = 0.25f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(HubColors.NeonGreen)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text          = title,
                color         = HubColors.NeonGreen,
                fontSize      = 14.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        // Neon divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HubColors.NeonGreen.copy(alpha = 0.15f))
        )

        LazyColumn(
            modifier        = Modifier.fillMaxWidth(),
            contentPadding  = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(LanguageUtils.SUPPORTED_LANGUAGES) { lang ->
                val isSelected = lang.mlKitCode == selected.mlKitCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.subtleNeonGlow(
                                cornerRadius = 8.dp,
                                glowRadius   = 5.dp,
                                intensity    = 0.14f
                            ) else Modifier
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) HubColors.NeonGreenFaint
                            else            Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) HubColors.NeonGreenBorder
                                    else            Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(lang) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = lang.displayName,
                        color      = if (isSelected) HubColors.NeonGreen
                                     else            HubColors.NeonGreen.copy(alpha = 0.60f),
                        fontSize   = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier   = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = null,
                            tint               = HubColors.NeonGreen,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
