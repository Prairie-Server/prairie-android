package org.siloserver.silo.common.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DiagnosticsStartupRobolectricTest {
    @Test
    fun crashCaptureInstallsBeforeCoordinatorStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<String>()

        DiagnosticsStartup.installCrashCapture(context) { events += "crash" }
        DiagnosticsStartup.startCoordinatorAction { events += "coordinator" }

        assertEquals(listOf("crash", "coordinator"), events)
    }

    @Test
    fun startupFailuresAreContained() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var coordinatorStarted = false

        val crashInstalled = DiagnosticsStartup.installCrashCapture(context) { error("marker storage unavailable") }
        val coordinatorFailed = DiagnosticsStartup.startCoordinatorAction { error("coordinator unavailable") }
        val coordinatorResult = DiagnosticsStartup.startCoordinatorAction { coordinatorStarted = true }

        assertTrue(crashInstalled.isFailure)
        assertTrue(coordinatorFailed.isFailure)
        assertTrue(coordinatorResult.isSuccess)
        assertTrue(coordinatorStarted)
    }
}
