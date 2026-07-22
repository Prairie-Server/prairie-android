# Android Client Diagnostics Implementation Plan

> **For implementation:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use subagents only if the user explicitly requests delegated execution. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver the complete Android and Android TV client-diagnostics feature in one pull request, compatible with Silo diagnostics schema v1 and the shipped self-hosted server ingest API.

**Architecture:** Platform-neutral wire models and Ktor upload code live in shared. Android-native logging, persistence, capture, identity, bundling, and upload orchestration live in android-shared. Phone and TV own Compose presentation and startup integration. Crash-time work stays bounded and synchronous; report conversion, review, bundle construction, and upload happen after restart under fail-closed identity and consent checks.

**Tech Stack:** Kotlin 2.1, Kotlin Multiplatform, kotlinx.serialization, Ktor 3.1, coroutines, DataStore, WorkManager, Compose/TV Compose, Robolectric, ApplicationExitInfo, USTAR/gzip, SHA-256.

## Global Constraints

- Deliver one comprehensive PR with ordered commits; do not create stacked PRs.
- Add no Sentry, GlitchTip, Crashlytics, OpenTelemetry, ACRA, or similar dependency.
- Target schema version 1 and copy canonical fixtures from Silo-Server/silo-server.
- Default crash reporting to Ask and debug logging to off.
- Bind consent and reports to server instance, account user, and notice version.
- Confirmed child-profile evidence is purged and cannot be reviewed or uploaded.
- Never retarget a report across server, account, or captured-profile boundaries.
- Install crash capture before Koin and always chain to the previous handler.
- Limit a crash marker to 512 KiB, pending reports to three per binding, and retention to seven days.
- Send exactly two multipart parts in order: manifest, then bundle.
- Focus Don't send by default on TV prompts.
- Do not install or launch either app unless separately requested.
- Use strict red-green-refactor for every behavior change.

Test snippets use local fixture helpers declared in the named test file: fixture(path) reads a classpath resource; validManifest() returns the canonical valid Android manifest; captureRequest(block) runs DiagnosticsApi against Ktor MockEngine and returns the single recorded HttpRequestData; capture(...), report, child/adult identities, exit(...), testCapture(...), hugeThrowable(), hugeRing(), untar(...), and the integration harness are deterministic builders/fakes with no production dependency.

---

## File map

Shared wire/API files:

- shared/src/commonMain/kotlin/org/siloserver/silo/model/diagnostics/DiagnosticsModels.kt
- shared/src/commonMain/kotlin/org/siloserver/silo/model/diagnostics/DiagnosticsValidation.kt
- shared/src/commonMain/kotlin/org/siloserver/silo/network/api/DiagnosticsApi.kt
- shared/src/commonMain/kotlin/org/siloserver/silo/network/DiagnosticsRequestScope.kt
- shared/src/androidUnitTest/kotlin/org/siloserver/silo/model/diagnostics/DiagnosticsContractTest.kt
- shared/src/commonTest/kotlin/org/siloserver/silo/network/api/DiagnosticsApiTest.kt
- shared/src/commonTest/resources/diagnostics/v1/ (canonical fixture tree and SOURCE metadata)

Android infrastructure under android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/:

- DiagnosticsRedactor.kt, SiloLog.kt, LogRing.kt, DiagnosticsFileLogger.kt
- DiagnosticsSettingsStore.kt, DiagnosticsIdentityResolver.kt, DiagnosticsRunLedger.kt
- PendingReportStore.kt, DiagnosticsBundleBuilder.kt
- DeviceSnapshotCollector.kt, CrashCapture.kt, ExitInfoCollector.kt
- DiagnosticsUploader.kt, DiagnosticsUploadWorker.kt, DiagnosticsCoordinator.kt
- DiagnosticsModule.kt, DiagnosticsPresentationModels.kt

Phone presentation lives under androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/diagnostics/. TV presentation lives under androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/.

---

### Task 1: Import and implement the canonical schema-v1 contract

**Files:**
- Create: shared/src/commonMain/kotlin/org/siloserver/silo/model/diagnostics/DiagnosticsModels.kt
- Create: shared/src/commonMain/kotlin/org/siloserver/silo/model/diagnostics/DiagnosticsValidation.kt
- Create: shared/src/androidUnitTest/kotlin/org/siloserver/silo/model/diagnostics/DiagnosticsContractTest.kt
- Create: shared/src/commonTest/resources/diagnostics/v1/ (canonical fixture tree and SOURCE metadata)
- Modify: shared/build.gradle.kts

**Interfaces:**
- Produces DiagnosticsManifest.validate(), DiagnosticsStatusResponse, DiagnosticsUploadResponse, DiagnosticsLogLine, and DeviceSnapshot.
- JSON names, enum values, bounds, and archive allowlist match server schema v1.

- [x] **Step 1: Copy canonical fixtures**

Copy docs/design/schemas/client-diagnostics/v1 from server main byte-for-byte. Add a SOURCE file containing the server commit SHA.

- [x] **Step 2: Write failing contract tests**

~~~kotlin
class DiagnosticsContractTest {
    @Test
    fun validAndroidCrashRoundTrips() {
        val manifest = SiloJson.decodeFromString<DiagnosticsManifest>(
            fixture("fixtures/valid/android-tv-crash-ueh.json"),
        )
        manifest.validate()
        assertEquals(DiagnosticsPlatform.ANDROID_TV, manifest.report.platform)
        assertEquals(DiagnosticsReportType.CRASH, manifest.report.type)
    }

    @Test
    fun rejectsUnknownArchiveEntry() {
        val manifest = validManifest().copy(
            archive = validManifest().archive.copy(
                entries = listOf("manifest.json", "secret.txt"),
            ),
        )
        assertFailsWith<DiagnosticsValidationException> { manifest.validate() }
    }
}
~~~

- [x] **Step 3: Verify RED**

~~~bash
./gradlew :shared:testDebugUnitTest --tests '*DiagnosticsContractTest'
~~~

Expected: compilation fails because diagnostics models do not exist.

- [x] **Step 4: Implement all schema types and bounds**

~~~kotlin
@Serializable
data class DiagnosticsManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    val report: DiagnosticsReport,
    val destination: DiagnosticsDestination,
    val consent: DiagnosticsConsent,
    val crash: DiagnosticsCrashInfo? = null,
    @SerialName("device_summary") val deviceSummary: DiagnosticsDeviceSummary,
    @SerialName("playback_session_ids") val playbackSessionIds: List<String> = emptyList(),
    @SerialName("log_summary") val logSummary: DiagnosticsLogSummary,
    val archive: DiagnosticsArchive,
)

@Serializable
enum class DiagnosticsReportType {
    @SerialName("crash") CRASH,
    @SerialName("anr") ANR,
    @SerialName("native_crash") NATIVE_CRASH,
    @SerialName("hang") HANG,
    @SerialName("abnormal_exit") ABNORMAL_EXIT,
    @SerialName("manual") MANUAL,
}

fun DiagnosticsManifest.validate() {
    requireDiagnostics(schemaVersion == 1, "schema_version")
    requireDiagnostics(archive.entries.firstOrNull() == "manifest.json", "archive.entries")
    requireDiagnostics(archive.entries.all(DiagnosticsArchive.ALLOWED_ENTRIES::contains), "archive.entries")
    requireDiagnostics(playbackSessionIds.size <= 20, "playback_session_ids")
    requireDiagnostics((report.type == DiagnosticsReportType.MANUAL) == (crash == null), "crash")
}
~~~

Implement every schema constraint, not only the sample assertions.

- [x] **Step 5: Verify GREEN**

~~~bash
./gradlew :shared:testDebugUnitTest --tests '*DiagnosticsContractTest'
~~~

Expected: all canonical valid fixtures pass and invalid fixtures throw DiagnosticsValidationException.

- [x] **Step 6: Commit**

~~~bash
git add shared
git commit -m "feat(diagnostics): add schema v1 contracts"
~~~

---

### Task 2: Add diagnostics status and multipart upload APIs

**Files:**
- Create: shared/src/commonMain/kotlin/org/siloserver/silo/network/api/DiagnosticsApi.kt
- Create: shared/src/commonMain/kotlin/org/siloserver/silo/network/DiagnosticsRequestScope.kt
- Create: shared/src/commonTest/kotlin/org/siloserver/silo/network/api/DiagnosticsApiTest.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/network/AuthInterceptorImpl.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/di/NetworkModule.kt

**Interfaces:**
- Produces DiagnosticsApi.getStatus() and upload(manifestJson, bundleBytes, capturedProfileId).
- Request scope makes the interceptor send exactly the captured profile or suppress the header for unattributed reports.

- [x] **Step 1: Write failing MockEngine tests**

~~~kotlin
@Test
fun uploadSendsExactlyTwoPartsAndSuppressesUnattributedProfile() = runTest {
    val request = captureRequest {
        api.upload(manifestBytes, bundleBytes, capturedProfileId = null)
    }
    assertEquals("/api/v1/diagnostics/reports", request.url.encodedPath)
    assertNull(request.headers["X-Profile-Id"])
    val body = request.body.toByteArray().decodeToString()
    assertTrue(body.indexOf("name=\"manifest\"") < body.indexOf("name=\"bundle\""))
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :shared:testDebugUnitTest --tests '*DiagnosticsApiTest'
~~~

- [x] **Step 3: Implement API and header scope**

~~~kotlin
enum class DiagnosticsProfileHeaderMode { ACTIVE, SUPPRESS, EXACT }

data class DiagnosticsRequestScope(
    val mode: DiagnosticsProfileHeaderMode,
    val exactProfileId: String? = null,
)

class DiagnosticsApi(private val client: HttpClient) {
    suspend fun getStatus(): ApiResult<DiagnosticsStatusResponse> = safeApiCall {
        client.get("/api/v1/diagnostics/status")
    }

    suspend fun upload(
        manifestJson: ByteArray,
        bundleBytes: ByteArray,
        capturedProfileId: String?,
    ): ApiResult<DiagnosticsUploadResponse> = safeApiCall {
        client.post("/api/v1/diagnostics/reports") {
            attributes.put(
                DiagnosticsRequestScopeKey,
                capturedProfileId?.let {
                    DiagnosticsRequestScope(DiagnosticsProfileHeaderMode.EXACT, it)
                } ?: DiagnosticsRequestScope(DiagnosticsProfileHeaderMode.SUPPRESS),
            )
            setBody(MultiPartFormDataContent(formData {
                append("manifest", manifestJson, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"manifest.json\"")
                })
                append("bundle", bundleBytes, Headers.build {
                    append(HttpHeaders.ContentType, "application/gzip")
                    append(HttpHeaders.ContentDisposition, "filename=\"bundle.tar.gz\"")
                })
            }))
        }
    }
}
~~~

The auth interceptor must add only ACTIVE or EXACT, and remove/suppress profile headers for SUPPRESS.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :shared:testDebugUnitTest --tests '*DiagnosticsApiTest'
~~~

Cover status limits and every stable server error code.

- [x] **Step 5: Commit**

~~~bash
git add shared
git commit -m "feat(diagnostics): add status and upload API"
~~~

---

### Task 3: Implement collection-time redaction and SiloLog

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsRedactor.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/SiloLog.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsRedactorTest.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/SiloLogTest.kt

**Interfaces:**
- Produces DiagnosticsRedactor, SiloLogAttribute, DiagnosticsLogSink, and pre-Koin SiloLog.
- SiloLog always forwards to android.util.Log and optionally offers a structured line.

- [x] **Step 1: Write failing leak and registry tests**

~~~kotlin
@Test
fun redactsCredentialsHostsAndQueries() {
    val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.x.y user@example.com https://secret.example/a?token=raw"
    val output = redactor.sanitize(input)
    assertFalse(output.contains("eyJ"))
    assertFalse(output.contains("user@example.com"))
    assertFalse(output.contains("token=raw"))
    assertFalse(output.contains("secret.example"))
}

@Test
fun unregisteredAttributeIsDropped() {
    val line = renderer.render(INFO, NETWORK, "Http", "completed", mapOf("body" to Text("secret")))
    assertFalse(line.contains("body"))
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsRedactorTest' --tests '*SiloLogTest'
~~~

- [x] **Step 3: Implement typed logging and canonical registry**

~~~kotlin
sealed interface SiloLogAttribute {
    data class Text(val value: String) : SiloLogAttribute
    data class Integer(val value: Long) : SiloLogAttribute
    data class Number(val value: Double) : SiloLogAttribute
    data class Flag(val value: Boolean) : SiloLogAttribute
    data class Url(val value: String) : SiloLogAttribute
    data class Failure(val value: Throwable) : SiloLogAttribute
}

fun interface DiagnosticsLogSink {
    fun offer(renderedJsonLine: String)
}

object SiloLog {
    private val sink = AtomicReference<DiagnosticsLogSink?>()

    fun installSink(value: DiagnosticsLogSink?) = sink.set(value)

    fun i(
        category: DiagnosticsLogCategory,
        tag: String,
        message: String,
        attrs: Map<String, SiloLogAttribute> = emptyMap(),
    ) {
        Log.i(tag, message)
        DiagnosticsLogRenderer.render(INFO, category, tag, message, attrs)
            ?.let { sink.get()?.offer(it) }
    }
}
~~~

Implement stable host hashing, loopback preservation, JWT/email/cookie/header scrubbing, typed attribute allowlists, throwable depth/byte limits, and production dropping of invalid keys.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsRedactorTest' --tests '*SiloLogTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): add safe structured logging"
~~~

---

### Task 4: Implement the ring and rotating persistent logger

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/LogRing.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsFileLogger.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/LogRingTest.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsFileLoggerTest.kt

**Interfaces:**
- LogRing.offer, snapshot, rotateGeneration, clear.
- DiagnosticsFileLogger.start, offer, freeze, cancel.

- [x] **Step 1: Write failing capacity and generation tests**

~~~kotlin
@Test
fun snapshotIsNewestLastAndGenerationIsolated() {
    val ring = LogRing(capacity = 3, maxBytes = 256)
    val old = ring.currentGeneration
    ring.offer("old")
    val next = ring.rotateGeneration()
    ring.offer("one")
    ring.offer("two")
    assertEquals(listOf("one", "two"), ring.snapshot(next).lines)
    assertTrue(ring.snapshot(old).lines.isEmpty())
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*LogRingTest' --tests '*DiagnosticsFileLoggerTest'
~~~

- [x] **Step 3: Implement bounded non-blocking storage**

~~~kotlin
data class LogSnapshot(
    val lines: List<String>,
    val droppedCount: Long,
    val generation: Long,
)

interface DiagnosticsLogBuffer : DiagnosticsLogSink {
    val currentGeneration: Long
    fun rotateGeneration(): Long
    fun snapshot(expectedGeneration: Long = currentGeneration): LogSnapshot
    fun clear()
}
~~~

Use atomic sequence publication, no writer lock, bounded byte accounting, a torn-entry counter, a 512-entry DROP_OLDEST channel, one IO writer, five 2 MiB append-only segments, and no-backup storage.

- [x] **Step 4: Verify GREEN and hot-path source constraints**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*LogRingTest' --tests '*DiagnosticsFileLoggerTest'
~~~

The regression test performs 100,000 warmed offers, verifies ordering/capacity, and source-checks that the offer path contains no synchronized, Mutex, runBlocking, or channel send.

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): add bounded log capture"
~~~

---

### Task 5: Implement consent, identity resolution, and run correlation

**Files:**
- Create: shared/src/commonMain/kotlin/org/siloserver/silo/network/IdentityTransitionBarrier.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/network/TokenManager.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/network/TokenManagerImpl.kt
- Modify: shared/src/androidMain/kotlin/org/siloserver/silo/network/EncryptedTokenManagerImpl.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/network/ServerRegistry.kt
- Modify: shared/src/androidMain/kotlin/org/siloserver/silo/network/AndroidServerRegistry.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/repository/ProfileRepository.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsSettingsStore.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsIdentityResolver.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsRunLedger.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsSettingsStoreTest.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsIdentityResolverTest.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsRunLedgerTest.kt

**Interfaces:**
- Settings store observes consent/debug/history and performs binding purges.
- Resolver returns only validated DiagnosticsCaptureContext.
- Run ledger maps an opaque token to binding/profile without putting identity in processStateSummary.

- [x] **Step 1: Write failing transition tests**

~~~kotlin
@Test
fun noticeBumpDemotesAlwaysToAsk() = runTest {
    store.setConsent(binding, ALWAYS, noticeVersion = 1)
    assertEquals(ASK, store.consent(binding, currentNoticeVersion = 2).mode)
}

@Test
fun temporaryScopeClosesPersistentCapture() = runTest {
    tokenManager.temporary = true
    assertNull(resolver.resolve(requirePersistentCapture = true))
}

@Test
fun signOutClosesGateBeforeCredentialMutation() = runTest {
    tokenManager.signOutCurrentServer()
    assertEquals(
        listOf(WILL_CHANGE, DID_CHANGE),
        identityTransitions.eventsFor(SIGN_OUT).map(IdentityTransition::phase),
    )
    assertFalse(captureGate.wasOpenDuringCredentialMutation)
}

@Test
fun profileSwitchRotatesEvidenceBeforeProfileMutation() = runTest {
    profileRepository.selectProfile("adult-b")
    assertFalse(fileLogger.containsGeneration(adultAGeneration))
    assertFalse(ring.snapshot(adultAGeneration).lines.isNotEmpty())
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsSettingsStoreTest' --tests '*DiagnosticsIdentityResolverTest' --tests '*DiagnosticsRunLedgerTest'
~~~

- [x] **Step 3: Implement DataStore records and fail-closed resolver**

~~~kotlin
data class DiagnosticsBinding(
    val serverInstanceId: String,
    val accountUserId: String,
)

enum class DiagnosticsConsentMode { ASK, ALWAYS, NEVER }

enum class IdentityTransitionPhase { WILL_CHANGE, DID_CHANGE }

enum class IdentityTransitionKind {
    SIGN_IN, SIGN_OUT, SERVER_SWITCH, SERVER_REMOVE, PROFILE_SWITCH,
    TEMPORARY_SCOPE_BEGIN, TEMPORARY_SCOPE_END,
}

data class IdentityTransition(
    val phase: IdentityTransitionPhase,
    val kind: IdentityTransitionKind,
    val generation: Long,
)

interface IdentityTransitionBarrier {
    val transitions: SharedFlow<IdentityTransition>
    val generation: StateFlow<Long>
    fun installGate(listener: (IdentityTransition) -> Unit)
    suspend fun <T> changing(kind: IdentityTransitionKind, block: suspend () -> T): T
}

data class DiagnosticsCaptureContext(
    val binding: DiagnosticsBinding,
    val profileId: String?,
    val profileEligible: Boolean,
    val noticeVersion: Int,
    val status: DiagnosticsAvailabilityStatus,
    val ownershipGeneration: Long,
) {
    val identityKey: DiagnosticsIdentityKey = DiagnosticsIdentityKey(
        binding = binding,
        profileId = profileId,
        ownershipGeneration = ownershipGeneration,
    )
}

data class DiagnosticsIdentityKey(
    val binding: DiagnosticsBinding,
    val profileId: String?,
    val ownershipGeneration: Long,
)

interface DiagnosticsIdentityResolver {
    suspend fun resolve(requirePersistentCapture: Boolean): DiagnosticsCaptureContext?
}
~~~

Inject one IdentityTransitionBarrier into TokenManager, ServerRegistry, and ProfileRepository. Its changing function invokes the installed gate callback inline with WILL_CHANGE before running the mutation, increments the ownership generation, and emits DID_CHANGE in finally. The replayless flow is for observation and UI refresh only; it is not the privacy barrier. Cover save/clear/invalidate/sign-out tokens, active-server switch/removal, profile select/clear, and temporary-scope begin/end with table-driven transition tests. The coordinator gate callback synchronously closes capture and rotates ring/file generations on WILL_CHANGE. Resolve status, /auth/me, active profile, and child state under one ownership generation; retry if the generation changes during resolution. Cache only positive results. Temporary auth scopes cannot use persistent capture. Confirmed child evidence is purged; unresolved profile eligibility is quarantined and never treated as account-scoped adult evidence.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsSettingsStoreTest' --tests '*DiagnosticsIdentityResolverTest' --tests '*DiagnosticsRunLedgerTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add shared android-shared
git commit -m "feat(diagnostics): isolate consent and identity"
~~~

---

### Task 6: Build atomic pending-report storage

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/PendingReportStore.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/PendingReportStoreTest.kt

**Interfaces:**
- save, list, load, delete, purge, markState, hasSeenFingerprint, and throttle APIs.
- Published reports contain binding.json, manifest.json, state.json, and device.json.

- [x] **Step 1: Write failing staging, cap, expiry, and fingerprint tests**

~~~kotlin
@Test
fun droppedLateReportIsNotMarkedSeen() {
    repeat(3) { store.save(capture(day = it + 2, fingerprint = "kept-" + it)) }
    assertFails { store.save(capture(day = 1, fingerprint = "late")) }
    assertFalse(store.hasSeenFingerprint("late"))
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*PendingReportStoreTest'
~~~

- [x] **Step 3: Implement staging and bounded metadata**

~~~kotlin
data class PendingReport(
    val id: String,
    val directory: File,
    val binding: PendingReportBinding,
    val manifest: DiagnosticsManifest,
    val state: PendingReportState,
)

data class PendingReportBinding(
    val serverInstanceId: String,
    val accountUserId: String,
    val profileId: String?,
    val ownershipGeneration: Long,
) {
    fun matches(context: DiagnosticsCaptureContext): Boolean =
        serverInstanceId == context.binding.serverInstanceId &&
            accountUserId == context.binding.accountUserId &&
            profileId == context.profileId &&
            ownershipGeneration == context.ownershipGeneration
}

interface PendingReportStore {
    fun save(capture: PendingReportCapture): PendingReport
    fun list(binding: DiagnosticsBinding): List<PendingReport>
    fun load(id: String): PendingReport?
    fun delete(id: String)
    fun purge(binding: DiagnosticsBinding)
    fun hasSeenFingerprint(fingerprint: String): Boolean
}
~~~

Implement the interface as FilePendingReportStore(root, nowMs). binding.json contains PendingReportBinding and never contains tokens, URLs, profile names, or account display fields. Reject traversal/non-allowlisted artifacts, exclude from backup, write into a sibling staging directory, fsync files and directory metadata, atomically rename, then update the seen-fingerprint index. Delete failed staging, prune after seven days, cap at three per binding, reject a late report rather than evicting newer evidence, and prune fingerprint/throttle maps.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*PendingReportStoreTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): add pending report storage"
~~~

---

### Task 7: Implement canonical tar/gzip bundle construction

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsBundleBuilder.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsBundleBuilderTest.kt

**Interfaces:**
- build(report, redactionTokens) returns finalized external manifest bytes and transmitted gzip bytes.

- [x] **Step 1: Write failing deterministic archive tests**

~~~kotlin
@Test
fun bundleUsesCanonicalOrderAndExternalHash() {
    val bundle = builder.build(report, redactionTokens = listOf("secret-token"))
    assertEquals("manifest.json", untar(bundle.bytes).first().name)
    assertEquals(sha256Hex(bundle.bytes), bundle.manifest.archive.sha256)
    assertFalse(untar(bundle.bytes).first().text.contains("\"archive\""))
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsBundleBuilderTest'
~~~

- [x] **Step 3: Implement USTAR, gzip, and defense-in-depth redaction**

~~~kotlin
data class DiagnosticsBundle(
    val manifest: DiagnosticsManifest,
    val manifestBytes: ByteArray,
    val bytes: ByteArray,
)

interface DiagnosticsBundleBuilder {
    fun build(report: PendingReport, redactionTokens: List<String>): DiagnosticsBundle
}

val CANONICAL_ARCHIVE_ORDER = listOf(
    "manifest.json",
    "device.json",
    "logs.jsonl",
    "crash/summary.json",
    "crash/stack.txt",
    "crash/tombstone.pb",
    "crash/metrickit.json",
    "breadcrumbs.jsonl",
)
~~~

Implement FileDiagnosticsBundleBuilder with an internal UstarWriter. Include only present allowlisted entries in canonical order. Use correct USTAR padding/end markers, SHA-256 of final gzip bytes, exact uncompressed tar-stream bytes, and a redaction-failure sentinel for invalid UTF-8 text. The embedded manifest omits archive metadata; the external manifest describes and hashes the finalized transmitted gzip.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsBundleBuilderTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): build canonical report bundles"
~~~

---

### Task 8: Export truthful Android device snapshots

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DeviceSnapshotCollector.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DeviceSnapshotCollectorTest.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/DisplayHdrProbe.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/AudioCapabilityManager.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/audio/PassthroughSuppressionRegistry.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/MediaCodecCapabilitiesProbe.kt

**Interfaces:**
- Read-only existing-probe snapshots; diagnostics never mutates player state.
- DeviceSnapshotCollector.capture and DeviceSnapshotCache.currentBytes.

- [x] **Step 1: Write failing fake-probe tests**

~~~kotlin
@Test
fun inaccessibleAudioProbeReportsUnknown() {
    val snapshot = collector(FakeDeviceProbe(audioFailure = SecurityException())).capture(PRE_FAILURE)
    assertEquals(DiagnosticsValue.UNKNOWN, snapshot.audio.outputs)
    assertEquals("NVIDIA", snapshot.identity.manufacturer)
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DeviceSnapshotCollectorTest'
~~~

- [x] **Step 3: Add immutable accessors and collector**

~~~kotlin
interface DiagnosticsDeviceProbe {
    fun identity(): DiagnosticsIdentitySnapshot
    fun display(): DiagnosticsDisplaySnapshot
    fun audio(): DiagnosticsAudioSnapshot
    fun codecs(): List<DiagnosticsCodecSnapshot>
    fun network(): DiagnosticsNetworkSnapshot
}

class DeviceSnapshotCache {
    private val bytes = AtomicReference<ByteArray?>(null)
    fun update(snapshot: DeviceSnapshot) {
        bytes.set(SiloJson.encodeToString(snapshot).encodeToByteArray())
    }
    fun currentBytes(): ByteArray? = bytes.get()?.copyOf()
}
~~~

Hash stable device/route identifiers, omit SSIDs and addresses, and use explicit unknown/not_collected values.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DeviceSnapshotCollectorTest' --tests '*PassthroughSuppressionRegistryTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): capture Android device evidence"
~~~

---

### Task 9: Install bounded JVM crash capture before Koin

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/CrashCapture.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/CrashCaptureTest.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/SiloApplication.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/SiloTvApplication.kt

**Interfaces:**
- CrashCapture.install(context) is pre-Koin and idempotent.
- CrashCapture.updateSnapshot accepts pre-rendered runtime state after startup.

- [x] **Step 1: Write failing delegation and bounds tests**

~~~kotlin
@Test
fun delegatesOnceWhenMarkerWriteFails() {
    var delegated = 0
    val capture = testCapture(failingWriter = true) { delegated++ }
    capture.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
    assertEquals(1, delegated)
}

@Test
fun markerNeverExceedsLimit() {
    assertTrue(capture.renderMarker(hugeThrowable(), hugeRing()).size <= 512 * 1024)
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*CrashCaptureTest'
~~~

- [x] **Step 3: Implement minimal synchronous crash path**

~~~kotlin
object CrashCapture {
    private val installed = AtomicBoolean(false)
    private val runtime = AtomicReference(CrashRuntimeSnapshot.empty())

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashMarkerWriter(context.noBackupFilesDir, runtime.get()).write(thread, throwable)
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }
}
~~~

No coroutine, Koin, DataStore, network, service probe, shared writer lock, or bundle construction is permitted. Use temp, fsync, rename, deterministic truncation, and a hard elapsed-time budget.

- [x] **Step 4: Wire applications before startKoin and verify GREEN**

~~~kotlin
override fun onCreate() {
    super.onCreate()
    CrashCapture.install(this)
    val koinApp = startKoin {
        androidContext(this@SiloApplication)
        modules(sharedModules() + playerModule + playerInfraModule + diagnosticsModule + androidModule)
    }
    koinApp.koin.get<DiagnosticsCoordinator>().start()
}
~~~

Apply the equivalent ordering in SiloTvApplication, using androidTvModule in place of androidModule. Preserve all existing modules and initialization work in both applications.

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*CrashCaptureTest' :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared androidApp androidTvApp
git commit -m "feat(diagnostics): capture JVM crashes"
~~~

---

### Task 10: Convert ApplicationExitInfo into deduplicated reports

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/ExitInfoCollector.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/ExitInfoCollectorTest.kt

**Interfaces:**
- AndroidExitInfoSource abstracts framework access.
- collect returns only ledger-correlated, profile-eligible, deduplicated reports.

- [x] **Step 1: Write failing classification and correlation tests**

~~~kotlin
@Test
fun nativeCrashStoresOpaqueTombstone() = runTest {
    val report = collector.collect(exit(reason = NATIVE_CRASH, trace = byteArrayOf(1, 2, 3))).single()
    assertTrue(report.file("crash/tombstone.pb").exists())
    assertNull(report.manifest.crash?.stackExcerpt)
}

@Test
fun unmatchedRunTokenIsNotUploadable() = runTest {
    assertTrue(collector.collect(exit(processStateSummary = bytes("unknown"))).isEmpty())
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*ExitInfoCollectorTest'
~~~

- [x] **Step 3: Implement API-guarded bounded collection**

~~~kotlin
interface AndroidExitInfoRecord {
    val reason: Int
    val timestampMs: Long
    val pid: Int
    val processName: String
    val status: Int
    val processStateSummary: ByteArray?
    fun trace(maxBytes: Int): ByteArray?
}

class ExitInfoCollector(
    private val source: AndroidExitInfoSource,
    private val ledger: DiagnosticsRunLedger,
    private val reports: PendingReportStore,
) {
    suspend fun collect(): List<PendingReport> = source.records().mapNotNull(::convert)
}
~~~

Publish only the opaque run token through setProcessStateSummary. Bound trace reads, distinguish ANR/JVM text from API-31+ tombstone bytes, and deduplicate against JVM markers before save.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*ExitInfoCollectorTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): collect Android exit evidence"
~~~

---

### Task 11: Implement guarded upload, WorkManager, and DI

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsUploader.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsUploadWorker.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsModule.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsUploaderTest.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/downloads/AppWorkerFactory.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/di/AndroidModule.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/watchnext/TvWorkerFactory.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/di/AndroidTvModule.kt

**Interfaces:**
- DiagnosticsUploader.upload(reportId) returns DiagnosticsUploadDecision.
- Worker enqueues connected-network unique work and performs the same identity checks.

- [x] **Step 1: Write failing identity-race/error tests**

~~~kotlin
@Test
fun profileSwitchDuringBuildPreventsPost() = runTest {
    bundleBuilder.onBuild = { identity.profileId = "other" }
    assertEquals(KEPT_IDENTITY_CHANGED, uploader.upload(report.id))
    assertEquals(0, api.uploadCalls)
}

@Test
fun unsupportedSchemaMarksServerUpdateRequired() = runTest {
    api.result = error("unsupported_schema")
    assertEquals(KEPT_SERVER_UPDATE_REQUIRED, uploader.upload(report.id))
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsUploaderTest'
~~~

- [x] **Step 3: Implement double validation and stable decisions**

~~~kotlin
sealed interface DiagnosticsUploadDecision {
    data class Uploaded(val shortId: String) : DiagnosticsUploadDecision
    data object KeptRetryable : DiagnosticsUploadDecision
    data object KeptIdentityChanged : DiagnosticsUploadDecision
    data object KeptTooLarge : DiagnosticsUploadDecision
    data object KeptServerUpdateRequired : DiagnosticsUploadDecision
    data object KeptUnavailable : DiagnosticsUploadDecision
    data object KeptInvalid : DiagnosticsUploadDecision
}

interface DiagnosticsUploader {
    suspend fun upload(reportId: String): DiagnosticsUploadDecision
}
~~~

Implement DefaultDiagnosticsUploader with PendingReportStore, DiagnosticsIdentityResolver, DiagnosticsBundleBuilder, DiagnosticsApi, and an explicit token-redaction provider. It loads the report, resolves `before`, rejects any binding/profile mismatch, builds the bundle, resolves `after`, and posts only when both identity keys match and the report still matches `after`. Pass exactly `report.binding.profileId`; null suppresses X-Profile-Id. Delete only after success. Persist retryable/permanent states and sent short IDs.

Map stable server results explicitly: retry network, busy, quota, and rate-limit responses; keep and label too_large, unsupported_schema, diagnostics_disabled, storage_unavailable, invalid_archive, invalid_manifest, stale_report, destination_mismatch, profile_mismatch, and child_profile_forbidden without retry loops. Add a table-driven test containing every server error code from the canonical contract. Register the shared diagnostics Koin module and add explicit DiagnosticsUploadWorker branches to AppWorkerFactory and TvWorkerFactory.

- [x] **Step 4: Verify GREEN and worker construction**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsUploaderTest' :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared androidApp androidTvApp
git commit -m "feat(diagnostics): upload reports safely"
~~~

---

### Task 12: Build coordinator and manual capture state machine

**Files:**
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsCoordinator.kt
- Create: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsPresentationModels.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsCoordinatorTest.kt

**Interfaces:**
- StateFlow exposes availability, consent, pending reports, prompt, timed capture, and sent history.
- Commands cover refresh, consent, one-shot, start/stop/cancel, upload, delete, and decline.

- [x] **Step 1: Write failing ownership and purge tests**

~~~kotlin
@Test
fun profileChangeInvalidatesTimedCapture() = runTest {
    coordinator.startTimedCapture()
    identity.emit(adultA)
    identity.emit(adultB)
    assertEquals(INVALIDATED, coordinator.state.value.timedCapture)
    assertTrue(fileLogger.activeSegments().isEmpty())
}

@Test
fun neverPurgesAllBindingEvidence() = runTest {
    coordinator.setConsent(NEVER)
    assertTrue(store.list(binding).isEmpty())
    assertTrue(recentSessions.forBinding(binding).isEmpty())
    assertFalse(fileLogger.hasPersistentEvidence())
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsCoordinatorTest'
~~~

- [x] **Step 3: Implement one serialized owner**

~~~kotlin
data class DiagnosticsUiState(
    val availability: DiagnosticsAvailabilityUi = OFFLINE,
    val consent: DiagnosticsConsentMode = ASK,
    val debugLogging: Boolean = false,
    val pending: List<DiagnosticsReportSummary> = emptyList(),
    val prompt: DiagnosticsPrompt? = null,
    val timedCapture: TimedCaptureState = IDLE,
    val sentHistory: List<SentDiagnosticsReport> = emptyList(),
)

interface DiagnosticsCoordinator {
    val state: StateFlow<DiagnosticsUiState>
    fun start()
    suspend fun startTimedCapture()
    suspend fun stopTimedCapture(): String
    suspend fun setConsent(mode: DiagnosticsConsentMode)
}
~~~

Implement DefaultDiagnosticsCoordinator with one Channel-backed actor. That actor owns identity transitions, persistent logging, prompt suppression, one-fingerprint-per-day throttling, timed-capture generations, and UI state. Crash runtime state remains an atomic read-only mirror. A WILL_CHANGE event closes the capture gate before the corresponding identity mutation can proceed; DID_CHANGE triggers fail-closed re-resolution before capture can reopen.

- [x] **Step 4: Verify GREEN**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsCoordinatorTest'
~~~

- [x] **Step 5: Commit**

~~~bash
git add android-shared
git commit -m "feat(diagnostics): coordinate capture and consent"
~~~

---

### Task 13: Add complete phone UI

**Files:**
- Create: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/diagnostics/DiagnosticsViewModel.kt
- Create: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/diagnostics/DiagnosticsSettingsScreen.kt
- Create: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/diagnostics/DiagnosticsReportScreen.kt
- Create: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/diagnostics/DiagnosticsPrompt.kt
- Create: androidApp/src/androidUnitTest/kotlin/org/siloserver/silo/android/ui/screens/settings/diagnostics/DiagnosticsPhoneStateTest.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/settings/SettingsScreen.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/navigation/Routes.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/ui/navigation/AppNavigation.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/di/AndroidModule.kt

**Interfaces:**
- ViewModel delegates to coordinator and owns no diagnostics rule.

- [x] **Step 1: Write failing visibility/state tests**

~~~kotlin
@Test
fun childHasNoDiagnosticsEntry() {
    assertFalse(settingsEntries(child, available).contains(DIAGNOSTICS))
}

@Test
fun disabledServerStillShowsDeleteControls() {
    val model = screenModel(DISABLED, pending = listOf(report))
    assertTrue(model.showPending)
    assertFalse(model.canUpload)
    assertTrue(model.canDelete)
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :androidApp:testDebugUnitTest --tests '*DiagnosticsPhoneStateTest'
~~~

- [x] **Step 3: Implement phone routes/screens**

~~~kotlin
@Composable
fun DiagnosticsSettingsScreen(
    state: DiagnosticsUiState,
    onConsentChanged: (DiagnosticsConsentMode) -> Unit,
    onStartCapture: () -> Unit,
    onSendNow: () -> Unit,
    onReportSelected: (String) -> Unit,
) {
    DiagnosticsStatusCard(state.availability)
    DiagnosticsConsentSection(state, onConsentChanged)
    DiagnosticsCaptureSection(state, onStartCapture, onSendNow)
    DiagnosticsPendingSection(state.pending, onReportSelected)
    DiagnosticsSentHistory(state.sentHistory)
}
~~~

Show exact archive entries, destination, captured profile, evidence size, expiry, and upload state. Always Send needs confirmation. Timed capture needs a persistent indicator and Stop & review.

- [x] **Step 4: Verify GREEN and compile**

~~~bash
./gradlew :androidApp:testDebugUnitTest --tests '*DiagnosticsPhoneStateTest' :androidApp:compileDebugKotlinAndroid
~~~

- [x] **Step 5: Commit**

~~~bash
git add androidApp
git commit -m "feat(diagnostics): add phone report experience"
~~~

---

### Task 14: Add complete Android TV UI

**Files:**
- Create: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsViewModel.kt
- Create: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsSettingsScreen.kt
- Create: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsReportScreen.kt
- Create: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsPromptScreen.kt
- Create: androidTvApp/src/androidUnitTest/kotlin/org/siloserver/silo/tv/ui/screens/settings/diagnostics/TvDiagnosticsStateTest.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/settings/TvSettingsScreen.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/navigation/TvRoute.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/navigation/TvAppNavigation.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/di/AndroidTvModule.kt

**Interfaces:**
- Same coordinator semantics as phone, mapped into remote-friendly focus models.

- [x] **Step 1: Write failing focus tests**

~~~kotlin
@Test
fun promptDefaultsToDontSend() {
    assertEquals(DONT_SEND, promptModel(report).initialFocus)
}

@Test
fun alwaysNeedsSecondConfirmation() {
    assertTrue(settingsModel(ASK).consentAction(ALWAYS).requiresConfirmation)
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDiagnosticsStateTest'
~~~

- [x] **Step 3: Implement TV screens**

~~~kotlin
@Composable
fun TvDiagnosticsPromptScreen(
    prompt: DiagnosticsPrompt,
    onReview: () -> Unit,
    onSend: () -> Unit,
    onAlwaysSend: () -> Unit,
    onDontSend: () -> Unit,
) {
    val dontSendRequester = remember { FocusRequester() }
    LaunchedEffect(prompt.id) { dontSendRequester.requestFocus() }
    TvDiagnosticsPromptContent(
        prompt = prompt,
        dontSendRequester = dontSendRequester,
        onReview = onReview,
        onSend = onSend,
        onAlwaysSend = onAlwaysSend,
        onDontSend = onDontSend,
    )
}
~~~

Use full-screen flows, large remote targets, predictable Back behavior, and non-obstructive timed-capture state.

- [x] **Step 4: Verify GREEN and compile**

~~~bash
./gradlew :androidTvApp:testDebugUnitTest --tests '*TvDiagnosticsStateTest' :androidTvApp:compileDebugKotlinAndroid
~~~

- [x] **Step 5: Commit**

~~~bash
git add androidTvApp
git commit -m "feat(diagnostics): add TV report experience"
~~~

---

### Task 15: Curate playback, network, focus, cast, download, and lifecycle evidence

**Files:**
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/PlaybackAnalyticsListener.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/PlayerStatsSnapshot.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/PlaybackSessionLifecycle.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/player/video/VideoPlaybackSessionCoordinator.kt
- Modify: android-shared/src/androidMain/kotlin/org/siloserver/silo/common/downloads/DownloadWorker.kt
- Modify: androidApp/src/androidMain/kotlin/org/siloserver/silo/android/cast/SiloCastSessionManager.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/cast/TvSiloCastReceiver.kt
- Modify: androidTvApp/src/androidMain/kotlin/org/siloserver/silo/tv/ui/shell/TvShellFocusState.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/network/SiloHttpClientImpl.kt
- Modify: shared/src/commonMain/kotlin/org/siloserver/silo/network/AuthInterceptorImpl.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsInstrumentationTest.kt

**Interfaces:**
- Instrumentation calls only SiloLog.
- Five-second stats snapshots run only for debug/timed capture.

- [x] **Step 1: Write failing safety tests**

~~~kotlin
@Test
fun networkEvidenceHasNoQueryOrBody() {
    val line = networkLogger.completed("GET", "/api/v1/items/{id}", 200, 42)
    assertFalse(line.contains("?"))
    assertFalse(line.contains("body"))
    assertTrue(line.contains("duration_ms"))
}

@Test
fun subtitleCueTextIsNeverCaptured() {
    assertFalse(allDiagnosticsSources().any { it.contains("onCues") || it.contains("cue.text") })
}
~~~

- [x] **Step 2: Verify RED**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsInstrumentationTest'
~~~

- [x] **Step 3: Add only contract-safe events**

~~~kotlin
SiloLog.i(
    DiagnosticsLogCategory.PLAYBACK,
    "Media3Analytics",
    "video decoder initialized",
    mapOf(
        "decoder" to SiloLogAttribute.Text(decoderName),
        "duration_ms" to SiloLogAttribute.Integer(initializationDurationMs),
    ),
)
~~~

Do not mechanically ingest existing Log strings. Exclude titles, subtitle text, raw URLs, bodies, headers, and arbitrary error messages.

- [x] **Step 4: Verify GREEN and module suites**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsInstrumentationTest' :androidApp:testDebugUnitTest :androidTvApp:testDebugUnitTest
~~~

- [x] **Step 5: Commit**

~~~bash
git add shared android-shared androidApp androidTvApp
git commit -m "feat(diagnostics): add curated client evidence"
~~~

---

### Task 16: Cross-layer privacy tests and final verification

**Files:**
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsPrivacyIntegrationTest.kt
- Create: android-shared/src/androidUnitTest/kotlin/org/siloserver/silo/common/diagnostics/DiagnosticsStartupRobolectricTest.kt
- Modify: README.md
- Modify: docs/README.md
- Update the design spec only if implementation facts changed

**Interfaces:**
- Final harness exercises capture-to-bundle and identity transitions through fakes.

- [x] **Step 1: Write final failing regressions**

~~~kotlin
@Test
fun childThenAdultManualBundleHasNoChildGeneration() = runTest {
    harness.activate(child)
    harness.log("child evidence")
    harness.activate(adult)
    harness.log("adult evidence")
    val bundle = harness.createManualBundle()
    assertFalse(bundle.textEntries().contains("child evidence"))
    assertTrue(bundle.textEntries().contains("adult evidence"))
}

@Test
fun jvmMarkerAndExitInfoProduceOneReport() = runTest {
    harness.writeJvmCrash(fingerprint = "same")
    harness.addExitInfo(fingerprint = "same")
    harness.startCoordinator()
    assertEquals(1, harness.pendingReports().size)
}
~~~

- [x] **Step 2: Verify RED, then fix only cross-layer gaps**

~~~bash
./gradlew :android-shared:testDebugUnitTest --tests '*DiagnosticsPrivacyIntegrationTest' --tests '*DiagnosticsStartupRobolectricTest'
~~~

- [x] **Step 3: Run all unit tests**

~~~bash
./gradlew --rerun-tasks --no-build-cache \
  :shared:testDebugUnitTest \
  :android-shared:testDebugUnitTest \
  :androidApp:testDebugUnitTest \
  :androidTvApp:testDebugUnitTest
~~~

Expected: zero failures.

- [x] **Step 4: Compile both applications**

~~~bash
./gradlew :androidApp:compileDebugKotlinAndroid :androidTvApp:compileDebugKotlinAndroid
~~~

Expected: success.

- [x] **Step 5: Build both debug APKs without installing**

~~~bash
./gradlew :androidApp:assembleDebug :androidTvApp:assembleDebug
~~~

Expected: success. Do not run install tasks.

- [x] **Step 6: Inspect scope and dependencies**

~~~bash
git diff --check origin/main...HEAD
git diff --stat origin/main...HEAD
rg -n -i 'sentry|glitchtip|crashlytics|opentelemetry|acra' gradle androidApp androidTvApp android-shared shared
~~~

Expected: no whitespace errors and no new diagnostics SDK reference.

- [x] **Step 7: Commit final hardening**

~~~bash
git add README.md docs shared android-shared androidApp androidTvApp
git commit -m "test(diagnostics): harden privacy and lifecycle boundaries"
~~~

- [x] **Step 8: Prepare the single PR**

The PR body must summarize architecture, consent, capture sources, phone/TV UX, privacy gates, test evidence, known platform limitations, and state that no device install or launch occurred. Link server PR 445 and the design specification. Request review only after final verification output is inspected.

---

## Self-review checklist

- Every design requirement maps to a task.
- Every production behavior has a named failing test first.
- Header behavior is explicit for captured-profile and unattributed reports.
- Child, Never, sign-out, removal, profile switch, and temporary-scope transitions are covered.
- Crash-time code has no coroutine, Koin, DataStore, probe, upload, or bundle dependency.
- Canonical fixtures, multipart order, embedded/external manifests, and archive hash are covered.
- Phone and TV ship in the same PR.
