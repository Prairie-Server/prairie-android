#!/usr/bin/env bash
# Fails when Prairie-only code listed in scripts/prairie-invariants.txt is gone.
#
# Upstream Silo syncs have resolved files wholesale to upstream and silently
# deleted Prairie wiring (Live TV navigation, LAN discovery, the Dusk palette,
# user-visible rebrand strings) while the Prairie code behind it kept
# compiling. This is a cheap, build-free tripwire that runs on every PR. It
# mirrors prairie-server's scripts/check-prairie-invariants.sh.
#
# Manifest format: one invariant per line, TAB-separated (tabs, not pipes, so
# regex alternation stays usable):
#   <path>  <min>  <extended regex>  <why / where it came from>
# <path> is a file, or a directory searched recursively through *.kt and *.xml.
# <min> is the minimum number of matching lines, or "absent" for a pattern that
# must not match at all (e.g. a user-visible "Silo" string).
#
# Portable bash (3.2+) with POSIX grep -E; no GNU-only flags.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
manifest="${1:-scripts/prairie-invariants.txt}"
tab="$(printf '\t')"
failures=0
checked=0

count_matches() {
  # $1 path, $2 pattern. Prints the number of matching lines, or "error" when
  # grep itself fails (status > 1, e.g. an invalid regex), so a broken
  # "absent" invariant cannot pass by matching nothing.
  out=""
  status=0
  if [ -d "$1" ]; then
    out="$(grep -rhE --include='*.kt' --include='*.xml' -e "$2" "$1")" || status=$?
  else
    out="$(grep -hE -e "$2" "$1")" || status=$?
  fi
  if [ "$status" -gt 1 ]; then
    echo error
  elif [ -z "$out" ]; then
    echo 0
  else
    printf '%s\n' "$out" | wc -l | tr -d ' '
  fi
}

while IFS="$tab" read -r path min pattern why || [ -n "${path:-}" ]; do
  case "$path" in ''|\#*) continue ;; esac
  checked=$((checked + 1))
  if [ -z "${pattern:-}" ] || [ -z "${min:-}" ]; then
    echo "::error file=$manifest::Prairie invariant: malformed line for $path (need 4 tab-separated fields)"
    failures=$((failures + 1))
    continue
  fi
  if [ ! -e "$path" ]; then
    echo "::error file=$path::Prairie invariant: path missing ($why)"
    failures=$((failures + 1))
    continue
  fi
  count="$(count_matches "$path" "$pattern")"
  if [ "$count" = "error" ]; then
    echo "::error file=$path::Prairie invariant: grep failed for /$pattern/ (invalid regex?) ($why)"
    failures=$((failures + 1))
    continue
  fi
  if [ "$min" = "absent" ]; then
    if [ "$count" -ne 0 ]; then
      echo "::error file=$path::Prairie invariant: /$pattern/ must not match but matched $count ($why)"
      if [ -d "$path" ]; then
        grep -rnE --include='*.kt' --include='*.xml' -e "$pattern" "$path" | head -n 10 || true
      fi
      failures=$((failures + 1))
    fi
  elif [ "$count" -lt "$min" ]; then
    echo "::error file=$path::Prairie invariant: /$pattern/ matched $count, need $min ($why)"
    failures=$((failures + 1))
  fi
done < "$manifest"

if [ "$failures" -gt 0 ]; then
  echo "$failures of $checked Prairie invariants failed. If an upstream sync dropped this code, restore it; do not delete the invariant." >&2
  exit 1
fi
echo "All $checked Prairie invariants hold."
