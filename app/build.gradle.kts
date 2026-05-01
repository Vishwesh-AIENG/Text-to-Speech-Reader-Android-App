import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ── Optional release signing config ────────────────────────────────────────────
// To sign a release AAB/APK locally, create `keystore.properties` at the project
// root (already git-ignored via `*.properties`/`keystore.properties`) with:
//
//   storeFile=/absolute/path/to/release.jks
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
//
// If the file is missing (e.g. CI or a fresh clone), the release build falls
// back to the debug signing config so Play App Signing can still re-sign the
// uploaded bundle on Google's side.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile")?.let { file(it).exists() } == true

android {
    namespace = "com.app.ttsreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.app.omnilingo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // No `ndk.abiFilters` override: the Android App Bundle generates per-ABI
        // splits automatically for all native libs shipped by ML Kit/CameraX,
        // which satisfies Google Play's 64-bit requirement while still serving
        // 32-bit-only devices on API 24+.
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile     = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias      = keystoreProperties.getProperty("keyAlias")
                keyPassword   = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // Compose BOM — manages all Compose library versions together
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // CameraX
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Text Recognition — camera OCR + PDF (PdfTextExtractor)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ML Kit Translation (on-device, models downloaded on demand)
    implementation("com.google.mlkit:translate:17.0.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // .await() for Google Task<T> (ML Kit, Firebase) — used in translate/ocr repositories
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // .await() for Guava ListenableFuture — used by ProcessCameraProvider.getInstance()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // DataStore Preferences — coroutines-native key-value persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Accompanist Permissions — declarative runtime permissions in Compose
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Room — local SQLite database for scan history
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // MediaPipe LLM Inference — on-device Gemma summarization (no API key required)
    implementation("com.google.mediapipe:tasks-genai:0.10.22")

    // Baseline Profile — precompiles critical code paths for faster startup
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Google Play In-App Review API — prompts users to rate without leaving the app
    implementation("com.google.android.play:review-ktx:2.0.2")
}
