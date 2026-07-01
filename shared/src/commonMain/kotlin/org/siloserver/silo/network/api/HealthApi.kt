package org.siloserver.silo.network.api

import org.siloserver.silo.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get

open class HealthApi(private val client: HttpClient) {

    open suspend fun checkHealth(): ApiResult<Unit> = safeApiCall {
        client.get("/api/v1/health")
    }
}
