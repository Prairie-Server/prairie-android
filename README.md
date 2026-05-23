# Silo Android

Android phone and Android TV clients for the [Silo](https://github.com/Silo-Server/silo-server) self-hosted media server.

The repo preserves the existing Android application IDs and Kotlin package namespaces for install continuity, but user-facing names, docs, and server references now use Silo.

## Layout

- `shared/` - Kotlin Multiplatform shared logic used by the Android clients
- `android-shared/` - Android-only playback, Media3, DI, and UI helpers shared by phone and TV
- `androidApp/` - Android phone app built with Jetpack Compose
- `androidTvApp/` - Android TV app built with Compose for TV
- `docs/media3/` - Android playback notes
- `scripts/` - Android utility scripts, including FFmpeg AAR helpers

## Prerequisites

- JDK 21
- Android SDK with the configured compile SDK
- A running Silo server for local auth, browsing, and playback validation

## Build

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidTvApp:assembleDebug
```

To install on a connected emulator or device:

```sh
./gradlew :androidApp:installDebug
./gradlew :androidTvApp:installDebug
```

Run available unit checks with:

```sh
./gradlew test
```

## Notes

- Android phone and TV app IDs remain `com.continuum.app` and `com.continuum.app.tv` in this migration.
- The Android modules target Java 21.
- The server repo lives at [`Silo-Server/silo-server`](https://github.com/Silo-Server/silo-server).

## License

Silo Android is licensed under `AGPL-3.0-or-later`. See [LICENSE](LICENSE).

The checked-in Media3 FFmpeg decoder AAR and other third-party dependencies retain their own licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
