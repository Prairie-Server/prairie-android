package org.siloserver.silo.model.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushDeviceRegisterRequest(
    val platform: String,
    val token: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("push_mode") val pushMode: String = "private_push",
)

@Serializable
data class PushDeviceRegisterResponse(
    val id: String? = null,
    @SerialName("push_mode") val pushMode: String = "private_push",
)
