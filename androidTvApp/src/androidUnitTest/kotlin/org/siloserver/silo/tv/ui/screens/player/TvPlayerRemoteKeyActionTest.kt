package org.siloserver.silo.tv.ui.screens.player

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvPlayerRemoteKeyActionTest {

    @Test
    fun mediaPlayPauseKeysTogglePlaybackOnKeyDown() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.PlayPause,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 0,
                ),
            )
        }
    }

    @Test
    fun mediaPlayPauseKeyUpIsConsumedWithoutTogglingPlayback() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.ConsumeOnly,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_UP,
                    repeatCount = 0,
                ),
            )
        }
    }

    @Test
    fun repeatedMediaKeyDownIsConsumedWithoutTogglingPlayback() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.ConsumeOnly,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_DOWN,
                    repeatCount = 1,
                ),
            )
        }
    }

    @Test
    fun downMovesFocusToTransportAndMenuAndSettingsOpenHudFromIdleOverlay() {
        assertEquals(
            TvPlayerRemoteKeyAction.FocusTransport,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        listOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS).forEach { keyCode ->
            assertEquals(
                TvPlayerRemoteKeyAction.OpenHud,
                tvPlayerRemoteKeyAction(
                    keyCode = keyCode,
                    action = KeyEvent.ACTION_UP,
                    repeatCount = 0,
                ),
            )
        }
    }

    @Test
    fun leftAndRightSeekDuringPlaybackInsteadOfOpeningChrome() {
        assertEquals(
            TvPlayerRemoteKeyAction.SkipBack,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.SkipForward,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.ConsumeOnly,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.ConsumeOnly,
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
            ),
        )
    }

    @Test
    fun visibleIdleOverlayLeftAndRightUseSkipActions() {
        assertEquals(
            TvPlayerRemoteKeyAction.SkipBack,
            tvPlayerIdleOverlayRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.SkipForward,
            tvPlayerIdleOverlayRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertEquals(
            TvPlayerRemoteKeyAction.ConsumeOnly,
            tvPlayerIdleOverlayRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun nonMatchingActionsAndUnhandledKeysFallThrough() {
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_MENU,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertNull(
            tvPlayerRemoteKeyAction(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
    }
}
