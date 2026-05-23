package com.continuum.app.network

data class ContinuumDeviceMetadata(
    val id: String,
    val name: String,
    val platform: String,
)

interface DeviceMetadataProvider {
    suspend fun current(): ContinuumDeviceMetadata?
}
