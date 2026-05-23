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

            // Preferences persistence (playback quality, subtitle defaults, etc.).
            implementation(libs.datastore.preferences)

            // Compose for TV — focus-aware TV-optimized components.
            implementation(libs.tv.material)
        }
    }
}

android {
    namespace = "com.continuum.app.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.continuum.app.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Shadow the android-shared BuildConfig field so per-app flavors can
        // override without rebuilding the shared module. See androidApp's
        // build.gradle.kts for rationale.
        buildConfigField("boolean", "FFMPEG_AUDIO_ENABLED", "true")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Mirror androidApp's ABI split strategy. See that file for rationale.
    bundle {
        abi {
            enableSplit = true
        }
    }
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
