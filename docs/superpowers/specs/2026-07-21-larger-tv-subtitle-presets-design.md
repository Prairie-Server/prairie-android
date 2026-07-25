# TV Subtitle Preset Calibration

## Goal

Keep the subtitle scale comfortable at normal television viewing distance while preserving the existing preset names and user-selection flow, and make the default presentation match the approved Shield reference.

## Design

Use the standard shared subtitle-size presets after Shield validation showed that the enlarged upper presets were excessive:

| Preset | Point size | Android fractional size |
| --- | ---: | ---: |
| Small | 36 | 0.032 |
| Medium | 44 | 0.040 |
| Large | 56 | 0.050 |
| XLarge | 68 | 0.060 |
| XXLarge | 82 | 0.072 |

`Large` remains the default preset. All five presets retain the established standard scale.

The scale applies wherever the shared subtitle appearance model is rendered, including Android TV and Android phone. Existing profiles retain their selected preset name. No settings migration or preference rewrite is needed.

## Default appearance calibration

The default appearance is white sans-serif text at the `Large` preset, with a subtle black outline, no background box, and bottom-center placement. Android renders bottom subtitles with a 9% safe margin. Selecting `Drop Shadow` overrides the default outline in both playback and the TV settings preview; selecting `Outline` or enabling the outline control restores an outline.

These values apply to new/default appearances and to fields absent from older serialized settings. Explicitly saved user appearance choices remain authoritative.

## Color-control activation fix

The inline text, background, and outline color swatches currently install an explicit `focusable` target immediately before a `clickable` target. D-pad navigation lands on the explicit focus target, but OK activation belongs to the separate clickable target, so pressing OK does not invoke the color callback. The HUD preview, effective appearance flow, player renderer, and persisted setting therefore remain unchanged.

Remove the redundant explicit focus target and let the existing `clickable` modifier own both focus and OK activation, matching the working HUD setting rows and picker options. Keep the shared interaction source so focus styling continues to work. This single component-level correction fixes all three color palettes without changing their layout or selection semantics.

## Testing and verification

- Update shared model tests to assert the standard point-size scale and that `Large` remains the default.
- Update Android renderer tests to assert the standard fractional sizes.
- Assert the default white/no-box/black-outline appearance and 9% bottom safe margin.
- Assert that the TV preview gives `Drop Shadow` the same precedence as playback.
- Add a TV HUD regression test that prevents color swatches from reintroducing a separate non-activating focus target.
- Run shared, Android-shared, TV, and phone unit tests.
- Build both TV debug and release APKs locally.
- Defer on-device visual validation until explicitly requested; do not install or launch Prairie on the Shield as part of this calibration.

## Scope

This change does not alter subtitle timing, font choices, palette layout, or picker labels. It changes only default background/outline behavior, bottom safe margin, and preview precedence; explicitly saved user choices are preserved.
