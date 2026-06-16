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
