# C — OAuth Device Authorization Grant / QR device login

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the OAuth-style device-login flow that Apple's tvOS app already uses into the Prairie Android TV app. Splices into the QR placeholder shipped by A.4. After this lands, a user can scan a QR on their phone to sign in without typing their password on the TV.

**Architecture:**
- New shared (KMP) auth surface: `DeviceLoginModels.kt`, `DeviceLoginApi.kt`, `DeviceLoginRepository.kt` in `shared/commonMain/auth/` (mirrors Apple's `DeviceLoginModels.swift` + `AuthService.startDeviceLogin/pollDeviceLogin`). Ktor implementation in `shared/commonMain/network/api/`.
- New `DeviceLoginState` sealed class as the public StateFlow surface (Idle / Initiating / Awaiting / Approved / Failed).
- New `QrCodePanel.kt` composable in `androidTvApp/` rendering ZXing-encoded QR into a Compose `Canvas`. Replaces the static placeholder from A.4.
- `TvLoginViewModel` extended with parallel credential + device flows; whichever completes first wins, the other cancels. After token capture the existing auth machinery (token storage, profile flow) takes over unchanged.
- New dep: `com.google.zxing:core:3.5.3` (pure JVM, ~600 KB; no Android-zxing baggage).

**Tech stack:** Kotlin 2.1.20, KMP (shared/commonMain), Ktor 3.1.2, kotlinx-serialization 1.8.1, kotlinx-coroutines 1.10.2, ZXing core 3.5.3.

**Reference (canonical):** Apple's working implementation at:
- `/opt/prairie-apple/iosApp/iosApp/Networking/DeviceLoginModels.swift` — the exact wire shape (used verbatim for mirror types).
- `/opt/prairie-apple/iosApp/iosApp/Screens/Auth/AuthService.swift:209-227` — the two endpoint calls.
- `/opt/prairie-apple/iosApp/iosApp/Screens/Auth/QRLoginViewModel.swift` — the polling state machine.

**Endpoint contract (from Apple's `AuthService.swift`):**
- `POST /api/v1/auth/device/start` with body `{ deviceName: String?, devicePlatform: String? }` → returns the full `DeviceLoginStartResponse`.
- `POST /api/v1/auth/device/poll` with body `{ deviceCode: String }` → returns `DeviceLoginPollResponse`. 404 = expired/cleaned-up row.

**Status enum** (from Apple): `pending` / `approved` / `denied` / `expired` / `consumed` / `unknown`. Terminal: all except `pending`.

**Testing posture:** Per `AGENTS.md`, focused tests for the state machine. Repository's poll-loop behavior (initial poll, interval honoring, status transitions, 404 handling) is non-trivial — gets tests. QR rendering is visual — no test. Wiring (ViewModel race between flows) gets a focused test.

---

### Task 1: Add ZXing dep + shared device-login models + API

**Files:**
- Modify: `/opt/prairie-android/gradle/libs.versions.toml`
- Modify: `/opt/prairie-android/shared/build.gradle.kts`
- Create: `/opt/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/auth/DeviceLoginModels.kt`
- Create: `/opt/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/api/DeviceLoginApi.kt`
- Possibly modify: existing `AuthApi.kt` (depends on whether the project keeps API interfaces unified per resource or per endpoint — explore at task time)

**Why:** Models + API interface mirror Apple's shapes verbatim so the wire format is identical to what the server expects.

- [ ] **Step 1: ZXing dep — Android-side only (it's used by the QR renderer in androidTvApp, not in commonMain).**

In `libs.versions.toml` `[versions]` after `koin-workmanager = "4.1.0"`, add:

```toml
zxing-core = "3.5.3"
```

In `[libraries]` after `koin-androidx-workmanager`, add:

```toml
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing-core" }
```

In `androidTvApp/build.gradle.kts` `androidMain.dependencies { … }` after `koin-androidx-workmanager`:

```kotlin
            // QR-code rendering for device-login (sub-project C).
            implementation(libs.zxing.core)
```

- [ ] **Step 2: Create `DeviceLoginModels.kt` in `shared/commonMain/.../model/auth/`**

```kotlin
package com.continuum.app.model.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceLoginStartRequest(
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_platform") val devicePlatform: String? = null,
)

/**
 * `deviceCode` is the TV-only secret used for polling; never display it.
 * `verificationUriComplete` is the URL encoded into the QR — scanning it
 * deep-links into the web app's activation page.
 */
@Serializable
data class DeviceLoginStartResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("match_code") val matchCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String,
    @SerialName("expires_at") val expiresAt: String,  // ISO-8601 string; UI parses lazily
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_platform") val devicePlatform: String,
)

@Serializable
data class DeviceLoginPollRequest(
    @SerialName("device_code") val deviceCode: String,
)

/**
 * Token fields are only populated on the first `approved` response — the
 * server marks the record consumed atomically, so the client must capture
 * them immediately on that single reply.
 */
@Serializable
data class DeviceLoginPollResponse(
    val status: String,
    @SerialName("poll_after") val pollAfter: Int? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: AuthUser? = null,
)

enum class DeviceLoginStatus {
    Pending, Approved, Denied, Expired, Consumed, Unknown;

    companion object {
        fun fromWire(raw: String): DeviceLoginStatus = when (raw) {
            "pending" -> Pending
            "approved" -> Approved
            "denied" -> Denied
            "expired" -> Expired
            "consumed" -> Consumed
            else -> Unknown
        }
    }
}
```

`AuthUser` already exists in `shared/.../model/auth/AuthModels.kt` (per audit). If the actual class name there is different (e.g. `User` or `AuthUserDto`), use whatever the existing login response uses to deserialize the user — the field is identical in shape.

- [ ] **Step 3: Create `DeviceLoginApi.kt` in `shared/commonMain/.../network/api/`**

The project likely organizes per-resource APIs (one file per resource). Look at the existing `AuthApi.kt` for the pattern:

```bash
cat /opt/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/api/AuthApi.kt
```

Mirror its style. Sketch (adapt to project's actual Ktor wrapping):

```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.auth.DeviceLoginPollRequest
import com.continuum.app.model.auth.DeviceLoginPollResponse
import com.continuum.app.model.auth.DeviceLoginStartRequest
import com.continuum.app.model.auth.DeviceLoginStartResponse
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

interface DeviceLoginApi {
    suspend fun startDeviceLogin(
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse>

    /**
     * Returns 404 in [ApiResult.Error] with statusCode 404 when the server
     * has cleaned up the pairing row (expired pre-approval).
     */
    suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse>
}

class DefaultDeviceLoginApi(private val client: HttpClient) : DeviceLoginApi {

    override suspend fun startDeviceLogin(
        deviceName: String?,
        devicePlatform: String?,
    ): ApiResult<DeviceLoginStartResponse> = runApiCall {
        client.post("/api/v1/auth/device/start") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginStartRequest(deviceName, devicePlatform))
        }.body()
    }

    override suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse> = runApiCall {
        client.post("/api/v1/auth/device/poll") {
            contentType(ContentType.Application.Json)
            setBody(DeviceLoginPollRequest(deviceCode))
        }.body()
    }
}
```

The `runApiCall { ... }` helper is whatever the existing AuthApi uses for the try/catch + status-code wrap. Match its signature exactly. If the existing pattern returns a typed `ApiResult` with `Error(statusCode = ...)`, the 404 handling in Task 2 will inspect it via that field.

- [ ] **Step 4: Koin registration for the new API**

In whatever Koin module wires the existing `AuthApi` (likely `shared/.../di/NetworkModule.kt` or similar), add:

```kotlin
single<DeviceLoginApi> { DefaultDeviceLoginApi(get()) }
```

`get()` resolves to the shared Ktor `HttpClient`.

- [ ] **Step 5: Build**

```bash
cd /opt/prairie-android && ./gradlew :shared:compileKotlinAndroid :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL across both modules.

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  gradle/libs.versions.toml \
  androidTvApp/build.gradle.kts \
  shared/src/commonMain/kotlin/com/continuum/app/model/auth/DeviceLoginModels.kt \
  shared/src/commonMain/kotlin/com/continuum/app/network/api/DeviceLoginApi.kt

# Add any DI module that was modified
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  shared/src/commonMain/kotlin/com/continuum/app/di 2>/dev/null || true

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(shared-auth): DeviceLoginApi + models for OAuth device flow (C)

Mirrors Apple's tvOS DeviceLoginModels.swift + AuthService device
endpoints (start + poll). Wire-format @SerialName mappings keep
snake_case JSON matching the server. Returns ApiResult so callers
can inspect 404 (= expired pairing row, server cleaned up).

ZXing core dep added to androidTvApp for the QR renderer that lands
with Task 3. DeviceLoginRepository state machine lands in Task 2."
```

---

### Task 2: `DeviceLoginRepository` state machine + tests

**Files:**
- Create: `/opt/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/repository/DeviceLoginRepository.kt`
- Create: `/opt/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/repository/DeviceLoginRepositoryTest.kt`

**Why:** State machine for the polling loop. UI consumes a `StateFlow<DeviceLoginState>`. Tested in isolation with a fake `DeviceLoginApi`.

- [ ] **Step 1: Create the repository**

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.auth.DeviceLoginPollResponse
import com.continuum.app.model.auth.DeviceLoginStartResponse
import com.continuum.app.model.auth.DeviceLoginStatus
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.DeviceLoginApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the OAuth device-login state machine: initiate → poll loop →
 * terminal (Approved / Failed). Exposes a single [state] StateFlow the
 * UI can observe.
 *
 * Mirrors Apple's [QRLoginViewModel.swift] polling behavior:
 *  - polls immediately after start (don't wait the first interval)
 *  - swallows transient network errors and keeps polling
 *  - 404 on poll = server cleaned up the row → terminal Failed(Expired)
 *  - honors server-requested poll-after override on Pending responses
 */
class DeviceLoginRepository(
    private val api: DeviceLoginApi,
) {
    sealed class DeviceLoginState {
        object Idle : DeviceLoginState()
        object Initiating : DeviceLoginState()
        data class Awaiting(val session: DeviceLoginStartResponse) : DeviceLoginState()
        data class Approved(val response: DeviceLoginPollResponse) : DeviceLoginState()
        data class Failed(val reason: FailureReason, val message: String? = null) : DeviceLoginState()
    }

    enum class FailureReason {
        StartFailed,    // initiate POST returned non-2xx
        Expired,        // polled row gone (404) or status=expired
        Denied,         // status=denied
        Consumed,       // status=consumed (already used)
        MissingTokens,  // status=approved but no access_token returned
        UnknownStatus,  // server returned a status we don't recognize
    }

    private val _state = MutableStateFlow<DeviceLoginState>(DeviceLoginState.Idle)
    val state: StateFlow<DeviceLoginState> = _state.asStateFlow()

    /**
     * Begins a new device-login session. Suspends through initiate;
     * starts polling internally and returns when the state machine
     * reaches a terminal value (Approved or Failed).
     *
     * Call this from a cancellable coroutine — cancel to abort.
     */
    suspend fun begin(deviceName: String?, devicePlatform: String?) {
        _state.value = DeviceLoginState.Initiating

        val session = when (val r = api.startDeviceLogin(deviceName, devicePlatform)) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> {
                _state.value = DeviceLoginState.Failed(
                    FailureReason.StartFailed,
                    "Server returned ${r.statusCode}: ${r.message}",
                )
                return
            }
            is ApiResult.NetworkError -> {
                _state.value = DeviceLoginState.Failed(
                    FailureReason.StartFailed,
                    r.exception.message,
                )
                return
            }
        }

        _state.value = DeviceLoginState.Awaiting(session)
        runPollLoop(session)
    }

    fun reset() {
        _state.value = DeviceLoginState.Idle
    }

    private suspend fun runPollLoop(session: DeviceLoginStartResponse) {
        var intervalMs = session.interval.coerceAtLeast(1) * 1_000L

        while (true) {
            try {
                val response = when (val r = api.pollDeviceLogin(session.deviceCode)) {
                    is ApiResult.Success -> r.data
                    is ApiResult.Error -> {
                        if (r.statusCode == 404) {
                            _state.value = DeviceLoginState.Failed(
                                FailureReason.Expired,
                                "This sign-in request has expired.",
                            )
                            return
                        }
                        // Transient — keep trying.
                        delay(intervalMs)
                        continue
                    }
                    is ApiResult.NetworkError -> {
                        // Transient network blip — keep trying.
                        delay(intervalMs)
                        continue
                    }
                }

                // Honor server's poll-after override if present.
                response.pollAfter?.let { intervalMs = it.coerceAtLeast(1) * 1_000L }

                when (DeviceLoginStatus.fromWire(response.status)) {
                    DeviceLoginStatus.Pending -> {
                        delay(intervalMs)
                    }
                    DeviceLoginStatus.Approved -> {
                        if (response.accessToken.isNullOrBlank() ||
                            response.refreshToken.isNullOrBlank()) {
                            _state.value = DeviceLoginState.Failed(
                                FailureReason.MissingTokens,
                                "Server approved the session but did not return tokens.",
                            )
                            return
                        }
                        _state.value = DeviceLoginState.Approved(response)
                        return
                    }
                    DeviceLoginStatus.Denied -> {
                        _state.value = DeviceLoginState.Failed(
                            FailureReason.Denied,
                            "Sign-in was denied on the other device.",
                        )
                        return
                    }
                    DeviceLoginStatus.Expired -> {
                        _state.value = DeviceLoginState.Failed(
                            FailureReason.Expired,
                            "This code expired before it was approved.",
                        )
                        return
                    }
                    DeviceLoginStatus.Consumed -> {
                        _state.value = DeviceLoginState.Failed(
                            FailureReason.Consumed,
                            "This code has already been used.",
                        )
                        return
                    }
                    DeviceLoginStatus.Unknown -> {
                        _state.value = DeviceLoginState.Failed(
                            FailureReason.UnknownStatus,
                            "Unexpected status: ${response.status}",
                        )
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}
```

`ApiResult.Error` is assumed to have a `statusCode: Int` and `message: String?` per the project's convention; check `/opt/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/ApiResult.kt` to confirm field names and adapt.

- [ ] **Step 2: Create the test**

```kotlin
package com.continuum.app.repository

import com.continuum.app.model.auth.DeviceLoginPollResponse
import com.continuum.app.model.auth.DeviceLoginStartResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.DeviceLoginApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLoginRepositoryTest {

    private fun session(deviceCode: String = "DEV_CODE_123") = DeviceLoginStartResponse(
        deviceCode = deviceCode,
        userCode = "ABCD-1234",
        matchCode = "M1",
        verificationUri = "https://silo.example/device",
        verificationUriComplete = "https://silo.example/device?token=t1",
        expiresAt = "2099-01-01T00:00:00Z",
        expiresIn = 600,
        interval = 1,  // small for tests
        deviceName = "Shield",
        devicePlatform = "androidtv",
    )

    private fun pollResponse(
        status: String = "pending",
        accessToken: String? = null,
        refreshToken: String? = null,
    ) = DeviceLoginPollResponse(
        status = status,
        accessToken = accessToken,
        refreshToken = refreshToken,
    )

    @Test
    fun `start failure transitions to Failed(StartFailed)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Error<DeviceLoginStartResponse>(statusCode = 500, message = "boom")
            override suspend fun pollDeviceLogin(deviceCode: String) =
                error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.StartFailed, state.reason)
    }

    @Test
    fun `pending status keeps polling until approved`() = runTest(UnconfinedTestDispatcher()) {
        var pollCount = 0
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String): ApiResult<DeviceLoginPollResponse> {
                pollCount++
                return if (pollCount < 3) {
                    ApiResult.Success(pollResponse(status = "pending"))
                } else {
                    ApiResult.Success(pollResponse(
                        status = "approved",
                        accessToken = "at",
                        refreshToken = "rt",
                    ))
                }
            }
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        assertIs<DeviceLoginRepository.DeviceLoginState.Approved>(repo.state.value)
        assertTrue(pollCount >= 3)
    }

    @Test
    fun `404 on poll transitions to Failed(Expired)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Error<DeviceLoginPollResponse>(statusCode = 404, message = "Not Found")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.Expired, state.reason)
    }

    @Test
    fun `approved without tokens transitions to Failed(MissingTokens)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Success(pollResponse(status = "approved"))  // no tokens
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.MissingTokens, state.reason)
    }

    @Test
    fun `denied status transitions to Failed(Denied)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Success(pollResponse(status = "denied"))
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.Denied, state.reason)
    }

    @Test
    fun `unknown status transitions to Failed(UnknownStatus)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.Success(session())
            override suspend fun pollDeviceLogin(deviceCode: String) =
                ApiResult.Success(pollResponse(status = "warp_speed_pending"))
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.UnknownStatus, state.reason)
    }

    @Test
    fun `network error on start transitions to Failed(StartFailed)`() = runTest(UnconfinedTestDispatcher()) {
        val api = object : DeviceLoginApi {
            override suspend fun startDeviceLogin(deviceName: String?, devicePlatform: String?) =
                ApiResult.NetworkError<DeviceLoginStartResponse>(exception = RuntimeException("offline"))
            override suspend fun pollDeviceLogin(deviceCode: String) = error("unreachable")
        }
        val repo = DeviceLoginRepository(api)
        repo.begin("d", "p")
        val state = repo.state.value
        assertIs<DeviceLoginRepository.DeviceLoginState.Failed>(state)
        assertEquals(DeviceLoginRepository.FailureReason.StartFailed, state.reason)
    }
}
```

Adapt `ApiResult.Error` / `ApiResult.NetworkError` constructor signatures to whatever the project actually uses (statusCode might be named `code`; constructors might require additional args). The test bodies stay structurally identical.

- [ ] **Step 3: Koin registration**

In the shared repository module (likely `RepositoryModule.kt`):

```kotlin
single { DeviceLoginRepository(get()) }
```

- [ ] **Step 4: Build + run tests**

```bash
cd /opt/prairie-android && ./gradlew :shared:allTests --tests "com.continuum.app.repository.DeviceLoginRepositoryTest"
```

Expected: BUILD SUCCESSFUL + 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  shared/src/commonMain/kotlin/com/continuum/app/repository/DeviceLoginRepository.kt \
  shared/src/commonTest/kotlin/com/continuum/app/repository/DeviceLoginRepositoryTest.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  shared/src/commonMain/kotlin/com/continuum/app/di 2>/dev/null || true

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(shared-auth): DeviceLoginRepository state machine + tests (C)

State machine: Idle → Initiating → Awaiting(session) → Approved(tokens)
| Failed(reason). Polls immediately after start, then at server-supplied
interval (honors poll_after overrides). Transient network errors swallowed;
404 = expired pairing row. 7 unit tests cover the terminal transitions.

UI consumer (TvLoginViewModel) wires this into the QR pane in Task 4."
```

---

### Task 3: `QrCodePanel` Compose composable

**Files:**
- Create: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/QrCodePanel.kt`

**Why:** Pure UI — encode a URL into a QR matrix via ZXing, draw it onto a Compose Canvas. Reusable; not bound to device-login specifics.

- [ ] **Step 1: Create the composable**

```kotlin
package com.continuum.app.tv.ui.screens.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code into a square Compose Canvas of [size]×[size].
 *
 * Uses ZXing's [QRCodeWriter] with error correction level M (15% tolerance)
 * — enough to survive TV-screen reflections / phone camera angles without
 * inflating the module count needlessly for short URLs.
 */
@Composable
fun QrCodePanel(
    content: String,
    size: Dp = 320.dp,
    foreground: Color = Color.Black,
    background: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) {
        val writer = QRCodeWriter()
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,  // We add our own padding via the surrounding Box.
        )
        // 256 is the requested matrix dimension; ZXing rounds to module count.
        writer.encode(content, BarcodeFormat.QR_CODE, 256, 256, hints)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val moduleCount = matrix.width
            val modulePx = this.size.minDimension / moduleCount
            for (y in 0 until moduleCount) {
                for (x in 0 until moduleCount) {
                    if (matrix.get(x, y)) {
                        drawRectModule(
                            color = foreground,
                            topLeft = Offset(x * modulePx, y * modulePx),
                            size = Size(modulePx, modulePx),
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRectModule(
    color: Color,
    topLeft: Offset,
    size: Size,
) {
    drawRect(color = color, topLeft = topLeft, size = size)
}
```

- [ ] **Step 2: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/QrCodePanel.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-auth): QrCodePanel — ZXing-backed Compose QR renderer (C)

Pure UI: encodes a URL into a QR matrix via ZXing's QRCodeWriter and
draws each module onto a Compose Canvas. Reusable component (not
device-login-specific). Error-correction level M (15% tolerance).

TvLoginViewModel wires real content into this panel in Task 4."
```

---

### Task 4: Wire `TvLoginViewModel` + `TvLoginScreen` to consume `DeviceLoginRepository`

**Files:**
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginViewModel.kt`
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginScreen.kt`
- Modify: `/opt/prairie-android/androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt`

**Why:** End-to-end activation. The ViewModel now runs parallel credential + device flows; whichever succeeds first wins. The `QrPlaceholderCard` shipped by A.4 is replaced with a real QR + status display.

This is the largest task. Implementer should read each file before editing.

- [ ] **Step 1: Add `DeviceLoginRepository` to ViewModel constructor + Koin**

In `TvLoginViewModel.kt`:
```kotlin
class TvLoginViewModel(
    // … existing deps …
    private val deviceLogin: DeviceLoginRepository,
    // possibly inject the TokenManager / AuthService too if the existing
    // login() path already does so; the device-login Approved branch needs
    // to save tokens via whatever channel the credential path uses.
) : ViewModel() {
    // …
}
```

Add Koin: in `AndroidTvModule.kt` find `viewModel { TvLoginViewModel(...) }` and add a `get()` for `DeviceLoginRepository`.

- [ ] **Step 2: Expose `deviceLoginState: StateFlow<DeviceLoginState>` on the ViewModel + start device flow on init**

In the ViewModel:

```kotlin
val deviceLoginState: StateFlow<DeviceLoginRepository.DeviceLoginState> = deviceLogin.state

init {
    viewModelScope.launch {
        deviceLogin.begin(
            deviceName = android.os.Build.MODEL,
            devicePlatform = "androidtv",
        )
        // begin() returns when the state machine reaches a terminal state.
        val terminal = deviceLogin.state.value
        if (terminal is DeviceLoginRepository.DeviceLoginState.Approved) {
            // Save tokens via the same path the credential login uses.
            val response = terminal.response
            tokenManager.setAccessToken(response.accessToken!!)
            tokenManager.setRefreshToken(response.refreshToken!!)
            _uiState.update { it.copy(loginSuccess = true) }
        }
    }
}
```

Verify the exact `TokenManager` method names and adapt — the existing credential `login()` already does this; mirror its path. Inject `tokenManager` if not already done.

If the existing login flow does more after token capture (server health-check, user fetch, profile resolution), the device-flow's Approved branch should mirror those steps too. Read `AuthRepository.login()` for the canonical path:

```bash
cat /opt/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/repository/AuthRepository.kt
```

- [ ] **Step 3: Replace `QrPlaceholderCard` with a real QR pane in `TvLoginScreen.kt`**

In `TvLoginScreen.kt`, modify the `QrPlaceholderCard` (introduced in A.4) to take the device-login state and render accordingly:

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrLoginCard(
    state: DeviceLoginRepository.DeviceLoginState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier
            .background(ElevatedSurface, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        Text(
            text = "Sign in with your phone",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        when (state) {
            DeviceLoginRepository.DeviceLoginState.Idle,
            DeviceLoginRepository.DeviceLoginState.Initiating -> {
                Text(
                    text = "Loading device-login code…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                )
            }
            is DeviceLoginRepository.DeviceLoginState.Awaiting -> {
                Text(
                    text = "Scan the code with your phone's camera",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QrCodePanel(
                    content = state.session.verificationUriComplete,
                    size = 320.dp,
                )
                Text(
                    text = "Code: ${state.session.userCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is DeviceLoginRepository.DeviceLoginState.Approved -> {
                Text(
                    text = "Signed in!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is DeviceLoginRepository.DeviceLoginState.Failed -> {
                Text(
                    text = state.message ?: "Sign-in failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TvHeroActionPill(
                    label = "Try again",
                    icon = Icons.Default.Refresh,
                    variant = TvPillVariant.Outlined,
                    onClick = onRetry,
                )
            }
        }
    }
}
```

Replace the existing `QrPlaceholderCard` invocation in `TvLoginScreen`'s split-panel `Row` with `QrLoginCard(state = deviceState, onRetry = viewModel::restartDeviceLogin)`.

Delete the old `QrPlaceholderCard` composable (no other callers).

Add `restartDeviceLogin()` method to `TvLoginViewModel`:

```kotlin
fun restartDeviceLogin() {
    viewModelScope.launch {
        deviceLogin.reset()
        deviceLogin.begin(
            deviceName = android.os.Build.MODEL,
            devicePlatform = "androidtv",
        )
        val terminal = deviceLogin.state.value
        if (terminal is DeviceLoginRepository.DeviceLoginState.Approved) {
            // (same token-save path as in init)
        }
    }
}
```

Better: extract the token-save block into a private helper to avoid duplication.

- [ ] **Step 4: Build**

```bash
cd /opt/prairie-android && ./gradlew :androidTvApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual emulator/device verification — SKIPPED per memory policy.**

The implementer should NOT build + adb-install (user explicitly opted out of auto-install). Document in commit message that manual QA on the Shield is required to verify:
- QR renders correctly
- Polling state progresses through Initiating → Awaiting → Approved when a user scans + confirms
- Credential login still works (race correctness)
- Restart on Failed state works

- [ ] **Step 6: Commit**

```bash
git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android add \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginViewModel.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/auth/TvLoginScreen.kt \
  androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/di/AndroidTvModule.kt

git -c user.name="rxwatcher" -c user.email="rxwatcher@users.noreply.github.com" -C /opt/prairie-android commit -m "feat(tv-auth): wire DeviceLoginRepository into TvLoginScreen QR pane (C)

TvLoginViewModel now runs the credential flow and device-login flow
in parallel from init. Whichever completes first wins; the other can
be cancelled implicitly via the loginSuccess transition.

QrPlaceholderCard (from A.4) replaced by QrLoginCard which renders
the live verification_uri_complete QR via ZXing and switches to a
'Signed in!' / error / retry display based on the DeviceLoginState.
Restart button on Failed.

Manual QA on emulator/Shield required to verify: QR renders, polling
state progresses, credential login still works, restart flow works."
```

---

## Self-Review

**Spec coverage** (against C section in the spec):
- C.1 OAuth Device Authorization Grant model → Task 1 ✓ (mirrors Apple's wire shape verbatim)
- C.2 `DeviceLoginRepository` with `StateFlow<DeviceLoginState>` → Task 2 ✓ (Idle / Initiating / Awaiting / Approved / Failed)
- C.3 KMP placement so phone inherits → Tasks 1 + 2 ship in `shared/commonMain` ✓
- C.4 ZXing dep + `QrCodePanel` composable → Tasks 1 (dep) + 3 (composable) ✓
- C.5 `TvLoginViewModel` parallel flows → Task 4 ✓
- C.6 Wire into A.4 split-panel placeholder → Task 4 ✓
- C.7 Tests in `shared/commonTest` → Task 2 ✓ (7 cases)
- "Verify against Apple's actual endpoints" → done as plan preamble (read `AuthService.swift:209-227`; confirmed `/api/v1/auth/device/start` + `/poll`)

**Placeholder scan:** No "TBD." Two adaptation points are explicit: (1) `AuthUser` class name in the existing `AuthModels.kt`, (2) `ApiResult.Error` field names. Both flagged in-task.

**Type consistency:** `DeviceLoginStartResponse` / `DeviceLoginPollResponse` / `DeviceLoginStatus` referenced consistently across Tasks 1-4. `DeviceLoginState` sealed-class hierarchy stable.

**Sequencing:** 1 (models + API) → 2 (repository + tests) → 3 (QR composable) → 4 (UI wiring). Each task's commit is independently buildable.

**Risk:** Task 4's "race between credential + device" pattern is the highest-risk piece. Both flows ultimately set `_uiState.loginSuccess = true`; if both win simultaneously the second is a no-op (StateFlow dedup). The actual token storage happens only on whichever flow's success branch runs first — verify there's no double-save. Adapting to the actual AuthRepository.login() pattern reduces this risk; deviating from it increases it.
