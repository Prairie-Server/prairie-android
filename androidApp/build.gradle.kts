plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":android-shared"))
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.activity.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.media3.ui)
            implementation(libs.media3.session)
            implementation(libs.media3.common.ktx)
            implementation(libs.media3.ui.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation("androidx.palette:palette-ktx:1.0.0")
        }
    }
}

android {
    namespace = "com.continuum.app.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.continuum.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Shadow the android-shared BuildConfig field so per-app flavors
        // (e.g., a "no-FFmpeg" sideload build for size-constrained QA) can
        // override without rebuilding the shared module. The runtime reads
        // the android-shared value — this field is reserved for future
        // flavor wiring.
        buildConfigField("boolean", "FFMPEG_AUDIO_ENABLED", "true")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Play Store App Bundle: enable per-ABI splits so Play serves a device
    // only the native libs it can run. Relevant once we ship the FFmpeg
    // AAR — the universal APK carries all three ABIs (~3 MB of native
    // libs); ABI-split bundles drop that to ~1 MB per device.
    bundle {
        abi {
            enableSplit = true
        }
    }
    // Per-ABI APKs for sideload QA (one per arch + a universal). Matches
    // the device-matrix QA procedure in
    // docs/plans/ffmpeg-audio-extension-plan.md Phase 4. `isUniversalApk`
    // keeps the all-ABIs APK around for dev convenience.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
