# Contributing to Prairie Android

Prairie Android is an AGPL-3.0 fork of the Silo Android clients. This guide
covers the Android-specific workflow for this repository.

## Before you start

Open an [issue](https://github.com/Prairie-Server/prairie-android/issues) before
implementing a feature, navigation or behavior change, large refactor, or work
that changes the shared server contract. Documentation, narrow fixes, and
well-scoped parity corrections can go straight to a pull request.

This repository owns the Android phone and Android TV clients. Server/API work
belongs in [`prairie-server`](https://github.com/Prairie-Server/prairie-server).

## Development setup

Read [README.md](README.md) for prerequisites and build commands, then read
[AGENTS.md](AGENTS.md) for the current product exposure, module ownership, and
testing guidance.

Build the two debug applications with JDK 21 and an Android SDK:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidTvApp:assembleDebug
```

A running Prairie server is required for realistic authentication, browsing, and
playback validation. Do not commit SDK overrides, signing material, generated
build output, logs, or media fixtures.

## Validate your change

Run focused module tests while iterating. Before opening a pull request, run the
same checks as CI:

```sh
./scripts/test-check-build-supply-chain.sh
./scripts/check-build-supply-chain.sh
./gradlew testDebugUnitTest
./gradlew \
  :android-shared:lintDebug :androidApp:lintDebug :androidTvApp:lintDebug \
  :androidApp:lintVitalRelease :androidTvApp:lintVitalRelease
```

Then build every affected app with `:androidApp:assembleDebug` and/or
`:androidTvApp:assembleDebug`. Do not regenerate a lint baseline to hide a new
finding. Exercise visible changes on the affected phone or TV surface and
include screenshots or a short recording when the result is visual.

## Open the pull request

Use a Conventional Commit title, explain which surfaces are affected, paste the
actual validation results, and call out any server coordination. If AI tools
helped produce the change, include an AI disclosure block (tool, model, and
level of involvement).

## Instructions for coding agents

Coding agents must read [AGENTS.md](AGENTS.md) before changing the repository
(`CLAUDE.md` points to the same guidance).
