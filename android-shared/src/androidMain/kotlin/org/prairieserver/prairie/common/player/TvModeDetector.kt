package org.prairieserver.prairie.common.player

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Determines whether the hosting device is a TV. Checks both the UI mode
 * manager (TV launchers set `UI_MODE_TYPE_TELEVISION`) and the `FEATURE_LEANBACK`
 * system feature (required for Android TV). Either signal is sufficient — some
 * cast-to-TV devices and emulators only set one.
 */
object TvModeDetector {
    fun isTv(context: Context): Boolean {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val uiModeTv = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val leanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return uiModeTv || leanback
    }
}
