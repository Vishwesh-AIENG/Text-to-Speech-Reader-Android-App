1. **HubScreen Composable** (`/d/TTS/app/src/main/java/com/app/ttsreader/ui/screens/HubScreen.kt`)
**Key Layout Structure:**
- Pure black background using `HubColors.Black` (0xFF000000)
- Staggered 2-column tile grid with 5 rows total
- Scrollable column with 16.dp horizontal padding
- 48.dp top spacer for status bar
**Tile Arrangement (exact heights):**
```
Row 1: Babel Engine (170dp)      | Dyslexia Focus (155dp)
Row 2: Instant Indexing (155dp)  | AR Magic Lens (170dp)
Row 3: E-Reader (155dp — full width)
Row 4: Classic TTS (140dp — full width)
Row 5: Languages (110dp — full width)
```
**Top Bar:**
- "TTS READER" title with NeonGreen color (22.sp, ExtraBold weight)
- Letter spacing: 3.sp
- Subtle neon glow modifier with 12.dp radius, 0.10f intensity
- Settings IconButton (48.dp, 22.dp icon size)
**Navigation Callbacks:**
- `onModeSelected(AppMode)` — Called when tile tapped
- `onOpenSettings()` — Settings button click
- `onOpenLanguages()` — Languages tile click
**String Resources Used:**
- `R.string.hub_title` = "TTS READER"
- Mode titles and subtitles (e.g., `R.string.mode_babel_title`, `R.string.mode_babel_subtitle`, etc.)
- `R.string.content_description_settings` for accessibility
---
### 2. **HubColors Theme** (`/d/TTS/app/src/main/java/com/app/ttsreader/ui/theme/HubColors.kt`)
**Color Palette (hardcoded, doesn't change with theme):**
```kotlin
object HubColors {
    val Black = Color(0xFF000000)                              // Background
    val NeonGreen = Color(0xFF39FF14)                          // Primary text/icons
    val NeonGreenDim = Color(0xFF39FF14).copy(alpha = 0.70f)  // Subtitles (70%)
    val NeonGreenFaint = Color(0xFF39FF14).copy(alpha = 0.12f) // Pressed fill/badges (12%)
    val NeonGreenBorder = Color(0xFF39FF14).copy(alpha = 0.50f) // Tile borders (50%)
    val TileSurface = Color(0xFF0A0A0A)                        // Card background
    val SwirlCenter = Color(0xFF39FF14).copy(alpha = 0.45f)   // Ripple effect center (45%)
}
```
**Custom Glow Modifier:** `subtleNeonGlow()`
- Draws behind the composable (not clipped by rounded corners)
- Creates concentric rounded rectangles for smooth Gaussian-like glow
- Default: 8.dp radius, 0.12f intensity, 5 layers
- Used with 12.dp radius & 0.10f intensity on the hub title
- Used with 6.dp radius & 0.11f intensity on tiles
---
### 3. **AppMode Enum** (`/d/TTS/app/src/main/java/com/app/ttsreader/domain/model/AppMode.kt`)
```kotlin
enum class AppMode(val isAvailable: Boolean) {
    CLASSIC_TTS(isAvailable = true),
    BABEL_CONVERSATION(isAvailable = true),
    DYSLEXIA_FOCUS(isAvailable = true),
    INSTANT_INDEXING(isAvailable = true),
    AR_MAGIC_LENS(isAvailable = true),
    E_READER(isAvailable = true)
}
```
All modes are currently available. `isAvailable = false` routes to `ComingSoonScreen`.
---
### 4. **Navigation & AppRoot** (`/d/TTS/app/src/main/java/com/app/ttsreader/MainActivity.kt`)
**Navigation State Machine:**
1. OnboardingScreen (first launch only)
2. SettingsScreen (overlay)
3. LanguagesScreen (overlay)
4. HubScreen (resting state — `activeMode == null`)
5. Mode-specific screens (when `activeMode` is set)
**State Persistence:**
- `activeMode: AppMode?` — Stored via `rememberSaveable` with custom Saver
- Serialized as enum name or empty string for null
- Survives process death and rotation
**Back Handler:**
- System back from any mode screen → returns to Hub (`activeMode = null`)
---
### 5. **HubModeTile Component** (`/d/TTS/app/src/main/java/com/app/ttsreader/ui/components/HubModeTile.kt`)
**Structure:**
- Rounded corner shape: 16.dp
- Border: 1.5.dp stroke with `NeonGreenBorder` color
- Background: `TileSurface` color (near-black)
- Subtle glow: 6.dp radius, 0.11f intensity
**Content Layout:**
- **Bottom-start:** Icon (30.dp) + Title (14.sp, Bold) + Subtitle (11.sp, Dim)
- **Top-right:** Badge (BETA or SOON if applicable)
**Icon Mappings:**
```kotlin
Icons.Default.Translate            // Babel Engine
Icons.Default.Visibility           // Dyslexia Focus
Icons.Default.ManageSearch         // Instant Indexing
Icons.Default.CameraAlt            // AR Magic Lens
Icons.AutoMirrored.Filled.MenuBook // E-Reader
Icons.Default.RecordVoiceOver      // Classic TTS
Icons.Default.Language             // Languages
```
**Interactive Feedback — Custom Neon Swirl Ripple:**
- Tap detection via `detectTapGestures` with `onPress` suspend lambda
- Ripple animation: 700ms expansion from touch point
- Radial gradient: center alpha 0.45f, middle 0.18f, edges transparent
- 3 concentric ring outlines expanding outward
- Navigation delayed until 700ms window completes (ensures ripple visibility)
**Parameters:**
```kotlin
title: String
subtitle: String
icon: ImageVector
isBeta: Boolean = false
isComingSoon: Boolean = false
onClick: () -> Unit
modifier: Modifier = Modifier
```
---
### 6. **String Resources** (`/d/TTS/app/src/main/res/values/strings.xml`)
**Hub-specific strings (lines 117-143):**
```xml
<string name="hub_title">TTS READER</string>
<string name="mode_languages_title">Languages</string>
<string name="mode_languages_subtitle">Download &amp; Manage Translation Models</string>
<string name="mode_classic_tts_title">Classic TTS</string>
<string name="mode_classic_tts_subtitle">Camera-to-Speech Reader</string>
<string name="mode_babel_title">Babel Engine</string>
<string name="mode_babel_subtitle">Bi-Directional Speech Translation</string>
<string name="mode_dyslexia_title">Dyslexia Focus</string>
<string name="mode_dyslexia_subtitle">Line-Focus &amp; Visual Anchoring</string>
<string name="mode_indexing_title">Instant Indexing</string>
<string name="mode_indexing_subtitle">Physical World Search &amp; Fuzzy Match</string>
<string name="mode_ar_lens_title">AR Magic Lens</string>
<string name="mode_ar_lens_subtitle">Real-Time Overlay Translation</string>
<string name="mode_ereader_title">E-Reader</string>
<string name="mode_ereader_subtitle">AI-Powered Bilingual PDF Library</string>
<string name="content_description_settings">Open settings</string>
```
---
### 7. **Theme System** (`/d/TTS/app/src/main/java/com/app/ttsreader/ui/theme/Theme.kt`)
**Important:** Hub uses hardcoded `HubColors` object — does NOT respect user's dark/light/sepia preference. All other screens use Material3 theme tokens normally.
Theme modes available: LIGHT, DARK, SEPIA, SYSTEM
---
### Key Implementation Details:
1. **Glow Effect:** Uses `drawBehind` with multiple concentric rectangles (not BlurMaskFilter) for performance
2. **Ripple Timing:** `detectTapGestures.onPress` suspend lambda with `tryAwaitRelease()` ensures 700ms animation completes before navigation
3. **Scrollability:** Only needed on very small screens; normal phones don't scroll
4. **State Persistence:** `AppMode` persisted via custom Saver, supporting rotation/process death