package com.illiad.troad.model

object BandwidthFormatter {
    /**
     * Converts a raw byte count per second into matching human-readable labels.
     * e.g., 1024 -> "1.00 KB/s", 5242880 -> "5.00 MB/s"
     */
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0.00 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")
        val digitGroups = (Math.log10(bytesPerSec.toDouble()) / Math.log10(1024.0)).toInt()
        val value = bytesPerSec / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.2f %s", value, units[digitGroups])
    }
}
