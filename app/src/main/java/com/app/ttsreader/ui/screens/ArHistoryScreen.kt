package com.app.ttsreader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.data.local.ArHistoryEntity
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.ui.theme.subtleNeonGlow
import com.app.ttsreader.utils.LanguageUtils
import com.app.ttsreader.viewmodel.ArHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AR translation history — neon-themed list of past translations seen in AR mode.
 *
 * Favorites pin to the top; each row supports tap-to-pronounce via the
 * shared [SpeechController], a favorite-star toggle, and per-row delete.
 */
@Composable
fun ArHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: ArHistoryViewModel = viewModel(),
) {
    BackHandler { onNavigateBack() }
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(HubColors.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HubColors.Black.copy(alpha = 0.85f))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .subtleNeonGlow(cornerRadius = 20.dp, glowRadius = 6.dp, intensity = 0.18f)
                        .clip(CircleShape)
                        .background(HubColors.TileSurface)
                        .border(1.dp, HubColors.NeonGreen.copy(alpha = 0.55f), CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = HubColors.NeonGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "AR HISTORY",
                    color = HubColors.NeonGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.0.sp,
                    modifier = Modifier.weight(1f),
                )
                if (entries.any { !it.isFavorite }) {
                    Text(
                        text = "CLEAR",
                        color = HubColors.NeonGreen.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.clearNonFavorites() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // Neon divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HubColors.NeonGreen.copy(alpha = 0.20f)),
            )

            if (entries.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onPronounce = { viewModel.pronounce(entry) },
                            onToggleFavorite = { viewModel.toggleFavorite(entry) },
                            onDelete = { viewModel.delete(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: ArHistoryEntity,
    onPronounce: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val sourceName = LanguageUtils.findByCode(entry.sourceLang)?.displayName ?: entry.sourceLang
    val targetName = LanguageUtils.findByCode(entry.targetLang)?.displayName ?: entry.targetLang

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .subtleNeonGlow(cornerRadius = 10.dp, glowRadius = 5.dp, intensity = 0.10f)
            .clip(RoundedCornerShape(10.dp))
            .background(HubColors.TileSurface)
            .border(BorderStroke(1.dp, HubColors.NeonGreenBorder), RoundedCornerShape(10.dp))
            .clickable { onPronounce() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.translatedText,
                color = HubColors.NeonGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.sourceText,
                color = HubColors.NeonGreen.copy(alpha = 0.55f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$sourceName → $targetName  ·  ${relativeTime(entry.lastSeenAtMs)}",
                color = HubColors.NeonGreen.copy(alpha = 0.40f),
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButtonGhost(
                icon = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                desc = if (entry.isFavorite) "Unfavorite" else "Favorite",
                tint = if (entry.isFavorite) HubColors.Accent else HubColors.NeonGreen.copy(alpha = 0.65f),
                onClick = onToggleFavorite,
            )
            Spacer(Modifier.height(6.dp))
            IconButtonGhost(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                desc = "Speak",
                tint = HubColors.NeonGreen.copy(alpha = 0.85f),
                onClick = onPronounce,
            )
            Spacer(Modifier.height(6.dp))
            IconButtonGhost(
                icon = Icons.Filled.Delete,
                desc = "Delete",
                tint = HubColors.NeonGreen.copy(alpha = 0.55f),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun IconButtonGhost(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NO HISTORY YET",
                color = HubColors.NeonGreen.copy(alpha = 0.60f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.0.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Translations seen in AR Magic Lens will appear here.",
                color = HubColors.NeonGreen.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        }
    }
}

private fun relativeTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val delta = now - ms
    return when {
        delta < 60_000          -> "just now"
        delta < 3_600_000       -> "${delta / 60_000}m ago"
        delta < 86_400_000      -> "${delta / 3_600_000}h ago"
        delta < 7 * 86_400_000  -> "${delta / 86_400_000}d ago"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    }
}
