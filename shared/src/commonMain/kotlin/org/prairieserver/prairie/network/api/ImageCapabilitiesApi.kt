package org.prairieserver.prairie.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.authScope
import org.prairieserver.prairie.network.requirePrairieAuth
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.network.apiv2.OwnerPolicy
import org.prairieserver.prairie.network.apiv2.ownedV2Call

@Serializable
data class ImageCapabilities(
    @SerialName("storage_backend") val storageBackend: String? = null,
    val delivery: String? = null,
)

class ImageCapabilitiesApi(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val gate: ApiV2Gate,
) {
    suspend fun get(scope: AuthScopeSnapshot): ApiResult<ImageCapabilities> =
        ownedV2Call<ImageCapabilities, ImageCapabilities>(gate, tokens, scope, OwnerPolicy.FULL, null, {
            client.get("/api/v2/images/capabilities") { authScope(scope); requirePrairieAuth() }
        }) { it }
}
