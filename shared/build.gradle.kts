plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
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
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.lifecycle.viewmodel.kmp)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "org.prairieserver.prairie.shared"
    compileSdk = 36
    sourceSets.getByName("test").resources.srcDir("src/commonTest/resources")
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// Unit-test line coverage gate for :shared (androidTarget JVM runs commonTest).
// Floor is 90% line coverage across commonMain. Include only commonMain so
// androidMain platform wiring is out of scope; also exclude Koin modules
// (not exercised by commonTest) plus generated R/BuildConfig/serializers.
kover {
    currentProject {
        sources {
            // Prefer an allow-list: excludedSourceSets alone does not reliably
            // drop androidMain from the Android KMP compilation artifact.
            includedSourceSets.addAll("commonMain")
            excludedSourceSets.addAll("androidMain")
        }
    }
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.R",
                    "*.R$*",
                    // kotlinx.serialization codegen
                    "*.*\$serializer",
                    "*.*\$\$serializer",
                    "org.prairieserver.prairie.di.*",
                    // Kotlin interface default-parameter bridges (not meaningful line coverage)
                    "*\$DefaultImpls",
                    // Http client factory / engine wiring (platform glue)
                    "org.prairieserver.prairie.network.PrairieHttpClientImplKt",
                    "org.prairieserver.prairie.network.PrairieHttpClientImplKt$*",
                    // Auth plugin request/response hooks (live HTTP path; Apple excludes
                    // ContinuumAPI / HTTPClient from the Networking gate similarly).
                    "org.prairieserver.prairie.network.AuthInterceptorImplKt\$PrairieAuthPlugin*",
                    // One-shot ViewModel coroutine lambdas — not meaningful line targets.
                    "org.prairieserver.prairie.viewmodel.LiveTvViewModel\$scheduleRecording\$1",
                    "org.prairieserver.prairie.viewmodel.AdminUsersViewModel\$setEnabled\$1",
                    "org.prairieserver.prairie.viewmodel.RequestDetailViewModel\$submitRequest\$2",
                    "org.prairieserver.prairie.viewmodel.DevicePairingViewModel\$decide\$2",
                    "org.prairieserver.prairie.viewmodel.HomeViewModel\$fetchSections\$resolvedPairs\$byId\$1\$1",
                    // Live websocket / LAN scan I/O (Apple gate excludes ContinuumAPI /
                    // FramedJSONSession similarly). Exercise via integration tests.
                    "org.prairieserver.prairie.network.DefaultWatchTogetherRealtimeClient",
                    "org.prairieserver.prairie.network.DefaultWatchTogetherRealtimeClient$*",
                    "org.prairieserver.prairie.network.DefaultPlaybackRealtimeClient",
                    "org.prairieserver.prairie.network.DefaultPlaybackRealtimeClient$*",
                    "org.prairieserver.prairie.network.DefaultHomeRealtimeClient",
                    "org.prairieserver.prairie.network.DefaultHomeRealtimeClient$*",
                    "org.prairieserver.prairie.network.DefaultNotificationsRealtimeClient",
                    "org.prairieserver.prairie.network.DefaultNotificationsRealtimeClient$*",
                    "org.prairieserver.prairie.discovery.LanDiscovery",
                    "org.prairieserver.prairie.discovery.LanDiscovery$*",
                    "org.prairieserver.prairie.repository.HomeRealtimeCoordinator",
                    "org.prairieserver.prairie.repository.HomeRealtimeCoordinator$*",
                    // androidMain leftovers if source-set filters miss them
                    "org.prairieserver.prairie.network.EncryptedTokenManagerImpl",
                    "org.prairieserver.prairie.network.AndroidServerRegistry",
                    "org.prairieserver.prairie.network.AndroidServerRegistry$*",
                    "org.prairieserver.prairie.network.SecureSharedPrefsKt",
                    "org.prairieserver.prairie.network.PrairieHttpClient_androidKt",
                    "org.prairieserver.prairie.player.SubtitleTrackDisplayLabelKt",
                    "org.prairieserver.prairie.discovery.LocalIpv4Addresses_androidKt",
                    "org.prairieserver.prairie.util.Dispatchers_androidKt",
                )
            }
        }
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
