#!/usr/bin/env bash
# Apply a code-review kit template into a target repository checkout.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: apply-code-review-kit.sh <template> <target-repo-root>

Templates (under .agents/code-review-kit/templates/):
  android | apple | server | plugin | plugin-sdk | plugins-catalog
  push-relay | themes | website | unraid | roku | smarttv

Also copies shared/REVIEW_PROTOCOL.md into the target at
.agents/code-review-kit/shared/REVIEW_PROTOCOL.md when missing.

Example:
  .agents/code-review-kit/scripts/apply-code-review-kit.sh android /path/to/silo-android
EOF
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

TEMPLATE="$1"
TARGET="$2"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KIT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC="$KIT_ROOT/templates/$TEMPLATE"

if [[ ! -d "$SRC" ]]; then
  echo "Unknown template: $TEMPLATE" >&2
  usage >&2
  exit 1
fi

if [[ ! -d "$TARGET/.git" && ! -f "$TARGET/.git" ]]; then
  echo "Target does not look like a git checkout: $TARGET" >&2
  exit 1
fi

mkdir -p "$TARGET/.agents/code-review-kit/shared"
cp "$KIT_ROOT/shared/REVIEW_PROTOCOL.md" \
  "$TARGET/.agents/code-review-kit/shared/REVIEW_PROTOCOL.md"

# Copy template tree (agents, skills, BUGBOT).
while IFS= read -r -d '' file; do
  rel="${file#"$SRC"/}"
  dest="$TARGET/$rel"
  mkdir -p "$(dirname "$dest")"
  cp "$file" "$dest"
  echo "wrote $rel"
done < <(find "$SRC" -type f -print0)

echo "Applied '$TEMPLATE' code-review kit to $TARGET"
