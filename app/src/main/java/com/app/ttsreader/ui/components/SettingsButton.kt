package com.app.ttsreader.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.app.ttsreader.R

/**
 * Floating 48dp gear-icon button placed at the top-right of the camera preview.
 *
 * Interactive:
 * - Ripple effect built in via [FilledTonalIconButton]
 * - Haptic feedback on tap
 * - Hover state: elevation rises 0→3dp with 150ms smooth transition (tablet/desktop)
 * - 48×48dp touch target (Material 3 minimum)
 */
@Composable
fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val shadowElevation by animateDpAsState(
        targetValue    = if (isHovered) 6.dp else 0.dp,
        animationSpec  = tween(150),
        label          = "settingsBtnElevation"
    )

    FilledTonalIconButton(
        onClick           = { haptic.tap(); onClick() },
        shape             = CircleShape,
        interactionSource = interactionSource,
        modifier          = modifier
            .hoverable(interactionSource)
            .padding(12.dp)
            .size(48.dp)
    ) {
        Icon(
            imageVector        = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.content_description_settings),
            tint               = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
