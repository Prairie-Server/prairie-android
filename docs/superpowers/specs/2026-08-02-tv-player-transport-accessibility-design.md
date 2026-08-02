# TV Player Transport Accessibility Design

## Goal

Make the Android TV playback transport the first destination of D-pad Down and improve control legibility without changing the transport row's visual language.

## Current Problems

- With playback controls hidden, D-pad Down opens the information/settings HUD because `TvPlayerRemoteKeyAction` deliberately maps that input to `OpenHud`.
- Transport buttons are 33dp circles. Secondary glyphs, including Closed Captioning, are only 12.5dp; Play/Pause is 15dp. On a television these controls are difficult to distinguish.

## Interaction Design

- D-pad Down while playback controls are hidden reveals the idle overlay and focuses Play/Pause.
- D-pad Down while the idle overlay is visible continues routing focus into the Play/Pause control.
- D-pad Down from the scrubber continues moving focus to Play/Pause.
- The remote Menu and Settings keys continue opening the information/settings HUD.
- Selecting the captions button continues opening the existing quick subtitle picker.
- Left/Right transport navigation, Up-to-scrubber navigation, playback actions, auto-hide behavior, and Back behavior remain unchanged.

## Visual Design

- Preserve circular controls, grouping, icon-only presentation, borders, colors, and white/black focus inversion.
- Increase every transport button from 33dp to 44dp.
- Increase Play/Pause from 15dp to 22dp.
- Increase all secondary glyphs from 12.5dp to 20dp.
- Keep the existing 5dp inter-button gap and left/right group layout. The row has sufficient horizontal room; changing the gap or adding labels would add unnecessary visual impact.

## Implementation Boundaries

- Change hidden-overlay Down mapping at the shared remote-key action boundary so the key-dispatch bridge and Compose overlay agree.
- Keep the transport dimensions centralized in `TvPlayerTransportCluster.kt` rather than special-casing captions.
- Do not alter the HUD, subtitle picker, subtitle-selection behavior, or player state model.

## Testing

- Update remote-key unit tests to require `FocusTransport` for D-pad Down regardless of whether the idle overlay is already visible.
- Retain coverage proving Menu and Settings keys open the HUD.
- Add a small, behavior-oriented sizing policy that tests the primary and secondary transport dimensions used by the composable.
- Run the Android TV unit suite and assemble the debug APK.
- Install the ARM64 debug APK on the Shield without launching it.

## Success Criteria

- From unobstructed playback, one D-pad Down press reveals the controls with Play/Pause focused.
- Captions, Settings, Close, skip, and Play/Pause glyphs are visually distinguishable at normal TV viewing distance.
- No labels or additional chrome appear.
- Existing transport actions and focus movement remain functional.
