package com.continuum.app.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.continuum.app.model.admin.*
import com.continuum.app.network.ApiResult
import kotlinx.serialization.json.JsonElement

class AdminApi(private val client: HttpClient) {

    // --- Stats ---

    suspend fun getStats(): ApiResult<AdminStats> = safeApiCall {
        client.get("/api/v1/admin/stats")
    }

    // --- Users ---

    suspend fun getUsers(): ApiResult<AdminUsersResponse> = safeApiCall {
        client.get("/api/v1/admin/users")
    }

    suspend fun createUser(request: CreateUserRequest): ApiResult<AdminUser> = safeApiCall {
        client.post("/api/v1/admin/users") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateUser(
        id: Int,
        request: UpdateUserRequest
    ): ApiResult<AdminUser> = safeApiCall {
        client.put("/api/v1/admin/users/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteUser(id: Int): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/admin/users/$id")
    }

    // --- Settings ---

    suspend fun getSettings(): ApiResult<AdminSettingsResponse> = safeApiCall {
        client.get("/api/v1/admin/settings")
    }

    suspend fun getSetting(key: String): ApiResult<AdminSettingValue> = safeApiCall {
        client.get("/api/v1/admin/settings/$key")
    }

    suspend fun updateSetting(
        key: String,
        request: UpdateSettingRequest
    ): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/admin/settings/$key") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // --- Sessions (active playback) ---

    suspend fun getActiveSessions(): ApiResult<AdminSessionsResponse> = safeApiCall {
        client.get("/api/v1/admin/sessions")
    }

    // --- Libraries ---

    suspend fun getLibraries(): ApiResult<AdminLibrariesResponse> = safeApiCall {
        client.get("/api/v1/admin/libraries")
    }

    // --- Scan ---

    suspend fun triggerScan(request: ScanRequest = ScanRequest()): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/admin/scan") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
