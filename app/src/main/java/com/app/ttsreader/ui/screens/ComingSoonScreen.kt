package com.app.ttsreader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.app.ttsreader.R
import com.app.ttsreader.domain.model.AppMode
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.theme.HubColors

/**
 * Placeholder screen shown when the user selects an unavailable mode from the Hub.
 *
 * Maintains the Hub's neon-green aesthetic so the visual experience is cohesive.
 * [BackHandler] intercepts the system back gesture in addition to the on-screen button.
 *
 * @param mode           The [AppMode] the user selected (used to show the correct title/icon).
 * @param onNavigateBack Called when the user navigates back to the Hub.
 */
@Composable
fun ComingSoonScreen(
    mode: AppMode,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        GlassBackground()
        Column(
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center,
            modifier             = Modifier.padding(horizontal = 32.dp)
        ) {
            // Mode icon — large, neon green
            Icon(
                imageVector        = mode.hubIcon(),
                contentDescription = null,
                tint               = HubColors.NeonGreen,
                modifier           = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Mode name
            Text(
                text       = mode.hubTitle(),
                color      = HubColors.NeonGreen,
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                textAlign  = TextAlign.Center,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "Coming Soon" label
            Text(
                text       = stringResource(R.string.coming_soon),
                color      = HubColors.NeonGreenDim,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text      = stringResource(R.string.coming_soon_description),
                color     = HubColors.NeonGreenDim,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Back button — outlined neon green
            OutlinedButton(
                onClick = onNavigateBack,
                border  = BorderStroke(1.5.dp, HubColors.NeonGreen),
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.outlinedButtonColors(
                    contentColor = HubColors.NeonGreen
                )
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text       = stringResource(R.string.action_back_to_hub),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
            }
        }
    }
}

// ── Helpers — resolve per-mode display data without coupling to UI resources in AppMode ──

@Composable
private fun AppMode.hubTitle(): String = when (this) {
    AppMode.BABEL_CONVERSATION -> stringResource(R.string.mode_babel_title)
    AppMode.DYSLEXIA_FOCUS     -> stringResource(R.string.mode_dyslexia_title)
    AppMode.AR_MAGIC_LENS      -> stringResource(R.string.mode_ar_lens_title)
    AppMode.CLASSIC_TTS        -> stringResource(R.string.mode_classic_tts_title)
    AppMode.E_READER           -> stringResource(R.string.mode_ereader_title)
}

private fun AppMode.hubIcon(): ImageVector = when (this) {
    AppMode.BABEL_CONVERSATION -> Icons.Default.Translate
    AppMode.DYSLEXIA_FOCUS     -> Icons.Default.Visibility
    AppMode.AR_MAGIC_LENS      -> Icons.Default.CameraAlt
    AppMode.CLASSIC_TTS        -> Icons.Default.CameraAlt
    AppMode.E_READER           -> Icons.AutoMirrored.Filled.MenuBook
}
