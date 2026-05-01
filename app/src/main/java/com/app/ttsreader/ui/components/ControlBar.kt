package com.app.ttsreader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.app.ttsreader.R

// ── Module-level constants — allocated once, never re-created per recomposition ─
private val SPEED_OPTIONS  = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val SLEEP_DURATIONS = listOf(0, 15, 30, 60)

/** Stateless format lambda — safe to hoist; captures nothing. */
private val formatMultiplier: (Float) -> String = { String.format("%.2fx", it) }

/**
 * Premium playback control bar.
 *
 * Layout:
 * ┌─────────────── 16dp outer margin ─────────────────┐
 * │  progress bar (3dp, only when multi-sentence)     │
 * │  ┌──────── 8dp inner padding ──────────────────┐  │
 * │  │ [Speed] [|<]  [▶/■ 56dp]  [>|]  [Timer]    │  │
 * │  └─────────────────────────────────────────────┘  │
 * │  28dp rounded pill, 88% surface tint              │
 * └───────────────────────────────────────────────────┘
 *
 * Long-press on the play button opens a speed/pitch bottom sheet.
 *
 * @param pitch          Current TTS pitch (0.5–2.0).
 * @param onPitchChange  Called when user drags the pitch slider in the sheet.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ControlBar(
    isSpeaking: Boolean,
    canSpeak: Boolean,
    speechRate: Float,
    pitch: Float,
    currentSentenceIndex: Int,
    totalSentences: Int,
    sleepTimerMinutes: Int,
    sleepTimerRemainingSeconds: Int,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSleepTimerChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showSpeedSheet by remember { mutableStateOf(false) }

    // Pulsing ring — driven by Animatable so the coroutine only runs while speaking.
    // When isSpeaking flips false the loop exits immediately, saving ~60 draw
    // invalidations per second that the old InfiniteTransition would have produced.
    val pulseProgress = remember { Animatable(0f) }
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            while (isActive) {
                pulseProgress.animateTo(1f, tween(900, easing = LinearEasing))
                pulseProgress.snapTo(0f)
            }
        } else {
            pulseProgress.snapTo(0f)
        }
    }

    // Glass accent
    val accentCyan       = Color(0xFF66FFD1)
    val hasMultipleSentences = totalSentences > 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.70f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
    ) {
        // Progress bar — only during multi-sentence playback
        if (hasMultipleSentences && currentSentenceIndex >= 0) {
            LinearProgressIndicator(
                progress = {
                    if (totalSentences > 1) currentSentenceIndex / (totalSentences - 1).toFloat()
                    else 0f
                },
                modifier  = Modifier.fillMaxWidth().height(3.dp),
                color      = accentCyan,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            // Speed chip ────────────────────────────────────────────────────
            SpeedChip(
                speechRate    = speechRate,
                onSpeedChange = { haptic.tap(); onSpeedChange(it) },
                modifier      = Modifier.weight(1f)
            )

            // Previous sentence ────────────────────────────────────────────
            IconButton(
                onClick  = { haptic.tap(); onPreviousSentence() },
                enabled  = currentSentenceIndex > 0,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.action_previous_sentence),
                    modifier           = Modifier.size(24.dp)
                )
            }

            // Play/Stop — combinedClickable for long-press speed sheet ─────
            val speakInteraction = remember { MutableInteractionSource() }
            val speakColor = if (isSpeaking)
                Color.White.copy(alpha = 0.85f) else accentCyan

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(56.dp)
                    .scaleOnPress(speakInteraction, pressedScale = 0.92f)
                    .then(
                        if (isSpeaking) {
                            Modifier.drawBehind {
                                val p      = pulseProgress.value   // draw-scope read only
                                val base   = 28.dp.toPx()
                                val expand = 24.dp.toPx()
                                drawCircle(
                                    color  = accentCyan,
                                    radius = base + p * expand,
                                    alpha  = (1f - p) * 0.40f
                                )
                            }
                        } else Modifier
                    )
                    .clip(CircleShape)
                    .background(if (isSpeaking || canSpeak) speakColor else speakColor.copy(alpha = 0.30f))
                    .combinedClickable(
                        interactionSource = speakInteraction,
                        indication        = androidx.compose.material3.ripple(
                            bounded = true,
                            color   = Color.White
                        ),
                        enabled   = isSpeaking || canSpeak,
                        onClick   = { haptic.tap(); if (isSpeaking) onStop() else onSpeak() },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSpeedSheet = true
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isSpeaking)
                        stringResource(R.string.action_stop)
                    else
                        stringResource(R.string.action_speak),
                    tint     = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Next sentence ────────────────────────────────────────────────
            IconButton(
                onClick  = { haptic.tap(); onNextSentence() },
                enabled  = currentSentenceIndex >= 0 && currentSentenceIndex < totalSentences - 1,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.action_next_sentence),
                    modifier           = Modifier.size(24.dp)
                )
            }

            // Sleep timer chip ──────────────────────────────────────────────
            SleepTimerChip(
                timerMinutes     = sleepTimerMinutes,
                remainingSeconds = sleepTimerRemainingSeconds,
                onTimerChange    = { haptic.tap(); onSleepTimerChange(it) },
                modifier         = Modifier.weight(1f)
            )
        }
    }

    // Speed & Pitch bottom sheet (long-press on play button)
    if (showSpeedSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            sheetState       = sheetState
        ) {
            SpeedPitchSheet(
                speechRate    = speechRate,
                pitch         = pitch,
                onSpeedChange = onSpeedChange,
                onPitchChange = onPitchChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

// ── Speed / Pitch bottom sheet content ───────────────────────────────────────

@Composable
private fun SpeedPitchSheet(
    speechRate: Float,
    pitch: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text       = stringResource(R.string.action_speed),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(bottom = 16.dp)
        )

        SheetSliderRow(
            label    = stringResource(R.string.settings_speech_rate),
            value    = speechRate,
            onChange = onSpeedChange,
            range    = 0.5f..2.0f,
            format   = formatMultiplier
        )

        Spacer(Modifier.height(8.dp))

        SheetSliderRow(
            label    = stringResource(R.string.settings_pitch),
            value    = pitch,
            onChange = onPitchChange,
            range    = 0.5f..2.0f,
            format   = formatMultiplier
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetSliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text       = format(value),
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(start = 8.dp)
        )
    }
    Slider(
        value         = value,
        onValueChange = onChange,
        valueRange    = range,
        modifier      = Modifier.fillMaxWidth()
    )
}

// ── Private chip composables ─────────────────────────────────────────────────

@Composable
private fun SpeedChip(
    speechRate: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds    = SPEED_OPTIONS
    val clamped   = speechRate.coerceIn(0.5f, 2.0f)
    val idx       = speeds.indexOfFirst { kotlin.math.abs(it - clamped) < 0.01f }.takeIf { it >= 0 } ?: 2
    val nextSpeed = speeds[(idx + 1) % speeds.size]

    val speedDesc = remember(speechRate) { String.format("Speed %.1fx. Tap to change.", speechRate) }
    val speedText = remember(speechRate) { String.format("%.1fx", speechRate) }
    Surface(
        onClick      = { onSpeedChange(nextSpeed) },
        modifier     = modifier
            .padding(horizontal = 4.dp)
            .sizeIn(minHeight = 44.dp)
            .semantics { contentDescription = speedDesc },
        shape        = RoundedCornerShape(12.dp),
        color        = Color.White.copy(alpha = 0.10f),
        contentColor = Color(0xFF66FFD1)
    ) {
        Text(
            text       = speedText,
            style      = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SleepTimerChip(
    timerMinutes: Int,
    remainingSeconds: Int,
    onTimerChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val durations    = SLEEP_DURATIONS
    val currentIdx   = durations.indexOf(timerMinutes).takeIf { it >= 0 } ?: 0
    val nextDuration = durations[(currentIdx + 1) % durations.size]
    val isActive     = timerMinutes > 0

    val timerDesc = remember(isActive, remainingSeconds) {
        if (isActive) {
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            String.format("Sleep timer %d minutes %d seconds remaining. Tap to change.", m, s)
        } else {
            "Sleep timer off. Tap to set."
        }
    }
    Surface(
        onClick      = { onTimerChange(nextDuration) },
        modifier     = modifier
            .padding(horizontal = 4.dp)
            .sizeIn(minHeight = 44.dp)
            .semantics { contentDescription = timerDesc },
        shape        = RoundedCornerShape(12.dp),
        color        = if (isActive)
            Color(0xFF66FFD1).copy(alpha = 0.15f)
        else
            Color.White.copy(alpha = 0.08f),
        contentColor = if (isActive)
            Color(0xFF66FFD1)
        else
            Color.White.copy(alpha = 0.50f)
    ) {
        val displayText = remember(isActive, remainingSeconds) {
            if (isActive) {
                val m = remainingSeconds / 60
                val s = remainingSeconds % 60
                String.format("%d:%02d", m, s)
            } else "∞"
        }

        Text(
            text       = displayText,
            style      = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        )
    }
}
