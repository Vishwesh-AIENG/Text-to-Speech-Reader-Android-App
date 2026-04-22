package com.app.ttsreader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
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
import com.app.ttsreader.ai.GemmaModelState
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.tts.TtsState
import com.app.ttsreader.utils.LanguageUtils
import com.app.ttsreader.viewmodel.ReaderTheme
import com.app.ttsreader.viewmodel.ReaderUiState
import com.app.ttsreader.viewmodel.ReaderViewModel

// ── Speed options for TTS ──────────────────────────────────────────────────────
private val SPEED_OPTIONS = listOf(0.5f, 1.0f, 1.5f, 2.0f)

@Composable
fun ReaderScreen(
    bookId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: ReaderViewModel = viewModel(
        factory = ReaderViewModel.Factory(
            application = context.applicationContext as android.app.Application,
            bookId = bookId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val theme = state.readerTheme

    // Stop TTS when the screen leaves composition.
    DisposableEffect(Unit) {
        onDispose { vm.pause() }
    }

    BackHandler(onBack = onNavigateBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Spacer(modifier = Modifier.height(48.dp))

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                Surface(
                    onClick = onNavigateBack,
                    shape = CircleShape,
                    color = theme.surfaceColor,
                    border = BorderStroke(1.dp, theme.borderColor),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back_to_hub),
                            tint = theme.textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title
                Text(
                    text = state.book?.title ?: "",
                    color = theme.textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Language chip
                Surface(
                    onClick = vm::openLanguagePicker,
                    shape = RoundedCornerShape(20.dp),
                    color = theme.surfaceColor,
                    border = BorderStroke(1.dp, theme.borderColor)
                ) {
                    Text(
                        text = "${state.sourceLang.displayName.take(2).uppercase()} → ${state.targetLang.displayName.take(2).uppercase()}",
                        color = theme.textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                // TTS play / stop button
                val ttsReady = state.ttsState is TtsState.Ready
                val ttsSpeaking = state.ttsState is TtsState.Speaking
                IconButton(
                    onClick = { if (ttsSpeaking || state.sentences.isNotEmpty()) vm.stopReading() else vm.readAloud() },
                    enabled = ttsReady || ttsSpeaking || state.sentences.isNotEmpty()
                ) {
                    Icon(
                        imageVector = if (ttsSpeaking || state.sentences.isNotEmpty()) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (ttsSpeaking) stringResource(R.string.reader_stop_reading) else stringResource(R.string.reader_read_aloud),
                        tint = if (ttsReady || ttsSpeaking || state.sentences.isNotEmpty()) theme.textColor else theme.dimTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // AI Summarize button — behaviour depends on Gemma model state
                when (val modelState = state.gemmaModelState) {
                    is GemmaModelState.Downloaded -> {
                        IconButton(
                            onClick = vm::requestSummary,
                            enabled = state.rawText.isNotEmpty() && !state.isSummarizing
                        ) {
                            Icon(
                                imageVector        = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.reader_summarize),
                                tint               = if (state.rawText.isNotEmpty()) theme.textColor
                                                     else theme.dimTextColor,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                    is GemmaModelState.Downloading -> {
                        // Show circular download progress
                        Box(
                            modifier          = Modifier.size(40.dp),
                            contentAlignment  = Alignment.Center
                        ) {
                            val pct = modelState.progressPercent
                            if (pct > 0) {
                                CircularProgressIndicator(
                                    progress           = { pct / 100f },
                                    modifier           = Modifier.size(22.dp),
                                    color              = theme.textColor,
                                    strokeWidth        = 2.dp,
                                    trackColor         = theme.borderColor
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(22.dp),
                                    color       = theme.textColor,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    else -> {
                        // Not downloaded — tap to show download prompt
                        IconButton(onClick = vm::promptGemmaDownload) {
                            Icon(
                                imageVector        = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.gemma_download_title),
                                tint               = theme.dimTextColor,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Typography toggle button
                IconButton(onClick = vm::toggleTypographyPanel) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = stringResource(R.string.reader_typography),
                        tint = if (state.showTypographyPanel) theme.textColor else theme.dimTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ── Typography panel ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.showTypographyPanel,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                TypographyPanel(state = state, theme = theme, vm = vm)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tab row (Original / Translation) ─────────────────────────────
            if (state.translatedText.isNotEmpty() || state.isTranslating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip(
                        label = stringResource(R.string.reader_original_tab),
                        selected = !state.showTranslation,
                        theme = theme,
                        onClick = { if (state.showTranslation) vm.toggleTranslation() }
                    )
                    ThemeChip(
                        label = stringResource(R.string.reader_translation_tab),
                        selected = state.showTranslation,
                        theme = theme,
                        onClick = { if (!state.showTranslation) vm.toggleTranslation() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Content area ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val displayText = if (state.showTranslation && state.translatedText.isNotEmpty())
                    state.translatedText else state.rawText

                when {
                    state.isLoadingText -> {
                        CircularProgressIndicator(color = theme.textColor)
                    }
                    state.showTranslation && state.isTranslating && state.translatedText.isEmpty() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = theme.textColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.reader_translating),
                                color = theme.dimTextColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                    state.sentences.isNotEmpty() -> {
                        // TTS sentence-highlight mode
                        SentenceListContent(state = state, theme = theme)
                    }
                    displayText.isNotEmpty() -> {
                        // Regular scrollable text mode
                        val scrollState = rememberScrollState()
                        LaunchedEffect(state.currentPage) { scrollState.animateScrollTo(0) }
                        Text(
                            text = displayText,
                            color = theme.textColor,
                            fontSize = state.fontSize.sp,
                            lineHeight = (state.fontSize * 1.65f).sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(vertical = 8.dp)
                        )
                    }
                    !state.isLoadingText -> {
                        Text(
                            text = stringResource(R.string.reader_no_text),
                            color = theme.dimTextColor,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── TTS speed bar (shown while sentences are active) ──────────────
            AnimatedVisibility(
                visible = state.sentences.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                TtsSpeedBar(state = state, theme = theme, vm = vm)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Page navigation bar ───────────────────────────────────────────
            if (state.totalPages > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavButton(
                        label   = "< Prev",
                        enabled = state.currentPage > 0,
                        theme   = theme,
                        onClick = vm::prevPage
                    )
                    Text(
                        text = stringResource(
                            R.string.reader_page_indicator,
                            state.currentPage + 1,
                            state.totalPages
                        ),
                        color = theme.dimTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    NavButton(
                        label   = "Next >",
                        enabled = state.currentPage < state.totalPages - 1,
                        theme   = theme,
                        onClick = vm::nextPage
                    )
                }
            }
        }

        // ── Language picker dialog ─────────────────────────────────────────────
        if (state.isPickingLanguage) {
            LanguagePickerDialog(
                currentLang = state.targetLang,
                theme       = theme,
                onSelect    = vm::setTargetLanguage,
                onDismiss   = vm::closeLanguagePicker
            )
        }

        // ── Gemma model download prompt ────────────────────────────────────────
        if (state.showGemmaDownloadPrompt) {
            val errorMsg = (state.gemmaModelState as? GemmaModelState.Error)?.message
            GemmaDownloadDialog(
                theme      = theme,
                errorMsg   = errorMsg,
                onDownload = vm::confirmGemmaDownload,
                onDismiss  = vm::dismissGemmaDownloadPrompt
            )
        }

        // ── Summary overlay ───────────────────────────────────────────────────
        if (state.showSummary) {
            SummarySheet(
                state   = state,
                theme   = theme,
                onRead  = {
                    if (state.summary.isNotEmpty()) {
                        vm.stopReading()
                        // Temporarily speak summary via TTS
                        val sents = state.summary
                            .lines()
                            .filter { it.isNotBlank() }
                        if (sents.isNotEmpty()) {
                            vm.stopReading()
                        }
                    }
                },
                onDismiss = vm::dismissSummary
            )
        }
    }
}

// ── Typography panel ──────────────────────────────────────────────────────────

@Composable
private fun TypographyPanel(
    state: ReaderUiState,
    theme: ReaderTheme,
    vm:    ReaderViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Font size row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = vm::decreaseFontSize,
                enabled = state.fontSize > 12,
                shape = RoundedCornerShape(8.dp),
                color = theme.bgColor,
                border = BorderStroke(1.dp, theme.borderColor),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("A−", color = theme.dimTextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "${state.fontSize}sp",
                color = theme.textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            Surface(
                onClick = vm::increaseFontSize,
                enabled = state.fontSize < 28,
                shape = RoundedCornerShape(8.dp),
                color = theme.bgColor,
                border = BorderStroke(1.dp, theme.borderColor),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("A+", color = theme.textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Theme chips row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.values().forEach { t ->
                ThemeChip(
                    label    = t.label,
                    selected = state.readerTheme == t,
                    theme    = theme,
                    onClick  = { vm.setReaderTheme(t) }
                )
            }
        }
    }
}

// ── TTS speed bar ─────────────────────────────────────────────────────────────

@Composable
private fun TtsSpeedBar(
    state: ReaderUiState,
    theme: ReaderTheme,
    vm:    ReaderViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Speed",
            color = theme.dimTextColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        SPEED_OPTIONS.forEach { speed ->
            val selected = state.speechRate == speed
            Surface(
                onClick = { vm.setSpeechRate(speed) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) theme.textColor.copy(alpha = 0.15f) else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) theme.textColor else theme.borderColor)
            ) {
                Text(
                    text = "${speed}×",
                    color = if (selected) theme.textColor else theme.dimTextColor,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Sentence navigation
        IconButton(
            onClick = vm::prevSentence,
            enabled = state.currentSentenceIndex > 0,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.reader_prev_sentence),
                tint = if (state.currentSentenceIndex > 0) theme.textColor else theme.dimTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = vm::nextSentence,
            enabled = state.currentSentenceIndex < state.sentences.size - 1,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.reader_next_sentence),
                tint = if (state.currentSentenceIndex < state.sentences.size - 1) theme.textColor else theme.dimTextColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Sentence-highlighted content (TTS mode) ───────────────────────────────────

@Composable
private fun SentenceListContent(state: ReaderUiState, theme: ReaderTheme) {
    val listState  = rememberLazyListState()
    val fontSize   = state.fontSize.sp
    val lineHeight = (state.fontSize * 1.65f).sp

    // Auto-scroll to active sentence
    LaunchedEffect(state.currentSentenceIndex) {
        val idx = state.currentSentenceIndex
        if (idx >= 0 && idx < state.sentences.size) {
            listState.animateScrollToItem(idx)
        }
    }

    LazyColumn(
        state    = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(state.sentences, key = { idx, _ -> idx }) { idx, sentence ->
            val isActive = idx == state.currentSentenceIndex
            val alpha = when {
                state.currentSentenceIndex < 0    -> 1f
                idx < state.currentSentenceIndex  -> 0.42f
                idx == state.currentSentenceIndex -> 1f
                else                              -> 0.85f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isActive) theme.sentenceHighlight else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                val wordStart = state.currentWordStart
                val wordEnd   = state.currentWordEnd

                if (isActive && wordStart >= 0 && wordEnd > wordStart && wordEnd <= sentence.length) {
                    // Word-level highlight within the active sentence
                    val annotated = remember(sentence, wordStart, wordEnd, alpha, theme) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = theme.textColor.copy(alpha = alpha))) {
                                append(sentence.substring(0, wordStart))
                            }
                            withStyle(SpanStyle(color = theme.textColor, background = theme.wordHighlight)) {
                                append(sentence.substring(wordStart, wordEnd))
                            }
                            withStyle(SpanStyle(color = theme.textColor.copy(alpha = alpha))) {
                                append(sentence.substring(wordEnd))
                            }
                        }
                    }
                    Text(text = annotated, fontSize = fontSize, lineHeight = lineHeight)
                } else {
                    Text(
                        text       = sentence,
                        color      = theme.textColor.copy(alpha = alpha),
                        fontSize   = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun ThemeChip(
    label:    String,
    selected: Boolean,
    theme:    ReaderTheme,
    onClick:  () -> Unit
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(16.dp),
        color   = if (selected) theme.textColor.copy(alpha = 0.15f) else Color.Transparent,
        border  = BorderStroke(1.dp, if (selected) theme.textColor else theme.borderColor)
    ) {
        Text(
            text       = label,
            color      = if (selected) theme.textColor else theme.dimTextColor,
            fontSize   = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun NavButton(label: String, enabled: Boolean, theme: ReaderTheme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape   = RoundedCornerShape(8.dp),
        color   = if (enabled) theme.surfaceColor else Color.Transparent,
        border  = BorderStroke(
            1.dp,
            if (enabled) theme.borderColor else theme.borderColor.copy(alpha = 0.25f)
        )
    ) {
        Text(
            text       = label,
            color      = if (enabled) theme.textColor else theme.dimTextColor.copy(alpha = 0.4f),
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

// ── AI Summary sheet ──────────────────────────────────────────────────────────

@Composable
private fun SummarySheet(
    state:    ReaderUiState,
    theme:    ReaderTheme,
    onRead:   () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = theme.surfaceColor,
        title = {
            Text(
                text       = stringResource(R.string.reader_summary_title),
                color      = theme.textColor,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isSummarizing -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = theme.textColor, strokeWidth = 2.dp)
                            Text(
                                text     = stringResource(R.string.reader_translating),
                                color    = theme.dimTextColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                    state.summaryError != null -> {
                        Text(
                            text      = stringResource(R.string.reader_summary_error),
                            color     = theme.textColor.copy(alpha = 0.80f),
                            fontSize  = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        val scrollState = rememberScrollState()
                        Text(
                            text       = state.summary,
                            color      = theme.textColor,
                            fontSize   = 14.sp,
                            lineHeight = 22.sp,
                            modifier   = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.summary.isNotEmpty()) {
                TextButton(onClick = onRead) {
                    Icon(
                        imageVector        = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint               = theme.textColor,
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text  = stringResource(R.string.reader_summary_read_aloud),
                        color = theme.textColor
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text  = stringResource(R.string.reader_summary_dismiss),
                    color = theme.dimTextColor
                )
            }
        }
    )
}

// ── Gemma model download dialog ───────────────────────────────────────────────

@Composable
private fun GemmaDownloadDialog(
    theme:      ReaderTheme,
    errorMsg:   String?,
    onDownload: () -> Unit,
    onDismiss:  () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = theme.surfaceColor,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint     = theme.textColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text       = stringResource(R.string.gemma_download_title),
                    color      = theme.textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text     = stringResource(R.string.gemma_download_body),
                    color    = theme.textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (errorMsg != null) {
                    Text(
                        text     = errorMsg,
                        color    = theme.textColor.copy(alpha = 0.70f),
                        fontSize = 12.sp
                    )
                }
                Text(
                    text     = stringResource(R.string.gemma_download_note),
                    color    = theme.dimTextColor,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(
                    text  = stringResource(R.string.gemma_download_confirm),
                    color = theme.textColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.gemma_download_cancel), color = theme.dimTextColor)
            }
        }
    )
}

@Composable
private fun LanguagePickerDialog(
    currentLang: AppLanguage,
    theme:       ReaderTheme,
    onSelect:    (AppLanguage) -> Unit,
    onDismiss:   () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = theme.surfaceColor,
        title = {
            Text(
                text       = stringResource(R.string.reader_pick_language),
                color      = theme.textColor,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp
            )
        },
        text = {
            LazyColumn {
                itemsIndexed(LanguageUtils.SUPPORTED_LANGUAGES, key = { _, lang -> lang.mlKitCode }) { _, lang ->
                    val selected = lang.mlKitCode == currentLang.mlKitCode
                    Surface(
                        onClick  = { onSelect(lang) },
                        color    = if (selected) theme.textColor.copy(alpha = 0.15f) else theme.surfaceColor,
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text       = lang.displayName,
                            color      = if (selected) theme.textColor else theme.dimTextColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 14.sp,
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = theme.dimTextColor)
            }
        }
    )
}
