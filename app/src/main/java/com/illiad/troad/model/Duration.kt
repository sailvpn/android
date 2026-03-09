package com.illiad.troad.model

enum class Duration(val minutes: Long, val label: String) {
    ONE_HOUR(60, "1 Hour (60 minutes)"),
    THREE_HOURS(180, "3 Hours (180 minutes)"),
    SIX_HOURS(360, "6 Hours (360 minutes)"),
    TWELVE_HOURS(720, "12 Hours (720 minutes)"),
    TWENTY_HOURS(1200, "20 Hours (1200 minutes)"),
    ONE_DAY(1440, "1 Day (1,440 minutes)"),
    THREE_DAYS(4320, "3 Days (4,320 minutes)"),
    ONE_WEEK(10080, "1 Week (10,080 minutes)"),
    TWO_WEEKS(20160, "2 Weeks (20,160 minutes)"),
    ONE_MONTH(43200, "1 Month (4,3200 minutes)"),
    TWO_MONTHS(86400, "2 Months (86,400 minutes)"),
    THREE_MONTHS(129600, "3 Months (129,600 minutes)"),
    SIX_MONTHS(259200, "6 Months (259,200 minutes)");

    companion object {
        // Helper to find an enum by its value (useful for DataStore/API results)
        fun fromMinutes(minutes: Long): Duration {
            return entries.find { it.minutes == minutes } ?: ONE_HOUR
        }

        fun fromLabel(label: String): Duration {
            return entries.find { it.label == label } ?: ONE_HOUR
        }

        val DEFAULT = ONE_HOUR
    }
}
