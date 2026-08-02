# Android TV Auth IME Relocation Design

**Date:** 2026-08-02  
**Status:** Approved for implementation planning  
**Baseline:** `Silo-Server/silo-android` `main` at `981c7a42`

## Problem

On Nvidia Shield, the platform keyboard can cover or visually crowd the focused server-address field even though the TV activity uses `adjustResize`, edge-to-edge IME inset dispatch, `imePadding()`, and `BringIntoViewRequester`. The current relocation request runs when focus changes, before the keyboard has finished opening and resizing the Compose viewport. The server screen also nests a scrollable manual-entry card inside a scrollable page, so relocation can be consumed by the wrong container.

The same timing and scroll-ownership problem exists on the username, password, invite, and profile fields used by the remaining TV authentication forms. A server-screen-only correction would leave equivalent failures on the next screen.

## Chosen Design

Create one shared Android TV authentication-form IME relocation behavior and apply it to:

- server connection;
- login;
- initial server setup;
- signup; and
- profile creation/editing forms that use the TV soft keyboard.

Each screen keeps its current keyboard-closed composition, styling, focus order, and D-pad behavior. The correction changes only scrolling while the IME is visible.

### Single scroll owner

Each affected screen has one outer vertical scroll container responsible for moving content around the IME. A child card or form must not own a competing vertical scroll container for the same fields. Fixed visual card sizing may remain where it does not clip content, but relocation always propagates to the outer screen container.

### Focused-field context

Each editable field associates a `BringIntoViewRequester` with a small context wrapper containing its visible label and field. The requested region includes 32dp of bottom clearance. This prevents the field from being positioned flush against the keyboard and keeps enough context visible to identify username, password, server address, or another active value.

The requested region is intentionally local. The screen does not attempt to keep its full hero, progress indicator, cards, or submit controls above the keyboard.

### IME-aware relocation timing

A shared composable helper observes both field focus and `WindowInsets.ime` visibility/size. It requests relocation when:

1. a field gains focus while the IME is already visible; or
2. the IME becomes visible or changes size while that field is focused.

The helper waits until the resized layout has been measured before requesting relocation. This makes the result depend on current keyboard geometry rather than the pre-keyboard viewport. Repeated equivalent inset updates are coalesced so they do not produce visible scroll jitter.

When the keyboard closes, the screen returns to its normal top position. No alternate compact screen or hidden content state is introduced.

## Component Boundaries

- The shared helper owns IME/focus observation and post-layout relocation only.
- Each screen owns its scroll state, field labels, keyboard actions, validation, and focus traversal.
- Each field or field wrapper owns its requester and declares the contextual region to reveal.
- `MainTvActivity` retains its current edge-to-edge inset configuration, and the manifest retains `adjustResize`.

The helper has no dependency on authentication view models or field values and can be tested independently from server and account logic.

## State and Error Handling

- Relocation failures caused by disposal or rapid navigation are ignored; they must not affect authentication state.
- A field disabled during submission does not trigger new relocation work.
- Validation errors remain in the existing form and may scroll normally when focus moves to their associated field.
- Hardware-keyboard use leaves the normal layout unchanged because the software IME inset is not visible.
- Keyboard dismissal, Back navigation, and screen transitions cancel pending relocation work through Compose lifecycle cancellation.

## Testing

### Automated

- Test the shared visibility/relocation trigger policy for focus-before-IME, IME-before-focus, IME size changes, keyboard closure, duplicate inset updates, and disposal.
- Add source or Compose tests confirming each affected TV auth form uses the shared helper and a single outer vertical scroll owner.
- Keep existing focus-order, validation, and authentication tests green.
- Build the Android TV debug APK.

### Shield validation

At the Shield's 4K output resolution with the installed TV keyboard:

- Open the server-address keyboard and confirm the label and complete field remain visible with clearance.
- Continue to login and verify both username and password while moving focus with the D-pad.
- Exercise setup, signup, and profile forms when reachable.
- Confirm closing the keyboard restores the original screen composition.
- Confirm no field jumps or oscillates while typing and no app screen is redesigned.

## Out of Scope

- A compact or dedicated text-entry screen.
- Authentication visual redesign.
- Phone-client behavior.
- Replacing the platform keyboard.
- Server, API, validation, or credential-storage changes.
