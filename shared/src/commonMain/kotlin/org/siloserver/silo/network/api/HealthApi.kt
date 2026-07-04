package org.siloserver.silo.network.api

import org.siloserver.silo.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    @SerialName("server_name")
    val serverName: String? = null,
    @SerialName("server_id")
    val serverId: String? = null,
)

open class HealthApi(private val client: HttpClient) {

    open suspend fun checkHealth(): ApiResult<HealthStatus> = safeApiCall {
        client.get("/api/v1/health")
    }
}
