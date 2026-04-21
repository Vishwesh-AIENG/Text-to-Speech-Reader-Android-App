package com.app.ttsreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.ttsreader.R

/**
 * Enhanced "downloading model" banner with indeterminate progress bar.
 *
 * ML Kit doesn't expose download progress, so we show an animated indeterminate
 * linear progress bar plus a spinner to communicate that work is happening.
 *
 * @param isDownloading Whether a model download is in progress.
 * @param modifier      Should be [Alignment.TopCenter] in the parent [Box].
 */
@Composable
fun ModelDownloadIndicator(
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible  = isDownloading,
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
            shape           = RoundedCornerShape(16.dp),
            color           = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier        = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text  = stringResource(R.string.downloading_model),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Indeterminate progress bar — gives visual motion so user knows it's active
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )
            }
        }
    }
}
