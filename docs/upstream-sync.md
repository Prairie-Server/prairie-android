# Syncing upstream Silo into Prairie Android

Prairie Android is an AGPL fork of `Silo-Server/silo-android`. Upstream syncs
are routine, and they are also the main way Prairie loses work: a conflicted
file resolved wholesale to upstream deletes Prairie's hunks while the Prairie
code behind them keeps compiling. Past losses include the phone and TV Live TV
navigation (screens compiled but nothing reached them), the TV LAN-discovery
server list, the phone Dusk palette, the artwork format cascade, the app update
row, the Quick Connect label and dozens of user-visible "Silo" strings.

## Rules

- Sync on a `sync/upstream-<date>` branch with a real `git merge upstream/main`,
  and land it with **"Create a merge commit"**. Never squash; it drops upstream
  ancestry and the next sync re-conflicts everything.
- **A sync PR merges only with CI green**, including the `Prairie invariants`
  job (`scripts/check-prairie-invariants.sh`). If it fails, restore the code.
  Do not edit the manifest to make it pass.
- Always pass `--repo Prairie-Server/prairie-android` to `gh`; it can resolve to
  the Silo upstream otherwise.

## After resolving conflicts

1. Rebrand fallout: rename new `Silo*` symbols, `silo://` links, `X-Silo-*`
   headers and `silo.*` WebSocket subprotocols to the Prairie names. The
   invariants job fails on user-visible `"…Silo…"` string literals in app code.
2. Hunk audit: list Prairie-only commits with
   `git log --no-merges upstream/main..origin/main` and confirm each one's
   added lines still exist in the merge result, and that new screens are still
   reachable from navigation (a composable nobody navigates to is lost wiring).
3. README and TRADEMARK: keep the Prairie identity and the Silo Media L.L.C.
   attribution; upstream README edits bring the Silo title back.

## When you restore something a sync dropped

Add an anchor for it to `scripts/prairie-invariants.txt` in the same PR: the
path, the minimum number of matching lines (or `absent`), an extended regex on
a stable identifier, and where it came from. Fields are tab-separated so regex
alternation stays usable. That is what stops the next sync from deleting it
again.
