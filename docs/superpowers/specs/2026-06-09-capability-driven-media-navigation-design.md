# Capability-Driven Media Navigation Design

## Goal

Reshape the Android clients around broad media modes instead of one fixed app structure for every account. The app should expose only the media surfaces the current user can actually use:

- Mobile can show `Video`, `Audio`, and `Reading`.
- Android TV can show `Video` and `Audio`.
- Android TV must never show `Reading`, even if the account has readable libraries.
- If a user has no libraries for a mode, that mode is hidden.

This is an app-wide information architecture change. Search, library browsing, home rails, requests, and offline/download entry points should fit under the same mode model over time.

## Media Modes

`Video` covers movies and TV/series. It owns video discovery, video library browsing, continue watching, video downloads, video search, and movie/TV requests.

`Audio` covers audiobooks now and music later. It owns audio discovery, audio library browsing, continue listening, audio downloads, audio search, and audiobook/music request surfaces as server support appears.

`Reading` covers ebooks now and comics/manga later. It owns reading discovery, reading library browsing, continue reading, readable downloads, reading search, and ebook/comic/manga request surfaces as server support appears. Reading is mobile-only.

## Capability Source

Use `/api/v1/user/libraries` through `PersonalDataRepository.listUserLibraries()` as the v1 capability signal. Each `UserLibrary.type` is normalized into one of the broad modes:

- Video: `movie`, `movies`, `series`, `show`, `shows`, `tv`, `video`
- Audio: `audiobook`, `audiobooks`, `music`, `album`, `albums`, `artist`, `artists`, `audio`
- Reading: `ebook`, `ebooks`, `book`, `books`, `comic`, `comics`, `manga`, `reading`

Unknown library types do not create a visible mode. The mapping should live in shared code so mobile and TV use the same normalization rules, with a platform constraint applied afterward to remove Reading on TV.

If the capability request is still loading, keep the existing safe default visible briefly instead of flashing an empty app. If it fails, show a non-destructive fallback that keeps the user able to reach stable surfaces such as Downloads, Search, Settings, and existing Home until retry succeeds.

## Mobile Navigation

Mobile primary navigation should become capability-driven:

- Show `Video` only when video capability exists.
- Show `Audio` only when audio capability exists.
- Show `Reading` only when reading capability exists.
- Keep `Downloads` global.
- Keep account/settings/more actions reachable through the existing header or a `More` destination if the bottom-nav structure needs it.

If exactly one media mode exists, the app should open directly into that mode and feel intentional. If no media modes are known yet, preserve the current loading/fallback experience rather than leaving the user on a blank shell.

Downloads stay global for now because users think in terms of "what did I save?" The Downloads screen can later add `All`, `Video`, `Audio`, and `Reading` filters, but this spec does not require that filter work in the first implementation slice.

## TV Navigation

Android TV primary navigation should be capability-driven:

- Show `Video` only when video capability exists.
- Show `Audio` only when audio capability exists.
- Never show `Reading`.
- Keep `Search`, `Requests`, and profile/settings access reachable.

TV should avoid an empty media shell. If only Audio exists, the app should land on Audio. If only Video exists, it should land on Video. If no TV-supported media mode exists, it should land on a stable utility surface with clear empty-state copy.

## Mode Surfaces

Each visible media mode should eventually have the same conceptual structure:

- Continue rail: `Continue Watching`, `Continue Listening`, or `Continue Reading`
- Library/discover rails scoped to the mode
- Mode-scoped search defaults
- Mode-appropriate request entry points
- Offline/download affordances where relevant

The first implementation does not need to fully rebuild every rail. It should establish the navigation model and route existing surfaces into mode destinations without regressing current playback, reading, downloads, search, or requests.

## Search And Requests

Search should respect visible modes:

- Mobile search can include Video, Audio, and Reading scopes only when those modes are visible.
- TV search can include Video and Audio scopes only when those modes are visible.
- Ebooks, comics, and manga must never appear as TV search scopes.

Requests should follow the same mode capabilities as server support grows. Current server request support is movie/series-oriented, so initial request UI can remain video-first while the navigation model leaves room for future audiobook, music, ebook, comic, and manga requests.

## Routing And State

The app must handle capability changes without stranding the user:

- If the current route's tab/mode becomes hidden after refresh, navigate to the first visible media mode.
- If no media mode is visible, navigate to a stable utility destination.
- Do not remove access to Downloads on mobile when media modes are hidden.
- Do not show Reading routes in TV chrome or TV search, even through shared normalization.

The mode model should be represented by small shared domain types rather than scattered string checks in UI files.

## Testing

Unit tests should cover shared media-mode normalization:

- Every known video type maps to Video.
- Every known audio type maps to Audio.
- Every known reading type maps to Reading.
- Unknown and blank types do not create capabilities.
- TV-supported modes exclude Reading.

Mobile tests should cover visible tab derivation for common accounts:

- Video only
- Audio only
- Reading only
- Video + Audio + Reading
- No known media modes

TV tests should cover:

- Video only
- Audio only
- Video + Audio
- Reading-only accounts hide Reading and use the utility fallback

Compile verification should run for shared, mobile, and TV targets touched by the implementation.

## Out Of Scope

This design does not implement music, comics, or manga content flows. It only reserves the navigation and capability model for them.

This design does not add new server request media types. It keeps current request behavior compatible and prepares the client UI to expose new request types later.

This design does not remove the global mobile Downloads destination.
