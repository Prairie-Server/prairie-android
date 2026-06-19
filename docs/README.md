# Silo Android Docs

Start with the root [README](../README.md) for architecture/build instructions and
[FEATURES](../FEATURES.md) for the current exposed feature inventory.

## Current Product Truth

- Android TV must not expose ebooks or Reading surfaces.
- Requests, Admin, and Watch Together are not currently accessible in either
  Android app. Treat old routes, repositories, tests, and design plans as inactive
  until a product decision exposes them again.
- Phone downloads preserve original filenames/formats in public storage so other
  apps can open downloaded videos, audiobooks, and ebooks.
- Android TV has a dedicated audiobook detail/player flow; ebooks remain
  phone-only.

## Folders

- `media3/` - Android playback research and implementation notes.
- `superpowers/specs/` - historical design specs. Useful context, but not always
  current product truth.
- `superpowers/plans/` - historical implementation plans. Prefer the root
  README/FEATURES files for what is actually exposed today.
- `superpowers/reference/` - captured Apple/tvOS visual references used for UI
  parity work.
