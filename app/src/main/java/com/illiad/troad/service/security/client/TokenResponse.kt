package com.illiad.troad.service.security.client

import kotlinx.serialization.Serializable

/**
 * Response object for token generation endpoint.
 * Contains the generated JWT token and its expiration time.
 */

@Serializable
data class TokenResponse(
    val success: Boolean? = null,
    val reasonCode: Int? = 0, // numeric reason code for programmatic handling (0 == OK/unset)
    val data: Data? = null
)
