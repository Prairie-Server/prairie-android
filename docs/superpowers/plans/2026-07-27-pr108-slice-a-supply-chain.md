# PR 108 Slice A Supply-Chain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild PR 108's build, dependency-locking, and native-provenance work as a reviewable branch from current upstream, with Linux CI dependency verification covered explicitly.

**Architecture:** The slice adds enforcement at the repository boundary: immutable GitHub Action SHAs, a pinned Gradle distribution, dependency locking for every resolvable configuration, strict verification metadata, and a self-testing policy script. Native Dolby Vision artifacts remain checked in, but their source, toolchains, lockfile, ABI hashes, and packaged AAR hash become reproducibly documented and verifiable.

**Tech Stack:** Gradle 8.12, Kotlin/Android Gradle Plugin, Bash, Ruby standard-library YAML/XML parsers, GitHub Actions, Rust/Cargo and Android NDK provenance.

## Global Constraints

- Start from `origin/main` at `da26f081efdd5d5ce069db937de105a30169ca16`.
- Preserve PR 108 at `445b5f39788657b1b8c58bb6b199f5c5404249a3` as the archival integration reference.
- Do not merge or admin-merge PR 108.
- Keep this slice limited to build policy, dependency locks/verification, and native playback provenance.
- Record original commit references and a path-level mapping for reviewer traceability.
- Do not trust hashes generated only from the local cache; verify new artifacts against the official repository response.

---

### Task 1: Port the Executable Supply-Chain Policy

**Files:**
- Create: `scripts/test-check-build-supply-chain.sh`
- Create: `scripts/check-build-supply-chain.sh`
- Modify: `.github/workflows/android-build.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `build.gradle.kts`

**Interfaces:**
- Consumes: GitHub workflow YAML, Gradle wrapper properties, and `gradle/verification-metadata.xml`.
- Produces: `scripts/check-build-supply-chain.sh`, which exits non-zero for mutable Actions, an unpinned wrapper, malformed/weak verification metadata, or missing CI platform artifacts.

- [ ] **Step 1: Install only the policy self-test from PR 108**

```bash
git show origin/pr/108:scripts/test-check-build-supply-chain.sh > /tmp/pr108-policy-test
```

Use `apply_patch` to add the exact file content at `scripts/test-check-build-supply-chain.sh`.

- [ ] **Step 2: Run the policy self-test to verify RED**

Run:

```bash
./scripts/test-check-build-supply-chain.sh
```

Expected: FAIL because `scripts/check-build-supply-chain.sh` does not exist on upstream.

- [ ] **Step 3: Port the policy and build configuration**

Apply the PR 108 net versions of:

```text
scripts/check-build-supply-chain.sh
.github/workflows/android-build.yml
.github/workflows/release.yml
gradle/wrapper/gradle-wrapper.properties
build.gradle.kts
```

The root build must enable `lockAllConfigurations()` and expose `resolveAndLockAll`; workflows must run both policy scripts before Gradle and pin every external Action to a 40-character commit SHA.

- [ ] **Step 4: Run the policy self-test to verify GREEN**

Run:

```bash
./scripts/test-check-build-supply-chain.sh
```

Expected: PASS once verification metadata is installed in Task 2.

### Task 2: Generate Locks and Verification Metadata for the Slice

**Files:**
- Create: `android-shared/gradle.lockfile`
- Create: `androidApp/gradle.lockfile`
- Create: `androidTvApp/gradle.lockfile`
- Create: `baselineprofile/gradle.lockfile`
- Create: `libass-bridge/gradle.lockfile`
- Create: `shared/gradle.lockfile`
- Create: `settings-gradle.lockfile`
- Create: `gradle/verification-metadata.xml`
- Modify: `scripts/test-check-build-supply-chain.sh`
- Modify: `scripts/check-build-supply-chain.sh`

**Interfaces:**
- Consumes: every resolvable Gradle configuration in this branch.
- Produces: strict SHA-256 verification metadata and per-project dependency lock state that allow both debug CI and release configurations to resolve.

- [ ] **Step 1: Add a failing Linux AAPT2 policy fixture**

Extend `scripts/test-check-build-supply-chain.sh` with a fixture containing a `com.android.tools.build:aapt2` component that has only `aapt2-8.10.1-12782657-osx.jar` and its POM.

Expected assertion:

```text
Missing Linux AAPT2 verification artifact
```

- [ ] **Step 2: Run the policy self-test to verify RED**

Run:

```bash
./scripts/test-check-build-supply-chain.sh
```

Expected: FAIL because the policy currently accepts metadata without the Linux classifier.

- [ ] **Step 3: Enforce CI platform coverage**

Update `scripts/check-build-supply-chain.sh` so every `com.android.tools.build:aapt2` component containing an OS-specific executable artifact includes a `-linux.jar` entry with a SHA-256 checksum.

- [ ] **Step 4: Run the policy self-test to verify GREEN**

Run:

```bash
./scripts/test-check-build-supply-chain.sh
```

Expected: PASS for the complete fixture set.

- [ ] **Step 5: Generate branch-specific locks and checksums**

Run:

```bash
./gradlew resolveAndLockAll --write-locks
./gradlew resolveAndLockAll --write-verification-metadata sha256
./gradlew testDebugUnitTest --write-verification-metadata sha256 --max-workers=2
./gradlew test --write-verification-metadata sha256 --max-workers=2
./gradlew :androidApp:assembleRelease :androidTvApp:assembleRelease \
  --write-verification-metadata sha256 --max-workers=2
```

Do not weaken verification or add broad trusted-artifact rules.

- [ ] **Step 6: Verify the Linux AAPT2 checksum independently**

Download the exact classifier from Google's Maven repository and compare:

```bash
curl -fsSLO \
  https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.10.1-12782657/aapt2-8.10.1-12782657-linux.jar
shasum -a 256 aapt2-8.10.1-12782657-linux.jar
```

The resulting literal must equal the value recorded in `gradle/verification-metadata.xml`.

### Task 3: Port and Validate Native Playback Provenance

**Files:**
- Modify: `THIRD_PARTY_NOTICES.md`
- Modify: `android-shared/libs/prairie-dovi-bridge-2.3.1.aar`
- Create: `android-shared/src/native/dovi/Cargo.lock`
- Modify: `android-shared/src/native/dovi/THIRD_PARTY_NOTICES.txt`
- Create: `android-shared/src/native/dovi/provenance.json`
- Modify: `scripts/build-dovi-aar.sh`

**Interfaces:**
- Consumes: pinned `quietvoid/dovi_tool` source commit, source archive checksum,
  Cargo lock checksums, Rust 1.85.0, Android NDK 26.3.11579264, and checked-in
  native outputs.
- Produces: `scripts/build-dovi-aar.sh --verify-provenance`, which verifies source inputs, toolchains, three ABI shared libraries, and the final AAR hash.

- [ ] **Step 1: Port the exact PR 108 native-provenance net diff**

Use the archival paths from commits:

```text
3b090b62fffe8f826d40be40d46c0a0e985b21ef
ccd223766fd4b37e29816973555f77eff62f92f3
```

- [ ] **Step 2: Verify the checked-in artifact without rebuilding**

Run:

```bash
./scripts/build-dovi-aar.sh --verify-provenance
```

Expected: PASS after validating the pinned source archive, toolchains, ABI hashes, and AAR hash. If the exact NDK/Rust toolchain is unavailable, record that as a device/toolchain blocker without weakening the script.

### Task 4: Traceability, Full Verification, Review, and Draft PR

**Files:**
- Create: `docs/notes/2026-07-27-pr108-forward-split-traceability.md`
- Modify: `docs/superpowers/plans/2026-07-27-pr108-slice-a-supply-chain.md`

**Interfaces:**
- Consumes: the final slice-A commit range and PR 108 archival commits.
- Produces: reviewer-facing path/commit mapping, verification evidence, and a draft PR that cannot merge automatically.

- [ ] **Step 1: Record path and commit mapping**

Document that slice A derives from:

```text
5fe404f2a197bf534ee794a7d02373ed7281e238
0b34912681ed4d672e6182f00c3be7806b96e5f2
dd26399745ab1d7fdfe1abb20185e73fc0832d9a
3b090b62fffe8f826d40be40d46c0a0e985b21ef
c47912579c4bae2b7fc6adcb6fc0c8d8bcd1fd1f
445b5f39788657b1b8c58bb6b199f5c5404249a3
```

Also explain that lock and verification files were regenerated for the slice rather than copied wholesale from PR 108, preventing dependencies from later slices being trusted early.

- [ ] **Step 2: Run CI-equivalent verification**

Run:

```bash
./scripts/test-check-build-supply-chain.sh
./scripts/check-build-supply-chain.sh
./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" \
  testDebugUnitTest --max-workers=2
./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" \
  test --max-workers=2
```

Then run release dependency resolution/build tasks needed to prove release locks are complete.

- [ ] **Step 3: Review the committed diff**

Request a code-review agent over `origin/main..HEAD`. Fix every Critical or Important finding and rerun the affected gates.

- [ ] **Step 4: Push and open a draft PR**

Push `split/108-a-supply-chain` and create a draft PR targeting `main`. The body must identify PR 108 as archival, list original commit mappings, include verification commands/results, and state that native Dolby Vision playback still requires Shield hardware validation before release.
