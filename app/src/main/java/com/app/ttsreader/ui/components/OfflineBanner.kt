package com.app.ttsreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.ttsreader.R

/**
 * Pill-shaped "offline" warning banner that slides in from the top.
 *
 * Spacing (8dp grid):
 * - 12dp top margin so it clears the status bar
 * - 16dp horizontal inner padding
 * - 8dp vertical inner padding
 * - 8dp gap between icon and label
 *
 * 2dp shadow elevation gives the pill visual lift above the camera feed.
 *
 * @param isOffline Whether to show the banner.
 * @param modifier  Applied to the outer [AnimatedVisibility] container.
 */
@Composable
fun OfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible  = isOffline,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec  = tween(250, easing = FastOutSlowInEasing),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            animationSpec = tween(180),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = tween(180))
    ) {
        Surface(
            shape           = RoundedCornerShape(50),
            color           = MaterialTheme.colorScheme.errorContainer,
            shadowElevation = 2.dp,
            modifier        = Modifier.padding(top = 12.dp)
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.Filled.WifiOff,
                    contentDescription = null,
                    modifier           = Modifier.size(16.dp),
                    tint               = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
