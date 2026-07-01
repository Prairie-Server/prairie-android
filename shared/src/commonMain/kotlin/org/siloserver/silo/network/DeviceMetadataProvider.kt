package org.siloserver.silo.network

data class SiloDeviceMetadata(
    val id: String,
    val name: String,
    val platform: String,
)

interface DeviceMetadataProvider {
    suspend fun current(): SiloDeviceMetadata?
}
