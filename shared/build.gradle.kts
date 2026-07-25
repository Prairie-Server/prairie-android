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
// Measured coverage is ~60% today across commonMain; 75% remains the goal as
// more repositories/ViewModels get focused tests. The floor blocks regressions.
// Exclude androidMain platform wiring and Koin modules (not exercised by commonTest),
// plus generated R/BuildConfig/serializers.
kover {
    currentProject {
        sources {
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
                )
            }
        }
        verify {
            rule {
                minBound(55)
            }
        }
    }
}
