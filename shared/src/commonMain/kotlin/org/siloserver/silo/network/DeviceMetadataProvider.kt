package org.siloserver.silo.network

data class SiloDeviceMetadata(
    val id: String,
    val name: String,
    val platform: String,
    val clientName: String? = null,
    val clientVersion: String? = null,
)

interface DeviceMetadataProvider {
    suspend fun current(): SiloDeviceMetadata?
}
