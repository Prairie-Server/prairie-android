package org.siloserver.silo.common.diagnostics

import android.content.Context
import android.util.Log

/**
 * Fail-contained Android diagnostics startup entry points. Crash capture stays
 * pre-Koin; coordinator startup stays post-Koin, and neither can make the app
 * fail to launch.
 */
object DiagnosticsStartup {
    fun installCrashCapture(context: Context): Result<Unit> =
        installCrashCapture(context, CrashCapture::install)

    internal fun installCrashCapture(
        context: Context,
        installer: (Context) -> Unit,
    ): Result<Unit> = runCatching { installer(context) }
        .onFailure { Log.w(TAG, "Crash capture init failed", it) }

    fun startCoordinator(provider: () -> DiagnosticsCoordinator): Result<Unit> =
        startCoordinatorAction { provider().start() }

    internal fun startCoordinatorAction(action: () -> Unit): Result<Unit> =
        runCatching(action)
            .onFailure { Log.w(TAG, "Diagnostics coordinator init failed", it) }

    private const val TAG = "DiagnosticsStartup"
}
