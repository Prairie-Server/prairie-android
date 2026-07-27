#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="2.3.1"
SOURCE_REPOSITORY="https://github.com/quietvoid/dovi_tool"
SOURCE_COMMIT="b25558062e4a56973482ec70133bd7b891320e48"
SOURCE_ARCHIVE_URL="https://codeload.github.com/quietvoid/dovi_tool/tar.gz/$SOURCE_COMMIT"
SOURCE_ARCHIVE_SHA256="595b1c756c9ad4acd5c1ddd851726eb3f0bd1786627c3d374998efc2929209ad"
ROOT_CARGO_LOCK_SHA256="a2d6127f9e4bad347fc98602282eb1cd8f711fc46ddf28723abfedc47ef30eac"
UPSTREAM_CARGO_LOCK_SHA256="e415cb414098495667f07a732633b632393adc1de2e0fa23c38c675b4fa81286"
CARGO_LOCK_SHA256="d6e609177b9cf349734f3972b41dca11c05f42aea1d7fac62a6869cb48f7d276"
RUST_VERSION="1.85.0"
NDK_VERSION="26.3.11579264"
SOURCE_DATE_EPOCH="1755858563"

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
NDK="${ANDROID_NDK_HOME:-$SDK/ndk/$NDK_VERSION}"
HOST_TAG="${ANDROID_NDK_HOST_TAG:-darwin-x86_64}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
WORK_ROOT="${DOVI_WORK_ROOT:-$ROOT/android-shared/build/dovi-aar}"
OUT="${DOVI_OUT:-$ROOT/android-shared/libs/prairie-dovi-bridge-$VERSION.aar}"
JNI_SOURCE="$ROOT/android-shared/src/native/dovi/prairie_dovi.cpp"
PROVENANCE="$ROOT/android-shared/src/native/dovi/provenance.json"
MANIFEST="$ROOT/android-shared/src/native/dovi/AndroidManifest.xml"
NOTICES="$ROOT/android-shared/src/native/dovi/THIRD_PARTY_NOTICES.txt"
BUILD_CARGO_LOCK="$ROOT/android-shared/src/native/dovi/Cargo.lock"
VERIFY_TMP=""

cleanup_verify_tmp() {
  if [[ -n "${VERIFY_TMP:-}" && -d "$VERIFY_TMP" ]]; then
    rm -rf "$VERIFY_TMP"
  fi
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

verify_toolchains() {
  [[ -x "$TOOLCHAIN/bin/clang++" ]] ||
    fail "Android NDK toolchain not found: $TOOLCHAIN"
  local installed_ndk
  installed_ndk="$(awk -F ' = ' '/^Pkg\.Revision = / { print $2 }' "$NDK/source.properties")"
  [[ "$installed_ndk" == "$NDK_VERSION" ]] ||
    fail "Android NDK version $installed_ndk does not match pinned $NDK_VERSION"
  command -v rustc >/dev/null || fail "rustc $RUST_VERSION is required"
  command -v cargo >/dev/null || fail "cargo $RUST_VERSION is required"
  [[ "$(rustc --version)" == "rustc $RUST_VERSION "* ]] ||
    fail "rustc version does not match pinned $RUST_VERSION"
  [[ "$(cargo --version)" == "cargo $RUST_VERSION "* ]] ||
    fail "cargo version does not match pinned $RUST_VERSION"
}

download_and_verify_source() {
  local archive="$1"
  curl -fsSL "$SOURCE_ARCHIVE_URL" -o "$archive"
  [[ "$(sha256_file "$archive")" == "$SOURCE_ARCHIVE_SHA256" ]] ||
    fail "dovi_tool source archive checksum mismatch"

  local root_lock_hash lock_hash
  root_lock_hash="$(
    tar -xOf "$archive" "dovi_tool-$SOURCE_COMMIT/Cargo.lock" |
      shasum -a 256 |
      awk '{print $1}'
  )"
  [[ "$root_lock_hash" == "$ROOT_CARGO_LOCK_SHA256" ]] ||
    fail "dovi_tool root Cargo.lock checksum mismatch"
  lock_hash="$(
    tar -xOf "$archive" "dovi_tool-$SOURCE_COMMIT/dolby_vision/Cargo.lock" |
      shasum -a 256 |
      awk '{print $1}'
  )"
  [[ "$lock_hash" == "$UPSTREAM_CARGO_LOCK_SHA256" ]] ||
    fail "dolby_vision Cargo.lock checksum mismatch"
}

expected_abi_hash() {
  node - "$PROVENANCE" "$1" <<'NODE'
const fs = require("node:fs");
const provenance = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const hash = provenance.outputs?.abis?.[process.argv[3]]?.sha256;
if (typeof hash !== "string") process.exit(1);
process.stdout.write(hash);
NODE
}

verify_provenance_document() {
  node - \
    "$PROVENANCE" \
    "$SOURCE_REPOSITORY" \
    "$VERSION" \
    "$SOURCE_COMMIT" \
    "$SOURCE_ARCHIVE_URL" \
    "$SOURCE_ARCHIVE_SHA256" \
    "$ROOT_CARGO_LOCK_SHA256" \
    "$UPSTREAM_CARGO_LOCK_SHA256" \
    "$CARGO_LOCK_SHA256" \
    "$RUST_VERSION" \
    "$NDK_VERSION" \
    "$SOURCE_DATE_EPOCH" <<'NODE'
const fs = require("node:fs");
const [
  file,
  repository,
  tag,
  commit,
  archiveUrl,
  archiveSha256,
  rootCargoLockSha256,
  upstreamCargoLockSha256,
  cargoLockSha256,
  rust,
  ndk,
  sourceDateEpoch,
] = process.argv.slice(2);
const provenance = JSON.parse(fs.readFileSync(file, "utf8"));
const expected = {
  "schemaVersion": 1,
  "source.repository": repository,
  "source.tag": tag,
  "source.commit": commit,
  "source.archive.url": archiveUrl,
  "source.archive.sha256": archiveSha256,
  "source.rootCargoLockSha256": rootCargoLockSha256,
  "source.upstreamCargoLockSha256": upstreamCargoLockSha256,
  "source.cargoLockSha256": cargoLockSha256,
  "toolchains.rust": rust,
  "toolchains.ndk": ndk,
  "build.sourceDateEpoch": sourceDateEpoch,
};
for (const [path, value] of Object.entries(expected)) {
  const actual = path.split(".").reduce((object, key) => object?.[key], provenance);
  if (actual !== value) {
    throw new Error(`Provenance ${path} does not match the pinned build input`);
  }
}
const expectedAbis = ["arm64-v8a", "armeabi-v7a", "x86_64"];
if (JSON.stringify(Object.keys(provenance.outputs?.abis ?? {}).sort()) !==
    JSON.stringify(expectedAbis)) {
  throw new Error("Provenance must contain exactly the three supported Android ABIs");
}
const hashes = [
  ...expectedAbis.map((abi) => provenance.outputs.abis[abi]?.sha256),
  provenance.outputs?.aar?.sha256,
];
if (hashes.some((hash) => typeof hash !== "string" || !/^[a-f0-9]{64}$/.test(hash))) {
  throw new Error("Provenance output hashes must be lowercase SHA-256 values");
}
NODE
}

verify_provenance() {
  verify_provenance_document
  verify_toolchains
  [[ "$(sha256_file "$BUILD_CARGO_LOCK")" == "$CARGO_LOCK_SHA256" ]] ||
    fail "checked-in dolby_vision build Cargo.lock checksum mismatch"

  VERIFY_TMP="$(mktemp -d "${TMPDIR:-/tmp}/prairie-dovi-verify.XXXXXX")"
  trap cleanup_verify_tmp EXIT
  download_and_verify_source "$VERIFY_TMP/dovi_tool.tar.gz"

  [[ -f "$OUT" ]] || fail "AAR not found: $OUT"
  local abi expected actual
  for abi in arm64-v8a armeabi-v7a x86_64; do
    expected="$(expected_abi_hash "$abi")"
    actual="$(
      unzip -p "$OUT" "jni/$abi/libprairie_dovi.so" |
        shasum -a 256 |
        awk '{print $1}'
    )"
    [[ "$actual" == "$expected" ]] ||
      fail "$abi output hash does not match provenance"
  done

  local expected_aar
  expected_aar="$(
    node -e \
      'process.stdout.write(JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8")).outputs.aar.sha256)' \
      "$PROVENANCE"
  )"
  [[ "$(sha256_file "$OUT")" == "$expected_aar" ]] ||
    fail "AAR hash does not match provenance"
  echo "Verified pinned libdovi source, toolchains, ABI outputs, and AAR provenance."
}

if [[ "${1:-}" == "--verify-provenance" ]]; then
  verify_provenance
  exit
elif [[ $# -ne 0 ]]; then
  fail "usage: $0 [--verify-provenance]"
fi

verify_toolchains
mkdir -p "$WORK_ROOT"
WORK="$(mktemp -d "$WORK_ROOT/run.XXXXXX")"
mkdir -p "$WORK/aar/jni" "$WORK/aar/META-INF"
cp "$MANIFEST" "$WORK/aar/AndroidManifest.xml"
cp "$NOTICES" "$WORK/aar/META-INF/PRAIRIE_DOVI_NOTICES.txt"

ARCHIVE="$WORK/dovi_tool.tar.gz"
download_and_verify_source "$ARCHIVE"
tar -xzf "$ARCHIVE" -C "$WORK"
UPSTREAM="$WORK/dovi_tool-$SOURCE_COMMIT"
[[ "$(sha256_file "$UPSTREAM/Cargo.lock")" == "$ROOT_CARGO_LOCK_SHA256" ]] ||
  fail "extracted dovi_tool root Cargo.lock checksum mismatch"
[[ "$(sha256_file "$UPSTREAM/dolby_vision/Cargo.lock")" == "$UPSTREAM_CARGO_LOCK_SHA256" ]] ||
  fail "extracted dolby_vision Cargo.lock checksum mismatch"
[[ "$(sha256_file "$BUILD_CARGO_LOCK")" == "$CARGO_LOCK_SHA256" ]] ||
  fail "checked-in dolby_vision build Cargo.lock checksum mismatch"
# The checked-in lock is reproducibly derived from the verified upstream child
# lock with these exact commands, in order:
#   cargo update -p bitvec_helpers --precise 4.0.2
#   cargo update -p anyhow --precise 1.0.103
#   cargo update -p crossbeam-epoch --precise 0.9.20
cp "$BUILD_CARGO_LOCK" "$UPSTREAM/dolby_vision/Cargo.lock"

node - "$UPSTREAM/dolby_vision/Cargo.toml" <<'NODE'
const fs = require("node:fs");
const file = process.argv[2];
const original = fs.readFileSync(file, "utf8");
const marker = "[lib]\ndoctest = false";
if (original.split(marker).length !== 2) {
  throw new Error("Unexpected dolby_vision Cargo.toml [lib] configuration");
}
fs.writeFileSync(file, original.replace(marker, `${marker}\ncrate-type = ["staticlib"]`));
NODE

build_abi() {
  local abi="$1" rust_target="$2" clang_target="$3" cargo_linker_variable="$4"
  local rust_linker="$TOOLCHAIN/bin/${clang_target}24-clang"
  local cxx_linker="$TOOLCHAIN/bin/${clang_target}24-clang++"
  [[ -x "$rust_linker" && -x "$cxx_linker" ]] ||
    fail "Android linker not found for $abi"
  rustc --print target-libdir --target "$rust_target" >/dev/null ||
    fail "Rust target is not installed: $rust_target"

  (
    cd "$UPSTREAM/dolby_vision"
    export CARGO_BUILD_JOBS=1
    export CARGO_INCREMENTAL=0
    export SOURCE_DATE_EPOCH
    export RUSTFLAGS="--remap-path-prefix=$UPSTREAM=/usr/src/dovi_tool -C debuginfo=0 -C panic=abort"
    export "$cargo_linker_variable=$rust_linker"
    cargo fetch --locked --target "$rust_target"
    cargo build \
      --locked \
      --offline \
      --release \
      --target "$rust_target" \
      --features capi \
      --lib
  )

  local libdir="$WORK/aar/jni/$abi"
  mkdir -p "$libdir"
  "$cxx_linker" \
    --sysroot="$TOOLCHAIN/sysroot" \
    -std=c++17 -O2 -fPIC -fvisibility=hidden -shared -static-libstdc++ \
    -Wl,--build-id=none -Wl,--no-undefined -Wl,--exclude-libs,ALL \
    -Wl,-z,max-page-size=16384 \
    "$JNI_SOURCE" \
    "$UPSTREAM/dolby_vision/target/$rust_target/release/libdolby_vision.a" \
    -llog -ldl -lm \
    -o "$libdir/libprairie_dovi.so"
  "$TOOLCHAIN/bin/llvm-strip" "$libdir/libprairie_dovi.so"
  if "$TOOLCHAIN/bin/llvm-readelf" -d "$libdir/libprairie_dovi.so" |
    grep -q 'libc++_shared.so'; then
    fail "unexpected libc++_shared.so dependency for $abi"
  fi
}

build_abi \
  arm64-v8a \
  aarch64-linux-android \
  aarch64-linux-android \
  CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER
build_abi \
  armeabi-v7a \
  armv7-linux-androideabi \
  armv7a-linux-androideabi \
  CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER
build_abi \
  x86_64 \
  x86_64-linux-android \
  x86_64-linux-android \
  CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER

mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
TZ=UTC touch -t 198001010000 \
  "$WORK/aar/AndroidManifest.xml" \
  "$WORK/aar/META-INF/PRAIRIE_DOVI_NOTICES.txt" \
  "$WORK/aar/jni/arm64-v8a/libprairie_dovi.so" \
  "$WORK/aar/jni/armeabi-v7a/libprairie_dovi.so" \
  "$WORK/aar/jni/x86_64/libprairie_dovi.so"
(
  cd "$WORK/aar"
  zip -X -q "$OUT" \
    AndroidManifest.xml \
    META-INF/PRAIRIE_DOVI_NOTICES.txt \
    jni/arm64-v8a/libprairie_dovi.so \
    jni/armeabi-v7a/libprairie_dovi.so \
    jni/x86_64/libprairie_dovi.so
)
echo "Built $OUT from quietvoid/dovi_tool@$SOURCE_COMMIT"
echo "Build work directory: $WORK"
