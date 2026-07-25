package org.prairieserver.prairie.tv.ui.screens.player

import android.view.KeyEvent

internal enum class TvPlayerRemoteKeyAction {
    PlayPause,
    FocusTransport,
    SkipBack,
    SkipForward,
    OpenHud,
    // Unconsumed media-key events reach the system media-key fallback, which
    // toggles the Media3 session a second time — so both the UP half and any
    // auto-repeat DOWN events must be swallowed here without acting on them.
    ConsumeOnly,
}

internal fun tvPlayerRemoteKeyAction(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    // Left/Right = seek is only safe while no focus-owning surface (transport
    // overlay, HUD, Up Next) is on screen. When one is, Left/Right must fall
    // through so Compose focus navigation keeps moving the selection.
    dpadHorizontalSeek: Boolean = true,
): TvPlayerRemoteKeyAction? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    -> if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
        TvPlayerRemoteKeyAction.PlayPause
    } else {
        TvPlayerRemoteKeyAction.ConsumeOnly
    }

    KeyEvent.KEYCODE_DPAD_DOWN ->
        when {
            action != KeyEvent.ACTION_DOWN -> null
            // tvOS parity (QA 2026-07-08): while playing with nothing on
            // screen, D-pad-down opens the hover menu (HUD). When a
            // focus-owning surface is up (dpadHorizontalSeek == false), Down
            // keeps moving focus into the transport instead.
            dpadHorizontalSeek -> TvPlayerRemoteKeyAction.OpenHud
            else -> TvPlayerRemoteKeyAction.FocusTransport
        }

    KeyEvent.KEYCODE_DPAD_LEFT ->
        when {
            !dpadHorizontalSeek -> null
            action == KeyEvent.ACTION_DOWN && repeatCount == 0 -> TvPlayerRemoteKeyAction.SkipBack
            else -> TvPlayerRemoteKeyAction.ConsumeOnly
        }

    KeyEvent.KEYCODE_DPAD_RIGHT ->
        when {
            !dpadHorizontalSeek -> null
            action == KeyEvent.ACTION_DOWN && repeatCount == 0 -> TvPlayerRemoteKeyAction.SkipForward
            else -> TvPlayerRemoteKeyAction.ConsumeOnly
        }

    KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_SETTINGS,
    -> if (action == KeyEvent.ACTION_UP) TvPlayerRemoteKeyAction.OpenHud else null

    else -> null
}

// The idle overlay is a focus-owning surface: the scrubber handles its own
// Left/Right skips when focused, and the transport cluster needs Left/Right
// for moving between buttons — so horizontal seek mapping stays off here.
internal fun tvPlayerIdleOverlayRemoteKeyAction(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
): TvPlayerRemoteKeyAction? =
    tvPlayerRemoteKeyAction(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        dpadHorizontalSeek = false,
    )
