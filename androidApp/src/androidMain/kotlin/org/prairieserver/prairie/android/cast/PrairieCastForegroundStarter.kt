package org.prairieserver.prairie.android.cast

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Drives the PrairieCast controller off the app foreground lifecycle, mirroring
 * prairie-apple's scenePhase wiring: on foreground either validate a live
 * session with an immediate ping or probe for a silent auto-resume of the
 * last-controlled TV; on background drop unengaged auto-resumed sessions.
 */
class PrairieCastForegroundStarter(
    private val controller: PrairieCastController,
) : DefaultLifecycleObserver {
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        controller.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        controller.onAppBackground()
    }
}
