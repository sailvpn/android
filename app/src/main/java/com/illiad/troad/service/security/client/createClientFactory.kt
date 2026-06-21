package com.illiad.troad.service.security.client

/**
 * Returns the concrete Android implementation of the HttpClientFactory.
 * This satisfies the TokenManager constructor requirements instantly on Android.
 */
fun createPlatformHttpClientFactory(): HttpClientFactory {
    return AndroidHttpClientFactory()
}
