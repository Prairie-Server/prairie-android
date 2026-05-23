# Third-Party Notices

Silo Android is licensed under `AGPL-3.0-or-later`. Third-party dependencies keep their original licenses.

## Media3 FFmpeg Decoder AAR

This repository includes `android-shared/libs/media3-decoder-ffmpeg-1.10.0.aar`, built from AndroidX Media3 1.10.0 and FFmpeg n6.0 using `scripts/build-ffmpeg-aar.sh`.

The local build script is intended to build FFmpeg in LGPL-only mode. Do not enable GPL or nonfree FFmpeg options without updating the release process and downstream distribution obligations.

Rebuild and source instructions are in [scripts/README-ffmpeg-aar.md](scripts/README-ffmpeg-aar.md).

## Gradle Wrapper

The Gradle wrapper scripts retain their upstream Apache-2.0 license.
