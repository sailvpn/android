package com.illiad.troad.service.security.client

data class Data(
    val expiresAt: String? = null, // ISO 8601 timestamp when token expires (parseable by Instant.parse)
    val token: String? = null // JWT token
)
