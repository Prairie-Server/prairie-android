# PR 108 Forward Split Traceability

## Slice A: Build, dependency verification, and native provenance

Branch: `split/108-a-supply-chain`

Upstream base:

```text
da26f081efdd5d5ce069db937de105a30169ca16
```

Archival integration reference:

```text
PR 108
445b5f39788657b1b8c58bb6b199f5c5404249a3
```

This branch intentionally reproduces the **net behavior** of the build and
provenance portion of PR 108 rather than replaying its integration history.
The original range is not safely cherry-pickable as an isolated feature: its
lock and verification files contain dependencies introduced by later security,
reader, player, subtitle, and Watch Together changes.

### Original commits mapped into this slice

| PR 108 commit | Original purpose | Slice-A disposition |
| --- | --- | --- |
| `5fe404f2a197bf534ee794a7d02373ed7281e238` | Lock all Gradle configurations, add verification metadata, pin the wrapper and Actions | Policy/configuration ported; locks and metadata regenerated from this slice |
| `0b34912681ed4d672e6182f00c3be7806b96e5f2` | Harden and self-test the supply-chain policy | Ported, then extended with Linux AAPT2 coverage |
| `dd26399745ab1d7fdfe1abb20185e73fc0832d9a` | Refresh dependency state after integrating upstream | Not copied; replaced by fresh resolution from `da26f081` |
| `3b090b62fffe8f826d40be40d46c0a0e985b21ef` | Pin Dolby Vision native source, toolchains, locks, ABI outputs, and AAR | Ported as the native-provenance commit |
| `ccd223766fd4b37e29816973555f77eff62f92f3` | Record the native rebuild's hardware-validation caveat | Preserved here and in the draft PR verification notes |
| `f3d62d19b6d589da654489e2f67bb6774e4c669f` | Record audit of regenerated dependency metadata | Superseded: this slice has a smaller freshly generated graph |
| `c47912579c4bae2b7fc6adcb6fc0c8d8bcd1fd1f` | Add release configurations to verification metadata | Covered by `resolveAndLockAll` plus the full `test` and release build gates |
| `445b5f39788657b1b8c58bb6b199f5c5404249a3` | Add the missing Guava parent POM seen on PR 108 CI | Not needed: that dependency belongs to a later slice and is absent here |

### Forward-split commits

```text
74308e48 build(android): enforce verified dependency state
39379263 build(android): pin native playback provenance
c8a08e18 docs(android): map PR 108 supply-chain split
e4ec5880 build(android): verify release tool dependencies
```

The patch IDs are intentionally different from the original commits because
the first commit regenerates dependency state against a different, narrower
source tree. Reviewer traceability is therefore path- and purpose-based rather
than claiming false one-to-one patch identity.

`e4ec5880` adds the 26 release/lint tool artifacts found by the first full
release build; each checksum matches the independently generated PR 108
metadata. This documentation correction follows those four functional and
traceability commits and does not change their patch content.

### Linux AAPT2 correction

PR 108 recorded only the macOS classifier for
`com.android.tools.build:aapt2:8.10.1-12782657`. GitHub Actions uses the Linux
classifier, so dependency verification failed before tests could compile.

Slice A adds:

```text
aapt2-8.10.1-12782657-linux.jar
sha256 52f864b7fd20a9ff09fc3db96162537a63c5b38ecc1c2549db4b491c6a517ff0
```

The artifact and checksum were fetched directly from Google's Maven repository:

```text
https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.10.1-12782657/aapt2-8.10.1-12782657-linux.jar
```

The policy self-test now proves that metadata with only the macOS classifier is
rejected. This catches the production failure at review time even when metadata
is generated on macOS.

### Validation record

- Upstream baseline: `testDebugUnitTest` passed before edits.
- TDD red: the policy self-test failed when the policy executable was absent.
- TDD green: the imported policy passed its original fixture suite.
- Regression red: metadata with only macOS AAPT2 was incorrectly accepted.
- Regression green: the policy rejected missing Linux AAPT2 and passed after
  the official Linux artifact checksum was recorded.
- Slice dependency graph: `resolveAndLockAll --write-locks
  --write-verification-metadata sha256` completed.
- Debug and release unit variants: `test --write-verification-metadata sha256`
  completed successfully.
- CI-equivalent debug unit suite: `testDebugUnitTest` completed successfully
  with verification enabled and no write flag.
- Release regression red: the first verification-enabled release build rejected
  26 Android lint/release-tool artifacts not traversed by `resolveAndLockAll`
  or `test`.
- Release regression green: `androidApp:assembleRelease` and
  `androidTvApp:assembleRelease` completed after recording those artifacts.
  Every newly recorded checksum exactly matched the independently generated
  metadata in archival PR 108.
- GitHub Actions regression red: the first clean Linux runner resolved three
  build-classpath artifacts injected by `gradle/actions/setup-gradle` that are
  absent from an uninjected local build: Guava parent `33.3.1-jre`, JUnit BOM
  `5.10.2.module`, and coroutines BOM `1.8.0`.
- GitHub Actions correction: all three SHA-256 values were independently
  downloaded from Maven Central and matched archival PR 108 before being
  recorded. The rerun is the clean-runner GREEN gate.
- The rerun exposed the Android-flavor Guava parent on the libass compile
  classpath. Its `33.3.1-android` POM was independently downloaded from Maven
  Central, verified as SHA-256
  `6e11986ea7250b51f847157e2dc937f32a306804dfce0007a5e81ddb9b95c579`,
  and recorded as the second clean-runner correction.
- The next clean runner reached `guava-parent:33.4.8-jre` only from the
  Android shared test runtime. Its Maven Central SHA-256
  `a03c5199a1be14443df7fd40ba8673b1c6aae04deef6688ce7d573ea489e3a20`
  was independently verified and recorded.
- Native provenance: `scripts/build-dovi-aar.sh --verify-provenance` verified
  the pinned source archive, Rust and NDK toolchains, three ABI outputs, and
  final AAR hash.

### Remaining release gate

The native artifact has not been played on an NVIDIA Shield in this slice.
Before a release, play a Dolby Vision title and verify Profile 7 conversion,
HDR fallback, seeking, and sustained playback. This is a hardware acceptance
gate, not a reason to weaken or defer the reproducibility checks.
