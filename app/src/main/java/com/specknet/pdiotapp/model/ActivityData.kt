package com.specknet.pdiotapp.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ActivityData(
    val activity: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
) {
    // Optional: Format to a string for display
    @RequiresApi(Build.VERSION_CODES.O)
    fun getFormattedStartTime(): String {
        return startTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getFormattedEndTime(): String {
        return endTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
    }

    // Get the day of the week
    @RequiresApi(Build.VERSION_CODES.O)
    fun getDayOfWeek(): String {
        return startTime.format(DateTimeFormatter.ofPattern("EEEE"))
    }
}