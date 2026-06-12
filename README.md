# Silo Android

Android **phone** and **Android TV** clients for the [Silo](https://github.com/Silo-Server/silo-server) self-hosted media server — stream and download your movies, shows, music, audiobooks, and ebooks, with quality-aware playback, multi-server/multi-profile support, and synchronized "watch together" sessions.

Built as a Kotlin Multiplatform project: one shared business-logic core, two Jetpack Compose apps (touch + 10-foot TV). The repo preserves the existing application IDs and Kotlin package namespaces (`com.continuum.app`) for install continuity, but all user-facing names and server references use Silo.

> **Status:** Early WIP (`v0.1.0`). The architecture is solid and the feature surface is broad; some areas are intentionally "bones-level" and under active redesign (see [Roadmap](#roadmap)).

---

## Table of contents

- [What's inside](#whats-inside)
- [Feature overview](#feature-overview)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Testing](#testing)
- [Conventions](#conventions)
- [Roadmap](#roadmap)
- [License](#license)

---

## What's inside

| | |
|---|---|
| **Apps** | Phone (`com.continuum.app`) · Android TV (`com.continuum.app.tv`) |
| **Language / UI** | Kotlin 2.1.20 · Jetpack Compose (Material 3) · Compose for TV (`androidx.tv`) |
| **Playback** | AndroidX **Media3 / ExoPlayer** 1.10.0 (+ optional FFmpeg audio extension) |
| **Networking** | **Ktor** 3.1.2 client · kotlinx.serialization · WebSockets for realtime |
| **DI** | **Koin** 4.1.0 |
| **Persistence** | AndroidX DataStore · EncryptedSharedPreferences (tokens) · WorkManager (downloads) |
| **Images** | Coil 3 (Ktor-backed) |
| **SDK** | minSdk 24 · targetSdk 35 · compileSdk 36 · JDK 21 |

The clients talk to a Silo server over its `/api/v1/*` REST + WebSocket API. The server owns the library, scanning, metadata, transcoding decisions, and auth; the clients render it and drive playback.

---

## Feature overview

A condensed tour. For the exhaustive, per-feature checklist (with phone/TV coverage and file pointers) see **[FEATURES.md](FEATURES.md)**.

### 📺 Playback (phone + TV)
- **Adaptive delivery** — the server picks **Direct Play**, **Remux**, or **Transcode** from the device's real capabilities; the client can also fall back to transcode at runtime if a track turns out to be undecodable (e.g. an AV receiver is unplugged mid-stream).
- **Deep capability detection** — enumerates hardware decoders (H.264, HEVC, AV1, VP9, **Dolby Vision** profiles 5/7/8), panel HDR (HDR10, HDR10+, HLG, DV), and audio-sink passthrough (**E-AC3 JOC/Atmos, TrueHD, DTS-HD**); an optional bundled **FFmpeg** audio decoder fills gaps for lossless codecs.
- **Quality-of-life** — per-content **refresh-rate matching** (phone), HDR toggle, intro auto-skip, chapters, sleep timer, playback speed, configurable video gravity (fit/fill/stretch).
- **Tracks & subtitles** — audio-track switching (incl. mid-stream), subtitle selection with **styling, position, and sync offset**, plus a subtitle suite: provider **search & download** and **AI transcription/translation** with quota tracking.
- **System integration** — single Media3 `MediaSession` powers lock-screen / notification / headset / Assistant controls; TV adds D-pad transport, an info HUD, a chapter scrubber, and HDMI EDID-driven display-mode selection.

### 👥 Watch Together (phone + TV)
Synchronized group playback: create/join a room, with **NTP-style clock sync**, host-vs-guest transport gating, drift correction, and graceful handling of reconnects and host-closed rooms.

### ⬇️ Offline & Downloads (phone)
WorkManager-backed downloads of video and books to device storage (scoped `MediaStore` on API 30+), with sidecar metadata, an **offline-first** playback path (no server session needed), and a Downloads manager UI. The app even boots straight to Downloads when launched offline.

### 📚 Library, browse & discovery (phone + TV)
- **Media modes** — Video / Audio / Reading tabs derived from your server libraries.
- **Home** with Continue Watching, Recently Added/Released, and server-curated recommendation rows.
- **Browse** with genre/rating filters, sorting, and infinite-scroll grids; **collections**; rich **item detail** (movies, series → seasons → episodes, multi-version files, cast/crew).
- **Search** scoped by media type, debounced and paginated.

### 📖 Reading & 🎧 Audio (phone)
- **Ebook reader** — EPUB, PDF, CBZ (comics), TXT/Markdown, FB2/FBZ — with themes (light/dark/sepia), text size, margins, table of contents, bookmarks, and progress that syncs to the server.
- **Audiobook player** — cover/metadata, chapters, resume, playback speed, sleep timer (incl. end-of-chapter), and bookmarks, sharing the same Media3 engine as video.

### 🔔 Personalization & engagement (phone + TV)
Multiple **household profiles** per account (PINs, child profiles, content-rating limits, per-profile language/subtitle prefs), favorites & watchlist, ratings, a content **request** flow, a release **calendar** (phone), and an in-app **notifications inbox** with realtime updates. TV mirrors continue-watching into the system **Watch Next** row.

### 🌐 Multi-server, accounts & admin (phone + TV)
Add and switch between multiple Silo servers (encrypted per-server token slots), device/QR sign-in for TVs, and an **admin** area (full on phone: dashboard, users, sessions, logs, scans; stats-only on TV) gated to primary-profile admins.

---

## Architecture

Three library layers under two app shells. Dependencies only point downward.

```
┌──────────────────────────┐   ┌──────────────────────────┐
│        :androidApp        │   │       :androidTvApp       │
│  Phone UI (Compose M3)    │   │  TV UI (Compose for TV)   │
│  MainActivity + nav graph │   │  MainTvActivity + drawer  │
└─────────────┬─────────────┘   └─────────────┬────────────┘
              └───────────────┬───────────────┘
                  ┌───────────▼────────────┐
                  │     :android-shared      │  Android-only:
                  │  Media3 player + service │  ExoPlayer, capability
                  │  downloads, DataStore,   │  probes, MediaAuth,
                  │  player DI               │  settings stores
                  └───────────┬──────────────┘
                  ┌───────────▼──────────────┐
                  │         :shared           │  KMP commonMain:
                  │  Ktor client + APIs,      │  repositories, shared
                  │  repositories, models,    │  ViewModels, RoomSync
                  │  ViewModels, DI           │  engine, ApiResult
                  └───────────────────────────┘
```

### Modules
- **`shared`** (KMP, `commonMain`) — the cross-platform core: Ktor `HttpClient`, the typed API classes, repositories, most ViewModels, domain models, the Watch Together sync engine, and the `ApiResult` type.
- **`android-shared`** — Android-only playback infrastructure shared by both apps: the Media3 `ContinuumPlaybackService`, player factory, capability probes, the stream-auth OkHttp interceptor, downloads, and DataStore-backed settings.
- **`androidApp`** — the phone app: Compose Material 3 screens, bottom-nav shell, `MainActivity`.
- **`androidTvApp`** — the TV app: Compose for TV, drawer shell, D-pad focus, `MainTvActivity`, plus TV-only Watch Next integration.

### Networking & auth
- A single Ktor `HttpClient` (OkHttp engine) with content negotiation, WebSockets, timeouts, and a custom **auth plugin** that attaches `Bearer` + profile headers, rewrites relative `/api/*` paths to the active server, and performs **single-flight token refresh** on `401`.
- Media streams (HLS/progressive) are fetched by ExoPlayer **outside** Ktor, so a parallel **`MediaAuthInterceptor`** (OkHttp) mirrors the same token-attach / refresh semantics for stream URLs.
- **`ServerRegistry`** + **`TokenManager`** provide multi-server support with isolated, encrypted per-server token slots. In-memory implementations live in `shared`; the Android apps override them with `EncryptedSharedPreferences`-backed versions via DI.
- Typed API classes (`AuthApi`, `CatalogApi`, `PlaybackApi`, `EbookReaderApi`, `NotificationsApi`, `WatchTogetherApi`, `AdminApi`, …) wrap endpoints and return `ApiResult<T>` (`Success` / `Error(code,…)` / `NetworkError`). Repositories sit on top and are the ViewModels' dependency surface.

### Dependency injection (Koin)
`sharedModules()` (network + repositories) is combined with the player and Android modules at app startup. The Android apps override the in-memory `TokenManager`/`ServerRegistry` with persistent implementations. ViewModels are resolved with `koinViewModel()`; nav arguments flow in through Koin parameters / `SavedStateHandle`.

### Navigation & boot
Each app computes a start destination from registry/token/profile/offline state (`ServerSetup → Login → ProfileSelection → main`), then runs a Compose nav graph. The phone uses a bottom-nav shell (Video/Audio/Reading/Downloads); the TV uses a top drawer shell. Deep links handle device pairing (`silo://…` / `continuum://…`).

### Playback pipeline
The UI never owns the player directly — a `MediaController` drives the shared `ContinuumPlaybackService` (a Media3 `MediaSessionService`), so there's exactly one session for system controls. `PlaybackSessionManager` negotiates the play method with the server, `PlaybackSessionLifecycle` handles progress reporting and outage/`404` recovery, and capability probes decide what's advertised. Offline playback bypasses the server entirely via a local `file://` URI.

### Persistence
Per-profile/device player settings live in DataStore (debounced, flushed on `onStop`), tokens in `EncryptedSharedPreferences`, reader/audiobook progress + bookmarks in scoped JSON file stores, and downloads in `MediaStore` + sidecars.

---

## Project structure

```
silo-android/
├── shared/            # KMP commonMain: network, repositories, models, ViewModels, RoomSync engine
├── android-shared/    # Media3 player + service, capability probes, downloads, settings stores, player DI
├── androidApp/        # Phone app (Jetpack Compose, Material 3)
├── androidTvApp/      # TV app (Compose for TV, D-pad)
├── docs/
│   ├── media3/        # Android playback notes
│   └── superpowers/   # Design specs + implementation plans (specs/, plans/)
├── scripts/           # Utility scripts, incl. FFmpeg AAR helpers
├── gradle/            # Version catalog (libs.versions.toml)
└── README.md / FEATURES.md
```

Tests live in each module's test source set (`commonTest`, `androidUnitTest`) using JUnit / `kotlin.test`, the Ktor `MockEngine` (shared API/repository tests), Robolectric (phone), and coroutines-test.

---

## Getting started

### Prerequisites
- **JDK 21**
- Android SDK with the configured compile SDK (36)
- A running **Silo server** for auth, browsing, and playback validation — see [`Silo-Server/silo-server`](https://github.com/Silo-Server/silo-server)

### Build

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidTvApp:assembleDebug
```

### Install on a connected device/emulator

```sh
./gradlew :androidApp:installDebug
./gradlew :androidTvApp:installDebug
```

On first launch, point the app at your Silo server URL, sign in, and pick a profile. (Android TV can't bootstrap first-time server setup — set the server up from the phone app or a web browser, then sign the TV in via username/password or QR/device pairing.)

---

## Testing

```sh
./gradlew test                          # all unit tests
./gradlew :shared:testDebugUnitTest     # shared KMP logic (ViewModels, repos, decoders)
./gradlew :android-shared:testDebugUnitTest
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidTvApp:testDebugUnitTest
```

---

## Conventions

- **Kotlin** with `gofmt`-equivalent cleanliness via the project's lint/format setup; keep packages lowercase and focused.
- **Shared first** — put platform-agnostic logic (models, networking, view-model logic, pure algorithms) in `shared`; keep Android-only concerns in `android-shared`; keep each app's module to its UI. New non-UI behavior that both apps need belongs in a shared module, not duplicated per app.
- **Compose** screens are thin; logic lives in ViewModels (testable in `commonTest` where possible).
- Design specs and implementation plans for larger efforts live under `docs/superpowers/{specs,plans}/`.
- This is part of a multi-repo Silo workspace — client-visible API/auth/playback changes often need coordinated work in `silo-server` (and the sibling `silo-apple` clients).

---

## Roadmap

Active design work lives in `docs/superpowers/specs/` with phased plans in `docs/superpowers/plans/`. Notable in-flight items:

- **Audiobook player redesign (phone + TV)** — chapter-aware UI, end-of-chapter sleep, configurable skip/speed, skip-silence + volume normalization, rich notification, Android Auto, and a phone widget. (TV has no dedicated audiobook UI today.)
- **Ebook reader enhancements** — real paginated EPUB (page turns), in-text search, highlights & notes (with a coordinated server change), font/brightness controls, and reading-time estimates across all server formats.
- **Picture-in-Picture** — not yet implemented on phone.

Known gaps the docs track: TV has no reader, audiobook UI, downloads management, or calendar (by design / not yet built).

---

## Notes

- Android phone and TV app IDs remain `com.continuum.app` and `com.continuum.app.tv` in this migration.
- The Android modules target Java 21.
- The server repo lives at [`Silo-Server/silo-server`](https://github.com/Silo-Server/silo-server).

## License

Silo Android is licensed under `AGPL-3.0-or-later`. See [LICENSE](LICENSE).

The checked-in Media3 FFmpeg decoder AAR and other third-party dependencies retain their own licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
