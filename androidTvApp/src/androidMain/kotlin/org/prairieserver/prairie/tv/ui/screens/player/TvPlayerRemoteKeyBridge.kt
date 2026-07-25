package org.prairieserver.prairie.tv.ui.screens.player

import android.view.KeyEvent

/**
 * Activity-level escape hatch for player remote keys when an AndroidView-backed
 * PlayerView has focus and Compose onPreviewKeyEvent does not see the first
 * D-pad event. Registered only while TvPlayerScreen is mounted.
 */
internal object TvPlayerRemoteKeyBridge {
    private var handler: ((KeyEvent) -> Boolean)? = null

    fun install(handler: (KeyEvent) -> Boolean) {
        this.handler = handler
    }

    fun clear(handler: (KeyEvent) -> Boolean) {
        if (this.handler === handler) {
            this.handler = null
        }
    }

    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) == true
}
