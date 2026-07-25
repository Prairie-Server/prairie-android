pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PrairieAndroid"
include(":shared")
include(":libass-bridge")
include(":android-shared")
include(":androidApp")
include(":androidTvApp")
include(":baselineprofile")
