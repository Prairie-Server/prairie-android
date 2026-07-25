package org.prairieserver.prairie.network

data class PrairieDeviceMetadata(
    val id: String,
    val name: String,
    val platform: String,
    val clientName: String? = null,
    val clientVersion: String? = null,
)

interface DeviceMetadataProvider {
    suspend fun current(): PrairieDeviceMetadata?
}
