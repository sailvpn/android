package com.illiad.troad.service.security.client

/**
 * Request object for token generation.
 * Supports 3 authentication alternatives:
 * 1. Username + Password (direct authentication)
 * 3. Existing valid token (token renewal/refresh)
 *
 * Only ONE of the three alternatives should be provided.
 *
 * Optional: sendEmail flag to have the token sent to user's email
 */

data class TokenGenerateRequest(
    // Alternative 1: Username + Password
    val username: String? = null,
    val password: String? = null,

    // Alternative 3: Existing valid token (for renewal)
    val currentToken: String? = null,

    // Required for all alternatives
    // Use wrapper Long so we can detect when the client omitted this field in JSON
    val expirationMinutes: Long? = null,

    // Optional: send token to user's email
    val sendEmail: Boolean? = false
)
