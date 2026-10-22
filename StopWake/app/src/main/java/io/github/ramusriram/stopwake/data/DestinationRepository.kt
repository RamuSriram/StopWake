package io.github.ramusriram.stopwake.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.github.ramusriram.stopwake.LocationUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stopwake_prefs")

/**
 * Repository for persisting saved destinations, commute schedules, trip history,
 * portable JSON export/import, and user preferences.
 */
class DestinationRepository(private val context: Context) {
    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        private val SAVED_DESTINATIONS_KEY = stringPreferencesKey("saved_destinations")
        private val COMMUTE_SCHEDULES_KEY = stringPreferencesKey("commute_schedules_v1")
        private val TRIP_HISTORY_KEY = stringPreferencesKey("trip_history")
        private val HAS_CLEANED_LEGACY_PRESETS_KEY = booleanPreferencesKey("has_cleaned_legacy_presets")
        private val LAST_ALERT_DISTANCE_KEY = stringPreferencesKey("last_alert_distance")
        private val LAST_ALERT_TYPE_KEY = stringPreferencesKey("last_alert_type")
        private val LAST_ALERT_LEAD_TIME_MINS_KEY = intPreferencesKey("last_alert_lead_time_mins")
        private val LAST_ALERT_SOUND_OPTION_KEY = intPreferencesKey("last_alert_sound_option")
        private val LAST_CUSTOM_RINGTONE_URI_KEY = stringPreferencesKey("last_custom_ringtone_uri")
        private val LAST_CUSTOM_RINGTONE_TITLE_KEY = stringPreferencesKey("last_custom_ringtone_title")
        private val LAST_EMERGENCY_CONTACT_KEY = stringPreferencesKey("last_emergency_contact")
        private val LAST_EMERGENCY_CONTACT_NAME_KEY = stringPreferencesKey("last_emergency_contact_name")
        private val LAST_OVERSHOOT_MARGIN_KEY = intPreferencesKey("last_overshoot_margin")
        private val LAST_UNACK_TIMEOUT_MINS_KEY = intPreferencesKey("last_unack_timeout_mins")
        private val LAST_SNOOZE_MINUTES_KEY = intPreferencesKey("last_snooze_minutes")
        private val HIGH_ACCURACY_MODE_KEY = booleanPreferencesKey("high_accuracy_mode")
    }

    /** Flow of all saved destinations, sorted by favorite first then most recent. */
    val savedDestinations: Flow<List<SavedDestination>> = context.dataStore.data.map { prefs ->
        val json = prefs[SAVED_DESTINATIONS_KEY]
        if (json.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<SavedDestination>>() {}.type
                val list: List<SavedDestination> = gson.fromJson(json, type) ?: emptyList()
                list.map { sanitizeDestination(it) }.sortedWith(
                    compareByDescending<SavedDestination> { it.isFavorite }
                        .thenByDescending { it.timestamp }
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /** Flow of all recurring commute schedules. */
    val commuteSchedules: Flow<List<CommuteSchedule>> = context.dataStore.data.map { prefs ->
        parseScheduleList(prefs[COMMUTE_SCHEDULES_KEY])
    }

    /** Flow of past trip history entries, sorted by most recent first. */
    val tripHistory: Flow<List<TripHistoryEntry>> = context.dataStore.data.map { prefs ->
        val json = prefs[TRIP_HISTORY_KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<TripHistoryEntry>>() {}.type
            val list: List<TripHistoryEntry> = gson.fromJson(json, type) ?: emptyList()
            list.sortedByDescending { it.endedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Clean up any legacy hardcoded sample stops from earlier builds so the app is 100% universal. */
    suspend fun ensurePresetsSeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[HAS_CLEANED_LEGACY_PRESETS_KEY] != true) {
                val currentList = parseSavedList(prefs[SAVED_DESTINATIONS_KEY])
                val hadLegacy = currentList.removeAll { it.id.startsWith("preset_") }
                if (hadLegacy) {
                    prefs[SAVED_DESTINATIONS_KEY] = gson.toJson(currentList)
                }
                prefs[HAS_CLEANED_LEGACY_PRESETS_KEY] = true
            }
        }
    }

    /**
     * Exports all saved destinations and commute schedules into a formatted, human-readable JSON string
     * that can be saved as a `.json` file or shared directly with friends.
     */
    suspend fun exportBackupJson(): String {
        val currentStops = savedDestinations.first()
        val currentSchedules = commuteSchedules.first()
        val payload = StopWakeBackupPayload(
            app = "StopWake",
            version = 1,
            exportedAt = System.currentTimeMillis(),
            savedDestinations = currentStops,
            commuteSchedules = currentSchedules
        )
        return prettyGson.toJson(payload)
    }

    /**
     * Imports Saved Stops and/or Commute Schedules from a JSON string (supports full StopWakeBackupPayload,
     * a JSON array of SavedDestinations, or a single SavedDestination JSON object).
     */
    suspend fun importBackupJson(rawJson: String): BackupImportResult {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) {
            return BackupImportResult(
                success = false,
                message = "The selected file or text is empty."
            )
        }

        val parsedStops = mutableListOf<SavedDestination>()
        val parsedSchedules = mutableListOf<CommuteSchedule>()

        val jsonCandidate = if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            val firstObj = trimmed.indexOf('{')
            val lastObj = trimmed.lastIndexOf('}')
            if (firstObj >= 0 && lastObj > firstObj) {
                trimmed.substring(firstObj, lastObj + 1)
            } else {
                trimmed
            }
        } else {
            trimmed
        }

        try {
            val root = JsonParser.parseString(jsonCandidate)
            when {
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    if (
                        obj.has("savedDestinations") ||
                        obj.has("destinations") ||
                        obj.has("stops") ||
                        obj.has("commuteSchedules") ||
                        obj.has("schedules")
                    ) {
                        val payload = gson.fromJson(jsonCandidate, StopWakeBackupPayload::class.java)
                        payload?.savedDestinations?.let { parsedStops.addAll(it) }
                        payload?.commuteSchedules?.let { parsedSchedules.addAll(it) }
                    } else if (obj.has("latitude") && obj.has("longitude")) {
                        val singleStop = gson.fromJson(jsonCandidate, SavedDestination::class.java)
                        if (singleStop != null) parsedStops.add(singleStop)
                    }
                }
                root.isJsonArray -> {
                    val listType = object : TypeToken<List<SavedDestination>>() {}.type
                    val list: List<SavedDestination>? = gson.fromJson(jsonCandidate, listType)
                    if (list != null) parsedStops.addAll(list)
                }
            }
        } catch (_: Exception) {
            return BackupImportResult(
                success = false,
                message = "Invalid JSON format. Please select a valid StopWake backup or stop file."
            )
        }

        val validStops = parsedStops
            .map { sanitizeDestination(it) }
            .filter { LocationUtils.isValidCoordinate(it.latitude, it.longitude) }

        val validSchedules = parsedSchedules
            .map { sanitizeSchedule(it) }
            .filter { LocationUtils.isValidCoordinate(it.destinationLat, it.destinationLng) }

        if (validStops.isEmpty() && validSchedules.isEmpty()) {
            return BackupImportResult(
                success = false,
                message = "No valid stops or schedules were found in the provided data."
            )
        }

        context.dataStore.edit { prefs ->
            if (validStops.isNotEmpty()) {
                val mergedStops = parseSavedList(prefs[SAVED_DESTINATIONS_KEY])
                for (incoming in validStops) {
                    mergedStops.removeAll { existing ->
                        existing.id == incoming.id ||
                            (kotlin.math.abs(existing.latitude - incoming.latitude) < 0.0003 &&
                                kotlin.math.abs(existing.longitude - incoming.longitude) < 0.0003) ||
                            existing.name.equals(incoming.name, ignoreCase = true)
                    }
                    mergedStops.add(incoming.copy(timestamp = System.currentTimeMillis()))
                }
                prefs[SAVED_DESTINATIONS_KEY] = gson.toJson(mergedStops)
            }

            if (validSchedules.isNotEmpty()) {
                val mergedSchedules = parseScheduleList(prefs[COMMUTE_SCHEDULES_KEY]).toMutableList()
                for (incoming in validSchedules) {
                    mergedSchedules.removeAll { existing ->
                        existing.id == incoming.id ||
                            (existing.label.equals(incoming.label, ignoreCase = true) &&
                                existing.startHour == incoming.startHour &&
                                existing.startMinute == incoming.startMinute)
                    }
                    mergedSchedules.add(incoming)
                }
                prefs[COMMUTE_SCHEDULES_KEY] = gson.toJson(mergedSchedules)
            }
        }

        val parts = buildList {
            if (validStops.isNotEmpty()) {
                add("${validStops.size} ${if (validStops.size == 1) "stop" else "stops"}")
            }
            if (validSchedules.isNotEmpty()) {
                add("${validSchedules.size} ${if (validSchedules.size == 1) "schedule" else "schedules"}")
            }
        }
        return BackupImportResult(
            success = true,
            importedStopsCount = validStops.size,
            importedSchedulesCount = validSchedules.size,
            message = "Imported ${parts.joinToString(" and ")} successfully"
        )
    }

    /** Save a new destination or update an existing one. */
    suspend fun saveDestination(destination: SavedDestination) {
        context.dataStore.edit { prefs ->
            val list = parseSavedList(prefs[SAVED_DESTINATIONS_KEY])
            list.removeAll {
                it.id == destination.id ||
                    (kotlin.math.abs(it.latitude - destination.latitude) < 0.0003 &&
                        kotlin.math.abs(it.longitude - destination.longitude) < 0.0003) ||
                    it.name.equals(destination.name, ignoreCase = true)
            }
            list.add(sanitizeDestination(destination))
            prefs[SAVED_DESTINATIONS_KEY] = gson.toJson(list)
        }
    }

    /** Toggle favorite status of a saved destination. */
    suspend fun toggleFavorite(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseSavedList(prefs[SAVED_DESTINATIONS_KEY])
            val updated = list.map {
                if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
            }
            prefs[SAVED_DESTINATIONS_KEY] = gson.toJson(updated)
        }
    }

    /** Delete a saved destination by ID. */
    suspend fun deleteDestination(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseSavedList(prefs[SAVED_DESTINATIONS_KEY])
            list.removeAll { it.id == id }
            prefs[SAVED_DESTINATIONS_KEY] = gson.toJson(list)
        }
    }

    // ── Commute Schedules CRUD ───────────────────────────────

    suspend fun saveSchedule(schedule: CommuteSchedule): List<CommuteSchedule> {
        var updatedList: List<CommuteSchedule> = emptyList()
        context.dataStore.edit { prefs ->
            val list = parseScheduleList(prefs[COMMUTE_SCHEDULES_KEY]).toMutableList()
            val index = list.indexOfFirst { it.id == schedule.id }
            val clean = sanitizeSchedule(schedule)
            if (index >= 0) {
                list[index] = clean
            } else {
                list.add(0, clean)
            }
            updatedList = list
            prefs[COMMUTE_SCHEDULES_KEY] = gson.toJson(list)
        }
        return updatedList
    }

    suspend fun toggleScheduleEnabled(id: String): List<CommuteSchedule> {
        var updatedList: List<CommuteSchedule> = emptyList()
        context.dataStore.edit { prefs ->
            val list = parseScheduleList(prefs[COMMUTE_SCHEDULES_KEY]).map {
                if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
            }
            updatedList = list
            prefs[COMMUTE_SCHEDULES_KEY] = gson.toJson(list)
        }
        return updatedList
    }

    suspend fun deleteSchedule(id: String): List<CommuteSchedule> {
        var updatedList: List<CommuteSchedule> = emptyList()
        context.dataStore.edit { prefs ->
            val list = parseScheduleList(prefs[COMMUTE_SCHEDULES_KEY]).toMutableList()
            list.removeAll { it.id == id }
            updatedList = list
            prefs[COMMUTE_SCHEDULES_KEY] = gson.toJson(list)
        }
        return updatedList
    }

    suspend fun getSchedulesSnapshot(): List<CommuteSchedule> {
        return commuteSchedules.first()
    }

    /** Record a completed trip in history (keeps up to 50 most recent trips). */
    suspend fun addTripHistory(entry: TripHistoryEntry) {
        context.dataStore.edit { prefs ->
            val json = prefs[TRIP_HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TripHistoryEntry>>() {}.type
            val list: MutableList<TripHistoryEntry> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            list.add(0, entry)
            val trimmed = list.take(50)
            prefs[TRIP_HISTORY_KEY] = gson.toJson(trimmed)
        }
    }

    /** Clear all trip history. */
    suspend fun clearTripHistory() {
        context.dataStore.edit { prefs ->
            prefs[TRIP_HISTORY_KEY] = "[]"
        }
    }

    /** Save last used settings for quick restore. */
    suspend fun saveLastSettings(
        alertDistance: Int,
        alertType: Int,
        alertLeadTimeMinutes: Int = 0,
        alertSoundOption: Int = 1,
        customRingtoneUri: String = "",
        customRingtoneTitle: String = "",
        emergencyContact: String,
        emergencyContactName: String = "",
        overshootMargin: Int = 100,
        unacknowledgedTimeoutMinutes: Int = 5,
        snoozeMinutes: Int = 5,
        highAccuracyMode: Boolean = true
    ) {
        context.dataStore.edit { prefs ->
            prefs[LAST_ALERT_DISTANCE_KEY] = alertDistance.toString()
            prefs[LAST_ALERT_TYPE_KEY] = alertType.toString()
            prefs[LAST_ALERT_LEAD_TIME_MINS_KEY] = alertLeadTimeMinutes
            prefs[LAST_ALERT_SOUND_OPTION_KEY] = alertSoundOption
            prefs[LAST_CUSTOM_RINGTONE_URI_KEY] = customRingtoneUri
            prefs[LAST_CUSTOM_RINGTONE_TITLE_KEY] = customRingtoneTitle
            prefs[LAST_EMERGENCY_CONTACT_KEY] = emergencyContact
            prefs[LAST_EMERGENCY_CONTACT_NAME_KEY] = emergencyContactName
            prefs[LAST_OVERSHOOT_MARGIN_KEY] = overshootMargin
            prefs[LAST_UNACK_TIMEOUT_MINS_KEY] = unacknowledgedTimeoutMinutes
            prefs[LAST_SNOOZE_MINUTES_KEY] = snoozeMinutes
            prefs[HIGH_ACCURACY_MODE_KEY] = highAccuracyMode
        }
    }

    /** Flow of last used alert distance. */
    val lastAlertDistance: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_ALERT_DISTANCE_KEY]?.toIntOrNull() ?: 250
    }

    /** Flow of last used alert type (1=notification, 2=vibration, 3=alarm, 4=smart adaptive). */
    val lastAlertType: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_ALERT_TYPE_KEY]?.toIntOrNull() ?: 4
    }

    /** Flow of last used alert lead-time in minutes (0 = distance only). */
    val lastAlertLeadTimeMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_ALERT_LEAD_TIME_MINS_KEY] ?: 0
    }

    /** Flow of last used alert sound option (1=Alarm, 2=Ringtone, 3=Chime, 4=Custom). */
    val lastAlertSoundOption: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_ALERT_SOUND_OPTION_KEY] ?: 1
    }

    val lastCustomRingtoneUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_CUSTOM_RINGTONE_URI_KEY] ?: ""
    }

    val lastCustomRingtoneTitle: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_CUSTOM_RINGTONE_TITLE_KEY] ?: ""
    }

    /** Flow of last used emergency contact number. */
    val lastEmergencyContact: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_EMERGENCY_CONTACT_KEY] ?: ""
    }

    /** Flow of last used emergency contact name. */
    val lastEmergencyContactName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LAST_EMERGENCY_CONTACT_NAME_KEY] ?: ""
    }

    /** Flow of overshoot distance margin (in meters beyond alert zone) for emergency SMS. */
    val lastOvershootMargin: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_OVERSHOOT_MARGIN_KEY] ?: 100
    }

    /** Flow of unacknowledged arrival alert response window in minutes. */
    val lastUnacknowledgedTimeoutMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_UNACK_TIMEOUT_MINS_KEY] ?: 5
    }

    /** Flow of snooze duration in minutes. */
    val lastSnoozeMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LAST_SNOOZE_MINUTES_KEY] ?: 5
    }

    /** Flow of GPS high accuracy vs adaptive battery-saving mode. */
    val highAccuracyMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIGH_ACCURACY_MODE_KEY] ?: true
    }

    @Suppress("USELESS_ELVIS")
    private fun sanitizeDestination(d: SavedDestination): SavedDestination {
        return d.copy(
            id = d.id ?: java.util.UUID.randomUUID().toString(),
            name = d.name ?: "Saved Stop",
            address = d.address ?: "",
            reminderNote = d.reminderNote ?: "",
            emergencyContact = d.emergencyContact ?: ""
        )
    }

    @Suppress("USELESS_ELVIS")
    private fun sanitizeSchedule(s: CommuteSchedule): CommuteSchedule {
        return s.copy(
            id = s.id ?: java.util.UUID.randomUUID().toString(),
            label = s.label ?: "Daily Commute",
            destinationName = s.destinationName ?: "Selected Stop",
            reminderNote = s.reminderNote ?: "",
            daysOfWeek = s.daysOfWeek ?: setOf(1, 2, 3, 4, 5, 6)
        )
    }

    private fun parseSavedList(json: String?): MutableList<SavedDestination> {
        if (json.isNullOrBlank()) {
            return mutableListOf()
        }
        return try {
            val type = object : TypeToken<MutableList<SavedDestination>>() {}.type
            val raw = gson.fromJson<MutableList<SavedDestination>>(json, type) ?: mutableListOf()
            raw.map { sanitizeDestination(it) }.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun parseScheduleList(json: String?): List<CommuteSchedule> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<CommuteSchedule>>() {}.type
            val raw = gson.fromJson<List<CommuteSchedule>>(json, type) ?: emptyList()
            raw.map { sanitizeSchedule(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
