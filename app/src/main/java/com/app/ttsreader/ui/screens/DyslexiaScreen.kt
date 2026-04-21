package com.app.ttsreader.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.R
import com.app.ttsreader.ui.components.CameraHintBanner
import com.app.ttsreader.ui.components.CameraPreview
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.components.PermissionHandler
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.ui.theme.subtleNeonGlow
import com.app.ttsreader.viewmodel.DyslexiaSubMode
import com.app.ttsreader.viewmodel.DyslexiaViewModel
import kotlinx.coroutines.launch

/**
 * Dyslexia Focus screen — two reading modes in one neon-dark UI.
 *
 * ## Camera mode
 * Live camera preview with a [DyslexiaFocusOverlay] that dims everything outside
 * a smoothly-tracked focus band locked onto the text line at the vertical centre
 * of the viewport. The focused text is rendered large in a neon panel at the bottom.
 *
 * ## Text mode
 * Paste any text → the app parses it into paragraphs and highlights one word at
 * a time. A large "spotlight" card shows the current word. Other paragraphs are
 * dimmed and blurred. Controls at the bottom let the user step manually or
 * enable auto-read at a configurable speed.
 *
 * @param onNavigateBack Returns the user to the Hub.
 */
@Composable
fun DyslexiaScreen(
    onNavigateBack: () -> Unit,
    viewModel: DyslexiaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Pause camera and auto-read when the screen leaves composition.
    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    BackHandler {
        when {
            uiState.subMode == DyslexiaSubMode.TEXT && !uiState.isEditingText ->
                viewModel.backToEdit()
            else -> onNavigateBack()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Glass aurora shows through in text mode; camera covers it in camera mode
        GlassBackground()

        // ── Main content ───────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar with back + title + mode toggle
            DyslexiaTopBar(
                subMode        = uiState.subMode,
                onBack         = {
                    if (uiState.subMode == DyslexiaSubMode.TEXT && !uiState.isEditingText)
                        viewModel.backToEdit()
                    else onNavigateBack()
                },
                onSwitchMode   = viewModel::switchMode
            )

            // Mode-specific content
            when (uiState.subMode) {
                DyslexiaSubMode.CAMERA -> CameraPane(uiState, viewModel)
                DyslexiaSubMode.TEXT   -> TextPane(uiState, viewModel)
            }
        }

        // Error toast
        if (uiState.errorMessage != null) {
            Surface(
                modifier       = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape          = RoundedCornerShape(12.dp),
                color          = HubColors.NeonGreenFaint,
                tonalElevation = 0.dp
            ) {
                Text(
                    text     = uiState.errorMessage!!,
                    color    = HubColors.NeonGreen,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { viewModel.clearError() }
                )
            }
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────────

@Composable
private fun DyslexiaTopBar(
    subMode: DyslexiaSubMode,
    onBack: () -> Unit,
    onSwitchMode: (DyslexiaSubMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 44.dp, start = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = HubColors.NeonGreen,
                modifier           = Modifier.size(22.dp)
            )
        }

        Text(
            text          = "DYSLEXIA FOCUS",
            color         = HubColors.NeonGreen,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 15.sp,
            letterSpacing = 2.sp,
            modifier      = Modifier
                .weight(1f)
                .subtleNeonGlow(glowRadius = 10.dp, intensity = 0.10f)
        )

        // Mode toggle pills — TEXT first (default mode)
        ModeTab(
            label    = "TEXT",
            icon     = Icons.Default.TextFields,
            selected = subMode == DyslexiaSubMode.TEXT,
            onClick  = { onSwitchMode(DyslexiaSubMode.TEXT) }
        )
        ModeTab(
            label    = "CAMERA",
            icon     = Icons.Default.CameraAlt,
            selected = subMode == DyslexiaSubMode.CAMERA,
            onClick  = { onSwitchMode(DyslexiaSubMode.CAMERA) }
        )
    }
}

@Composable
private fun ModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg     = if (selected) HubColors.NeonGreenFaint else Color.Transparent
    val border = if (selected) HubColors.NeonGreen      else HubColors.NeonGreenBorder
    val tint   = if (selected) HubColors.NeonGreen      else HubColors.NeonGreenDim

    Row(
        modifier = Modifier
            .subtleNeonGlow(cornerRadius = 20.dp, glowRadius = if (selected) 6.dp else 2.dp, intensity = if (selected) 0.12f else 0.03f)
            .clip(RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(20.dp))
            .background(bg, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(14.dp)
        )
        Text(
            text          = label,
            color         = tint,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

// ── Camera pane ────────────────────────────────────────────────────────────────

@Composable
private fun CameraPane(
    uiState: com.app.ttsreader.viewmodel.DyslexiaUiState,
    viewModel: DyslexiaViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    PermissionHandler(permission = Manifest.permission.CAMERA) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Live preview
            CameraPreview(modifier = Modifier.fillMaxSize()) { previewView ->
                viewModel.startCamera(lifecycleOwner, previewView)
            }

            // ── Focus band overlay — always visible so the user knows where to scan ──
            // Dims above and below the current band; draws a neon green border on the band.
            val topFrac    = uiState.focusBand.topFraction
            val bottomFrac = uiState.focusBand.bottomFraction
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Above focus band — 88 % opaque black
                        drawRect(
                            color = HubColors.Black.copy(alpha = 0.88f),
                            size  = Size(size.width, topFrac * size.height)
                        )
                        // Below focus band — 88 % opaque black
                        drawRect(
                            color = HubColors.Black.copy(alpha = 0.88f),
                            topLeft = Offset(0f, bottomFrac * size.height),
                            size  = Size(size.width, (1f - bottomFrac) * size.height)
                        )
                        // Cyan glass border around the focus band
                        val bandTop    = topFrac    * size.height
                        val bandHeight = (bottomFrac - topFrac) * size.height
                        drawRect(
                            color   = androidx.compose.ui.graphics.Color(0xFF66FFD1),
                            topLeft = Offset(0f, bandTop),
                            size    = Size(size.width, bandHeight),
                            style   = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                        )
                    }
            )

            // ── Focused text panel ─────────────────────────────────────────────
            if (uiState.hasDetectedText && uiState.focusBand.focusedText.isNotEmpty()) {
                FocusedTextPanel(
                    text     = uiState.focusBand.focusedText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }

            // Lighting hint — fades out after 4 s
            CameraHintBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )

            // ── Scanning hint inside the band before detection ─────────────────
            if (!uiState.hasDetectedText) {
                Text(
                    text      = "Point camera so text falls in the green band",
                    color     = HubColors.NeonGreenDim,
                    fontSize  = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier  = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 100.dp)
                )
            }
        }
    }
}

/**
 * Dark neon panel anchored to the bottom of the camera view that displays the
 * text currently locked inside the focus band.
 */
@Composable
private fun FocusedTextPanel(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier       = modifier,
        color          = Color.Black.copy(alpha = 0.76f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, HubColors.NeonGreenBorder),
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Label
            Text(
                text          = "FOCUS",
                color         = HubColors.NeonGreenDim,
                fontSize      = 9.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            // The detected text — large, neon green, readable
            Text(
                text       = text,
                color      = HubColors.NeonGreen,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                maxLines   = 4,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.subtleNeonGlow(glowRadius = 8.dp, intensity = 0.08f)
            )
        }
    }
}

// ── Text pane ──────────────────────────────────────────────────────────────────

@Composable
private fun TextPane(
    uiState: com.app.ttsreader.viewmodel.DyslexiaUiState,
    viewModel: DyslexiaViewModel
) {
    if (uiState.isEditingText) {
        TextInputView(uiState, viewModel)
    } else {
        TextReadingView(uiState, viewModel)
    }
}

// ── Text input view ────────────────────────────────────────────────────────────

@Composable
private fun TextInputView(
    uiState: com.app.ttsreader.viewmodel.DyslexiaUiState,
    viewModel: DyslexiaViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text       = "Paste your text below and tap READ to begin word-by-word reading.",
            color      = HubColors.NeonGreenDim,
            fontSize   = 13.sp,
            lineHeight = 18.sp
        )

        // Text field (neon bordered)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .subtleNeonGlow(cornerRadius = 12.dp, glowRadius = 4.dp, intensity = 0.06f)
                .border(BorderStroke(1.dp, HubColors.NeonGreenBorder), RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            BasicTextField(
                value         = uiState.rawText,
                onValueChange = viewModel::setRawText,
                modifier      = Modifier.fillMaxSize(),
                textStyle     = TextStyle(
                    color      = Color.White.copy(alpha = 0.90f),
                    fontSize   = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush   = SolidColor(HubColors.NeonGreen),
                decorationBox = { inner ->
                    if (uiState.rawText.isEmpty()) {
                        Text(
                            text      = "Paste text here…",
                            color     = HubColors.NeonGreenDim.copy(alpha = 0.40f),
                            fontSize  = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                    inner()
                }
            )
        }

        // READ button
        Button(
            onClick  = { viewModel.loadText() },
            enabled  = uiState.rawText.isNotBlank(),
            shape    = RoundedCornerShape(50),
            colors   = ButtonDefaults.buttonColors(
                containerColor    = HubColors.NeonGreenFaint,
                contentColor      = HubColors.NeonGreen,
                disabledContainerColor = HubColors.NeonGreenFaint.copy(alpha = 0.3f),
                disabledContentColor   = HubColors.NeonGreenDim.copy(alpha = 0.3f)
            ),
            border   = BorderStroke(1.5.dp, HubColors.NeonGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .subtleNeonGlow(cornerRadius = 50.dp, glowRadius = 8.dp, intensity = 0.14f)
        ) {
            Text(
                text          = "READ",
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 14.sp,
                letterSpacing = 3.sp
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Text reading view ──────────────────────────────────────────────────────────

@Composable
private fun TextReadingView(
    uiState: com.app.ttsreader.viewmodel.DyslexiaUiState,
    viewModel: DyslexiaViewModel
) {
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    // Auto-scroll to current paragraph when it changes
    LaunchedEffect(uiState.activeParagraphIdx) {
        scope.launch {
            listState.animateScrollToItem(uiState.activeParagraphIdx)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Spotlight word card ────────────────────────────────────────────────
        val currentWord = uiState.paragraphs
            .getOrNull(uiState.activeParagraphIdx)
            ?.getOrNull(uiState.activeWordIdx)
            ?: ""

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .subtleNeonGlow(cornerRadius = 14.dp, glowRadius = 12.dp, intensity = 0.20f)
                .border(
                    BorderStroke(1.5.dp, HubColors.NeonGreenBorder),
                    RoundedCornerShape(14.dp)
                )
                .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text          = currentWord,
                color         = HubColors.NeonGreen,
                fontSize      = 40.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign     = TextAlign.Center,
                maxLines      = 1,
                overflow      = TextOverflow.Ellipsis,
                modifier      = Modifier
                    .padding(horizontal = 16.dp)
                    .subtleNeonGlow(glowRadius = 14.dp, intensity = 0.22f)
            )
        }

        // ── Paragraph list ─────────────────────────────────────────────────────
        LazyColumn(
            modifier      = Modifier.weight(1f),
            state         = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp, vertical = 12.dp
            )
        ) {
            itemsIndexed(uiState.paragraphs) { pIdx, para ->
                if (pIdx == uiState.activeParagraphIdx) {
                    // Active paragraph — word-level highlighting
                    ActiveParagraph(
                        words       = para,
                        activeWord  = uiState.activeWordIdx,
                        onWordTap   = { wIdx ->
                            // Allow tapping a word to jump to it
                        }
                    )
                } else {
                    // Inactive paragraph — dimmed + blurred
                    DimmedParagraph(text = para.joinToString(" "))
                }
            }
        }

        // ── Reading controls ───────────────────────────────────────────────────
        ReadingControls(uiState, viewModel)
    }
}

@Composable
private fun ActiveParagraph(
    words: List<String>,
    activeWord: Int,
    onWordTap: (Int) -> Unit
) {
    val annotated = buildAnnotatedString {
        words.forEachIndexed { idx, word ->
            if (idx == activeWord) {
                withStyle(
                    SpanStyle(
                        color      = HubColors.NeonGreen,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        background = HubColors.NeonGreenFaint
                    )
                ) { append(word) }
            } else {
                withStyle(
                    SpanStyle(
                        color      = Color.White.copy(alpha = 0.78f),
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                ) { append(word) }
            }
            if (idx < words.size - 1) append(" ")
        }
    }

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, HubColors.NeonGreenBorder.copy(alpha = 0.40f)),
                RoundedCornerShape(10.dp)
            ),
        shape          = RoundedCornerShape(10.dp),
        color          = Color.White.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Text(
            text       = annotated,
            lineHeight = 28.sp,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun DimmedParagraph(text: String) {
    Text(
        text     = text,
        color    = Color.White.copy(alpha = 0.15f),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .blur(5.dp)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun ReadingControls(
    uiState: com.app.ttsreader.viewmodel.DyslexiaUiState,
    viewModel: DyslexiaViewModel
) {
    Surface(
        modifier  = Modifier.fillMaxWidth(),
        color     = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, HubColors.NeonGreenBorder.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Previous word
            ControlButton(
                icon        = Icons.Default.SkipPrevious,
                description = "Previous word",
                onClick     = { viewModel.prevWord() }
            )

            // Speed − button
            ControlChip(label = "−", onClick = { viewModel.adjustSpeed(faster = false) })

            // Speed display — shown as a multiplier (e.g. "1×", "1.25×")
            Text(
                text          = DyslexiaViewModel.speedLabel(uiState.autoSpeedMs),
                color         = HubColors.NeonGreenDim,
                fontSize      = 12.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            // Speed + button
            ControlChip(label = "+", onClick = { viewModel.adjustSpeed(faster = true) })

            // Auto-read toggle
            ControlButton(
                icon        = if (uiState.isAutoReading) Icons.Default.Pause else Icons.Default.PlayArrow,
                description = if (uiState.isAutoReading) "Pause" else "Auto-read",
                tint        = if (uiState.isAutoReading) HubColors.NeonGreen else HubColors.NeonGreenDim,
                onClick     = { viewModel.toggleAuto() }
            )

            // Next word
            ControlButton(
                icon        = Icons.Default.SkipNext,
                description = "Next word",
                onClick     = { viewModel.nextWord() }
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = HubColors.NeonGreenDim,
    onClick: () -> Unit
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = description,
            tint               = tint,
            modifier           = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun ControlChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(BorderStroke(1.dp, HubColors.NeonGreenBorder), RoundedCornerShape(8.dp))
            .background(HubColors.NeonGreenFaint, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            color      = HubColors.NeonGreen,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
