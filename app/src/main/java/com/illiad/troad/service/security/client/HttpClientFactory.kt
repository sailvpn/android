package com.illiad.troad.service.security.client

import io.ktor.client.*

/**
 * Custom architectural interface used to decouple Ktor HttpClient instantiation.
 * Isolates engine-specific configurations (like Android JVM SSL TrustManagers) from your main business logic.
 */
interface HttpClientFactory {

    /**
     * Instantiates an on-demand HttpClient context.
     * @param cacertPem An optional custom CA certificate PEM string to trust for this client session.
     */
    fun createSecureClient(cacertPem: String?): HttpClient
}
