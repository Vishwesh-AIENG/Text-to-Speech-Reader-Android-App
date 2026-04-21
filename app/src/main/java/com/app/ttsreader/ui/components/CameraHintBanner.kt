package com.app.ttsreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A transient camera-environment hint shown when the user first opens any
 * screen that uses the live camera (Classic TTS, Dyslexia Focus, Instant
 * Indexing, AR Lens).
 *
 * The banner fades in immediately and fades out automatically after
 * [visibleMs] milliseconds so it never permanently obstructs content.
 */
@Composable
fun CameraHintBanner(
    modifier:  Modifier = Modifier,
    visibleMs: Long     = 4_000L
) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(visibleMs)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(),
        exit    = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.60f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Default.WbSunny,
                contentDescription = null,
                tint               = Color(0xFFFFC107),   // amber sun
                modifier           = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text          = "Best results in a bright environment",
                color         = Color.White.copy(alpha = 0.90f),
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
    }
}
