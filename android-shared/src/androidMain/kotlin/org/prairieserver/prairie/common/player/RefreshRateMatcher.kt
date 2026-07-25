package org.prairieserver.prairie.common.player

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window
import android.view.WindowManager
import androidx.annotation.MainThread

/**
 * Phone-oriented analogue of [HdrDisplayController]. Drives
 * `Window.attributes.preferredDisplayModeId` on the basis of *refresh rate
 * alone* — resolution is intentionally ignored because phone panels have a
 * fixed native resolution and filtering by it (as [HdrDisplayController] does
 * for HDMI sinks) would either no-op or pick a downgraded pipeline on
 * foldables / external-display scenarios.
 *
 * Safe to attach on all phones — [applyForFrameRate] no-ops on devices that
 * only advertise a single mode. The display-switch handshake is async, so
 * [restore] is best-effort fire-and-forget.
 */
class RefreshRateMatcher {

    companion object {
        private const val TAG = "RefreshRateMatcher"
    }

    private var window: Window? = null
    private var originalModeId: Int = 0
    private var lastAppliedFrameRate: Float = 0f

    @MainThread
    fun attach(activity: Activity) {
        window = activity.window
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
        originalModeId = display?.mode?.modeId ?: 0
    }

    @MainThread
    fun applyForFrameRate(frameRateHz: Float) {
        val w = window ?: return
        if (frameRateHz <= 0f) return
        if (kotlin.math.abs(lastAppliedFrameRate - frameRateHz) < 0.01f) return

        val display = defaultDisplay(w) ?: return
        val modes = display.supportedModes
        if (modes.size <= 1) return

        // Only switch refresh rate within the CURRENT resolution. Some phones expose
        // multiple physical resolutions (e.g. the Pixel's 1344x2992 native vs the
        // 1080x2404 default), and switching resolution triggers an Activity config
        // change/recreate — the branded startup splash re-runs — and resizes the
        // video surface (boxed video). Refresh-rate-only switching is seamless.
        val current = display.mode
        val sameResolution = modes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val best = RefreshRateSelector.pickByRefreshRateAmong(sameResolution, frameRateHz) ?: return
        val params = w.attributes
        if (params.preferredDisplayModeId == best.modeId) {
            lastAppliedFrameRate = frameRateHz
            return
        }
        params.preferredDisplayModeId = best.modeId
        w.attributes = params
        lastAppliedFrameRate = frameRateHz
        Log.i(
            TAG,
            "Applied display mode ${best.modeId} (${best.refreshRate} Hz) for content $frameRateHz Hz",
        )
    }

    @MainThread
    fun restore() {
        val w = window ?: return
        val params = w.attributes
        if (params.preferredDisplayModeId == 0 && lastAppliedFrameRate == 0f) return
        params.preferredDisplayModeId = originalModeId
        w.attributes = params
        lastAppliedFrameRate = 0f
    }

    private fun defaultDisplay(window: Window): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.context.display
        } else {
            @Suppress("DEPRECATION")
            (window.context.getSystemService(WindowManager::class.java))?.defaultDisplay
        }
    }
}
