package com.illiad.troad.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeedMetricsPayload(
    val down: Long = 0L,
    val up: Long = 0L
)
