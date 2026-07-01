package org.siloserver.silo.model.common

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String = "",
    val message: String = ""
)

@Serializable
data class PaginatedRequest(
    val offset: Int? = null,
    val limit: Int? = null,
    val sort: String? = null,
    val order: String? = null
)
