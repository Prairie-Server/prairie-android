# Android Subtitle Aspect Reconciliation and Phone Sizing Design

## Context

PR #127 makes the shared Android subtitle canvas follow the displayed video
area in Fit, Fill, and Stretch modes. On a physical Pixel running the PR head,
changing Fill to Fit reproduced a remaining defect: subtitle cues stayed
vertically displaced and were clipped below the display. Depending on the cue
and dialogue gap, subtitles appeared enabled but absent. Playback, subtitle
selection, cue delivery, and video decoding remained healthy, and subsequent
captures showed the same track rendering normally in Fill mode.

The same device validation also showed that the Android default subtitle size
is too small on a phone. The shared default is `Large`, but Android maps it to
`32 / 720` of the subtitle canvas, below Media3's default fractional size and
smaller than the shared model's nominal 56-point Large value.

## Goals

- Keep subtitle cues fully visible and correctly centered after every supported
  aspect-mode transition.
- Make every phone subtitle-size preset legible at normal handheld viewing
  distance while preserving the relative steps between presets.
- Preserve existing Android TV subtitle sizing.
- Preserve subtitle selection, cue styling, authored positioning, libass/ASS,
  bitmap subtitle, letterbox, and title-safe behavior.

## Non-goals

- Changing subtitle tracks, server subtitle processing, or playback protocols.
- Changing the shared preset names or persisted subtitle appearance schema.
- Changing Android TV's existing font-size scale.
- Reimplementing Media3's aspect-ratio measurement algorithm.
- Adding unbounded frame callbacks, polling, delays, or timeout-based layout
  workarounds.

## Design

### Stable post-layout reconciliation

`SubtitleVideoRectSync` remains the single owner of subtitle-view geometry. An
aspect change may expose old `exo_content_frame` bounds during the immediate
Compose `AndroidView.update` callback. The sync will therefore reconcile from
the actual post-layout content-frame geometry and verify that the rectangle it
applied still matches the current resize mode and content-frame snapshot.

If the snapshot changes during that traversal, one further pre-draw
reconciliation is scheduled. The operation is generation-bound and capped at
two post-layout passes for each explicit sync request. A newer request replaces
the older generation, repeated requests coalesce, and detach/dispose cancels
pending work. No callback remains installed after the rectangle is stable or
the bound is reached. At the bound, the latest measured rectangle remains
applied; the permanent content-frame layout listener still handles any later
real layout change without spinning.

The sync continues using Media3's measured `exo_content_frame` instead of
duplicating its aspect calculations. Geometry remains expressed in the
subtitle view's parent-local coordinate space.

### Phone-only subtitle scaling

Font-size conversion will accept an explicit Android presentation class:
`Phone` or `Television`. Phone uses a 1.25 multiplier over the current
fractions:

| Preset | Phone | Television |
| --- | ---: | ---: |
| Small | 25 / 720 | 20 / 720 |
| Medium | 32.5 / 720 | 26 / 720 |
| Large | 40 / 720 | 32 / 720 |
| XLarge | 50 / 720 | 40 / 720 |
| XXLarge | 60 / 720 | 48 / 720 |

The phone and TV dependency-injection modules construct `SubtitleManager` with
their fixed presentation class. The persisted preset remains unchanged, so an
existing `Large` preference becomes more legible on phone without a migration
and retains its current appearance on TV.

Fractional sizing remains relative to the active subtitle canvas. It therefore
continues to respond naturally to orientation and displayed-video bounds.

## Correctness and lifecycle constraints

- Immediate synchronization remains available for already-stable layouts.
- Reconciliation reads the current player, resize mode, video size, and content
  frame on every pass; it must not apply a rectangle captured for an older
  mode.
- At most one pre-draw listener exists per `PlayerView`.
- Detaching the view removes listeners and prevents late mutation.
- A replaced player cannot receive or influence later reconciliation.
- Existing cue forwarding and libass overlay attachment remain unchanged.

## Testing

Unit and mounted Robolectric coverage will prove:

- Fill to Fit and Stretch to Fit settle to the final parent-local rectangle
  without retaining a cropped top/left margin.
- Fit to Fill and rapid Fit/Fill/Stretch changes use the latest mode.
- A changed content-frame snapshot receives the bounded second pass.
- Stable geometry uses no extra pass, repeated explicit syncs coalesce, and
  detach cancels pending work.
- Every phone preset is exactly 1.25 times its TV fraction.
- The default `Large` preset resolves to `40 / 720` on phone and `32 / 720` on
  TV.
- Phone and TV construction paths select their intended presentation class.

Focused shared, phone, and TV subtitle tests will run first, followed by the
full unit suite and phone/TV release assemblies. Physical validation will use
the Pixel only and exercise Fit, Fill, Stretch, rapid transitions, multi-line
cues, and cue gaps. The Shield will not be installed or modified without
separate authorization.

## Success criteria

- The reproduced Fill-to-Fit cue is fully visible immediately after the sheet
  closes and remains visible across subsequent cues.
- No supported aspect transition leaves stale subtitle margins or dimensions.
- Default phone subtitles are visibly larger while all phone presets remain
  ordered and selectable.
- TV output, persistence, selection, styling, and subtitle formats show no
  regression in automated verification.
