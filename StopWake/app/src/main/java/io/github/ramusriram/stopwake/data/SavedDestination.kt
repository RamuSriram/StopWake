package io.github.ramusriram.stopwake.data

import com.google.gson.annotations.SerializedName
import java.util.Locale
import java.util.UUID

/**
 * Represents a saved destination that the user can quickly reuse.
 */
data class SavedDestination(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val alertDistanceMeters: Int = 500,
    @SerializedName(value = "alertType", alternate = ["alertTypeValue"])
    val alertType: Int = 2,
    val reminderNote: String = "",
    val emergencyContact: String = "",
    val isFavorite: Boolean = false,
    @SerializedName(value = "timestamp", alternate = ["createdAt"])
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a completed or past tracking trip entry in history.
 */
data class TripHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val destinationName: String,
    val latitude: Double,
    val longitude: Double,
    val alertDistanceMeters: Int,
    val startedAt: Long,
    val endedAt: Long = System.currentTimeMillis(),
    val outcome: String, // e.g., "Confirmed Safe Arrival", "Arrived", "Stopped", "Emergency Alert Sent"
    val minDistanceReachedMeters: Float? = null
)

/**
 * Represents an automated recurring commute schedule that starts and stops tracking
 * on selected days of the week (1 = Monday .. 7 = Sunday).
 */
data class CommuteSchedule(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val alertDistanceMeters: Int = 300,
    @SerializedName(value = "alertTypeValue", alternate = ["alertType"])
    val alertTypeValue: Int = 3,
    val reminderNote: String = "",
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 9,
    val endMinute: Int = 30,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6), // Default: Mon–Sat (every day except Sunday)
    val isEnabled: Boolean = false
) {
    fun formattedStartTime(): String = formatTime(startHour, startMinute)
    fun formattedEndTime(): String = formatTime(endHour, endMinute)

    fun formattedDaysSummary(): String {
        val sorted = daysOfWeek.sorted()
        return when {
            sorted == listOf(1, 2, 3, 4, 5, 6, 7) -> "Every day"
            sorted == listOf(1, 2, 3, 4, 5, 6) -> "Mon – Sat (Except Sun)"
            sorted == listOf(1, 2, 3, 4, 5) -> "Weekdays (Mon – Fri)"
            sorted == listOf(6, 7) -> "Weekends (Sat – Sun)"
            sorted.isEmpty() -> "No days selected"
            else -> sorted.joinToString(", ") { dayShortName(it) }
        }
    }

    companion object {
        fun formatTime(hour24: Int, minute: Int): String {
            val period = if (hour24 < 12) "AM" else "PM"
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
        }

        fun dayShortName(day: Int): String = when (day) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> ""
        }

        fun daySingleLetter(day: Int): String = when (day) {
            1 -> "M"
            2 -> "T"
            3 -> "W"
            4 -> "T"
            5 -> "F"
            6 -> "S"
            7 -> "S"
            else -> ""
        }
    }
}

/**
 * Portable JSON bundle for exporting, sharing, and importing Saved Stops and Commute Schedules
 * across devices or with friends.
 */
data class StopWakeBackupPayload(
    @SerializedName(value = "app", alternate = ["appName"])
    val app: String = "StopWake",
    @SerializedName(value = "version", alternate = ["schemaVersion"])
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    @SerializedName(value = "savedDestinations", alternate = ["destinations", "stops"])
    val savedDestinations: List<SavedDestination> = emptyList(),
    @SerializedName(value = "commuteSchedules", alternate = ["schedules"])
    val commuteSchedules: List<CommuteSchedule> = emptyList()
)

/**
 * Summary result returned after importing a StopWake JSON configuration file or shared snippet.
 */
data class BackupImportResult(
    val success: Boolean,
    val importedStopsCount: Int = 0,
    val importedSchedulesCount: Int = 0,
    val message: String
)

