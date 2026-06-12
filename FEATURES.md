# Silo Android — Feature List

A detailed inventory of what the Android **phone** and **TV** clients do today. Legend:

- ✅ implemented
- 🟡 partial / basic ("bones-level") — works but slated for improvement
- 🚧 planned (design/plan exists, not built)
- ➖ not present on this platform (by design or not yet built)

File pointers are repository-relative.

---

## Playback

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Media3/ExoPlayer engine via shared `MediaSessionService` | ✅ | ✅ | One session per process; UI drives it via `MediaController`. `android-shared/.../player/ContinuumPlaybackService.kt` |
| Direct Play | ✅ | ✅ | Progressive HTTP; server-selected from advertised capabilities |
| Remux (HLS, container/audio re-mux) | ✅ | ✅ | `PlaybackSessionManager.startTranscodeFallback` |
| Transcode (HLS full re-encode) | ✅ | ✅ | Server-chosen or runtime fallback |
| Runtime preflight fallback (undecodable track → transcode) | ✅ | ✅ | `PlaybackPreflightListener` |
| Mid-stream audio-track switch | ✅ | ✅ | May trigger server re-mux |
| Hardware decoder enumeration (H.264/HEVC/AV1/VP9/DV) | ✅ | ✅ | `MediaCodecCapabilitiesProbe` |
| Dolby Vision profiles 5 / 7 / 8 (P7 dual-instance gate) | ✅ | ✅ | |
| Panel HDR probe (HDR10, HDR10+, HLG, DV) + per-profile HDR toggle | ✅ | ✅ | `DisplayHdrProbe` |
| Audio passthrough (E-AC3 JOC/Atmos, TrueHD, DTS-HD) | ✅ | ✅ | TV prioritizes passthrough; `AudioCapabilityManager` |
| FFmpeg audio extension (lossless fallback) | ✅ | ✅ | Build-flag gated; `FfmpegAudioSupport` |
| Refresh-rate matching | ✅ | ➖ | Phone display mode; TV defers to HDMI sink |
| HDMI EDID-driven display mode | ➖ | ✅ | `HdrDisplayController` |
| Subtitle selection + styling (font/bg/position) | ✅ | ✅ | `SubtitleManager` |
| Subtitle sync offset (±10s) / audio sync (±5s) | ✅ | ✅ | Per-profile |
| Subtitle provider search + download | ✅ | ✅ | |
| AI subtitle transcription / translation (quota-tracked) | ✅ | ✅ | TV: `TvAiTranslateDialog` |
| Intro auto-skip (+ manual skip banner) | ✅ | ✅ | |
| Chapters | ✅ | ✅ | Server-extracted; TV scrubber markers |
| Sleep timer | ✅ | ✅ | Configurable default |
| Playback speed | ✅ | ✅ | |
| Video gravity (fit / fill / stretch) | ✅ | ✅ | |
| Lock-screen / notification / headset / Assistant controls | ✅ | ✅ | Via `MediaSession` |
| D-pad transport, info HUD, chapter scrubber | ➖ | ✅ | `TvPlayerHud`, `TvPlayerScrubber` |
| Landscape-on-play (auto-rotate aware) | 🚧 | ➖ | Implemented then reverted; pending re-apply |
| Picture-in-Picture | 🚧 | ➖ | Not yet implemented |

## Watch Together (synchronized playback)

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Create / join / leave room | ✅ | ✅ | `WatchTogetherRepository` |
| Clock sync (NTP-style) + drift correction | ✅ | ✅ | `RoomSyncEngine` (shared) |
| Host vs guest transport gating | ✅ | ✅ | Guests' controls disabled unless permitted |
| Room snapshots / member list / suggestions / voting | ✅ | ✅ | TV: `TvWatchTogetherLobbyScreen` |
| Graceful reconnect + host-closed auto-exit | ✅ | ✅ | |

## Offline & Downloads

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Download video & books (WorkManager) | ✅ | ➖ | TV is streaming-only |
| Scoped `MediaStore` storage (API 30+) + sidecars | ✅ | ➖ | `DownloadStorage` |
| Offline-first playback (local `file://`, no session) | ✅ | ➖ | `OfflineMediaResolver` |
| Downloads manager UI (per-item / per-section delete, storage usage) | ✅ | ➖ | `DownloadsScreen` |
| Boot directly to Downloads when launched offline | ✅ | ➖ | |

## Library, browse & discovery

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Media modes (Video / Audio / Reading) from server libraries | ✅ | 🟡 | TV exposes Video/Audio (Reading excluded) |
| Home: Continue Watching, Recently Added/Released, recommendations | ✅ | ✅ | Server-defined sections |
| Library browse: genre/rating filters, sort, infinite grid | ✅ | ✅ | |
| Collections (global + library-scoped) | ✅ | ✅ | |
| Item detail: movies, series → seasons → episodes, multi-version, cast/crew | ✅ | ✅ | |
| Search (scoped by media type, debounced, paginated) | ✅ | ✅ | |
| System "Watch Next" row integration | ➖ | ✅ | `WatchNextRepository` (tvprovider) |

## Reading (ebooks)

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| EPUB reader | 🟡 | ➖ | WebView, chapter-paged (no in-chapter pagination yet) |
| PDF reader | ✅ | ➖ | `PdfRenderer` |
| CBZ (comic) reader | ✅ | ➖ | |
| TXT / Markdown reader | ✅ | ➖ | |
| FB2 / FBZ (FictionBook) reader | ✅ | ➖ | |
| Themes (light/dark/sepia), text size, margins | ✅ | ➖ | |
| Table of contents / sections | ✅ | ➖ | EPUB |
| Bookmarks (local + server sync) | ✅ | ➖ | |
| Reading progress tracking + server sync | ✅ | ➖ | |
| In-text search | 🚧 | ➖ | Planned |
| Highlights & notes | 🚧 | ➖ | Server model exists; UI planned (+ server generalization) |
| Font family / brightness controls | 🚧 | ➖ | Planned |

## Audio (audiobooks)

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Audiobook player (cover, metadata, chapters) | 🟡 | ➖ | Shares the Media3 engine; redesign planned |
| Resume + periodic progress save | ✅ | ➖ | `AudiobookPositionStore` |
| Playback speed | ✅ | ➖ | |
| Sleep timer (incl. end-of-chapter) | ✅ | ➖ | |
| Bookmarks | 🟡 | ➖ | Local-only (no server endpoint yet) |
| Direct-play of cover-art audiobooks (no needless transcode) | ✅ | ➖ | Advertises still-image codecs |
| Chapter navigation (prev/next), current-chapter UI | 🚧 | 🚧 | Planned (phone + TV) |
| Skip-silence / volume boost, Android Auto, widget | 🚧 | ➖ | Planned |

## Profiles, personalization & engagement

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Household profiles (multiple per account) | ✅ | ✅ | `ProfileRepository` |
| PIN-protected & child profiles, content-rating limits | ✅ | ✅ | |
| Per-profile language / subtitle / playback prefs | ✅ | ✅ | |
| Library access restrictions per profile | ✅ | ✅ | |
| Favorites & watchlist | ✅ | ✅ | TV: from Settings |
| Ratings | ✅ | ✅ | |
| Watch history | ✅ | ✅ | |
| Content requests (browse/search TMDB, status tracking) | ✅ | ✅ | |
| Release calendar | ✅ | ➖ | |
| Notifications inbox (paginated, realtime updates, mark-read) | ✅ | ✅ | REST + WebSocket |

## Servers, accounts & admin

| Feature | Phone | TV | Notes |
|---|:---:|:---:|---|
| Multiple Silo servers + switching (encrypted per-server tokens) | ✅ | ✅ | `ServerRegistry` |
| First-time server setup (admin creation) | ✅ | ➖ | TV signs in to already-set-up servers |
| Username/password login | ✅ | ✅ | |
| QR / device pairing sign-in | 🟡 | ✅ | TV displays code; phone approves device logins |
| Single-flight token refresh (REST + media streams) | ✅ | ✅ | Auth plugin + `MediaAuthInterceptor` |
| Settings (account, appearance, playback, subtitles, notifications) | ✅ | ✅ | Effective-settings cascade synced to server |
| Admin: dashboard / stats | ✅ | ✅ | TV is stats-only |
| Admin: users / sessions / logs / scans | ✅ | ➖ | Phone only |
| Admin gate (account admin **and** primary profile) | ✅ | ✅ | `isActingAdmin` |

---

## Platform summary

**Phone** is the full-featured client: playback, downloads/offline, the ebook reader, the audiobook player, the release calendar, and full admin.

**TV** is a 10-foot, D-pad client focused on browsing and playback (incl. Watch Together and the subtitle suite), with system Watch Next integration and a stats-only admin view. It intentionally omits the reader, the dedicated audiobook UI, downloads management, and the calendar.

Both apps share the same networking, auth, repositories, most ViewModels, and the entire Media3 playback/capability stack.
