package org.prairieserver.prairie.common.diagnostics

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.prairieserver.prairie.model.diagnostics.DiagnosticsAvailabilityStatus
import org.prairieserver.prairie.model.diagnostics.DiagnosticsDeviceSummary
import org.prairieserver.prairie.model.diagnostics.DiagnosticsLogCategory
import org.prairieserver.prairie.model.diagnostics.DiagnosticsPlatform
import org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier
import org.prairieserver.prairie.network.IdentityTransitionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsPrivacyIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() {
        PrairieLog.installSink(null)
    }

    @Test
    fun childThenAdultManualBundleHasNoChildGeneration() = runTest {
        val root = temporaryFolder.newFolder()
        val ring = LogRing()
        val reports = FilePendingReportStore(root, nowMs = { CAPTURED_AT })
        val capture = captureController(root, ring, reports, UnconfinedTestDispatcher(testScheduler))
        val identity = MutableIdentity(CHILD)
        val transitions = DefaultIdentityTransitionBarrier()
        val coordinator = coordinator(
            root = root,
            identity = identity,
            transitions = transitions,
            capture = capture,
            reports = reports,
            actorDispatcher = UnconfinedTestDispatcher(testScheduler),
            scope = backgroundScope,
        )
        coordinator.start()
        coordinator.refresh()

        PrairieLog.i(DiagnosticsLogCategory.OTHER, "PrivacyTest", "child evidence")
        transitions.changing(IdentityTransitionKind.PROFILE_SWITCH) {
            identity.current = ADULT
        }
        coordinator.refresh()
        PrairieLog.i(DiagnosticsLogCategory.OTHER, "PrivacyTest", "adult evidence")

        val reportId = requireNotNull(coordinator.captureNow())
        val report = requireNotNull(reports.load(reportId))
        val bundle = FileDiagnosticsBundleBuilder().build(report, redactionTokens = emptyList())
        val archiveText = GZIPInputStream(ByteArrayInputStream(bundle.bytes)).use { it.readBytes() }.decodeToString()

        assertFalse(archiveText.contains("child evidence"))
        assertTrue(archiveText.contains("adult evidence"))
        assertEquals(ADULT.ownershipGeneration, report.binding.ownershipGeneration)
        assertEquals(ADULT.profileId, report.binding.profileId)
    }

    @Test
    fun jvmMarkerAndExitInfoProduceOneCoordinatorReport() = runTest {
        val root = temporaryFolder.newFolder()
        val reports = FilePendingReportStore(root, nowMs = { CAPTURED_AT + 1_000 })
        val ledger = DiagnosticsRunLedger(root, tokenFactory = { RUN_TOKEN })
        ledger.beginRun(ADULT, CAPTURED_AT - 10_000, "capture-1")
        val marker = JvmCrashMarkerRecord(
            occurredAtEpochMs = CAPTURED_AT,
            threadName = "main",
            threadId = 1,
            throwableType = "java.lang.IllegalStateException",
            stack = "java.lang.IllegalStateException: boom",
            binding = PendingReportBinding(
                serverInstanceId = ADULT.binding.serverInstanceId,
                accountUserId = ADULT.binding.accountUserId,
                profileId = ADULT.profileId,
                ownershipGeneration = ADULT.ownershipGeneration,
            ),
            captureSessionId = "capture-1",
            runToken = RUN_TOKEN,
            foreground = true,
            playbackSessionIds = emptyList(),
            deviceSnapshotJson = DEVICE_JSON,
            logLines = emptyList(),
            logDroppedCount = 0,
            logTornCount = 0,
            logGeneration = ADULT.ownershipGeneration,
            truncated = false,
        )
        val markers = InMemoryMarkers(marker)
        val collector = ExitInfoCollector(
            source = AndroidExitInfoSource { listOf(jvmExit()) },
            ledger = ledger,
            reports = reports,
            markers = markers,
            environment = ENVIRONMENT,
            deviceSnapshotBytes = { DEVICE_JSON.encodeToByteArray() },
            noticeVersion = { ADULT.noticeVersion },
        )
        val transitions = DefaultIdentityTransitionBarrier()
        val coordinator = coordinator(
            root = root,
            identity = MutableIdentity(ADULT),
            transitions = transitions,
            capture = NoOpCapture,
            reports = reports,
            actorDispatcher = UnconfinedTestDispatcher(testScheduler),
            scope = backgroundScope,
            incidentCollector = DiagnosticsIncidentCollector { _, _ -> collector.collect() },
        )

        coordinator.start()
        coordinator.refresh()

        assertEquals(1, reports.list(ADULT.binding).size)
        assertEquals(1, coordinator.state.value.pending.size)
        assertTrue(markers.deleted)
    }

    private fun coordinator(
        root: File,
        identity: MutableIdentity,
        transitions: DefaultIdentityTransitionBarrier,
        capture: DiagnosticsCaptureController,
        reports: PendingReportStore,
        actorDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        scope: kotlinx.coroutines.CoroutineScope,
        incidentCollector: DiagnosticsIncidentCollector = DiagnosticsIncidentCollector { _, _ -> emptyList() },
    ): DiagnosticsCoordinator {
        val settings = DiagnosticsSettingsStore(
            PreferenceDataStoreFactory.create {
                root.resolve("settings-${System.nanoTime()}.preferences_pb")
            },
            DiagnosticsBindingPurger { },
        )
        return DefaultDiagnosticsCoordinator(
            scope = scope,
            actorDispatcher = actorDispatcher,
            identity = identity,
            identityTransitions = transitions,
            settings = settings,
            reports = reports,
            capture = capture,
            uploader = DiagnosticsUploader { DiagnosticsUploadDecision.KeptUnavailable },
            uploadScheduler = DiagnosticsUploadScheduler { },
            incidentCollector = incidentCollector,
        )
    }

    private fun captureController(
        root: File,
        ring: LogRing,
        reports: PendingReportStore,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = FileDiagnosticsCaptureController(
        logBuffer = ring,
        fileLogger = DiagnosticsFileLogger(root, dispatcher),
        reports = reports,
        deviceSnapshots = DeviceSnapshotCollector(StableProbe, nowRfc3339 = { "2026-07-22T00:00:00Z" }),
        deviceSnapshotCache = DeviceSnapshotCache(),
        environment = ENVIRONMENT,
        nowMs = { CAPTURED_AT },
        sessionIdFactory = { "manual-session" },
    )

    private fun jvmExit() = object : AndroidExitInfoRecord {
        override val reason = AndroidExitReason.JVM_CRASH
        override val timestampMs = CAPTURED_AT + 100
        override val pid = 123
        override val processName = "org.prairieserver.prairie"
        override val status = 0
        override val processStateSummary = RUN_TOKEN.encodeToByteArray()
        override fun trace(maxBytes: Int): ByteArray? = null
    }

    private class MutableIdentity(var current: DiagnosticsCaptureContext?) : DiagnosticsIdentityResolver {
        override suspend fun resolve(requirePersistentCapture: Boolean): DiagnosticsCaptureContext? = current
    }

    private class InMemoryMarkers(private val marker: JvmCrashMarkerRecord) : JvmCrashMarkerSource {
        var deleted = false
        override fun records(): List<JvmCrashMarkerRecord> = if (deleted) emptyList() else listOf(marker)
        override fun delete(marker: JvmCrashMarkerRecord) {
            deleted = true
        }
    }

    private object NoOpCapture : DiagnosticsCaptureController {
        override fun closeGate() = Unit
        override suspend fun start(context: DiagnosticsCaptureContext) =
            ActiveDiagnosticsCapture(1, context.identityKey, CAPTURED_AT)
        override suspend fun stop(active: ActiveDiagnosticsCapture, context: DiagnosticsCaptureContext): PendingReport? = null
        override suspend fun cancel(active: ActiveDiagnosticsCapture) = Unit
        override suspend fun captureNow(context: DiagnosticsCaptureContext): PendingReport? = null
        override suspend fun purge(binding: DiagnosticsBinding) = Unit
    }

    private object StableProbe : DiagnosticsDeviceProbe {
        override fun identity() = DiagnosticsIdentitySnapshot(
            manufacturer = "Google",
            model = "Pixel",
            device = "pixel",
            osRelease = "16",
            sdkInt = 36,
            formFactor = "phone",
            buildFingerprintHash = "0".repeat(32),
        )
        override fun display(): DiagnosticsDisplaySnapshot? = null
        override fun audio(): DiagnosticsAudioSnapshot? = null
        override fun codecs(): List<DiagnosticsCodecSnapshot>? = null
        override fun network(): DiagnosticsNetworkSnapshot? = null
    }

    private companion object {
        const val CAPTURED_AT = 1_700_000_000_000L
        const val RUN_TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val DEVICE_JSON = "{\"captured_at\":\"2026-07-22T00:00:00Z\"}"
        val ENVIRONMENT = ExitReportEnvironment(
            appVersion = "1.0",
            appBuild = "1",
            platform = DiagnosticsPlatform.ANDROID,
            osVersion = "Android 36",
            deviceSummary = DiagnosticsDeviceSummary("Google", "Pixel", "Android 36", "phone"),
        )
        val CHILD = DiagnosticsCaptureContext(
            binding = DiagnosticsBinding("server-1", "user-1"),
            profileId = "child",
            profileEligible = false,
            noticeVersion = 2,
            status = DiagnosticsAvailabilityStatus.AVAILABLE,
            ownershipGeneration = 1,
        )
        val ADULT = CHILD.copy(profileId = "adult", profileEligible = true, ownershipGeneration = 2)
    }
}
