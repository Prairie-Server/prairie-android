# TV player controls and buffer safety design

## Context

The current Android TV player works, but the controls feel too technical and scattered for a living-room app. The scrubber takes first focus, transport controls sit in a split row, options are far from primary playback actions, and the current top HUD hides audio, subtitles, chapters, and quality behind a separate tab mode.

The recent build-buffer-first experiment also exposed a playback safety issue on the Shield: logcat showed an HLS source error caused by an `OutOfMemoryError` while loading chunks. The `Smooth playback` prototype allowed a very deep time buffer and prioritized time over size, which can overrun the TV process heap on high-bitrate streams. Buffer policy must be bounded before UI polish can be evaluated honestly.

## Goals

- Make TV player controls feel premium, predictable, and easy to drive with a D-pad.
- Put play/pause at the center of the mental model.
- Keep the timeline visible and readable without forcing users into timeline-editing mode.
- Move secondary choices into a professional side panel instead of a top tab HUD.
- Add a real playback buffer preference with safe profiles.
- Ensure buffer choices apply to the next playback session, not mid-stream.
- Preserve existing playback features: skip, scrubbing, chapters, audio/subtitle selection, quality/version options, stats, AI subtitle actions, Watch Together controls, and exit behavior.

## Buffer Policy

Introduce three local buffer profiles:

- `Quick start`: starts sooner and uses conservative memory.
- `Balanced`: default for general playback once the setting is exposed broadly.
- `Smooth playback`: starts/resumes more patiently, but remains memory-bounded.

For the immediate TV build, `Smooth playback` should no longer prioritize time over size. It should use a deeper buffer than the original baseline while allowing Media3's byte thresholds to protect the heap. This avoids the Shield OOM observed during HLS playback.

The buffer setting is stored locally through `PlayerSettingsStore`. It applies when `ContinuumPlaybackService` creates the next ExoPlayer instance. If exposed while playback is active later, copy must say it applies after restarting playback.

## Player Overlay

Replace the current split controls with a center-bottom control dock:

`Back | Rewind | Play/Pause | Forward | Audio/Subtitles | More`

The dock should sit inside a focused bottom overlay with clear spacing, large hit targets, and a visible focus ring. Play/pause should receive initial focus when controls open. Rewind and forward stay adjacent to play/pause. Secondary actions stay close enough to discover but visually subordinate.

The timeline sits above the dock and shows:

- current position on the left
- remaining/runtime on the right
- played, buffered, and unplayed ranges
- chapter ticks when available

The title and lightweight metadata move to the top-left overlay so the lower controls can breathe.

## More Panel

Replace the top HUD tab panel with a right-side panel opened from `More`.

Panel sections:

- `Info`: title, current position, runtime, technical summary.
- `Quality`: available versions/fill mode/HDR where applicable.
- `Audio`: audio tracks and audio delay.
- `Subtitles`: subtitle tracks, subtitle delay, subtitle search, AI translate when available.
- `Chapters`: chapter list when present.
- `Watch Together`: room status and leave/close actions when in a room.

Back behavior:

- Back closes the side panel first.
- Back hides controls if the panel is closed.
- Back exits the player only when controls are not open, preserving the existing room close confirmation semantics.

## Testing

- Unit tests for buffer profile values and memory-safety invariants.
- Source-level TV usability tests that enforce center dock, default play/pause focus, visible buffered timeline, and side-panel replacement of the top tab HUD.
- Existing player remote-key tests should continue to pass.
- Build and install on the Shield.
- Live Shield checks: open player, reveal controls after auto-hide, move through dock, open side panel, visit subtitles/audio/chapters, close panel, exit player, and monitor logcat for OOM/source errors.
