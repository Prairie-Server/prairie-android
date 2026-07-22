package org.siloserver.silo.model.diagnostics

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.siloserver.silo.network.SiloJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticsContractTest {
    @Test
    fun canonicalValidManifestFixturesDecodeValidateAndRoundTrip() {
        val fixtures = listOf(
            "fixtures/valid/android-manual-no-profile.json",
            "fixtures/valid/android-manual.json",
            "fixtures/valid/android-tv-crash-ueh.json",
            "fixtures/valid/ios-hang-metrickit.json",
            "fixtures/valid/tvos-abnormal-exit.json",
        )

        fixtures.forEach { fixtureName ->
            val manifest = parseManifest(fixtureName)
            val roundTripped = decodeDiagnosticsManifest(SiloJson.encodeToString(manifest))
            roundTripped.validate()
            assertEquals(manifest, roundTripped, fixtureName)
        }
    }

    @Test
    fun validAndroidTvCrashExposesTypedContract() {
        val manifest = parseManifest("fixtures/valid/android-tv-crash-ueh.json")

        assertEquals(DiagnosticsPlatform.ANDROID_TV, manifest.report.platform)
        assertEquals(DiagnosticsReportType.CRASH, manifest.report.type)
        assertEquals(DiagnosticsCrashSource.UEH, manifest.crash?.source)
        assertEquals("prof_living_room", manifest.report.profileId)
        assertEquals("manifest.json", manifest.archive.entries.first())
    }

    @Test
    fun canonicalInvalidManifestFixturesAreRejected() {
        val fixtures = listOf(
            "fixtures/invalid/archive-entry-outside-allowlist.json",
            "fixtures/invalid/bad-schema-version.json",
            "fixtures/invalid/missing-consent.json",
            "fixtures/invalid/platform-macos-reserved.json",
            "fixtures/invalid/stack-excerpt-over-8kib.json",
            "fixtures/invalid/unknown-report-type.json",
        )

        fixtures.forEach { fixtureName ->
            assertFailsWith<DiagnosticsValidationException>(fixtureName) {
                parseManifest(fixtureName)
            }
        }
    }

    @Test
    fun archiveRequiresManifestFirstUniqueAllowlistedEntriesAndValidDigest() {
        val valid = parseManifest("fixtures/valid/android-manual.json")

        listOf(
            valid.archive.copy(entries = listOf("device.json", "manifest.json")),
            valid.archive.copy(entries = listOf("manifest.json", "device.json", "device.json")),
            valid.archive.copy(entries = listOf("manifest.json", "secret.txt")),
            valid.archive.copy(sha256 = "not-a-digest"),
            valid.archive.copy(bytes = -1),
        ).forEach { invalidArchive ->
            assertFailsWith<DiagnosticsValidationException> {
                valid.copy(archive = invalidArchive).validate()
            }
        }
    }

    @Test
    fun manualAndEventCrashShapesAreMutuallyExclusive() {
        val manual = parseManifest("fixtures/valid/android-manual.json")
        val crash = parseManifest("fixtures/valid/android-tv-crash-ueh.json")

        assertFailsWith<DiagnosticsValidationException> {
            manual.copy(crash = crash.crash).validate()
        }
        assertFailsWith<DiagnosticsValidationException> {
            crash.copy(crash = null).validate()
        }
    }

    @Test
    fun stringBoundsUseUtf8BytesAndCollectionsUseServerCaps() {
        val valid = parseManifest("fixtures/valid/android-manual.json")
        val multiByteValueOver128Bytes = "🚀".repeat(33)

        assertFailsWith<DiagnosticsValidationException> {
            valid.copy(
                report = valid.report.copy(captureSessionId = multiByteValueOver128Bytes),
            ).validate()
        }
        assertFailsWith<DiagnosticsValidationException> {
            valid.copy(playbackSessionIds = List(21) { "session-$it" }).validate()
        }
        assertFailsWith<DiagnosticsValidationException> {
            valid.copy(
                logSummary = valid.logSummary.copy(
                    categories = listOf(DiagnosticsLogCategory.NETWORK, DiagnosticsLogCategory.NETWORK),
                ),
            ).validate()
        }
    }

    @Test
    fun deviceFixtureSupportsOpenTypedSectionsAndSentinels() {
        val snapshot = decodeDiagnosticsDeviceSnapshot(fixture("fixtures/valid/device.json"))
        snapshot.validate()

        assertEquals(DiagnosticsDeviceProvenance.PRE_FAILURE, snapshot.provenance)
        assertEquals(JsonPrimitive("NVIDIA"), snapshot.identity.jsonObject["manufacturer"])
        assertTrue(snapshot.videoCodecs.jsonArray.isNotEmpty())

        snapshot.copy(
            identity = JsonPrimitive(DiagnosticsDeviceSentinel.UNKNOWN.wireValue),
            display = JsonPrimitive(DiagnosticsDeviceSentinel.NOT_COLLECTED.wireValue),
        ).validate()
    }

    @Test
    fun canonicalLogLinesValidateAndDropNoRegisteredData() {
        val lines = fixture("fixtures/valid/loglines.jsonl")
            .lineSequence()
            .filter(String::isNotBlank)
            .map(::decodeDiagnosticsLogLine)
            .toList()

        lines.forEach(DiagnosticsLogLine::validate)
        assertEquals(3, lines.size)
        assertEquals(DiagnosticsLogLevel.WARNING, lines.first().level)
        assertEquals(JsonPrimitive("HDMI"), lines.first().attributes?.get("sink"))
    }

    @Test
    fun statusAndUploadResponsesMatchServerWireNames() {
        val status = SiloJson.decodeFromString<DiagnosticsStatusResponse>(
            """{
                "status":"available",
                "server_instance_id":"server-1",
                "accepted_schema_versions":[1],
                "max_bundle_bytes":10485760,
                "max_manifest_bytes":65536,
                "retention_days":30,
                "consent_notice_version":2
            }""".trimIndent(),
        )
        val upload = SiloJson.decodeFromString<DiagnosticsUploadResponse>(
            """{"report_id":"report-1","short_id":"ABC123"}""",
        )

        assertEquals(DiagnosticsAvailabilityStatus.AVAILABLE, status.status)
        assertEquals("server-1", status.serverInstanceId)
        assertEquals(listOf(1), status.acceptedSchemaVersions)
        assertEquals(10_485_760L, status.maxBundleBytes)
        assertEquals(65_536L, status.maxManifestBytes)
        assertEquals(30, status.retentionDays)
        assertEquals(2, status.consentNoticeVersion)
        assertEquals("report-1", upload.reportId)
        assertEquals("ABC123", upload.shortId)
    }

    @Test
    fun profileIdRemainsOptionalForPreProfileManualCapture() {
        val manifest = parseManifest("fixtures/valid/android-manual-no-profile.json")

        assertNull(manifest.report.profileId)
        assertFalse(SiloJson.encodeToString(manifest).contains("profile_id"))
    }

    @Test
    fun invalidDeviceAndLogValuesFailWithContractException() {
        val device = decodeDiagnosticsDeviceSnapshot(fixture("fixtures/valid/device.json"))
        val line = decodeDiagnosticsLogLine(
            fixture("fixtures/valid/loglines.jsonl").lineSequence().first(),
        )

        assertFailsWith<DiagnosticsValidationException> {
            device.copy(capturedAt = "not-a-date").validate()
        }
        assertFailsWith<DiagnosticsValidationException> {
            line.copy(message = "x".repeat(2_049)).validate()
        }
        assertIs<JsonPrimitive>(line.attributes?.get("fmt"))
    }

    @Test
    fun contractDecoderRejectsNonStandardAndTrailingJson() {
        val validManifest = fixture("fixtures/valid/android-manual.json")
        val validDevice = fixture("fixtures/valid/device.json")

        assertFailsWith<DiagnosticsValidationException> {
            decodeDiagnosticsManifest("$validManifest{}")
        }
        assertFailsWith<DiagnosticsValidationException> {
            decodeDiagnosticsDeviceSnapshot(
                validDevice.replace("\"network\": {", "\"network\": {\"strength\": NaN,"),
            )
        }
    }

    private fun parseManifest(fixtureName: String): DiagnosticsManifest =
        decodeDiagnosticsManifest(fixture(fixtureName)).also(DiagnosticsManifest::validate)

    private fun fixture(relativePath: String): String {
        val resourceName = "diagnostics/v1/$relativePath"
        val resource = checkNotNull(javaClass.classLoader?.getResource(resourceName)) {
            "Missing test resource $resourceName"
        }
        return resource.readText()
    }
}
