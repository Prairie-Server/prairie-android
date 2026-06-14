plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.activity.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.process)
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
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.koin.androidx.workmanager)
            implementation("androidx.palette:palette-ktx:1.0.0")
        }

        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-junit"))
            // NotificationRow's `reason_flags` default arg is a kotlinx JsonObject;
            // constructing it in unit tests needs the json artifact on the test
            // classpath (the :shared dep is `implementation`, so it isn't transitive).
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            // AdminEntryViewModelTest drives a viewModelScope coroutine, so it
            // needs the test dispatcher / runTest helpers on the test classpath.
            implementation(libs.kotlinx.coroutines.test)
            // WatchTogetherEntryDestinationTest asserts on real Route.route
            // strings, which call android.net.Uri.encode — Robolectric supplies
            // the real Android impl under plain JVM unit tests.
            implementation(libs.robolectric)
            // ReflowBridgeTest uses org.json directly in plain JVM unit tests.
            // The Android-bundled org.json is mocked; this standalone jar provides
            // the real implementation so tests don't need the Robolectric runner.
            implementation("org.json:json:20240303")
        }
    }
}

android {
    namespace = "com.continuum.app.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.continuum.app"
        minSdk = 24
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
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
