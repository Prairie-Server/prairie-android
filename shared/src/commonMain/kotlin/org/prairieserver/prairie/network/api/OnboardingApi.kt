package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.prairieserver.prairie.model.onboarding.OnboardingFlow
import org.prairieserver.prairie.model.onboarding.OnboardingProgressRequest
import org.prairieserver.prairie.model.onboarding.OnboardingState
import org.prairieserver.prairie.network.ApiResult

/**
 * Server-driven onboarding tour. All endpoints are profile-scoped — the auth
 * interceptor attaches X-Profile-Id, so these are only callable once a
 * profile is active.
 */
class OnboardingApi(private val client: HttpClient) {

    /** surface is "phone" or "tv"; the server filters unsuitable steps. */
    suspend fun getFlow(surface: String): ApiResult<OnboardingFlow> = safeApiCall {
        client.get("/api/v1/onboarding/flow") {
            parameter("surface", surface)
        }
    }

    suspend fun getState(): ApiResult<OnboardingState> = safeApiCall {
        client.get("/api/v1/onboarding/state")
    }

    suspend fun postProgress(request: OnboardingProgressRequest): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/onboarding/progress") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
