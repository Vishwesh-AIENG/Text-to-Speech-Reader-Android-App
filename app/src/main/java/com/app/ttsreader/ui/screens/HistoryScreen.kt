package com.app.ttsreader.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.R
import com.app.ttsreader.data.local.ScanRecord
import com.app.ttsreader.ui.components.tap
import com.app.ttsreader.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GlassSurface = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
)
private val GlassBorder  = Color.White.copy(alpha = 0.18f)
private val Accent        = Color(0xFF66FFD1)

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    historyViewModel: HistoryViewModel = viewModel()
) {
    val scans  by historyViewModel.scans.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Entry animation trigger
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label         = "historyFade"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Spacer(modifier = Modifier.height(16.dp))

            // ── Top bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text          = stringResource(R.string.history_title).uppercase(),
                    color         = Color.White,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 20.sp,
                    letterSpacing = 2.sp,
                    modifier      = Modifier.weight(1f)
                )

                // Clear history — glass circle button
                if (scans.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .then(
                                Modifier.clip(CircleShape).run {
                                    this
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                haptic.tap()
                                historyViewModel.clearHistory()
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector        = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.history_clear),
                                tint               = Color.White.copy(alpha = 0.65f),
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (scans.isEmpty()) {
                // ── Empty state ────────────────────────────────────────────────
                Box(
                    modifier          = Modifier.fillMaxSize(),
                    contentAlignment  = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, GlassBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.History,
                                contentDescription = null,
                                tint               = Color.White.copy(alpha = 0.40f),
                                modifier           = Modifier.size(38.dp)
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text       = stringResource(R.string.history_empty),
                            color      = Color.White.copy(alpha = 0.55f),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            } else {
                // ── Scan list ──────────────────────────────────────────────────
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding      = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 4.dp
                    )
                ) {
                    itemsIndexed(scans, key = { _, s -> s.id }) { index, scan ->
                        ScanHistoryCard(
                            scan           = scan,
                            animationDelay = (index * 60).coerceAtMost(300)
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ScanHistoryCard(
    scan: ScanRecord,
    animationDelay: Int = 0
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic           = LocalHapticFeedback.current
    val dateFormat       = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    val cardShape        = remember { RoundedCornerShape(20.dp) }
    val chipShape        = remember { RoundedCornerShape(8.dp) }

    // Staggered entry animation — same pattern as Hub tiles and Settings cards
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val cardAlpha by animateFloatAsState(
        targetValue   = if (show) 1f else 0f,
        animationSpec = tween(350, delayMillis = animationDelay, easing = FastOutSlowInEasing),
        label         = "cardAlpha"
    )
    val cardSlide by animateFloatAsState(
        targetValue   = if (show) 0f else 40f,
        animationSpec = tween(400, delayMillis = animationDelay, easing = FastOutSlowInEasing),
        label         = "cardSlide"
    )

    Surface(
        onClick = {
            haptic.tap()
            val text = scan.translatedText.ifBlank { scan.recognizedText }
            clipboardManager.setText(AnnotatedString(text))
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha             = cardAlpha
                this.translationY      = cardSlide
            }
            .background(GlassSurface, cardShape)
            .border(1.dp, GlassBorder, cardShape)
            .clip(cardShape),
        shape  = cardShape,
        color  = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // Header: language pair + timestamp
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Language chip
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.10f), chipShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text       = "${scan.sourceLanguageCode.uppercase()} → ${scan.targetLanguageCode.uppercase()}",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color      = Accent
                    )
                }

                Text(
                    text      = dateFormat.format(Date(scan.timestamp)),
                    fontSize  = 10.sp,
                    color     = Color.White.copy(alpha = 0.40f)
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            Spacer(Modifier.height(10.dp))

            // Recognized text
            Text(
                text      = scan.recognizedText,
                fontSize  = 13.sp,
                lineHeight = 18.sp,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                color     = Color.White.copy(alpha = 0.90f)
            )

            // Translated text
            if (scan.translatedText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = scan.translatedText,
                    fontSize  = 12.sp,
                    lineHeight = 17.sp,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    color     = Color.White.copy(alpha = 0.50f)
                )
            }
        }
    }
}
