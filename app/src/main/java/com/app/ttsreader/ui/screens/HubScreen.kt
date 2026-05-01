package com.app.ttsreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.ttsreader.R
import com.app.ttsreader.domain.model.AppMode
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.components.HubModeTile

/**
 * Modal Hub — glassmorphism launchpad.
 *
 * ## Layout — staggered zig-zag, single column, all cards 252 dp tall
 *
 * Cards alternate left ↔ right with a 30 dp horizontal inset and an 18 dp
 * vertical drop on every right-side card. Right cards are scaled to 0.97×
 * with alpha 0.94 to feel subtly receded — a depth layer without any new
 * component. This is purely a layout + graphicsLayer transformation on the
 * existing [HubModeTile]; its glass design is untouched.
 *
 * ```
 * ┌──────────────────────────────┐
 * │  AR Magic Lens          LEFT │ ← scale 1.0, full alpha
 * └──────────────────────────────┘
 *          ┌──────────────────────────────┐
 *          │  Babel Engine          RIGHT │ ← scale 0.97, alpha 0.94, +18dp down
 *          └──────────────────────────────┘
 * ┌──────────────────────────────┐
 * │  E-Reader               LEFT │
 * └──────────────────────────────┘
 *          ┌──────────────────────────────┐
 *          │  Dyslexia Focus        RIGHT │
 *          └──────────────────────────────┘
 * ┌──────────────────────────────┐
 * │  Classic TTS            LEFT │
 * └──────────────────────────────┘
 *          ┌──────────────────────────────┐
 *          │  Languages             RIGHT │
 *          └──────────────────────────────┘
 * ```
 */
@Composable
fun HubScreen(
    onModeSelected: (AppMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLanguages: () -> Unit = {}
) {
    val settingsDesc = stringResource(R.string.content_description_settings)

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Nebula background ────────────────────────────────────────────
        GlassBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            // ── Status-bar spacer ────────────────────────────────────────
            Spacer(modifier = Modifier.height(48.dp))

            // ── Top bar: title + settings ────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text          = stringResource(R.string.hub_title),
                    color         = Color.White,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 20.sp,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Settings gear — slightly larger than before
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable { onOpenSettings() }
                        .semantics { contentDescription = settingsDesc },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Settings,
                        contentDescription = null,
                        tint               = Color.White.copy(alpha = 0.75f),
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Staggered zig-zag — single column, cards alternate L / R ──
            //
            // LEFT  → padding(end=30dp), scale 1.0,  alpha 1.0   (foreground)
            // RIGHT → padding(start=30dp, top=18dp), scale 0.97, alpha 0.94 (receded)
            //
            // The 30 dp inset + 18 dp vertical drop creates the natural
            // "one up / one down" zig-zag without touching card internals.
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ── 1  Babel Engine — LEFT ────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(end = 30.dp)) {
                    HubModeTile(
                        title          = stringResource(R.string.mode_babel_title),
                        subtitle       = stringResource(R.string.mode_babel_subtitle),
                        icon           = Icons.Default.Translate,
                        onClick        = { onModeSelected(AppMode.BABEL_CONVERSATION) },
                        animationDelay = 80,
                        modifier       = Modifier.fillMaxWidth().height(252.dp)
                    )
                }

                // ── 2  E-Reader — RIGHT ───────────────────────────────────
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, top = 18.dp)
                ) {
                    HubModeTile(
                        title          = stringResource(R.string.mode_ereader_title),
                        subtitle       = stringResource(R.string.mode_ereader_subtitle),
                        icon           = Icons.AutoMirrored.Filled.MenuBook,
                        onClick        = { onModeSelected(AppMode.E_READER) },
                        animationDelay = 150,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .height(252.dp)
                            .graphicsLayer(scaleX = 0.97f, scaleY = 0.97f, alpha = 0.94f)
                    )
                }

                // ── 3  Languages — LEFT ───────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(end = 30.dp)) {
                    HubModeTile(
                        title          = stringResource(R.string.mode_languages_title),
                        subtitle       = stringResource(R.string.mode_languages_subtitle),
                        icon           = Icons.Default.Language,
                        onClick        = onOpenLanguages,
                        animationDelay = 220,
                        modifier       = Modifier.fillMaxWidth().height(252.dp)
                    )
                }

                // ── 4  Classic TTS — RIGHT ────────────────────────────────
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, top = 18.dp)
                ) {
                    HubModeTile(
                        title          = stringResource(R.string.mode_classic_tts_title),
                        subtitle       = stringResource(R.string.mode_classic_tts_subtitle),
                        icon           = Icons.Default.RecordVoiceOver,
                        onClick        = { onModeSelected(AppMode.CLASSIC_TTS) },
                        animationDelay = 290,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .height(252.dp)
                            .graphicsLayer(scaleX = 0.97f, scaleY = 0.97f, alpha = 0.94f)
                    )
                }

                // ── 5  Dyslexia Focus — LEFT ──────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(end = 30.dp)) {
                    HubModeTile(
                        title          = stringResource(R.string.mode_dyslexia_title),
                        subtitle       = stringResource(R.string.mode_dyslexia_subtitle),
                        icon           = Icons.Default.Visibility,
                        onClick        = { onModeSelected(AppMode.DYSLEXIA_FOCUS) },
                        animationDelay = 360,
                        modifier       = Modifier.fillMaxWidth().height(252.dp)
                    )
                }

                // ── 6  AR Magic Lens — RIGHT (bottom) ──────────────────────
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, top = 18.dp)
                ) {
                    HubModeTile(
                        title          = stringResource(R.string.mode_ar_lens_title),
                        subtitle       = stringResource(R.string.mode_ar_lens_subtitle),
                        icon           = Icons.Default.CameraAlt,
                        isBeta         = true,
                        onClick        = { onModeSelected(AppMode.AR_MAGIC_LENS) },
                        animationDelay = 430,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .height(252.dp)
                            .graphicsLayer(scaleX = 0.97f, scaleY = 0.97f, alpha = 0.94f)
                    )
                }
            }

            // ── "Developed By Vishwesh" footer ────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text          = "Developed By Vishwesh",
                    color         = Color.White.copy(alpha = 0.28f),
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier      = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

