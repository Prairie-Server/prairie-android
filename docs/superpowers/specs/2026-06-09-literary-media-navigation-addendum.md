# Literary Media Navigation Addendum

## Goal

Adjust capability-driven navigation so audiobooks behave like literary media on mobile while remaining playable on Android TV.

The previous media-mode model treated audiobooks as `Audio` everywhere. The updated product direction is intent-based:

- Mobile `Reading` includes ebooks and audiobooks, plus comics and manga later.
- Mobile `Audio` is reserved for music and other non-book audio later.
- Android TV still supports audiobooks, but never exposes `Reading`; audiobook libraries therefore surface under TV `Audio`.
- Ebooks, comics, and manga remain hidden from Android TV.

## Capability Rules

Mobile capability derivation:

- `Video`: `movie`, `movies`, `series`, `show`, `shows`, `tv`, `video`
- `Reading`: `audiobook`, `audiobooks`, `ebook`, `ebooks`, `book`, `books`, `comic`, `comics`, `manga`, `reading`
- `Audio`: `music`, `album`, `albums`, `artist`, `artists`, `audio`

Android TV capability derivation:

- `Video`: `movie`, `movies`, `series`, `show`, `shows`, `tv`, `video`
- `Audio`: `audiobook`, `audiobooks`, `music`, `album`, `albums`, `artist`, `artists`, `audio`
- `Reading`: `ebook`, `ebooks`, `book`, `books`, `comic`, `comics`, `manga`, `reading`, then excluded from visible TV destinations

This means:

- An audiobook-only mobile account opens into `Reading`.
- An audiobook-only TV account opens into `Audio`.
- A music-only mobile account opens into `Audio`.
- An ebook-only TV account lands on the utility fallback, not a Reading destination.

## Search

Mobile search should expose only scopes backed by visible mobile modes.

`Reading` search must include both ebooks and audiobooks. Until the server exposes a combined reading query type, clients should query broadly and filter visible results to literary media types for the Reading scope.

`Audio` search should not appear on mobile for audiobook-only accounts. It appears when music/audio-native library types exist.

TV search keeps its existing server-safe movie, series, and audiobook filters, and still excludes ebook/Reading content.

## Unified Work Contract

The future server-owned work model will link ebook and audiobook content items under one literary work identity. The Android clients should not try to infer work links locally in this slice. They should only prepare the navigation/search model so audiobook and ebook content live together in the mobile Reading surface.
