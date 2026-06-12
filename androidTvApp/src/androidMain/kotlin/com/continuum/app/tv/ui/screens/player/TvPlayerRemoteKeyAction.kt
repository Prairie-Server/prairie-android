package com.continuum.app.tv.ui.screens.player

import android.view.KeyEvent

internal enum class TvPlayerRemoteKeyAction {
    PlayPause,
    FocusTransport,
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
        if (action == KeyEvent.ACTION_DOWN) TvPlayerRemoteKeyAction.FocusTransport else null

    KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_SETTINGS,
    -> if (action == KeyEvent.ACTION_UP) TvPlayerRemoteKeyAction.OpenHud else null

    else -> null
}
