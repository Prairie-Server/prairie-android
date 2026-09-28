package org.prairieserver.prairie.network.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.prairieserver.prairie.model.auth.*
import org.prairieserver.prairie.network.ApiErrorBody
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.map
import org.prairieserver.prairie.network.singleAttempt
import org.prairieserver.prairie.network.skipPrairieAuth
import org.prairieserver.prairie.network.apiv2.Account
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.network.apiv2.SetupStatus
import org.prairieserver.prairie.network.apiv2.safeApiV2Call

class AuthApi(
    private val client: HttpClient,
    private val apiV2Gate: ApiV2Gate,
) {

    suspend fun login(request: LoginRequest, serverUrl: String? = null): ApiResult<LoginResponse> = safeApiV2Call<TokenPairV2>(apiV2Gate) {
        client.post("${serverUrl?.trimEnd('/').orEmpty()}/api/v2/auth/login") {
            skipPrairieAuth(); singleAttempt()
            contentType(ContentType.Application.Json); setBody(request)
        }.requireAuthStatus(200)
    }.map { it.domain() }

    suspend fun refresh(request: RefreshRequest): ApiResult<RefreshResponse> = safeApiV2Call(apiV2Gate) {
        client.post("/api/v2/auth/refresh") {
            skipPrairieAuth()
            contentType(ContentType.Application.Json); setBody(request)
        }.requireAuthStatus(200)
    }

    suspend fun signup(request: SignupRequest, serverUrl: String? = null): ApiResult<LoginResponse> = safeApiV2Call<TokenPairV2>(apiV2Gate) {
        client.post("${serverUrl?.trimEnd('/').orEmpty()}/api/v2/auth/signup") {
            skipPrairieAuth(); singleAttempt()
            contentType(ContentType.Application.Json); setBody(request)
        }.requireAuthStatus(201)
    }.map { it.domain() }

    suspend fun setup(username: String, email: String, password: String, serverUrl: String? = null): ApiResult<LoginResponse> =
        safeApiV2Call<TokenPairV2>(apiV2Gate) {
            client.post("${serverUrl?.trimEnd('/').orEmpty()}/api/v2/auth/setup") {
                skipPrairieAuth(); singleAttempt()
                contentType(ContentType.Application.Json)
                setBody(SetupRequest(username, email, password))
            }.requireAuthStatus(201)
        }.map { it.domain() }

    suspend fun getSetupStatus(): ApiResult<SetupStatusResponse> =
        safeApiV2Call<SetupStatus>(apiV2Gate) {
            // Public, exactly like the explicit-server variant below — which
            // already opted out. Without this the relative form carries a bearer
            // it never needed, and a dead session would fail it.
            client.get("/api/v2/system/setup") { skipPrairieAuth() }
        }.map { status -> SetupStatusResponse(needsSetup = status.needsSetup) }

    /**
     * Explicit-server form: probes a candidate the app is not connected to
     * yet, so the ACTIVE entry's verdict must not gate it — otherwise an
     * UPDATE_REQUIRED active server would block adding (or reconnecting to)
     * a supported one. The candidate's own gate is the contract probe the
     * add-server flow runs before switching.
     */
    suspend fun getSetupStatus(serverUrl: String): ApiResult<SetupStatusResponse> =
        safeApiV2Call<SetupStatus>(ApiV2Gate.Unrestricted) {
            client.get("${serverUrl.trimEnd('/')}/api/v2/system/setup") {
                skipPrairieAuth()
            }
        }.map { status -> SetupStatusResponse(needsSetup = status.needsSetup) }

    suspend fun getSignupStatus(): ApiResult<SignupStatusResponse> = safeApiV2Call(apiV2Gate) {
        client.get("/api/v2/auth/signup") { skipPrairieAuth() }
    }

    suspend fun getSignupStatus(serverUrl: String): ApiResult<SignupStatusResponse> = safeApiV2Call(ApiV2Gate.Unrestricted) {
        client.get("${serverUrl.trimEnd('/')}/api/v2/auth/signup") {
            skipPrairieAuth()
        }
    }

    /**
     * Resolves an emailed-invitation claim token against the given server.
     * Unauthenticated: this runs before any account exists.
     */
    suspend fun lookupInvitation(
        serverUrl: String,
        token: String,
    ): ApiResult<InvitationLookupResponse> = safeApiV2Call(ApiV2Gate.Unrestricted) {
        // The token arrives from an emailed link and is not ours to trust as
        // path-safe: a '/' or '?' in it would otherwise re-shape the request.
        client.get("${serverUrl.trimEnd('/')}/api/v2/invitations/${token.encodeURLPathPart()}") {
            skipPrairieAuth()
        }.requireAuthStatus(200)
    }

    suspend fun invitationCapabilities(serverUrl: String): ApiResult<InvitationCapabilities> =
        safeApiV2Call(ApiV2Gate.Unrestricted) {
            client.get("${serverUrl.trimEnd('/')}/api/v2/invitations/capabilities") { skipPrairieAuth() }
                .requireAuthStatus(200)
        }

    suspend fun acceptInvitation(serverUrl: String, token: String, password: String): ApiResult<InvitationAcceptance> =
        safeApiV2Call<InvitationAcceptanceV2>(ApiV2Gate.Unrestricted) {
            client.post("${serverUrl.trimEnd('/')}/api/v2/invitations/${token.encodeURLPathPart()}/accept") {
                skipPrairieAuth(); singleAttempt()
                contentType(ContentType.Application.Json)
                setBody(AcceptInvitationRequest(password = password))
            }.requireAuthStatus(201)
        }.map { it.domain() }

    suspend fun getMe(): ApiResult<User> =
        safeApiV2Call<Account>(apiV2Gate) { client.get("/api/v2/account/me") }.map { account -> account.toUser() }

    suspend fun logout(): ApiResult<Unit> = safeApiV2Call(apiV2Gate) {
        client.post("/api/v2/auth/logout").requireAuthStatus(204)
    }

}

/**
 * Wraps an HTTP call in error handling, returning a typed [ApiResult].
 *
 * On success (2xx), deserializes the response body to [T].
 * On HTTP error, attempts to parse a standard [ApiErrorBody].
 * On network/parsing exceptions, returns [ApiResult.NetworkError].
 */
internal suspend inline fun <reified T> safeApiCall(
    block: () -> HttpResponse
): ApiResult<T> {
    return try {
        val response = block()
        if (response.status.isSuccess()) {
            // Handle Unit return type for endpoints that return no body
            if (T::class == Unit::class) {
                @Suppress("UNCHECKED_CAST")
                ApiResult.Success(Unit as T)
            } else {
                ApiResult.Success(response.body<T>())
            }
        } else {
            val error = try {
                response.body<ApiErrorBody>()
            } catch (_: Exception) {
                ApiErrorBody()
            }
            ApiResult.Error(response.status.value, error.error, error.message)
        }
    } catch (e: Exception) {
        ApiResult.NetworkError(e)
    }
}

/** Adapts the v2 account to the v1-shaped [User] the repositories and screens consume. */
internal fun Account.toUser(): User = User(
    id = id,
    username = username,
    email = email,
    role = role.wire,
    downloadAllowed = downloadAllowed,
    impersonation = impersonation?.let {
        ImpersonationInfo(
            active = it.active,
            impersonatorUserId = it.impersonatorUserId,
            impersonatorUsername = it.impersonatorUsername,
        )
    },
)
