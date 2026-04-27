package com.app.ttsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.ttsreader.domain.model.AppMode
import com.app.ttsreader.ui.components.GlassBackground
import com.app.ttsreader.ui.screens.BabelScreen
import com.app.ttsreader.ui.screens.ComingSoonScreen
import com.app.ttsreader.ui.screens.DyslexiaScreen
import com.app.ttsreader.ui.screens.ArLensScreen
import com.app.ttsreader.ui.screens.EReaderLibraryScreen
import com.app.ttsreader.ui.screens.ReaderScreen
import com.app.ttsreader.ui.screens.HistoryScreen
import com.app.ttsreader.ui.screens.HubScreen
import com.app.ttsreader.ui.screens.LanguagesScreen
import com.app.ttsreader.ui.screens.MainScreen
import com.app.ttsreader.ui.screens.OnboardingScreen
import com.app.ttsreader.ui.screens.SettingsScreen
import com.app.ttsreader.review.InAppReviewManager
import com.app.ttsreader.ui.theme.OmniLingoTheme
import com.app.ttsreader.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Pre-warm the Play review flow early so ReviewInfo is ready when needed.
        InAppReviewManager.preWarm(this)
        setContent { AppRoot() }
    }
}

/**
 * Top-level composable that manages the entire navigation state.
 *
 * ## Navigation hierarchy
 * ```
 * OnboardingScreen          (first launch only — clears itself via DataStore)
 * SettingsScreen            (overlay, reachable from Hub and from Classic TTS)
 * HubScreen                 (resting state — the Modal Hub dashboard)
 *   └─ Classic TTS mode     (camera screen + glass bottom nav + history tab)
 *   └─ ComingSoonScreen     (placeholder for all other modes)
 * ```
 */
@Composable
private fun AppRoot() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var showSettings   by rememberSaveable { mutableStateOf(false) }
    var showLanguages  by rememberSaveable { mutableStateOf(false) }
    var selectedTab    by rememberSaveable { mutableIntStateOf(0) }
    var selectedBookId by rememberSaveable { mutableStateOf<Long?>(null) }

    // ── Nullable AppMode saver — serialises as the enum name or "" for null ──
    val appModeSaver = Saver<AppMode?, String>(
        save    = { it?.name ?: "" },
        restore = { if (it.isEmpty()) null else AppMode.valueOf(it) }
    )
    var activeMode by rememberSaveable(stateSaver = appModeSaver) { mutableStateOf(null) }

    OmniLingoTheme {
        when {
            // ── 1. Onboarding (first launch) ─────────────────────────────────
            !settingsState.onboardingShown -> {
                OnboardingScreen(
                    onFinished = { settingsViewModel.completeOnboarding() }
                )
            }

            // ── 2. Settings overlay ───────────────────────────────────────────
            showSettings -> {
                SettingsScreen(
                    onNavigateBack    = { showSettings = false },
                    settingsViewModel = settingsViewModel
                )
            }

            // ── 2b. Languages screen ──────────────────────────────────────────
            showLanguages -> {
                LanguagesScreen(
                    onNavigateBack    = { showLanguages = false },
                    settingsViewModel = settingsViewModel
                )
            }

            // ── 3. Hub — resting state ────────────────────────────────────────
            activeMode == null -> {
                HubScreen(
                    onModeSelected  = { mode -> activeMode = mode },
                    onOpenSettings  = { showSettings = true },
                    onOpenLanguages = { showLanguages = true }
                )
            }

            // ── 4. Classic TTS — camera + glass nav + history tab ─────────────
            activeMode == AppMode.CLASSIC_TTS -> {
                BackHandler { activeMode = null }

                // Outer box carries the aurora so both tabs share the same background
                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackground()

                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            GlassNavigationBar(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it }
                            )
                        }
                    ) { innerPadding ->
                        Box(Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                0 -> MainScreen(
                                    onOpenSettings   = { showSettings = true },
                                    onOpenLanguages  = { showLanguages = true },
                                    bottomNavPadding = innerPadding.calculateBottomPadding()
                                )
                                1 -> HistoryScreen(
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }

            // ── 5. Babel Engine ───────────────────────────────────────────────
            activeMode == AppMode.BABEL_CONVERSATION -> {
                BabelScreen(
                    onNavigateBack  = { activeMode = null },
                    onOpenLanguages = { showLanguages = true }
                )
            }

            // ── 6. Dyslexia Focus ─────────────────────────────────────────────
            activeMode == AppMode.DYSLEXIA_FOCUS -> {
                DyslexiaScreen(
                    onNavigateBack = { activeMode = null }
                )
            }

            // ── 7. AR Magic Lens ──────────────────────────────────────────────
            activeMode == AppMode.AR_MAGIC_LENS -> {
                ArLensScreen(
                    onNavigateBack  = { activeMode = null },
                    onOpenLanguages = { showLanguages = true }
                )
            }

            // ── 8a. E-Reader — Reader (book selected) ─────────────────────────
            activeMode == AppMode.E_READER && selectedBookId != null -> {
                ReaderScreen(
                    bookId = selectedBookId!!,
                    onNavigateBack = { selectedBookId = null }
                )
            }

            // ── 8b. E-Reader — Library (no book selected) ─────────────────────
            activeMode == AppMode.E_READER -> {
                EReaderLibraryScreen(
                    onNavigateBack = { activeMode = null },
                    onBookSelected = { id -> selectedBookId = id }
                )
            }

            // ── 9. Coming Soon — placeholder for unavailable modes ────────────
            else -> {
                ComingSoonScreen(
                    mode            = activeMode!!,
                    onNavigateBack  = { activeMode = null }
                )
            }
        }
    }
}

/**
 * Frosted-glass bottom navigation bar for the Classic TTS mode.
 * Dark semi-transparent background with white/cyan glass chip indicators.
 */
@Composable
private fun GlassNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val accentCyan = Color(0xFF66FFD1)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        NavigationBar(
            containerColor = Color.Black.copy(alpha = 0.72f),
            contentColor   = Color.White
        ) {
            // ── Home (Camera) ──────────────────────────────────────
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick  = { onTabSelected(0) },
                icon = {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.nav_home),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                },
                label = {
                    Text(
                        text       = stringResource(R.string.nav_home),
                        fontSize   = 11.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = accentCyan,
                    selectedTextColor   = accentCyan,
                    indicatorColor      = Color.White.copy(alpha = 0.10f),
                    unselectedIconColor = Color.White.copy(alpha = 0.50f),
                    unselectedTextColor = Color.White.copy(alpha = 0.50f)
                )
            )

            // ── History ────────────────────────────────────────────
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick  = { onTabSelected(1) },
                icon = {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.nav_history),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                },
                label = {
                    Text(
                        text       = stringResource(R.string.nav_history),
                        fontSize   = 11.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = accentCyan,
                    selectedTextColor   = accentCyan,
                    indicatorColor      = Color.White.copy(alpha = 0.10f),
                    unselectedIconColor = Color.White.copy(alpha = 0.50f),
                    unselectedTextColor = Color.White.copy(alpha = 0.50f)
                )
            )
        }
    }
}
