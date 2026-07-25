package org.prairieserver.prairie.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.prairieserver.prairie.model.profile.*
import org.prairieserver.prairie.network.ApiResult

class ProfileApi(private val client: HttpClient) {

    suspend fun listProfiles(): ApiResult<ProfilesResponse> = safeApiCall {
        client.get("/api/v1/profiles")
    }

    suspend fun createProfile(request: CreateProfileRequest): ApiResult<Profile> = safeApiCall {
        client.post("/api/v1/profiles") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateProfile(
        id: String,
        request: UpdateProfileRequest
    ): ApiResult<Profile> = safeApiCall {
        client.put("/api/v1/profiles/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteProfile(id: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/profiles/$id")
    }

    suspend fun verifyPin(
        id: String,
        pin: String
    ): ApiResult<VerifyPinResponse> = safeApiCall {
        client.post("/api/v1/profiles/$id/verify-pin") {
            contentType(ContentType.Application.Json)
            setBody(VerifyPinRequest(pin))
        }
    }
}
