package com.app.ttsreader.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.R
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.components.tap
import com.app.ttsreader.utils.LanguageUtils
import com.app.ttsreader.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            settingsViewModel.dismissError()
        }
    }

    LaunchedEffect(Unit) { settingsViewModel.refreshDownloadedModels() }

    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ── Top bar ────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable { onNavigateBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_close),
                        tint               = Color.White.copy(alpha = 0.65f),
                        modifier           = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text          = stringResource(R.string.settings_title).uppercase(),
                    color         = Color.White,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 20.sp,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Section 1: Languages ───────────────────────────────────
            GlassSettingsCard(
                title          = stringResource(R.string.settings_section_languages),
                animationDelay = 100
            ) {
                GlassLanguagePickerItem(
                    label            = stringResource(R.string.settings_source_language),
                    selected         = uiState.sourceLanguage,
                    languages        = uiState.availableLanguages,
                    downloadedCodes  = uiState.downloadedModelCodes,
                    onSelected       = { settingsViewModel.setSourceLanguage(it) }
                )

                GlassDivider()

                GlassLanguagePickerItem(
                    label            = stringResource(R.string.settings_target_language),
                    selected         = uiState.targetLanguage,
                    languages        = uiState.availableLanguages,
                    downloadedCodes  = uiState.downloadedModelCodes,
                    onSelected       = { settingsViewModel.setTargetLanguage(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section 2: Voice & Playback ────────────────────────────
            GlassSettingsCard(
                title          = stringResource(R.string.settings_section_voice),
                animationDelay = 200
            ) {
                GlassSliderItem(
                    label         = stringResource(R.string.settings_speech_rate),
                    value         = uiState.speechRate,
                    onValueChange = { settingsViewModel.setSpeechRate(it) },
                    range         = 0.5f..2.0f,
                    steps         = 5,
                    valueLabel    = String.format("%.2fx", uiState.speechRate)
                )

                GlassDivider()

                GlassSliderItem(
                    label         = stringResource(R.string.settings_pitch),
                    value         = uiState.pitch,
                    onValueChange = { settingsViewModel.setPitch(it) },
                    range         = 0.5f..2.0f,
                    steps         = 5,
                    valueLabel    = String.format("%.2fx", uiState.pitch)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section 3: Accessibility ───────────────────────────────
            GlassSettingsCard(
                title          = stringResource(R.string.settings_section_accessibility),
                animationDelay = 300
            ) {
                GlassSliderItem(
                    label         = stringResource(R.string.settings_font_size),
                    value         = uiState.fontSize.toFloat(),
                    onValueChange = { settingsViewModel.setFontSize(it.toInt()) },
                    range         = 12f..32f,
                    steps         = 4,
                    valueLabel    = "${uiState.fontSize}sp"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section 4: Downloaded Models ───────────────────────────
            GlassSettingsCard(
                title          = stringResource(R.string.settings_section_models),
                animationDelay = 400
            ) {
                if (uiState.downloadedModelCodes.isEmpty()) {
                    Text(
                        text     = stringResource(R.string.settings_no_models),
                        color    = Color.White.copy(alpha = 0.50f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                } else {
                    val haptic = LocalHapticFeedback.current
                    uiState.downloadedModelCodes.sorted().forEachIndexed { index, code ->
                        val language    = LanguageUtils.findByCode(code)
                        val displayName = language?.displayName ?: code
                        val isDeleting  = uiState.deletingModelCode == code

                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint               = Color(0xFF66FFD1),
                                modifier           = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text     = displayName,
                                color    = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color       = Color.White.copy(alpha = 0.65f)
                                )
                            } else {
                                IconButton(
                                    onClick  = { haptic.tap(); settingsViewModel.deleteModel(code) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector        = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.settings_delete_model),
                                        tint               = Color.White.copy(alpha = 0.55f),
                                        modifier           = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (index < uiState.downloadedModelCodes.size - 1) {
                            GlassDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section 5: About ───────────────────────────────────────
            GlassSettingsCard(
                title          = stringResource(R.string.settings_section_about),
                animationDelay = 500
            ) {
                val context = LocalContext.current
                val versionName = remember {
                    runCatching {
                        context.packageManager
                            .getPackageInfo(context.packageName, 0)
                            .versionName
                    }.getOrDefault("–")
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text       = stringResource(R.string.settings_version, versionName ?: "–"),
                        color      = Color.White.copy(alpha = 0.85f),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = stringResource(R.string.settings_about_description),
                        color    = Color.White.copy(alpha = 0.50f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // ── "Developed By Vishwesh" footer ─────────────────────────
            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── Private composables ──────────────────────────────────────────────────────

@Composable
private fun GlassSettingsCard(
    title: String,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val cardBg = remember {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
        )
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(animationDelay.toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label         = "cardAlpha"
    )
    val slide by animateFloatAsState(
        targetValue   = if (appeared) 0f else 50f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label         = "cardSlide"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha; translationY = slide }
            .clip(shape)
            .background(cardBg)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
    ) {
        Text(
            text       = title,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        content()
    }
}

@Composable
private fun GlassDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color    = Color.White.copy(alpha = 0.10f),
        modifier = modifier
    )
}

@Composable
private fun GlassSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text       = label,
                color      = Color.White.copy(alpha = 0.85f),
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text       = valueLabel,
                color      = Color(0xFF66FFD1),
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = range,
            steps         = steps,
            modifier      = Modifier.fillMaxWidth(),
            colors        = SliderDefaults.colors(
                thumbColor           = Color.White,
                activeTrackColor     = Color(0xFF66FFD1),
                inactiveTrackColor   = Color.White.copy(alpha = 0.20f)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassLanguagePickerItem(
    label: String,
    selected: AppLanguage,
    languages: List<AppLanguage>,
    downloadedCodes: Set<String>,
    onSelected: (AppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded          = expanded,
        onExpandedChange  = { expanded = it },
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextField(
            value       = selected.displayName,
            onValueChange = {},
            readOnly    = true,
            label       = {
                Text(label, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp)
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier    = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors      = TextFieldDefaults.colors(
                focusedTextColor          = Color.White,
                unfocusedTextColor        = Color.White.copy(alpha = 0.85f),
                focusedContainerColor     = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor   = Color.White.copy(alpha = 0.05f),
                focusedIndicatorColor     = Color(0xFF66FFD1),
                unfocusedIndicatorColor   = Color.White.copy(alpha = 0.20f),
                focusedTrailingIconColor  = Color.White.copy(alpha = 0.65f),
                unfocusedTrailingIconColor = Color.White.copy(alpha = 0.45f)
            )
        )

        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { language ->
                val isDownloaded = language.mlKitCode in downloadedCodes
                DropdownMenuItem(
                    text  = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDownloaded) {
                                Icon(
                                    imageVector        = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier           = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(language.displayName)
                        }
                    },
                    onClick = {
                        onSelected(language)
                        expanded = false
                    }
                )
            }
        }
    }
}
