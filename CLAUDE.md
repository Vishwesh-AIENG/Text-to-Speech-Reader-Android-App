# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.app.ttsreader.ExampleUnitTest"

# Lint
./gradlew lintDebug

# Clean build
./gradlew clean assembleDebug
```

The native C++ layer compiles automatically as part of `assembleDebug` via CMake. No separate step needed.

---

## Architecture Overview

### Single-module Android app (`app/`)
- **Language**: Kotlin + C++17 (JNI)
- **UI**: Jetpack Compose + Material3
- **Min SDK**: 24, **Target SDK**: 35
- **ABI**: `arm64-v8a` only
- **No DI framework** — repositories and `NativeOcrEngine` are instantiated directly in ViewModels

### Navigation (MainActivity.kt → AppRoot composable)
Mode selection lives in `HubScreen`. Each mode maps to an `AppMode` enum variant and its own screen/ViewModel:

| AppMode | Screen | ViewModel |
|---|---|---|
| CLASSIC_TTS | MainScreen | MainViewModel |
| BABEL_CONVERSATION | BabelScreen | BabelViewModel |
| DYSLEXIA_FOCUS | DyslexiaScreen | DyslexiaViewModel |
| INSTANT_INDEXING | IndexingScreen | IndexingViewModel |
| AR_MAGIC_LENS | ArLensScreen | ArLensViewModel |
| E_READER | ReaderScreen / EReaderLibraryScreen | ReaderViewModel |

---

## OCR Pipeline (The Core Engine)

### Full data flow

```
CameraX ImageProxy
  → toBitmap() + HARDWARE→ARGB_8888 guard
  → NativeOcrEngine.processImage(bitmap, rotation)        [Kotlin JNI wrapper]
  → nativeProcessImage() in native-lib.cpp                [JNI]
  → AndroidBitmap_lockPixels → cv::Mat (zero-copy wrap)
  → OcrEngine::extractTextStrips()                        [ocr_engine.cpp]
       BGRA→Gray → cv::rotate → adaptiveThreshold
       MSER text-blob detection → perspective warp
       CLAHE → resize to [48×320] → float32 RGB
  → CrnnInference::recognize()                            [crnn_inference.cpp]
       TFLite model [1,48,320,3] NHWC → [1,40,6625]
       CTC greedy decode with ch_dict.txt charset
  → List<SpatialWord>(text, quad[8], confidence)
  → TextRecognitionRepository (StateFlow<OcrResult>)
  → ViewModel → UI
```

### Camera analyzers (`camera/` package)
All four camera modes have their own `ImageAnalysis.Analyzer`. **All use `NativeOcrEngine` — no ML Kit in camera paths.**

| Analyzer | Mode | Throttle | Hardware bitmap guard |
|---|---|---|---|
| TextAnalyzer | Classic TTS | 500ms + AtomicBoolean | ✅ |
| ArLensAnalyzer | AR Magic Lens | 150ms | ✅ |
| DyslexiaFocusAnalyzer | Dyslexia Focus | 250ms | ✅ |
| IndexingAnalyzer | Instant Indexing | 300ms | ✅ |

**Critical**: Every analyzer must convert `HARDWARE` config bitmaps to `ARGB_8888` before the JNI call — NDK `AndroidBitmap_lockPixels` will fail silently on hardware bitmaps.

### Native model assets (`app/src/main/assets/`)
- `crnn_quantized.tflite` — CRNN model, input `[1,48,320,3]` NHWC float32, output `[1,40,6625]`
- `ch_dict.txt` — 6623-line charset (one UTF-8 char per line); index 0 = CTC blank, indices 1–6623 = dict lines. Loaded by `CrnnInference::loadCharset()` after model load.

---

## Native Layer (C++/JNI)

**Files**: `app/src/main/cpp/`
- `native-lib.cpp` — JNI entry points: `nativeSmokeTest`, `nativeLoadModel`, `nativeProcessImage`. Owns global singletons `g_ocrEngine` and `g_crnnInference`.
- `ocr_engine.cpp` — OpenCV preprocessing pipeline. `STRIP_HEIGHT=48`, `STRIP_WIDTH=320`.
- `crnn_inference.cpp` — TFLite inference. Compiled with `HAS_TFLITE` flag (set by CMake when Prefab finds the TFLite package). Without it, `loadModel()` returns false and `recognize()` returns empty — no crash.
- `include/spatial_word.h` — shared C++ struct mirroring `SpatialWord.kt`.

**CMake**: `app/src/main/cpp/CMakeLists.txt`. Uses OpenCV via Prefab (`find_package(OpenCV)`). TFLite via Prefab (`find_package(tensorflowlite)`). Links `android`, `log`, `jnigraphics`.

**JNI class path for ProGuard**: `com/app/ttsreader/ocr/SpatialWord` — must match `buildSpatialWordList()` in native-lib.cpp exactly.

---

## ML Kit Usage (Non-camera paths only)

- **PDF OCR** (`pdf/PdfTextExtractor.kt`): ML Kit `TextRecognition` on `PdfRenderer`-decoded page bitmaps.
- **Translation** (`translate/TranslationRepository.kt`): ML Kit on-device translation, cached per language pair.
- **Summarization** (`ai/GemmaSummarizer.kt`): On-device Gemma via MediaPipe LLM Inference (no API key, no network after one-time model download).

---

## State & Data

### OcrResult sealed class (`ocr/OcrResult.kt`)
`Empty` | `Success(fullText: String, words: List<SpatialWord>)` | `Error(exception)`

`TextRecognitionRepository` holds `StateFlow<OcrResult>` and is the single source of truth for all camera OCR results.

### Translation pipeline gotcha (MainViewModel)
The `startTranslationPipeline()` flow maps all `OcrResult` variants to **nullable strings** first (`null` for non-Success/blank), then `filterNotNull()`, then `distinctUntilChanged()`, then `debounce(800ms)`. This ordering is critical — if you put `distinctUntilChanged` before `filterNotNull`, `OcrResult.Empty` won't reset the chain and the same text won't re-trigger translation after a blank frame.

### Room database (`data/local/AppDatabase.kt`)
Version 2. Entities: `ScanRecord`, `BookEntity`. MIGRATION_1_2 adds the books table.

### Settings persistence
`DataStore<Preferences>` via `SettingsRepository`. Theme (`ThemeMode`), language pair, onboarding state.

---

## OpenGL SDF Overlay (`gl/` package)

`SdfOverlayView` (GLSurfaceView) + `SdfOverlayRenderer` render recognized text quads using signed-distance-field glyph rendering. The display threshold is `displayAlpha > 0.05f` (was 0.3f — raised causes 2-3 frame visual delay). Glyph data lives in `SdfAtlas`.

---

## Hub Screen (Glassmorphism Redesign)

### Layout & Components (`ui/screens/HubScreen.kt`)
Staggered masonry grid (two side-by-side Columns with asymmetric heights):
- **Left Column**: AR Magic Lens (252 dp) → Dyslexia (120) → Indexing (120) → Languages (120)
- **Right Column**: Babel Engine (120) → E-Reader (252) → Classic TTS (120)

**Top Bar**: "TTS READER" title + 28 dp frosted-glass circles (notification bell, settings gear)

**Background** (`GlassBackground()`): Animated aurora with cubic-bezier S-curve path, `BlendMode.Screen` additive blending. 14-second cycle driven by `sin(t)`. Consists of:
- Base: dark navy gradient `#103850 → #185C80 → #0E3040 → #0A1E30`
- S-curve aurora sweep (alphas 0.72/0.62/0.50)
- Upper green flare, left teal glow, bottom deep glow (all Screen blend mode)
- 120 deterministic grain stars (no heap allocation)

### HubModeTile Component (`ui/components/HubModeTile.kt`)
Frosted-glass card with **5 press interactions**:
1. **Glass highlight sweep** — 600 ms light band across card
2. **Outer glow** — expanding white shadow behind card (via `drawBehind` before `clip`)
3. **Icon micro-bounce** — spring physics `dampingRatio=0.4f, stiffness=800f` with Y lift
4. **Card expansion** — scale 1.03× (container-transform hint)
5. **Haptic feedback** — `HapticFeedbackType.LongPress` on press

**Entry Animation**: Staggered fade-in + slide-up via `animateFloatAsState`, triggered by `LaunchedEffect` after `animationDelay` ms.

---

## E-Reader Document Support

### File Formats
E-Reader accepts:
- **PDF** (via `PdfRenderer` — ML Kit text recognition on page bitmaps)
- **DOCX** (via Apache POI extract, ML Kit recognition)
- **DOC** (via Apache POI extract, ML Kit recognition)
- **TXT** (direct file read)

### Document Handling (`data/LibraryRepository.kt`)
- **SAF file import**: Content URI → copy to `filesDir/ereader_books/` with timestamp prefix
- **Title normalization**: Strip `.pdf`, `.docx`, `.doc`, `.txt` suffixes
- **File cleanup**: Delete physical file when book is removed from library

---

## Animation Patterns

### Spring Physics
Used for interactive feedback (icon bounces, card expansion):
```kotlin
spring(dampingRatio = 0.4f, stiffness = 800f)  // Bouncy, snappy feel
```

### Infinite Transitions
Background aurora and other continuous effects:
```kotlin
rememberInfiniteTransition(label = "aurora")
val anim by transition.animateFloat(..., animationSpec = infiniteRepeatable(
    animation = tween(14_000, easing = LinearEasing)
))
```
Use `label` parameter to avoid Compose inspector warnings.

### Entry Stagger
Per-tile staggered entrance (fade + slide):
```kotlin
animateFloatAsState(targetValue = if (show) 1f else 0f,
    animationSpec = tween(800, delayMillis = animationDelay))
```

---

## ProGuard

`app/proguard-rules.pro` keeps:
- `com.app.ttsreader.ocr.SpatialWord` (JNI reflection)
- `com.app.ttsreader.ocr.NativeOcrEngine` (JNI reflection)
- ML Kit, CameraX, Kotlin coroutines, Room

If `SpatialWord` is renamed or removed, `buildSpatialWordList()` in `native-lib.cpp` will throw a JNI exception at the `FindClass` call.
