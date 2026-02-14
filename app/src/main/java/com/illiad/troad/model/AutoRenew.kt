package com.illiad.troad.model

enum class AutoRenew(val minutes: Long, val label: String) {
    NEVER(0, "Never"),
    FIVE_MINUTES(5, "5 Minutes (5 minutes)"),
    TEN_MINUTES(10, "10 Minutes (10 minutes)"),
    FIFTEEN_MINUTES(15, "15 Minutes (15 minutes)"),
    THIRTY_MINUTES(30, "30 Minutes (30 minutes)"),
    ONE_HOUR(60, "1 Hour (60 minutes)"),
    THREE_HOURS(180, "3 Hours (180 minutes)"),
    SIX_HOURS(360, "6 Hours (360 minutes)"),
    TWELVE_HOURS(720, "12 Hours (720 minutes)"),
    TWENTY_HOURS(1200, "20 Hours (1200 minutes)"),
    ONE_DAY(1440, "1 Day (1,440 minutes)"),
    THREE_DAYS(4320, "3 Days (4,320 minutes)");

    companion object {
        // Helper to find an enum by its value (useful for DataStore/API results)
        fun fromMinutes(minutes: Long): AutoRenew {
            return entries.find { it.minutes == minutes } ?: NEVER
        }
        val DEFAULT = NEVER
    }
}