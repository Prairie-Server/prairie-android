package org.siloserver.silo.network.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.siloserver.silo.model.auth.*
import org.siloserver.silo.network.ApiErrorBody
import org.siloserver.silo.network.ApiResult

class AuthApi(private val client: HttpClient) {

    suspend fun login(request: LoginRequest): ApiResult<LoginResponse> = safeApiCall {
        client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun refresh(request: RefreshRequest): ApiResult<RefreshResponse> = safeApiCall {
        client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun signup(request: SignupRequest): ApiResult<LoginResponse> = safeApiCall {
        client.post("/api/v1/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun setup(
        username: String,
        email: String,
        password: String
    ): ApiResult<LoginResponse> = safeApiCall {
        client.post("/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody(SetupRequest(username = username, email = email, password = password))
        }
    }

    suspend fun getSetupStatus(): ApiResult<SetupStatusResponse> = safeApiCall {
        client.get("/api/v1/auth/setup")
    }

    suspend fun getSignupStatus(): ApiResult<SignupStatusResponse> = safeApiCall {
        client.get("/api/v1/auth/signup")
    }

    suspend fun getMe(): ApiResult<User> = safeApiCall {
        client.get("/api/v1/auth/me")
    }

    suspend fun logout(): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/auth/logout")
    }

    suspend fun getSessions(): ApiResult<SessionsResponse> = safeApiCall {
        client.get("/api/v1/auth/sessions")
    }

    suspend fun revokeSession(id: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/auth/sessions/$id")
    }

    suspend fun deleteSession(id: String): ApiResult<Unit> = revokeSession(id)
}

/**
 * Checks membership endpoints that return HTTP 204 (present) / 404 (absent) with no body.
 *
 * - 2xx  → [ApiResult.Success]`(true)`
 * - 404  → [ApiResult.Success]`(false)`
 * - other HTTP errors → [ApiResult.Error]
 * - network failures  → [ApiResult.NetworkError]
 */
internal suspend fun safeStatusCall(
    block: suspend () -> HttpResponse
): ApiResult<Boolean> {
    return try {
        val response = block()
        when {
            response.status.isSuccess() -> ApiResult.Success(true)
            response.status == HttpStatusCode.NotFound -> ApiResult.Success(false)
            else -> {
                val error = try {
                    response.body<ApiErrorBody>()
                } catch (_: Exception) {
                    ApiErrorBody()
                }
                ApiResult.Error(response.status.value, error.error, error.message)
            }
        }
    } catch (e: Exception) {
        ApiResult.NetworkError(e)
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
