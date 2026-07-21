# Larger TV Subtitle Presets

## Goal

Make the default `Large` subtitle preset readable at normal television viewing distance while preserving the existing preset names and user-selection flow.

## Design

Recalibrate the upper three shared subtitle-size presets:

| Preset | Point size | Android fractional size |
| --- | ---: | ---: |
| Small | 36 | 0.032 |
| Medium | 44 | 0.040 |
| Large | 68 | 0.060 |
| XLarge | 82 | 0.072 |
| XXLarge | 96 | 0.084 |

`Large` remains the default preset. `Small` and `Medium` remain unchanged. The larger presets stay distinct rather than making `Large` an alias for the old `XLarge` while leaving the rest of the scale untouched.

The change applies wherever the shared subtitle appearance model is rendered, including Android TV and Android phone. Existing profiles that selected `Large`, `XLarge`, or `XXLarge` retain their selected preset name and receive its newly calibrated rendered size. No settings migration or preference rewrite is needed.

## Color-control activation fix

The inline text, background, and outline color swatches currently install an explicit `focusable` target immediately before a `clickable` target. D-pad navigation lands on the explicit focus target, but OK activation belongs to the separate clickable target, so pressing OK does not invoke the color callback. The HUD preview, effective appearance flow, player renderer, and persisted setting therefore remain unchanged.

Remove the redundant explicit focus target and let the existing `clickable` modifier own both focus and OK activation, matching the working HUD setting rows and picker options. Keep the shared interaction source so focus styling continues to work. This single component-level correction fixes all three color palettes without changing their layout or selection semantics.

## Testing and verification

- Update shared model tests to assert the new point-size scale and that `Large` remains the default.
- Update Android renderer tests to assert the new fractional sizes.
- Add a TV HUD regression test that prevents color swatches from reintroducing a separate non-activating focus target.
- Run shared, Android-shared, TV, and phone unit tests.
- Build and install the TV debug APK on the Shield.
- On the Shield, select Yellow with the remote and verify that the saved appearance changes to `#ffff00` and live subtitles render yellow.
- Visually verify a two-line subtitle at the recalibrated `Large` size with the existing no-background appearance.

## Scope

This change does not alter subtitle timing, background behavior, font family, position, palette layout, or picker labels.
