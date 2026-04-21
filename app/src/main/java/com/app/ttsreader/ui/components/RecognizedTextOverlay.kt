package com.app.ttsreader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ttsreader.R
import kotlinx.coroutines.delay

/**
 * Premium white reading card that slides up when text is recognized.
 *
 * Layout (per 8dp grid):
 * ┌─ 12dp outer margin ────────────────────────────┐
 * │  Surface: 20dp rounded, 8dp elevation          │
 * │  ┌─ 16dp inner padding ─────────────────────┐  │
 * │  │ Detected label        [📋 copy 44dp]     │  │
 * │  │ ─────────────────────────── (1dp divider)│  │
 * │  │ Recognized text (bodyMedium, onSurface)   │  │
 * │  │                                           │  │
 * │  │ 8dp spacer ── if translated ──────────── │  │
 * │  │ Translation label (primary, labelSmall)   │  │
 * │  │ Translated text (highlighted if speaking) │  │
 * │  └───────────────────────────────────────────┘  │
 * └───────────────────────────────────────────────┘
 *
 * Interactive:
 * - Copy button: 44dp touch target, haptic on tap, "Copied!" fade transition
 * - Active sentence highlighted with light purple/sepia background
 *
 * @param recognizedText     Raw OCR text.
 * @param translatedText     Translated result (empty = hide section).
 * @param isTranslating      Whether translation is in progress (shows shimmer).
 * @param sentences          Sentence list for TTS highlighting.
 * @param currentSentenceIndex Index currently being spoken (-1 = no highlight).
 * @param currentWordStart    Start offset of current word within sentence (API 26+).
 * @param currentWordEnd      End offset of current word within sentence (API 26+).
 * @param modifier           Parent container modifier.
 */
@Composable
fun RecognizedTextOverlay(
    recognizedText: String,
    translatedText: String = "",
    isTranslating: Boolean = false,
    sentences: List<String> = emptyList(),
    currentSentenceIndex: Int = -1,
    currentWordStart: Int = -1,
    currentWordEnd: Int = -1,
    modifier: Modifier = Modifier
) {
    val hasRecognized = recognizedText.isNotBlank()
    val hasTranslated = translatedText.isNotBlank()
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(2000L); copied = false }
    }

    AnimatedVisibility(
        visible = hasRecognized,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMedium
            ),
            initialOffsetY = { it / 3 }
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            animationSpec = tween(180),
            targetOffsetY = { it / 3 }
        ) + fadeOut(animationSpec = tween(180))
    ) {
        // Frosted-glass card — semi-transparent so the camera bleeds through slightly.
        // On API 31+ the system WindowBlur handles the blur; on older devices the
        // alpha + shadow give sufficient depth without a blurry backdrop.
        Surface(
            modifier        = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp)),
            shape           = RoundedCornerShape(20.dp),
            color           = Color.Black.copy(alpha = 0.72f),
            shadowElevation = 8.dp,
            tonalElevation  = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Header: label + copy button ──────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = stringResource(R.string.overlay_detected),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White.copy(alpha = 0.50f),
                        modifier   = Modifier.weight(1f)
                    )

                    // Copy button — 44dp min touch target, haptic + animated feedback
                    AnimatedContent(
                        targetState    = copied,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                        label          = "copyFeedback"
                    ) { isCopied ->
                        if (isCopied) {
                            Text(
                                text       = stringResource(R.string.overlay_copied),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = Color(0xFF66FFD1),
                                modifier   = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        } else {
                            // sizeIn ensures 44dp minimum touch target even if icon is smaller
                            IconButton(
                                onClick  = {
                                    haptic.tap()
                                    clipboardManager.setText(
                                        AnnotatedString(if (hasTranslated) translatedText else recognizedText)
                                    )
                                    copied = true
                                },
                                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.overlay_copy_text),
                                    modifier           = Modifier.size(18.dp),
                                    tint               = Color.White.copy(alpha = 0.60f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color    = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // ── Recognized text (highlighted if source is being spoken) ─
                val recognizedAnnotated = remember(
                    recognizedText, sentences, currentSentenceIndex,
                    currentWordStart, currentWordEnd, hasTranslated
                ) {
                    buildHighlightedText(
                        text             = recognizedText,
                        sentences        = sentences,
                        currentIndex     = currentSentenceIndex,
                        wordStart        = currentWordStart,
                        wordEnd          = currentWordEnd,
                        isTranslatedMode = hasTranslated
                    )
                }
                Text(
                    text     = recognizedAnnotated,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                )

                // ── Translated text section (shimmer while loading) ─────────────
                if (hasTranslated || isTranslating) {
                    Spacer(Modifier.height(8.dp))

                    HorizontalDivider(
                        color    = Color.White.copy(alpha = 0.10f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Text(
                        text       = stringResource(R.string.overlay_translation),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color(0xFF66FFD1),
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    if (isTranslating && !hasTranslated) {
                        // Shimmer skeleton while translation is in progress
                        ShimmerEffect(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            lineCount = 3
                        )
                    } else {
                        // Translated text — highlighted if translation is being spoken
                        val translatedAnnotated = remember(
                            translatedText, sentences, currentSentenceIndex,
                            currentWordStart, currentWordEnd
                        ) {
                            buildHighlightedText(
                                text             = translatedText,
                                sentences        = sentences,
                                currentIndex     = currentSentenceIndex,
                                wordStart        = currentWordStart,
                                wordEnd          = currentWordEnd,
                                isTranslatedMode = true
                            )
                        }
                        Text(
                            text     = translatedAnnotated,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Sentence highlight builder ────────────────────────────────────────────────

private fun buildHighlightedText(
    text: String,
    sentences: List<String>,
    currentIndex: Int,
    wordStart: Int = -1,
    wordEnd: Int = -1,
    isTranslatedMode: Boolean
): AnnotatedString {
    if (currentIndex < 0 || currentIndex >= sentences.size || sentences.isEmpty()) {
        return AnnotatedString(text)
    }
    val sentence    = sentences[currentIndex]
    val startOffset = text.indexOf(sentence)
    if (startOffset < 0) return AnnotatedString(text)

    // Glass card is dark — use cyan/white highlights for both modes.
    val sentenceHighlight = if (isTranslatedMode)
        Color(0xFF66FFD1).copy(alpha = 0.22f)   // cyan glow for translated sentence
    else
        Color.White.copy(alpha = 0.14f)           // soft white for source sentence

    val wordHighlight = if (isTranslatedMode)
        Color(0xFF66FFD1).copy(alpha = 0.50f)   // stronger cyan for active word
    else
        Color.White.copy(alpha = 0.28f)           // brighter white for source word

    return buildAnnotatedString {
        append(text)

        // Sentence-level background (soft highlight)
        addStyle(
            style = SpanStyle(
                background = sentenceHighlight,
                color = if (isTranslatedMode) Color.White else Color.Unspecified
            ),
            start = startOffset,
            end   = startOffset + sentence.length
        )

        // Word-level highlight on top (API 26+ only)
        if (wordStart >= 0 && wordEnd > wordStart && wordEnd <= sentence.length) {
            addStyle(
                style = SpanStyle(
                    background = wordHighlight,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                start = startOffset + wordStart,
                end   = startOffset + wordEnd
            )
        }
    }
}
