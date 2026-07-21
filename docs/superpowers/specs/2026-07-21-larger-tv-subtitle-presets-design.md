# Larger TV Subtitle Presets

## Goal

Make the default `Large` subtitle preset readable at normal television viewing distance while preserving the existing preset names and user-selection flow.

## Design

Recalibrate the upper three shared subtitle-size presets:

| Preset | Point size | Android fractional size |
| --- | ---: | ---: |
| Small | 36 | 0.034 |
| Medium | 44 | 0.042 |
| Large | 68 | 0.060 |
| XLarge | 82 | 0.072 |
| XXLarge | 96 | 0.084 |

`Large` remains the default preset. `Small` and `Medium` remain unchanged. The larger presets stay distinct rather than making `Large` an alias for the old `XLarge` while leaving the rest of the scale untouched.

The change applies wherever the shared subtitle appearance model is rendered, including Android TV and Android phone. Existing profiles that selected `Large`, `XLarge`, or `XXLarge` retain their selected preset name and receive its newly calibrated rendered size. No settings migration or preference rewrite is needed.

## Testing and verification

- Update shared model tests to assert the new point-size scale and that `Large` remains the default.
- Update Android renderer tests to assert the new fractional sizes.
- Run shared, Android-shared, TV, and phone unit tests.
- Build and install the TV debug APK on the Shield.
- Visually verify a two-line subtitle at the `Large` default with the existing no-background appearance.

## Scope

This change does not alter subtitle timing, styling, background behavior, font family, position, or picker labels.
