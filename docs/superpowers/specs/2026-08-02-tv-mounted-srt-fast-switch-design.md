# TV Mounted SRT Fast Switching Design

## Goal

Make an ordinary Android TV switch to an already-mounted SRT subtitle track complete without interrupting or rebuffering video, while retaining the existing transactional server-replan path whenever local selection cannot safely satisfy the request.

## Current Behavior and Root Cause

At playback mount, Silo converts every mountable sidecar in `subtitleUrls` into a Media3 `SubtitleConfiguration` and attaches the full list to the active `MediaItem`. The selected SRT therefore commonly already exists in `Player.currentTracks`.

The subtitle transaction adapter nevertheless limits its local fast path to `Embedded`, `Downloaded`, and `LocalMedia3` identities. A mounted `ServerSidecar` skips that path, stages a new server playback request, adopts the replacement session, changes `transportMountNonce`, and causes the screen to call `setMediaItem` and `prepare` again. Preserving position makes the switch correct, but re-preparing the video produces the visible buffering delay.

## Considered Approaches

1. **Resolve any locally selectable identity before replanning (chosen).** Ask the existing mounted-track resolver whether the exact requested identity is present, then use the established local mount-confirmation transaction. This reuses typed identity matching and preserves the server fallback.
2. **Treat every `ServerSidecar` as local.** This is simpler but unsafe: catalog rows can be absent from the current Media3 snapshot, unsupported, or require a different server rendering route.
3. **Rebuild the MediaItem locally without a server request.** This avoids session staging but still calls `setMediaItem` and `prepare`, so it retains the user-visible video interruption.

## Approved Behavior

- Selecting a `ServerSidecar` that resolves exactly against the current mounted Media3 subtitle tracks uses the local transaction path.
- The player changes only the text-track override. The active video `MediaItem`, stream URL, playback session, position, and buffer remain untouched.
- The selection is committed only after the normal player-boundary confirmation reports that the requested track became selected.
- Persistence and committed/pending UI state continue to use the existing subtitle transaction machinery.
- Selecting Off remains local and does not reprepare playback.
- Embedded, downloaded, and local Media3 identities retain their current behavior.
- A sidecar that is not currently mounted, cannot be resolved exactly, requires server burn-in or conversion, or is combined with an audio, quality, or output-route mutation continues through the existing server-replan path.
- If local selection fails or times out, existing rollback and error behavior remains authoritative; this change does not silently commit an unconfirmed selection.

## Architecture and Data Flow

The transaction adapter's local-selection eligibility will be based on two facts:

1. The identity requires confirmation at the player boundary rather than server burn-in.
2. The injected `isLocallyMountable(identity)` resolver finds the requested typed identity in the current Media3 snapshot.

`ServerSidecar` becomes eligible for that check. The adapter then calls the existing `beginLocalSelection` flow, publishes the pending mount identity, and waits for the existing remount/reselection observer to resolve and select the mounted track. The backend applies a Media3 text-track override; it does not invoke the session manager or media mounter.

If the resolver returns false, the adapter follows its unchanged staged-request path. This keeps the optimization capability-driven rather than assuming that every server sidecar is locally usable.

## Error Handling and State

The current local-mount deadline, selection acknowledgement, rollback, persistence, and supersession rules remain unchanged. The optimization does not create a second transaction mechanism. It only allows an already-mounted `ServerSidecar` to enter the mechanism currently used by other locally selectable subtitle identities.

The exact typed resolver remains the guard against choosing a same-language or same-label track with a different identity.

## Testing

- Add a regression test proving that a mounted `ServerSidecar` enters local mount confirmation and does not stage a server request.
- Add a fallback test proving that an unmounted `ServerSidecar` still stages the server request.
- Retain coverage that audio, quality, and output-route mutations prevent the local shortcut.
- Run focused transaction and TV player tests, then the full Gradle test suite and TV debug assembly.
- On the Shield, verify that switching between already-mounted SRT tracks does not enter player buffering and does not replace the current media item.

## Out of Scope

- Preloading subtitle files that are absent from the active `MediaItem`.
- Avoiding a reprepare after downloading or generating a brand-new subtitle.
- Changing server burn-in or subtitle conversion decisions.
- Changing phone playback behavior.
- Refactoring the broader subtitle transaction architecture.
