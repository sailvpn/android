package com.illiad.troad.model
/**
 * The lightweight presentation model consumed by your UI layer.
 * Enforces targeted recomposition and keeps text counters synchronized.
 */
data class SpeedMetrics(
    val down: String = "0.00 B/s",
    val up: String = "0.00 B/s"
)