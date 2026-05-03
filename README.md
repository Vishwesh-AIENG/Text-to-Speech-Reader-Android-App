<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1a6b96,50:2d9cdb,100:0a1e30&height=200&section=header&text=OmniLingo&fontSize=72&fontColor=ffffff&fontAlignY=38&desc=Your%20All-in-One%20Assistive%20Language%20Companion&descAlignY=58&descSize=18&animation=fadeIn" width="100%"/>
> 🌐 **[See it in action →](https://vishwesh-aieng.github.io/Text-to-Speech-Reader-Android-App/)** — interactive demo, no install needed.

<br/>

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min_SDK-24-brightgreen?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

<br/>

[![ML Kit](https://img.shields.io/badge/ML_Kit-FF6F00?style=flat-square&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![CameraX](https://img.shields.io/badge/CameraX-4CAF50?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/training/camerax)
[![Gemma On-Device](https://img.shields.io/badge/Gemma-On--Device_AI-4285F4?style=flat-square&logo=google&logoColor=white)](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
[![Room](https://img.shields.io/badge/Room_DB-FF5722?style=flat-square&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Material3](https://img.shields.io/badge/Material_3-757575?style=flat-square&logo=material-design&logoColor=white)](https://m3.material.io)
[![Live Site](https://img.shields.io/badge/Live%20Site-Visit%20Now-2d9cdb?style=for-the-badge&logo=github)](https://vishwesh-aieng.github.io/Text-to-Speech-Reader-Android-App/)
<br/><br/>

> **OmniLingo** is a premium assistive Android app that turns your camera into a universal language engine —  
> read, translate, speak, and understand text in real time, in any language, anywhere.
> 
<br/>

</div>

---

## ✨ What Makes OmniLingo Special?

<div align="center">

```
📷 Point  →  🔍 Read  →  🌐 Translate  →  🔊 Speak
```

</div>

Six purpose-built modes, one glassmorphism UI. Every mode is fully isolated — switching modes stops all background processes instantly, so your battery and microphone are always in your control.

---

## 🚀 Features

<details open>
<summary><b>🔤 Classic TTS — Camera-to-Speech Reader</b></summary>
<br/>

Point your camera at **any printed or handwritten text** and hear it read aloud — instantly, in your chosen language.

- 📸 **Live OCR** via ML Kit Text Recognition — no internet needed after model download
- 🌍 **Real-time translation** before speaking (50+ languages)
- ⏱️ **Sleep timer** — auto-stop reading after a set duration
- 🔦 **Torch control** — built-in flashlight toggle for low-light reading
- 📜 **Scan history** — every detected passage saved locally via Room DB
- ⚡ **Word-by-word highlighting** with sentence navigation controls
- 🎛️ **Adjustable speed & pitch** for the TTS voice

<br/>
</details>

---

<details>
<summary><b>🗣️ Babel Engine — Bi-Directional Speech Translator</b></summary>
<br/>

Have a **real-time spoken conversation** across two languages — no typing required.

- 🎙️ Two-speaker mode: **Speaker A ↔ Speaker B**, each with their own language
- 🔄 **Auto-flip** — after one speaker finishes, the mic automatically switches sides
- 🌊 **Live waveform visualizer** shows mic signal in real time
- ⚡ **On-device speech recognition** via Android SpeechRecognizer (no cloud STT fees)
- 🌐 **On-device ML Kit translation** — works fully offline once models are downloaded
- 🔊 **TTS playback** of translated speech on the receiving side
- 📋 Full **conversation history** preserved for the session

<br/>
</details>

---

<details>
<summary><b>👁️ Dyslexia Focus — Line-Focus & Visual Anchoring</b></summary>
<br/>

Designed for readers with dyslexia or reading difficulties — a **calm, distraction-free** reading environment.

- 🎯 **Focus band** highlights one line at a time via camera tracking
- 📝 **Text input mode** — paste any text and have it read word-by-word
- 🏃 **Auto-read mode** — advances words automatically at your pace
- ◀▶ **Manual navigation** — step backwards and forwards word by word
- 🎛️ **EMA-smoothed** focus tracking — no jitter, no jarring jumps
- 🔊 Integrated TTS with **language-aware voice selection**

<br/>
</details>

---

<details>
<summary><b>🔮 AR Magic Lens — Real-Time Overlay Translation</b></summary>
<br/>

See the world **in your language** — translated text appears directly on top of the real scene.

- 🌐 **Live overlay translation** rendered directly over camera frames
- ✨ **OpenGL SDF rendering** — crisp, anti-aliased text at any size, 60fps
- 🗺️ **Spatial word tracking** — translated labels follow the real-world position of source text
- 🌍 **50+ language pairs** with automatic model download management
- 💾 **LRU translation cache** (200 entries) — repeated phrases translate instantly
- 📡 **Offline-first** — all processing is on-device once models are downloaded

<br/>
</details>

---

<details>
<summary><b>📚 E-Reader — AI-Powered Bilingual Library</b></summary>
<br/>

Import documents, read them bilingually, and get **AI-generated summaries** powered by on-device Gemma — no internet, no API key, no cloud.

- 📄 **Supports PDF, DOCX, DOC, TXT** — import any document
- 🌐 **Side-by-side bilingual view** — original & translation tabs on every page
- 🤖 **On-device Gemma Summaries** — 4–6 bullet points per page, runs entirely on your device
- 🔒 **100% private** — your documents never leave your phone
- 🔊 **Read-aloud** with sentence navigation for immersive listening
- 📖 **Personal library** — full local book management with import/delete
- 🔡 **Adjustable font size** for comfortable reading
- 💾 **LRU page cache** — smooth page transitions with zero re-processing

<br/>
</details>

---

<details>
<summary><b>🌐 Languages — Model Manager</b></summary>
<br/>

Full control over which translation models live on your device.

- 📥 **Download on demand** — only pull the languages you need
- 🗑️ **Delete any model** to reclaim storage
- 📶 **Offline detection** — warns you if you try to download without internet
- 🔄 **Automatic model management** — all modes share the same downloaded model pool

<br/>
</details>

---

## 🎨 UI Design — Glassmorphism

<div align="center">

| Feature | Detail |
|---|---|
| 🌌 **Aurora Background** | Animated nebula with cubic-bezier S-curve, 14-second cycle |
| 🪟 **Frosted-Glass Cards** | `linearGradient` brush with `White.copy(0.10f → 0.04f)` |
| ✨ **Entry Animations** | Staggered fade-in + slide-up with 100ms–500ms delays |
| 💫 **Press Interactions** | Glass sweep + outer glow + icon micro-bounce + haptic feedback |
| 🌙 **Always Dark Canvas** | Hardcoded dark Material3 — optimised for AMOLED |

</div>

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                      UI Layer                        │
│   Jetpack Compose + Material3 Glassmorphism UI       │
│   6 Screens · ViewModel per Screen · StateFlow       │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│                  Domain / Data Layer                  │
│   SettingsRepository · LibraryRepository             │
│   TranslationRepository · TextRecognitionRepository  │
│   Room DB (v2) · DataStore Preferences               │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│                  Device / SDK Layer                   │
│   CameraX · ML Kit OCR · ML Kit Translate            │
│   Android TTS · SpeechRecognizer · OpenGL ES 2.0     │
│   Gemma (on-device, MediaPipe) · PdfRenderer         │
└─────────────────────────────────────────────────────┘
```

### Mode → Screen → ViewModel

| Mode | Screen | ViewModel |
|---|---|---|
| 🔤 Classic TTS | `MainScreen` | `MainViewModel` |
| 🗣️ Babel Engine | `BabelScreen` | `BabelViewModel` |
| 👁️ Dyslexia Focus | `DyslexiaScreen` | `DyslexiaViewModel` |
| 🔮 AR Magic Lens | `ArLensScreen` | `ArLensViewModel` |
| 📚 E-Reader | `ReaderScreen` / `EReaderLibraryScreen` | `ReaderViewModel` |
| 🌐 Languages | `LanguagesScreen` | `SettingsViewModel` |

---

## 🛠️ Tech Stack

<div align="center">

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose · Material3 |
| **Camera** | CameraX |
| **OCR** | ML Kit Text Recognition |
| **Translation** | ML Kit On-Device Translator |
| **AI Summaries** | Google Gemma (on-device · MediaPipe LLM Inference) |
| **Speech-to-Text** | Android SpeechRecognizer |
| **Text-to-Speech** | Android TextToSpeech |
| **Database** | Room (SQLite) |
| **Preferences** | Jetpack DataStore |
| **Overlay Rendering** | OpenGL ES 2.0 · SDF Glyph Rendering |
| **Build** | Gradle (Kotlin DSL) · R8 minification |
| **Min Android** | API 24 (Android 7.0) |
| **Target Android** | API 35 (Android 15) |

</div>

---

## ⚡ Performance Highlights

- 🔒 **Background isolation** — exiting any mode immediately unbinds the camera, stops TTS/STT and cancels all coroutines
- 🧠 **`derivedStateOf`** — waveform and UI state changes only trigger recomposition for the composables that actually changed
- 📦 **LRU caches** — translation results and PDF page bitmaps are cached to prevent redundant processing
- 🎨 **Canvas allocations hoisted** — `Brush`, `Path`, and `AnnotatedString` objects are `remember`-ed to avoid per-frame allocations
- 🖼️ **VBO dirty flag** — OpenGL buffers only re-upload geometry when the word list actually changes
- 🔑 **Unique utterance IDs** — `nanoTime()`-based IDs prevent TTS completion callbacks from firing on stale utterances

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android device or emulator with API 24+
- ~1.3 GB free storage for the on-device Gemma AI model (downloaded in-app on first use)

### Build & Run

```bash
# Clone the repository
git clone https://github.com/Vishwesh-AIENG/Text-to-Speech-Reader-Android-App.git
cd Text-to-Speech-Reader-Android-App

# Debug build
./gradlew assembleDebug

# Release AAB (requires keystore.properties — see below)
./gradlew bundleRelease

# Run tests
./gradlew test

# Lint
./gradlew lintRelease
```

### Release Signing

Create `keystore.properties` at the project root (already git-ignored):

```properties
storeFile=/path/to/your/upload.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

---

## 📱 Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Live OCR, AR Lens, Dyslexia Focus |
| `RECORD_AUDIO` | Babel Engine speech recognition |
| `INTERNET` | ML Kit model downloads · Gemma model download (one-time) |
| `ACCESS_NETWORK_STATE` | Offline detection banner |

> All permissions are requested at runtime with clear rationale dialogs.  
> Microphone hardware is declared **optional** — non-Babel modes work without a mic.

---

## 🗺️ Roadmap

- [x] Classic TTS with live OCR
- [x] Babel bi-directional conversation engine
- [x] Dyslexia Focus mode
- [x] AR Magic Lens overlay
- [x] E-Reader with on-device Gemma AI summaries (no API key · fully private)
- [x] Glassmorphism UI redesign
- [x] Background execution isolation
- [ ] Instant Indexing mode *(in development)*
- [ ] Widget support
- [ ] Wear OS companion

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add amazing feature'`
4. Push to the branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:0a1e30,50:1a6b96,100:2d9cdb&height=120&section=footer&animation=fadeIn" width="100%"/>

**Built with ❤️ using Kotlin & Jetpack Compose**

⭐ Star this repo if you find it useful!

</div>
