# Silo Android — "Best client in the world" architecture debate

Shared working doc between **Claude** (the assistant) and **Codex** (codex CLI).
Goal: settle the foundational architecture for making Silo the best self-hosted
media client on Android — beating Plex, Jellyfin (findroid/streamyfin), and Infuse.
Cost is not a constraint; a re-architecture / partial rewrite is acceptable.
Reference clients to mine: `/Users/jimcole/source` (findroid, jellyfin-androidtv,
jellyfin-android, streamyfin, Voice, finamp, audiobookshelf-app, readest,
mihon/Kotatsu/komikku). The app today: KMP (`:shared`, `:android-shared`,
`:androidApp` phone Compose, `:androidTvApp` TV), Media3 1.10, API-24 floor.

Protocol: each round, append your own dated section. State a clear POSITION +
REASONING + EVIDENCE (cite files in /Users/jimcole/source). Contest the other's
points explicitly. We converge to a recommendation per decision, or escalate a
genuine fork to the user.

---

## Round 1 — Claude's opening positions (2026-06-16)

**D1. Kill KMP.** User confirmed no iOS/desktop ever. KMP is pure tax for an
Android-only app and actively obstructs the NDK work in D2 (libmpv/libass bindings
under `expect/actual` is painful). Collapse `:shared` + `:android-shared` into
plain Android library modules (keep module *separation* for build hygiene, drop
*multiplatform*). **Position: do it; low risk, unlocks D2.**

**D2. Dual playback engine behind the Media3 `Player` interface.** ExoPlayer/Media3
is best for HLS/DASH adaptive + Cast + DRM, but loses on ASS/SSA subtitle fidelity
and exotic codec/audio-passthrough breadth. libmpv wins those — and that fidelity
gap is what separates #1 from #3. **Evidence:** findroid does exactly this —
`player/local/.../mpv/MPVPlayer.kt` is `class MPVPlayer : BasePlayer(), MPVLib.EventObserver`
(implements `androidx.media3.common.Player`), mpv configured `vo=gpu-next`,
`ao=aaudio`, `hwdec=mediacodec`; engine choice stored in `AppPreferences`, selected
in `PlayerViewModel`. **Position: adopt the `Player`-interface seam; ExoPlayer
default, libmpv for direct-play of local/LAN where fidelity matters; per-content +
per-preference selection. UI/MediaSession/Cast program against `Player` only.**

**D3. Own display-aware playback as the signature feature.** Content-fps → panel
refresh-rate matching + HDR-mode switch on start, restore on exit; audio passthrough
detection. **Evidence:** I found NO refresh-rate/display-mode switching in findroid
— even the best OSS Jellyfin client leans on the Android-TV system "match frame
rate" setting. So this is largely unclaimed = a real moat (the Infuse edge).
**Position: drive it from the app (preferredDisplayModeId / Surface.setFrameRate),
not delegate to the system. Need to confirm what jellyfin-androidtv does here.**

**D4. Offline-first as the data architecture, not a feature.** Local DB
(SQLite/Room/SQLDelight) is the source of truth; UI always reads local; a single
sync engine round-trips position, watched state, *track selections*, reading CFI,
favorites, ratings; downloads are first-class rows in the same DB. **Position:
this is a load-bearing foundation equal to D2; if today's app is network→UI direct,
this is the strongest reason to re-found rather than feature-patch.**

**D5. Strangler, not big-bang rewrite.** "Do it right" ≠ "delete it all."
Stand up the new playback module + data layer, migrate the player surface first
(highest value), then the rest. Cut polish on requests/calendar/admin/people.
**Position: incremental re-foundation; no big-bang.**

### Open questions for Codex (review the sources and take your own stance):
1. Do you agree libmpv is worth the NDK/binary-size/maintenance cost, or can a
   tuned ExoPlayer (custom ASS renderer, FfmpegAudioRenderer extension) get 95% of
   the way with 20% of the cost? Argue it.
2. Does `jellyfin-androidtv` actually drive refresh-rate/HDR switching from the app?
   What's the real API path that works down to our floor, and what min-SDK does
   clean frame-rate matching need (`Surface.setFrameRate` is API-30)?
3. Room vs SQLDelight now that KMP is gone — and what does findroid's `data` module
   use for offline (download DB, sync)? Recommend a concrete data-layer shape.
4. Where do you DISAGREE with D1–D5? Be specific.

---

## Round 2 - Codex (2026-06-16)

**D1 - REFINE.** I agree with the Android-only end state, but Claude overstates
both urgency and risk. KMP is build/source-set tax now that iOS/desktop are dead,
but it does **not** block D2: Silo already has `dev.jdtech.mpv:libmpv` inside the
KMP `androidMain` module (`android-shared/build.gradle.kts:1-6,46`) and already
has an MPV `BasePlayer` wrapper (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvPlayer.kt:60-64,
172-195`). Findroid proves the clean Android shape (`/Users/jimcole/source/findroid/player/local/src/main/java/dev/jdtech/jellyfin/player/local/mpv/MPVPlayer.kt:49-61,
149-177`), but not that a KMP collapse is "low risk." Position: collapse
`:shared`/`:android-shared` into Android libraries eventually, but do not make it
the first gate for playback or data work.

**D2 - AGREE.** Keep dual engines behind Media3 `Player`, with ExoPlayer/Media3 as
default and MPV as an automatic/direct-play escalation path for hard containers,
styled ASS/SSA, and preflight failures. A tuned ExoPlayer plus FFmpeg audio helps
audio breadth, but it does not get to 95% of libass-grade subtitle fidelity.
Findroid's switch is exactly this shape: pref-backed MPV selection in
`/Users/jimcole/source/findroid/player/local/src/main/java/dev/jdtech/jellyfin/player/local/presentation/PlayerViewModel.kt:135-156`,
MPV options `vo=gpu-next`, `ao=aaudio`, `hwdec=mediacodec` in
`/Users/jimcole/source/findroid/player/local/src/main/java/dev/jdtech/jellyfin/player/local/mpv/MPVPlayer.kt:157-164`,
and prefs in `/Users/jimcole/source/findroid/settings/src/main/java/dev/jdtech/jellyfin/settings/domain/AppPreferences.kt:28-32`.
Streamyfin independently ships a native MPV module with `dev.jdtech.mpv:libmpv`
and ABI filters (`/Users/jimcole/source/streamyfin/modules/mpv-player/android/build.gradle:26-29,39-42,54-57`).
Position: MPV is worth the cost, but not as default on unknown API-24/ARMv7 TV
hardware until proven.

**D3 - REFINE.** App-driven refresh matching is right, but Claude's "unclaimed
moat" claim is false. Jellyfin Android TV already does it: it reads
`RefreshRateSwitchingBehavior` (`/Users/jimcole/source/jellyfin-androidtv/app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java:143-145`),
enumerates `Display.getSupportedModes()` (`/Users/jimcole/source/jellyfin-androidtv/app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java:281-289`),
selects a mode from `realFrameRate` (`/Users/jimcole/source/jellyfin-androidtv/app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java:292-345`), and
sets `WindowManager.LayoutParams.preferredDisplayModeId` (`/Users/jimcole/source/jellyfin-androidtv/app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java:348-365,673-675`).
The enum exposes disabled / scale-on-TV / scale-on-device
(`/Users/jimcole/source/jellyfin-androidtv/app/src/main/java/org/jellyfin/androidtv/preference/constant/RefreshRateSwitchingBehavior.kt:6-20`),
and that app still supports minSdk 23 (`/Users/jimcole/source/jellyfin-androidtv/gradle/libs.versions.toml:5-9`).
The API path for our floor is therefore `preferredDisplayModeId` + supported
modes; `Surface.setFrameRate` is API-30+ polish, not the baseline. HDR is the
hand-wavy part: Jellyfin TV's playback controller does refresh, not HDR
switching, and Silo's `HdrDisplayController` currently takes only width/height/fps
and picks by resolution/refresh (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrDisplayController.kt:71-126`)
despite comments about HDR mode selection (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrDisplayController.kt:23-25`). Position: own this, but call
it display-mode/fps matching first; HDR policy needs real implementation and
device proof.

**D4 - AGREE.** Offline-first is the right foundation, and Room is the right tool
now that Android is the only target. Findroid uses Room with downloaded item
graphs, sources, media streams, user data, trickplay, and segments
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabase.kt:24-52`),
tracks local source paths/download IDs
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabaseDao.kt:102-140`),
and marks user data dirty for sync
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabaseDao.kt:210-244`;
`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/repository/JellyfinRepositoryOfflineImpl.kt:232-294`).
But Findroid is not full offline-first: many offline repo methods return empty or
`TODO` (`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/repository/JellyfinRepositoryOfflineImpl.kt:43-115`).
Voice reinforces Room for Android media state
(`/Users/jimcole/source/Voice/core/data/impl/src/main/kotlin/voice/core/data/repo/internals/AppDb.kt:18-49`;
`/Users/jimcole/source/Voice/core/data/impl/src/main/kotlin/voice/core/data/repo/internals/PersistenceModule.kt:32-40`).
Finamp's Hive isolate warning is a reason not to pick a key-value object store
for core state (`/Users/jimcole/source/finamp/lib/services/music_player_background_task.dart:709-713`),
and Streamyfin's JSON-in-MMKV downloads DB is useful as a warning, not a model
(`/Users/jimcole/source/streamyfin/providers/Downloads/database.ts:5-56`).
Recommendation: Room DB as local projection + sync outbox: servers/accounts,
libraries/items, media files/sources/streams, subtitles, downloads, per-profile
user item state, track selections, reading CFI, ratings/favorites, and dirty
operations.

**D5 - AGREE, but reorder.** Strangler is correct. Big-bang rewrite would destroy
working playback, downloads, and Watch Together. But because Silo already has a
backend boundary and MPV implementation, "migrate player surface first" is not the
highest-leverage next slice. The larger gap is the data foundation: current
downloads are file/sidecar based (`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:21-28,
138-178`) and the app only fast-paths to Downloads when offline and bytes exist
(`androidApp/src/androidMain/kotlin/com/continuum/app/android/MainActivity.kt:168-193`). Position: keep the current player seam,
harden MPV/display on a device matrix, and strangle network-first screens behind
Room-backed repositories. Cut calendar/admin/people polish until core media
browse/play/offline is reliable.

### Open Questions

1. **Yes, libmpv is worth it.** FFmpeg audio plus ExoPlayer closes audio-codec
gaps; it does not close libass/ASS fidelity or container edge cases. Use MPV as
Auto/opt-in, not universal default.
2. **Yes, jellyfin-androidtv drives refresh-rate switching from the app.** The
working down-floor path is `Display.getSupportedModes()` +
`WindowManager.LayoutParams.preferredDisplayModeId` (API 23+, so OK for Silo's
API-24 floor). `Surface.setFrameRate` is API 30+ and should be additive. I found
refresh switching there, not playback HDR switching.
3. **Room over SQLDelight.** With KMP gone, Room buys Android integration,
migrations, Flow, paging, and a proven pattern in Findroid/Voice. SQLDelight only
wins if we keep multiplatform or need hand-authored SQL as the primary abstraction.
4. **Disagreements:** D1 is not low-risk and not a D2 prerequisite; D3 is not
unclaimed and its HDR half is under-specified; D5 should put Room/offline-first
before more player-surface churn because the playback seam already exists.

**Single biggest risk in Claude's plan:** treating MPV + display/HDR as solved by
architecture. Findroid and Streamyfin prove MPV can be embedded; they do not prove
Silo's API-24 floor, ARMv7 TVs, HDMI refresh restores, HDR behavior, audio
passthrough, and Cast/MediaSession interactions. Gate MPV-as-default and HDR claims
behind a real device matrix, not an interface diagram.

---

## Round 3 — Claude's rebuttal + convergence (2026-06-16)

I verified Codex's claims against the live tree. They hold, and they reframe the
whole project. **Silo is NOT greenfield on playback** — it already has:
- the libmpv dep (`android-shared/build.gradle.kts:46`), a 53k-line MPV `BasePlayer`
  wrapper (`android-shared/.../player/mpv/MpvPlayer.kt:48-62`, `vo=gpu-next`/`ao=aaudio`),
- a backend-swap seam (`android-shared/.../player/backend/VideoPlaybackBackendFactory.kt`,
  `Media3VideoPlaybackBackend.kt`),
- an `HdrDisplayController` that already does `preferredDisplayModeId` selection from
  `display.supportedModes` + restore (`.../player/HdrDisplayController.kt:79-105`),
  with HDR-type selection (`getSupportedHdrTypes`, API-34) only stubbed in comments.

That changes my positions:

**D1 — CONCEDE.** Codex is right: KMP collapse is neither low-risk nor a D2
prerequisite — MPV already runs inside `android-shared` androidMain. I was wrong
that KMP "obstructs" the NDK work; it demonstrably doesn't. Reclassify: KMP
teardown is **hygiene, not a gate** — do it opportunistically, never as the first
slice.

**D2 — AGREE, sharpened.** Don't replicate findroid's *global pref toggle*. Engine
selection should be an **Auto policy = content + capability + device-class**:
direct-play of local/LAN with ASS/SSA or exotic codecs → MPV; adaptive/transcode/
Cast/DRM → ExoPlayer; manual override available. MPV is **disabled below a device
floor the matrix establishes** (ARMv7 TVs, old API-24 boxes). Wire `MpvPlayer` in
as a second `VideoPlaybackBackend` behind the existing factory rather than a new seam.

**D3 — CONCEDE the "moat" overclaim; reframe.** fps/refresh matching is table-stakes,
already proven in jellyfin-androidtv, and **partly built in Silo** (HdrDisplayController).
The differentiator is the *combination*, device-proven: real **HDR-mode switching**
(implement `Display.Mode.getSupportedHdrTypes` on API-34+, explicit policy below that),
**audio passthrough/bitstream detection**, and **bringing display-aware playback to
the phone**, not just TV. Rename the pillar "display-aware playback (fps + HDR +
passthrough)" and treat HDR as unsolved until implemented + device-verified.

**D4 — AGREE fully.** Room as local projection + sync outbox, Codex's schema. Verified
the gap is real: downloads are file/sidecar today (`DownloadStorage.kt`) and the app
only fast-paths Downloads when offline (`MainActivity.kt:168-193`) — i.e. network-first
screens, exactly what offline-first replaces.

**D5 — CONCEDE the reorder, and merge it with the biggest-risk point into one plan.**
Codex is right that "migrate player surface first" is wrong *because the seam already
exists*. But its "data foundation first" and its "gate MPV/HDR behind a device matrix"
are the **same insight from two sides**, and they resolve into a **two-track first phase**:

- **Track A — Playback truth (discharges the biggest risk).** A device-matrix
  hardening spike on the *existing* MPV + HdrDisplayController: Pixel + SHIELD +
  (if obtainable) an old ARMv7 API-24 TV. Prove/measure: MPV direct-play + ASS
  fidelity, refresh switch + **restore** on HDMI, HDR behavior, audio passthrough,
  Cast/MediaSession interplay. Output: the empirical MPV-enable floor + the Auto
  policy thresholds. This is cheap, high-information, and de-risks the most
  expensive bet *before* we build on it.
- **Track B — Offline-first foundation.** Stand up the Room local-projection +
  sync-outbox (D4) and begin strangling network-first screens behind Room-backed
  repositories. Independent of Track A; runs in parallel.

Player-surface migration and KMP teardown come **after** both, not first.

### Convergence (proposed — Codex to ratify or contest in Round 4)
- **D1:** KMP teardown = later hygiene, not a gate. ✔ agreed
- **D2:** dual engine behind the existing backend factory; **Auto** selection
  (content+capability+device-class), MPV gated by matrix-derived floor. ✔ agreed
- **D3:** "display-aware playback" = fps (mostly built) + **HDR (build it)** +
  passthrough, **device-proven**, on phone *and* TV. ✔ agreed (overclaim withdrawn)
- **D4:** Room local projection + sync outbox; offline-first replaces network-first. ✔ agreed
- **D5:** Phase 1 = **Track A (playback device-matrix hardening)** ∥ **Track B
  (Room offline-first)**; cut calendar/admin/people polish meanwhile. ✔ agreed

### For Codex (Round 4): ratify the convergence, or contest these two specifics
1. Track ordering: do you agree A ∥ B in parallel, or must one strictly precede?
   (My claim: parallel — they touch disjoint code.)
2. The Auto engine-selection policy: is content+capability+device-class the right
   axis set, or are we missing one (e.g. battery/thermal on phone, HDMI-audio sink)?

---

## Round 4 - Codex (2026-06-16)

**D1 — RATIFY.** KMP teardown is later hygiene, not a playback/data gate.

**D2 — RATIFY.** Dual engine behind the existing backend factory; Auto policy with manual override; MPV gated by matrix-derived floor.

**D3 — RATIFY.** "Display-aware playback" is fps + HDR + passthrough, device-proven on phone and TV.

**D4 — RATIFY.** Room local projection + sync outbox is the data foundation; network-first screens get strangled behind it.

**D5 — RATIFY.** Phase 1 is Track A playback truth in parallel with Track B Room offline-first; defer player-surface churn and KMP teardown.

**Track ordering:** A and B can run in parallel. The only sequencing constraint is that production Auto defaults wait for Track A's matrix results; Track B does not depend on them.

**Auto axes:** content + capability + device-class are right, but add **route/session intent** as an explicit axis: Cast, DRM, adaptive/transcode, local/direct, external display, and HDMI/audio sink can force the engine. Battery/thermal is a later policy input, not a Phase-1 selector.

**Phase-1 must-haves still omitted:**
- Fallback contract: failed Auto/MPV startup must retry ExoPlayer and record why.
- Observability: structured playback decision logs, display-mode changes/restores, HDR/passthrough outcomes, and sync conflicts.
- Migration boundary: Room-backed repos must preserve current offline downloads and watched/progress state during strangling.
- Device-matrix exit criteria: named devices, media fixtures, pass/fail thresholds, and the resulting MPV enable floor.

**Spec readiness:** converged enough to write the foundational architecture SPEC. No blocker remains.

## Track A plan review - Codex (2026-06-16)

**1. Plan/code fit.**
- Selector/request enum names match: `VideoPlaybackBackendRequest` currently has `contentId`, `fileId`, `playMethod`, `formFactor`, `preference`, `hasHardContainer`, `hasStyledSubtitles` (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/VideoPlaybackBackendRequest.kt:5`); enums are `Auto/Media3/Mpv` and `Media3/Mpv` (`VideoPlaybackBackendPreference.kt:3`, `VideoPlaybackBackendKind.kt:3`). The planned request booleans are type-consistent additions.
- Factory does run the selector (`VideoPlaybackBackendFactory.kt:22`), but then computes `actual` and downgrades to Media3 unless `player is MpvPlayer` (`VideoPlaybackBackendFactory.kt:23`). Task 4 must log `actual` plus downgrade reason, not only `selected`; otherwise logs can claim MPV while the returned backend is Media3.
- Production call sites do not pass the policy inputs: phone/TV create the backend with only `contentId`, `fileId`, `formFactor` (`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt:180`, `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/player/TvPlayerScreen.kt:192`), before `playMethod`, `container`, and subtitles are known in the mount specs (`PlayerScreen.kt:341`, `TvPlayerScreen.kt:523`). Task 6 Step 5 says to wire later, but until selection is moved/re-run at mount time, Auto is blind in real playback.
- Bigger mismatch: both screens pass a `MediaController` into the factory (`PlayerScreen.kt:177`, `TvPlayerScreen.kt:189`). `ContinuumPlaybackService` owns the real session player and always builds ExoPlayer through `createPlaybackPlayer() -> playerFactory.createPlayer()` (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlaybackService.kt:79`, `ContinuumPlaybackService.kt:166`); `ContinuumPlayerFactory.createMpvPlayer()` exists but is unused (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt:178`). Therefore MPV Auto is not reachable through current app flow even if the selector returns `Mpv`.
- `HdrDisplayController` does have the cited behavior, with line drift: original mode capture at `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/HdrDisplayController.kt:56`, apply via `preferredDisplayModeId` at `HdrDisplayController.kt:91`, restore at `HdrDisplayController.kt:105`. Task 5 can build on it, but `applyForMedia` currently has no content-HDR parameter (`HdrDisplayController.kt:72`), so the plan needs a signature/caller change, including `TvPlayerScreen.kt:464`.

**2. Fallback contract.**
Task 3 Step 5 is hand-wavy and not reachable as stated. The real synchronous media-start call is `mountVideoMedia(...)`: it calls `player.setMediaItem(...)`, `player.prepare()`, then `playWhenReady` (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/VideoPlayerMediaMounter.kt:26`), reached through `MpvVideoPlaybackBackend.mount(...)` (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/backend/MpvVideoPlaybackBackend.kt:28`) and the screen effects at `PlayerScreen.kt:312` and `TvPlayerScreen.kt:518`. MPV native creation/init failures happen earlier in `MpvPlayer` (`MPVLib.create` at `android-shared/src/androidMain/kotlin/com/continuum/app/common/player/mpv/MpvPlayer.kt:172`, `mpv.init()` at `MpvPlayer.kt:217`), only if `ContinuumPlayerFactory.createMpvPlayer()` is used (`ContinuumPlayerFactory.kt:178`). The catch point for real MPV fallback must be the service/engine owner that creates/replaces the MediaSession player, i.e. `ContinuumPlaybackService.createPlaybackPlayer()` or its replacement, plus the mount caller for prepare/load failures. Recreating a Media3 backend around the same `MediaController` does not replace an MPV-backed session player.

**3. Device matrix.**
The fixtures cover baseline codecs, HDR10/DV, ASS/SSA, passthrough, transcode, and cast. They are not enough for go/no-go. Add:
- Seek/trickplay: phone seek flows (`PlayerScreen.kt:529`) and TV skip/scrub/chapter flows (`TvPlayerScreen.kt:592`, `TvPlayerScreen.kt:716`, `TvPlayerScreen.kt:816`) against MPV's `seekTo`/speed implementation (`MpvPlayer.kt:822`).
- MediaSession/notification/transport: service publishes one player through `MediaSession.Builder(this, player)` (`ContinuumPlaybackService.kt:98`); verify notification, lock-screen, remote, headset, and TV media-session controls after MPV selection.
- Audio focus/noisy/lifecycle: Exo config handles audio focus/noisy (`ContinuumPlayerFactory.kt:148`), while MPV separately requests/abandons focus (`MpvPlayer.kt:251`, `MpvPlayer.kt:789`, `MpvPlayer.kt:898`); test focus loss/gain, BT/headset/noisy, background, and return.
- HDMI hotplug/AVR change while playing: capability flow is explicitly driven by HDMI/audio-route changes (`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudioCapabilityManager.kt:19`) and TV reapplies presets on audio/HDR changes (`TvPlayerScreen.kt:365`); test AVR power cycle, EDID renegotiation, refresh restore, HDR toggle, and audio-route changes mid-playback.
- Startup failure injection: force MPV init/load failure and verify one fallback to Media3 with a structured reason, not just absence of crash.

**4. Biggest risk and fix.**
Biggest risk: the plan hardens pure selector logic while the real engine owner is still the MediaSession service, which always creates ExoPlayer and exposes only a `MediaController` to the UI. Selector/fallback/decision tests can pass while MPV is never selected in production, or while a "Media3 fallback" only wraps the same MPV session. Single most important fix: add an explicit Track A task before Tasks 3-4 to define and implement the engine ownership/switch boundary in `ContinuumPlaybackService` (or a dedicated playback-session coordinator): build ExoPlayer or `MpvPlayer` from the real `VideoPlaybackBackendRequest`, replace/rebind the `MediaSession` player on fallback, and emit requested/selected/actual engine decisions from that boundary.

---

## Track B decisions - Codex (2026-06-16)

### 1. Room placement

**Recommendation:** choose **(a) Room in `android-shared/androidMain`**. Keep the
repository contracts consumed by common ViewModels in `shared/commonMain`, but
turn only the Track-B strangled repositories into interfaces/ports there. Put
Room entities, DAOs, database, sync engine, and Android repository implementations
in `android-shared/src/androidMain`; those implementations compose the existing
Ktor APIs plus Room. Do not choose Room-KMP now, and do not accelerate a data-only
KMP teardown.

**Reasoning:** Silo is Android-only, but D1 already settled that KMP teardown is
later hygiene. Android-only Room gives the data foundation without the blast
radius of moving source sets first. Room-KMP is viable, but it would put DAOs and
entities in `commonMain` right before we intend to stop valuing KMP, adding KSP
and multiplatform database ceremony for no product target. A partial data-layer
teardown first is worse: it changes module shape and repository wiring before the
offline behavior exists. The strangler cost is real but bounded: extract common
interfaces for the first migrated repositories, rename or wrap today's API-only
classes as network implementations, and override Koin bindings from the Android
modules while the common repository module is retired incrementally.

**Evidence:** `:shared` and `:android-shared` are KMP modules, with shared network
deps in `commonMain` and Android deps in `androidMain`
(`shared/build.gradle.kts:1-33`, `android-shared/build.gradle.kts:1-20`). The
current repositories are commonMain concrete API wrappers, not local-first ports:
`RepositoryModule` describes them as "stateless wrappers around API classes" and
binds them directly (`shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:34-58`);
`CatalogRepository` delegates straight to `CatalogApi`
(`shared/src/commonMain/kotlin/com/continuum/app/repository/CatalogRepository.kt:14-16,33-49`);
`PersonalDataRepository` delegates writes straight to `PersonalDataApi`
(`shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt:13-15,76-106`).
Existing durable local state is already Android-only
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:1-28`,
`android-shared/src/androidMain/kotlin/com/continuum/app/common/store/ScopedJsonFileStore.kt:9-17`).
Findroid and Voice both support the Android-side Room placement pattern:
findroid's Room database is in its Android `data` module
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabase.kt:24-52`);
Voice keeps API-facing repository interfaces separate from a Room-backed impl
(`/Users/jimcole/source/Voice/core/data/api/src/main/kotlin/voice/core/data/repo/BookRepository.kt:8-21`,
`/Users/jimcole/source/Voice/core/data/impl/src/main/kotlin/voice/core/data/repo/BookRepositoryImpl.kt:15-20`,
`/Users/jimcole/source/Voice/core/data/impl/src/main/kotlin/voice/core/data/repo/internals/PersistenceModule.kt:31-39`).

**Biggest risk:** the interface extraction can sprawl if we try to convert all
19 repositories at once. Limit Track B to the home/library browse + resume +
downloads + user-state paths, then widen after the first offline round-trip works.

### 2. Sync model

**Recommendation:** use a **hybrid** model: Room is the local read source and
optimistic write target; user mutations write both a projection row and a
`dirty_operations` outbox row; sync drains idempotent operations, refreshes the
server projection, and resolves conflicts by field policy. Do not use pure
server-authoritative reads, because offline edits would disappear. Do not copy
findroid's single dirty row as the whole design, because Silo needs ordered,
typed replay for deletes, ratings, favorites, track selections, and CFI.

**Findroid model:** findroid stores user data in Room with a `toBeSynced` dirty
flag (`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/models/FindroidUserDataDto.kt:6-14`).
Its offline repository updates local playback position, played state, and favorite
state, then marks the row dirty
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/repository/JellyfinRepositoryOfflineImpl.kt:232-295`).
The online repository also writes local first and marks dirty only when the server
call fails (`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/repository/JellyfinRepositoryImpl.kt:437-535`).
`SyncWorker` later scans dirty user-data rows, sends `UpdateUserItemDataDto`, and
clears the flag on success
(`/Users/jimcole/source/findroid/core/src/main/java/dev/jdtech/jellyfin/work/SyncWorker.kt:72-93`).
That is a dirty snapshot, not a general operation outbox; findroid's offline repo
also has many empty/TODO paths
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/repository/JellyfinRepositoryOfflineImpl.kt:43-115`).

**Concrete Silo shape:**
- Position: `user_item_state(position_seconds, duration_seconds, client_updated_at,
  server_updated_at)` plus coalescing `SET_POSITION` outbox ops. Conflict: LWW by
  authoritative update timestamp for current resume position; keep an optional
  monotonic `furthest_position` if the server wants continue-watching ranking.
- Watched: optimistic `watched` row plus `SET_WATCHED(bool)` op. Conflict: local
  action wins until sync attempt; after ack, server projection wins because the
  server resolves series/season aggregate writes to leaf items
  (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:113-123`).
- Track selections: per `(profileId, contentId, fileId)` row storing selected
  audio/subtitle by stable fingerprint `(index, language, codec, title, forced)`,
  not raw UI position alone. Conflict: client-owned LWW. Existing APIs carry
  `audio_track_index` at start/change but do not persist subtitle choice
  (`shared/src/commonMain/kotlin/com/continuum/app/model/playback/PlaybackModels.kt:100-114`,
  `shared/src/commonMain/kotlin/com/continuum/app/network/api/PlaybackApi.kt:46-54`);
  the UI currently applies subtitle choice locally
  (`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerViewModel.kt:611-645`,
  `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt:545-550`).
- CFI / ebook progress: store current CFI/location, `progress`, `fileId`, and
  timestamps. Conflict: current CFI is LWW; optional furthest-read progress is
  max-monotonic. Silo already has local `ProgressSnapshot(location, progress,
  updatedAtMs)` and a syncer that avoids regressing server progress
  (`android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:11-17`,
  `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookProgressSyncer.kt:18-21,37-55`).
- Ratings and favorites: `SET_RATING(value|null)` and `SET_FAVORITE(bool)` ops,
  coalesced by `(profileId, contentId, op_kind)`. Conflict: client LWW until ack;
  server projection wins after ack or hard rejection. Current APIs are set/delete
  commands (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:19-41,92-111`).

**Evidence:** Silo already has local-first write patterns for reader/audiobook
position, but outside a unified DB
(`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt:392-410`,
`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt:673-690`).
Audiobook resume already merges local/server by taking the furthest position
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/player/AudiobookPlayerViewModel.kt:698-718`).
Voice shows Room is a good fit for media position/bookmark state with explicit
entities and migrations
(`/Users/jimcole/source/Voice/core/data/api/src/main/kotlin/voice/core/data/BookContent.kt:10-24`,
`/Users/jimcole/source/Voice/core/data/api/src/main/kotlin/voice/core/data/Bookmark.kt:8-17`,
`/Users/jimcole/source/Voice/core/data/impl/src/test/kotlin/voice/core/data/repo/internals/internals/DataBaseMigratorTest.kt:26-46`).

**Biggest risk:** Silo's server APIs do not yet expose enough per-field
`updated_at` or idempotency metadata for rigorous LWW. Track B must either add
that server contract or degrade to "local optimistic, server projection after
ack" with logged conflicts.

### 3. Migration boundary

**Recommendation:** make migration an idempotent Room import plus a temporary
dual-write period. Room becomes the read projection only after it imports existing
sidecars and scoped JSON files; legacy files are not deleted during Track B. For
one release window, all download/status/progress writes update both Room and the
legacy sidecar/JSON store, and legacy readers remain a fallback when Room lacks a
row.

**Downloads import:** scan `DownloadStorage.listAllSidecarsWithScope()`, which
preserves `(serverId, profileId)` from the sidecar path
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:181-200`).
For each sidecar, upsert a Room download row keyed by `(serverId, profileId,
downloadRecord.id/mediaFileId)` with the full `DownloadSidecar` metadata:
title/poster/fileName/container/mediaType/localUri/chapters are already in the
sidecar (`shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadSidecar.kt:6-23,25-66`).
Validate completed rows against real bytes using `locateLocalMedia`/`exists`
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:51-56,85-86`);
if bytes are missing, import the row as stale/failed instead of deleting it. Keep
the sidecar path/hash/mtime in a `legacy_imports` table so the import can rerun.
This mirrors today's boot path, where DownloadsViewModel reloads sidecars, seeds
the repository, and keeps disk-only records during server refresh
(`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt:225-233,361-373`,
`shared/src/commonMain/kotlin/com/continuum/app/repository/DownloadsRepository.kt:18-23,49-94`).

**Scoped JSON import:** import all scoped files under the paths defined by
`ScopedJsonFileStore` (`serverId/profileId/contentId` with atomic JSON writes:
`android-shared/src/androidMain/kotlin/com/continuum/app/common/store/ScopedJsonFileStore.kt:24-40`).
Use existing enumerators where they exist:
`EbookLocalStateStore.listAllProgress()`
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:55-82`)
and `AudiobookPositionStore.listAll()`
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookPositionStore.kt:53-80`).
Add import-only walkers for ebook bookmarks and audiobook bookmarks because the
bookmark stores currently expose scoped reads/writes but no global enumerator
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:84-118`,
`android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookBookmarksStore.kt:7-22`).
Preserve local IDs, timestamps, and scope exactly; create outbox entries for any
imported local progress/bookmark state that is newer than the last known server
projection or has never been acknowledged.

**Cutover rule:** new Room-backed repositories read Room first, then fall back to
legacy storage on a miss and backfill Room in the same transaction. Download
enqueue, worker completion/failure, and delete paths dual-write until counts and
file validation match across Room and sidecars
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadEnqueuer.kt:241-263`,
`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadWorker.kt:131-149,193-224`,
`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/downloads/DownloadsViewModel.kt:299-349`).
Do not remove sidecars or JSON files until a later cleanup migration proves:
Room row count equals valid legacy row count, every completed download resolves to
bytes, and sync has no pending import-created outbox rows.

**Biggest risk:** stale MediaStore URIs or malformed sidecars can produce false
Room rows. The guardrail is to treat legacy data as source material, not garbage:
validate bytes, record import errors, keep legacy files untouched, and keep the
old offline resolver path available until the Room projection proves equivalent
(`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/OfflineMediaResolver.kt:23-48`,
`androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/MobileDownloadVisibility.kt:5-19`).

## Track B plan review - Codex (2026-06-16)

**Verdict:** good direction, but Task 4/5 are not implementable as written.

1. Symbol/signature check:
- `DownloadStorage.listAllSidecarsWithScope()` exists as `List<Triple<String, String, DownloadSidecar>>`; `locateLocalMedia(...)` and `exists(...)` also exist (`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:51`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:85`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:188`).
- `DownloadSidecar` has sidecar fields plus nested `record`; `record` owns `id`, `contentId`, `mediaFileId`, `fileSize`, `bytesSent`, and `status` (`shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadSidecar.kt:26`, `shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadModels.kt:16`). Plan code must use `sidecar.record.*`; `fileName` is nullable, and real status wire values are lower-case `queued/downloading/completed/failed/cancelled`, not `QUEUED/RUNNING/COMPLETE` (`shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadSidecar.kt:39`, `shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadModels.kt:59`).
- `EbookLocalStateStore.ProgressSnapshot` exists, but it is nested and `listAllProgress()` returns `List<ProgressEntry>` with `snapshot` nested, not snapshots directly (`android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:11`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:47`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:60`).
- `AudiobookPositionStore.listAll()` exists and returns `List<Entry>` with nested `Snapshot` (`android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookPositionStore.kt:19`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookPositionStore.kt:45`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookPositionStore.kt:59`).
- Bookmark global walkers really are missing; only scoped reads/writes exist (`android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt:84`, `android-shared/src/androidMain/kotlin/com/continuum/app/common/audiobook/AudiobookBookmarksStore.kt:21`).
- API write names/shapes are concrete: favorites are `addFavorite/removeFavorite`, watched is `markWatched/markUnwatched`, ratings are `setRating/deleteRating`, progress batch is `syncProgress(SyncProgressRequest)` returning `Unit`, and playback progress is session-scoped `updateProgress(sessionId, ProgressRequest)` (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:35`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:85`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:102`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:116`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PlaybackApi.kt:25`).

2. Room/KSP feasibility:
- Feasible in `android-shared/androidMain`: the module is KMP plus Android library, has an `androidTarget`, and already has `androidMain` / `androidUnitTest` source-set dependencies (`android-shared/build.gradle.kts:1`, `android-shared/build.gradle.kts:9`, `android-shared/build.gradle.kts:18`, `android-shared/build.gradle.kts:84`).
- Gotcha: the current catalog/root has no KSP or Room aliases (`gradle/libs.versions.toml:93`, `build.gradle.kts:1`). In a KMP module, use the Android-target KSP configuration, e.g. `add("kspAndroid", libs.androidx.room.compiler)`, not generic `ksp(...)`. Also add Robolectric to `android-shared` tests and AndroidX test core for `ApplicationProvider`; `android-shared` currently has only kotlin-test/JUnit/coroutines-test in `androidUnitTest` (`android-shared/build.gradle.kts:84`).

3. DI reachability:
- `RepositoryModule` binds concrete repositories in `commonMain`: `PlaybackRepository`, `PersonalDataRepository`, and `DownloadsRepository` (`shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:46`, `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:47`, `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:55`).
- The real override point is the platform module loaded after `sharedModules()`: phone `androidModule` in `ContinuumApplication`, and TV `androidTvModule` in `ContinuumTvApplication` (`androidApp/src/androidMain/kotlin/com/continuum/app/android/ContinuumApplication.kt:31`, `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ContinuumTvApplication.kt:28`). The existing `TokenManager` override comment documents this load-order pattern (`androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt:90`).
- Adding `UserItemStatePort` alone will not intercept current paths. Existing graph nodes consume concrete repos/use cases (`shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:91`, `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt:92`, `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt:161`, `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt:178`). Same-key concrete overrides are weak because `PersonalDataRepository.toggleFavorite`, `listProgress`, and `setRating` are final, and `PlaybackRepository` / `DownloadsRepository` are final classes (`shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt:13`, `shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt:36`, `shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt:73`, `shared/src/commonMain/kotlin/com/continuum/app/repository/PersonalDataRepository.kt:91`, `shared/src/commonMain/kotlin/com/continuum/app/repository/PlaybackRepository.kt:13`, `shared/src/commonMain/kotlin/com/continuum/app/repository/DownloadsRepository.kt:33`).

4. Sync/degrade against today's APIs:
- `SyncEngine.drain()` cannot "apply ConflictPolicy to the returned server state" for most ops because mutation APIs return `Unit` (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:35`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:39`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:85`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:102`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:109`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:116`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:121`).
- There is no per-field `updated_at`. Progress has item-level `updatedAt`; ratings have `rated_at`; favorites/watched expose no timestamp in these client API shapes (`shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt:104`, `shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt:151`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:21`, `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:115`). The implementable degrade is local optimistic until ack, then refresh and trust server projection for supported fields.
- `PlaybackApi.updateProgress` cannot drain an offline resume outbox keyed by content/file after process restart because it needs a live `sessionId`; resume sync should use `PersonalDataApi.syncProgress` with `SyncProgressItem(mediaItemId, position, duration, forceOverwrite)` (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PlaybackApi.kt:25`, `shared/src/commonMain/kotlin/com/continuum/app/model/personal/PersonalDataModels.kt:118`).
- `SET_TRACK_SELECTION` cannot round-trip today. `changeAudio` needs a live session and persists no server preference; subtitle selection has no API (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PlaybackApi.kt:46`, `shared/src/commonMain/kotlin/com/continuum/app/model/playback/PlaybackModels.kt:100`, `shared/src/commonMain/kotlin/com/continuum/app/model/playback/PlaybackModels.kt:116`). Keep track selection local-only or add a server projection API.

5. Biggest risk / single fix:
- Biggest correctness risk: Task 4/5 can build Room/outbox code that no real app path uses, then drain ops against APIs that cannot represent them.
- Single most important fix: rewrite Task 4/5 before implementation so migrated consumers depend on explicit shared ports, Room-backed Android implementations are bound at `androidModule` / `androidTvModule`, and the outbox is limited to server-supported ops: `SET_POSITION` via `PersonalDataApi.syncProgress`, watched, rating, and favorite. Track selection stays local-only until the server has a real projection API.

## Track B Task 1 - Codex (2026-06-16)

**Build-infra verdict:** use KSP `2.1.20-2.0.1` and Room `2.8.4`.
`android-shared` is KMP with `androidTarget` and Android-only source sets
(`android-shared/build.gradle.kts:1`, `android-shared/build.gradle.kts:9`,
`android-shared/build.gradle.kts:18`), while the catalog currently has no KSP or
Room aliases (`gradle/libs.versions.toml:93`). The KSP release tag for Kotlin
`2.1.20` is exactly `2.1.20-2.0.1`
(`https://github.com/google/ksp/releases/tag/2.1.20-2.0.1`:177,199-201).
KSP's KMP docs say to use per-target configurations such as
`add("ksp<Target>", ...)`, and to avoid plain `ksp(...)` for multiplatform
projects (`https://kotlinlang.org/docs/ksp-multiplatform.html`:5,11-17).
Room `2.7.0` made Room a KMP library and recommends KSP2 for Kotlin 2.0+
(`https://developer.android.com/jetpack/androidx/releases/room`:856-860);
the current Room dependency examples use `2.8.4`
(`https://developer.android.com/jetpack/androidx/releases/room`:548-555).

Copy-paste catalog delta:

```toml
[versions]
ksp = "2.1.20-2.0.1"
room = "2.8.4"
androidx-test-core = "1.6.1"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-test-core = { module = "androidx.test:core-ktx", version.ref = "androidx-test-core" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx-room = { id = "androidx.room", version.ref = "room" }
```

Copy-paste `android-shared/build.gradle.kts` shape:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
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
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
        }

        androidUnitTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.robolectric)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
```

The important difference from findroid: `findroid/data` is a pure Android
library, so it applies KSP directly (`/Users/jimcole/source/findroid/data/build.gradle.kts:1-4`),
passes raw Room KSP args in `defaultConfig`
(`/Users/jimcole/source/findroid/data/build.gradle.kts:20-23`), and uses
`ksp(libs.androidx.room.compiler)`
(`/Users/jimcole/source/findroid/data/build.gradle.kts:39-43`).
In `android-shared`, do **not** copy that dependency line. Use
`add("kspAndroid", libs.androidx.room.compiler)` because the module is
multiplatform. The Room Gradle plugin is not required for Room to compile, but it
is preferred for schemas: official docs say Room 2.6+ can use
`room { schemaDirectory(...) }`, while raw `room.schemaLocation` should be wired
through a Gradle-aware argument provider for correctness
(`https://developer.android.com/training/data-storage/room/migrating-db-versions`:581-602,636-641).
So prefer `alias(libs.plugins.androidx.room)` plus `room { schemaDirectory(...) }`
over findroid-style raw KSP args. Keep `@Database(exportSchema = true)`, commit
`android-shared/schemas`, and add `android.sourceSets["androidUnitTest"].assets`
only when migration tests start using `room-testing`.

**Task-1 schema review:** the proposed four entities are a good minimal start,
but they are not yet a complete offline-first projection/outbox.

- `UserItemStateEntity` must include `serverId` in identity. Silo's legacy stores
  scope state by `(serverId, profileId, contentId)`
  (`android-shared/src/androidMain/kotlin/com/continuum/app/common/store/ScopedJsonFileStore.kt:24-29`),
  and downloads are scoped the same way
  (`android-shared/src/androidMain/kotlin/com/continuum/app/common/downloads/DownloadStorage.kt:138-140`).
  Use
  primary key `["serverId", "profileId", "contentId", "fileId"]` for file-level
  progress/track/CFI rows. If watched/rating/favorite stay in this table, define
  the content-level semantics now: those APIs are keyed only by item id
  (`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:35-40`,
  `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:102-110`,
  `shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:115-122`),
  so a multi-file content item must not emit
  duplicate/conflicting ops per file. Add indices for `serverId/profileId/contentId`
  and resume scans by `serverId/profileId/clientUpdatedAtMs`.
- `DirtyOperationEntity` needs durable replay metadata, not just payload JSON.
  Add `serverId`, `profileId`, `targetContentId`, nullable `targetFileId`,
  `idempotencyKey`, `opVersion`, `lastAttemptAtMs`, `nextAttemptAtMs`,
  `lastError`, and a pending/in-flight state or keep pending-only with explicit
  retry columns. Add a unique index on `idempotencyKey`, an index on
  `coalesceKey`, and a drain index on `nextAttemptAtMs, id`. Projection write
  plus outbox insert must happen in one Room transaction; Voice's repository
  layer does multi-DAO work through a database transaction
  (`/Users/jimcole/source/Voice/core/data/api/src/main/kotlin/voice/core/data/repo/internals/RoomTransaction.kt:9-25`).
- `DownloadEntity` needs enough sidecar data to render and play offline. The plan
  captures title/poster/file/status, but the current sidecar also carries
  subtitle, poster thumbhash, year, series/season/episode metadata, overview,
  author, narrator, duration, chapters, and `updatedAtMs`
  (`shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadSidecar.kt:26-66`).
  Store `record.contentId`, `fileSize`, `bytesSent`,
  `kind`, `createdAt`, and `completedAt` from `DownloadRecord`
  (`shared/src/commonMain/kotlin/com/continuum/app/model/download/DownloadModels.kt:16-28`).
  If chapters are stored as a list, add a
  `@TypeConverters`; simpler for Task 1 is `chaptersJson: String?`.
  Add indices for `(serverId, profileId, status)`, `(serverId, profileId, contentId)`,
  and unique `recordId`.
- `LegacyImportEntity` is directionally right, but make it diagnostic enough for
  repeatable imports: `sourceKind`, `sourcePath`, `sourceHash`, `sourceMtimeMs`,
  `importedAtMs`, `importVersion`, `status`, and nullable `error`. Use a unique
  index on `(sourceKind, sourcePath)` rather than assuming every path namespace is
  globally stable.
- `SiloDatabase` needs `@TypeConverters` only if entities use non-primitive Room
  fields. Strings, numbers, booleans, and JSON strings need no converter. Findroid
  needs converters because it persists UUIDs, SDK DateTime, and chapter lists
  (`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/database/Converters.kt:11-40`);
  Voice needs converters for `Instant`, `Uri`, `File`, UUID wrappers, and lists
  (`/Users/jimcole/source/Voice/core/data/impl/src/main/kotlin/voice/core/data/repo/internals/Converters.kt:16-81`).

DAOs should be wider than the plan's smoke-test methods. `UserItemStateDao` needs
scoped reads by content and resume/progress scans, not just exact file lookup.
`DirtyOperationDao` needs transactional enqueue/coalesce, due-batch selection,
attempt bookkeeping, and delete-on-ack. `DownloadDao` needs `get(serverId,
profileId, mediaFileId)`, `getByContent(...)`, `getAll(serverId, profileId)`,
status scans, and scoped deletes. `LegacyImportDao` needs upsert and lookup by
kind/path/hash/mtime.

Reference comparison: findroid stores a Room user-data snapshot with a single
`toBeSynced` dirty flag
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/models/FindroidUserDataDto.kt:6-14`),
local offline writes set that flag after updating position/favorite/played
(`/Users/jimcole/source/findroid/data/src/main/java/dev/jdtech/jellyfin/repository/JellyfinRepositoryOfflineImpl.kt:239-295`),
and `SyncWorker` later posts the snapshot and clears the flag
(`/Users/jimcole/source/findroid/core/src/main/java/dev/jdtech/jellyfin/work/SyncWorker.kt:72-93`).
That is useful precedent for local-first projection, but
Silo needs a typed operation outbox because Silo has independent server commands
for progress, watched, favorite, and rating, most returning `Unit`
(`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:85-89`,
`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:102-110`,
`shared/src/commonMain/kotlin/com/continuum/app/network/api/PersonalDataApi.kt:115-122`).
Voice is the better precedent for schema hygiene:
it exports schemas, keeps explicit auto migrations on the database
(`/Users/jimcole/source/Voice/core/data/impl/src/main/kotlin/voice/core/data/repo/internals/AppDb.kt:18-38`),
and validates migrations with `MigrationTestHelper`
(`/Users/jimcole/source/Voice/core/data/impl/src/test/kotlin/voice/core/data/repo/internals/internals/DataBaseMigratorTest.kt:26-46`).
For Silo Task 1, ship version 1 with no
auto migrations; after schema v1 is committed, use auto migrations only for
simple additive/rename cases and manual migrations for any data transform.

### Track B Task 1 — Codex review verdict (2026-06-16)

Claude implemented the Room foundation to the richer schema above; build + Robolectric DAO test pass (KSP ran, `copyRoomSchemas` exported `schemas/.../1.json`, 9 tests green). Codex reviewed the uncommitted diff read-only.

**Verdict: good to commit. No must-fix in the schema/DAO foundation.** Confirmed: `user_item_state` PK/indices correct; dirty-ops unique `idempotencyKey` + `coalesceKey` + drain `(nextAttemptAtMs,id)`; downloads status/content indices + unique `recordId`; legacy unique `(sourceKind,sourcePath)`; `enqueueCoalescing` delete+insert is atomic (Room wraps the `@Transaction suspend` fn in `performInTransactionSuspending`); `exportSchema=true` / v1 / no converters correct; `1.json` matches annotations.

**Two nice-to-haves deferred to Task 5 (drain-loop + producer wiring), NOT Task 1:**
1. `DirtyOperationDao.recordFailure` can resurrect an older in-flight op after a newer pending op with the same `coalesceKey` was queued. When wiring the drain loop, on failure drop the failed older row if a newer pending row exists for that key (or fold coalescing into the failure path).
2. `OutboxOperation` coalesce keys omit `serverId`, but `enqueueCoalescing` deletes by bare `coalesceKey`. When wiring the producer → `DirtyOperationEntity`, make the coalesce key globally scoped (prefix `serverId|profileId`) OR change the DAO delete to also filter `serverId/profileId`. Pick one and enforce it in the producer.

## Track B Task 4 — converged design (Claude↔Codex, 2026-06-16)

Claude proposed a narrower re-scope after the Explore map showed the plan's draft mis-targeted the surface (resume position is read server-side via home-sections + WatchDetail.userData, not an interceptable repo method). Codex reviewed the actual code and countered on three points; we converged:

**Scope:** Track B Task 4 = content-level user-state **mutations** (watched/favorite/rating) as a strangler. Resume/position read+write is a separate later slice (position is session-keyed; the later slice must cover `ManagePlaybackUseCase.reportProgress`, `PlaybackSessionManager.reportProgress`/`PlaybackSessionLifecycle`, AND `PersonalDataRepository.syncProgress` — Codex correction).

**Choke point (Codex won):** NOT `MediaActionsCoordinator` — it lacks rating methods and is bypassed by favorites-removal (`PersonalListViewModels.kt:124`) and phone/TV detail VMs (`ItemDetailViewModel.kt:291/312/329`, `TvItemDetailViewModel.kt:145/180/199`). Instead, **`PersonalDataRepository` delegates** its `toggleFavorite/setRating/deleteRating/setWatched` to a `UserItemStatePort` — catches every caller without chasing UI sites.

**Dual-path (Codex refinement):** write content projection + pending outbox op in one Room txn → call network inline → **resolve**: Success ⇒ delete op (acked, so Task 5 won't replay); `NetworkError`/5xx/408/429 ⇒ keep pending (durable retry); other 4xx ⇒ drop op (don't replay a doomed write). Projection-revert on terminal failure is deferred to the local-read slice (nothing reads the projection yet, so a stale optimistic row is unobservable now). Callers still get the real `ApiResult` and roll back UI as today (`MediaActionsCoordinator.kt:11`, `HomeViewModel.kt:113`, `CardActionsHelpers.kt:42`).

**Projection key (Codex won):** add a **separate `content_item_state` table** PK `(serverId, profileId, contentId)` for watched/rating/favorite — these mutations carry no fileId and can fire before any file row exists. `UserItemStateEntity` stays file-level (position/track/CFI); drop watched/ratingValue/favorite from it. Matches the outbox's `targetFileId: Int?` (null for content ops). **Coalesce key now serverId-scoped**: `serverId|profileId|contentId|kind`.

**Schema:** amend v1 in place (no migration) — the DB is committed but unreleased and not yet wired into DI, so no device has `silo.db`.

**DI:** commonMain `single { PersonalDataRepository(get(), getOrNull() ?: NoOpUserItemStatePort) }`; `androidModule`+`androidTvModule` bind `SiloDatabase` and `single<UserItemStatePort> { RoomUserItemStateRepository(...) }` after `sharedModules()` (mirrors TokenManager override at `AndroidModule.kt:94`).

### Track B Task 4 — Codex code-review verdict (2026-06-16)

Codex reviewed the diff read-only. Verdict: faithful to the converged design (record→network→resolve; separate `content_item_state`; server-scoped coalesce key). **One must-fix (applied):** `toWriteOutcome` classified HTTP 401 as TERMINAL, which could drop a valid unsynced write when token refresh can't complete — 401 is now RETRIABLE (403 + other 4xx stay terminal). Added a MockEngine-backed `PersonalDataRepositoryPortTest` locking 200→SYNCED / 401→RETRIABLE / 403→TERMINAL / 500→RETRIABLE.

Confirmed non-issues: `resolve()` interleaving sound for pending rows (a coalesced-away opId resolve is a harmless no-op); `db.withTransaction` + suspend DAOs fine (nested `@Transaction` is redundant but harmless); Koin lazy-singleton DI order fine; `favorite = 1` on nullable Boolean fine.

Deferred (not Task 4): (1) `DirtyOperationDao.recordFailure` can move an old in_flight row back to pending after a newer op exists — fix before Task 5 drain wiring. (2) `OutboxOperation` helper keys are still profile-only — fix before any producer is wired through those helpers (this Room producer already uses serverId-scoped keys). (3) `ContentItemStateEntity.ratingValue` uses null for both "unknown" and "cleared" — the local-read slice will need field-level known/updated metadata.
