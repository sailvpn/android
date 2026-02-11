package com.illiad.troad.model

enum class Validation(val minutes: Int, val label: String) {
    NULL(0, "Null"),
    ONE_DAY(1440, "1 Day (1,440 minutes)"),
    THREE_DAYS(4320, "3 Days (4,320 minutes)"),
    ONE_WEEK(10080, "1 Week (10,080 minutes)"),
    TWO_WEEKS(20160, "2 Weeks (20,160 minutes)"),
    ONE_MONTH(43200, "1 Month (4,3200 minutes)"),
    THREE_MONTHS(129600, "3 Months (129,600 minutes)"),
    SIX_MONTHS(259200, "6 Months (259,200 minutes)");

    companion object {
        // Helper to find an enum by its value (useful for DataStore/API results)
        fun fromMinutes(minutes: Int): Validation {
            return entries.find { it.minutes == minutes } ?: NULL
        }

        // check for NULL to make surue that the user set a valid Validation v
        val DEFAULT = NULL
    }
}
