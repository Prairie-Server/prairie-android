package com.continuum.app.network.api

import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get

open class HealthApi(private val client: HttpClient) {

    open suspend fun checkHealth(): ApiResult<Unit> = safeApiCall {
        client.get("/api/v1/health")
    }
}
