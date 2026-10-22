package io.github.ramusriram.stopwake.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ramusriram.stopwake.LocationService
import io.github.ramusriram.stopwake.LocationUtils
import io.github.ramusriram.stopwake.ScheduleAlarmScheduler
import io.github.ramusriram.stopwake.TrackingSessionConfig
import io.github.ramusriram.stopwake.data.BackupImportResult
import io.github.ramusriram.stopwake.data.CommuteSchedule
import io.github.ramusriram.stopwake.data.DestinationRepository
import io.github.ramusriram.stopwake.data.SavedDestination
import io.github.ramusriram.stopwake.data.TripHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Represents the different alert modes the user can choose. */
enum class AlertType(
    val label: String,
    val shortLabel: String,
    val description: String,
    val value: Int
) {
    NOTIFICATION_ONLY(
        label = "Silent Banner",
        shortLabel = "Silent",
        description = "Shows a pop-up notification at the top of the screen without sound or vibration.",
        value = 1
    ),
    WITH_VIBRATION(
        label = "Continuous Vibration",
        shortLabel = "Vibrate",
        description = "Shows a pop-up notification and vibrates continuously until dismissed.",
        value = 2
    ),
    WITH_VIBRATION_AND_RINGTONE(
        label = "Alarm & Vibration",
        shortLabel = "Alarm",
        description = "Plays your wake-up alarm and vibrates continuously until dismissed.",
        value = 3
    ),
    SMART_ADAPTIVE(
        label = "Smart Mode",
        shortLabel = "Smart",
        description = "Vibrates gently while you're using your phone, or plays a full alarm when the screen is locked.",
        value = 4
    )
}

/** Represents the wake-up sound options for Alarm & Smart Adaptive modes. */
enum class AlertSoundOption(
    val label: String,
    val value: Int
) {
    ALARM_SIREN("Alarm Siren", 1),
    PHONE_RINGTONE("Ringtone", 2),
    GENTLE_CHIME("Chime", 3),
    CUSTOM_SOUND("Custom Tone", 4)
}

/** Represents the overall tracking state. */
enum class TrackingState {
    IDLE,
    TRACKING,
    ARRIVED,
    SNOOZED
}

/** UI state for the StopWake application. */
data class TrackingUiState(
    val destinationLat: Double = 20.5937,
    val destinationLng: Double = 78.9629,
    val destinationName: String = "Select Destination Stop",
    val destinationAddress: String = "Search a stop, tap the map, or choose a saved stop",
    val coordinateInputText: String = "",
    val reminderNote: String = "",
    val alertDistanceMeters: Int = 500,
    val alertLeadTimeMinutes: Int = 0, // 0 = Distance Only, or 2 / 5 / 10 min advance warning
    val alertType: AlertType = AlertType.SMART_ADAPTIVE,
    val alertSoundOption: AlertSoundOption = AlertSoundOption.ALARM_SIREN,
    val customRingtoneUri: String = "",
    val customRingtoneTitle: String = "",
    val emergencyContact: String = "",
    val emergencyContactName: String = "",
    val overshootMarginMeters: Int = 100,
    val unacknowledgedTimeoutMinutes: Int = 5,
    val snoozeDurationMinutes: Int = 5,
    val highAccuracyMode: Boolean = true,
    val gpsPowerModeLabel: String = "Adaptive Battery Saver",
    val isCachingOffline: Boolean = false,
    val isOfflineRouteCached: Boolean = true,
    val trackingState: TrackingState = TrackingState.IDLE,
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val currentAccuracyMeters: Float = 0f,
    val initialDistanceMeters: Float? = null,
    val distanceToDestination: Float? = null,
    val minDistanceReachedMeters: Float? = null,
    val bearingToDestination: Float = 0f,
    val pathPoints: List<Pair<Double, Double>> = emptyList(),
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = true,
    val snoozeEndTime: Long = 0L,
    val isWithinAlertZone: Boolean = false,
    val hasEnteredAlertZone: Boolean = false,
    val isAlarmPlaying: Boolean = false,
    val emergencySmsSent: Boolean = false,
    val lastEmergencySmsTimeMillis: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val isReverseGeocoding: Boolean = false,
    val statusBannerMessage: String? = null,
    val errorMessage: String? = null
) {
    /**
     * Parsed list of up to 5 trusted emergency contacts.
     */
    val emergencyContactsList: List<LocationUtils.EmergencyContactEntry>
        get() = LocationUtils.parseEmergencyContacts(emergencyContact, emergencyContactName)

    /**
     * Returns true if the currently selected destination coordinates match any item in [savedList].
     */
    fun isSavedIn(savedList: List<SavedDestination>): Boolean {
        if (!LocationUtils.isValidCoordinate(destinationLat, destinationLng)) return false
        return savedList.any { saved ->
            val dist = LocationUtils.calculateDistanceMeters(
                destinationLat,
                destinationLng,
                saved.latitude,
                saved.longitude
            )
            dist <= 45f || (dist <= 150f && saved.name.equals(destinationName.trim(), ignoreCase = true))
        }
    }
}

/** Represents a geocoding search result from Nominatim / Geocoder / Presets. */
data class GeocodingResult(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

/**
 * Central ViewModel managing all StopWake app state, live tracking telemetry,
 * saved places, trip history, offline route caching, and automated commute schedules.
 */
class StopWakeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = DestinationRepository(appContext)

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    val savedDestinations: StateFlow<List<SavedDestination>> = repository.savedDestinations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commuteSchedules: StateFlow<List<CommuteSchedule>> = repository.commuteSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tripHistory: StateFlow<List<TripHistoryEntry>> = repository.tripHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    private var reverseGeocodeJob: Job? = null
    private var offlineCacheJob: Job? = null
    private var hasUserManuallySelectedDestination = false
    private var hasAutoLocalizedInitialPin = false

    init {
        refreshPermissionsState()

        viewModelScope.launch {
            repository.ensurePresetsSeeded()
            val savedList = repository.savedDestinations.first()
            val firstSaved = savedList.firstOrNull { it.isFavorite } ?: savedList.firstOrNull()
            val distance = repository.lastAlertDistance.first()
            val type = repository.lastAlertType.first()
            val leadMins = repository.lastAlertLeadTimeMinutes.first()
            val soundOpt = repository.lastAlertSoundOption.first()
            val customUri = repository.lastCustomRingtoneUri.first()
            val customTitle = repository.lastCustomRingtoneTitle.first()
            val contact = repository.lastEmergencyContact.first()
            val contactName = repository.lastEmergencyContactName.first()
            val overshoot = repository.lastOvershootMargin.first()
            val unackTimeout = repository.lastUnacknowledgedTimeoutMinutes.first()
            val snoozeMins = repository.lastSnoozeMinutes.first()
            val highAccuracy = repository.highAccuracyMode.first()

            val preserveUserChoice = hasUserManuallySelectedDestination
            _uiState.value = _uiState.value.copy(
                destinationLat = if (preserveUserChoice) _uiState.value.destinationLat else (firstSaved?.latitude ?: _uiState.value.destinationLat),
                destinationLng = if (preserveUserChoice) _uiState.value.destinationLng else (firstSaved?.longitude ?: _uiState.value.destinationLng),
                destinationName = if (preserveUserChoice) _uiState.value.destinationName else (firstSaved?.name ?: _uiState.value.destinationName),
                destinationAddress = if (preserveUserChoice) _uiState.value.destinationAddress else (firstSaved?.address ?: _uiState.value.destinationAddress),
                coordinateInputText = if (preserveUserChoice) {
                    _uiState.value.coordinateInputText
                } else if (firstSaved != null) {
                    "${"%.5f".format(Locale.US, firstSaved.latitude)}, ${"%.5f".format(Locale.US, firstSaved.longitude)}"
                } else {
                    _uiState.value.coordinateInputText
                },
                reminderNote = if (preserveUserChoice) _uiState.value.reminderNote else (firstSaved?.reminderNote ?: _uiState.value.reminderNote),
                alertDistanceMeters = if (preserveUserChoice) _uiState.value.alertDistanceMeters else distance,
                alertLeadTimeMinutes = leadMins,
                alertType = if (preserveUserChoice) _uiState.value.alertType else (AlertType.entries.find { it.value == type } ?: AlertType.SMART_ADAPTIVE),
                alertSoundOption = AlertSoundOption.entries.find { it.value == soundOpt } ?: AlertSoundOption.ALARM_SIREN,
                customRingtoneUri = customUri,
                customRingtoneTitle = customTitle,
                emergencyContact = contact,
                emergencyContactName = contactName,
                overshootMarginMeters = overshoot,
                unacknowledgedTimeoutMinutes = unackTimeout,
                snoozeDurationMinutes = snoozeMins,
                highAccuracyMode = highAccuracy
            )

            // Sync all enabled schedules with AlarmManager
            val schedules = repository.getSchedulesSnapshot()
            ScheduleAlarmScheduler.syncAllSchedules(appContext, schedules)

            refreshDeviceLocation()
        }

        // Observe active alert state (covers both Test Alerts and Real Proximity Alerts)
        viewModelScope.launch {
            LocationService.isAnyAlertActive.collect { active ->
                _uiState.value = _uiState.value.copy(isAlarmPlaying = active)
            }
        }

        // Collect live telemetry from LocationService without overwriting text fields while user edits
        viewModelScope.launch {
            LocationService.trackingSnapshot.collect { snap ->
                val cur = _uiState.value
                if (snap.isServiceRunning) {
                    val wasIdle = cur.trackingState == TrackingState.IDLE
                    val coordString = "${"%.5f".format(Locale.US, snap.config.destinationLat)}, ${"%.5f".format(Locale.US, snap.config.destinationLng)}"
                    _uiState.value = cur.copy(
                        trackingState = snap.trackingState,
                        destinationName = if (wasIdle) snap.config.destinationName else cur.destinationName,
                        destinationLat = snap.config.destinationLat,
                        destinationLng = snap.config.destinationLng,
                        coordinateInputText = if (wasIdle) coordString else cur.coordinateInputText,
                        reminderNote = if (wasIdle) snap.config.reminderNote else cur.reminderNote,
                        alertDistanceMeters = snap.config.alertDistanceMeters,
                        alertLeadTimeMinutes = snap.config.alertLeadTimeMinutes,
                        alertType = AlertType.entries.find { it.value == snap.config.alertTypeValue } ?: cur.alertType,
                        currentLat = if (snap.currentLat != 0.0) snap.currentLat else cur.currentLat,
                        currentLng = if (snap.currentLng != 0.0) snap.currentLng else cur.currentLng,
                        currentSpeedMps = snap.currentSpeedMps,
                        currentAccuracyMeters = snap.currentAccuracyMeters,
                        initialDistanceMeters = snap.initialDistanceMeters,
                        distanceToDestination = snap.distanceToDestination,
                        minDistanceReachedMeters = snap.minDistanceReachedMeters,
                        bearingToDestination = snap.bearingToDestination,
                        pathPoints = snap.pathPoints,
                        isWithinAlertZone = snap.isWithinAlertZone,
                        hasEnteredAlertZone = snap.hasEnteredAlertZone,
                        isAlarmPlaying = snap.isAlarmPlaying || LocationService.isAnyAlertActive.value,
                        snoozeEndTime = snap.snoozeEndTimeMillis,
                        emergencySmsSent = snap.emergencySmsSent,
                        lastEmergencySmsTimeMillis = snap.lastEmergencySmsTimeMillis,
                        gpsPowerModeLabel = snap.gpsPowerModeLabel
                    )
                } else if (cur.trackingState != TrackingState.IDLE) {
                    val idleDistance = if (LocationUtils.isValidCoordinate(cur.currentLat, cur.currentLng) &&
                        LocationUtils.isValidCoordinate(cur.destinationLat, cur.destinationLng)
                    ) {
                        LocationUtils.calculateDistanceMeters(
                            cur.currentLat, cur.currentLng,
                            cur.destinationLat, cur.destinationLng
                        )
                    } else null

                    _uiState.value = cur.copy(
                        trackingState = TrackingState.IDLE,
                        distanceToDestination = idleDistance,
                        initialDistanceMeters = null,
                        isWithinAlertZone = false,
                        hasEnteredAlertZone = false,
                        isAlarmPlaying = LocationService.isAnyAlertActive.value,
                        snoozeEndTime = 0L,
                        pathPoints = emptyList()
                    )
                }
            }
        }

        // Collect completed session events to record trip history
        viewModelScope.launch {
            LocationService.completedSessions.collect { event ->
                repository.addTripHistory(
                    TripHistoryEntry(
                        destinationName = event.destinationName,
                        latitude = event.destinationLat,
                        longitude = event.destinationLng,
                        alertDistanceMeters = event.alertDistanceMeters,
                        startedAt = event.startedAtMillis,
                        endedAt = event.endedAtMillis,
                        outcome = event.outcome,
                        minDistanceReachedMeters = event.minDistanceReachedMeters
                    )
                )
            }
        }
    }

    // ── Destination Selection & Geocoding ────────────────────

    fun setDestination(
        lat: Double,
        lng: Double,
        name: String = "Selected Location",
        address: String = "",
        reminderNote: String? = null,
        fromUserAction: Boolean = true
    ) {
        if (!LocationUtils.isValidCoordinate(lat, lng)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Invalid coordinates. Latitude must be -90..90 and Longitude -180..180."
            )
            return
        }
        if (fromUserAction) {
            hasUserManuallySelectedDestination = true
        }
        val state = _uiState.value
        val matchingSaved = savedDestinations.value.firstOrNull {
            LocationUtils.calculateDistanceMeters(lat, lng, it.latitude, it.longitude) <= 45f
        }
        val newDist = if (LocationUtils.isValidCoordinate(state.currentLat, state.currentLng)) {
            LocationUtils.calculateDistanceMeters(state.currentLat, state.currentLng, lat, lng)
        } else {
            state.distanceToDestination
        }
        val newBearing = if (LocationUtils.isValidCoordinate(state.currentLat, state.currentLng)) {
            LocationUtils.calculateBearingDegrees(state.currentLat, state.currentLng, lat, lng)
        } else {
            state.bearingToDestination
        }
        val coordText = "${"%.5f".format(Locale.US, lat)}, ${"%.5f".format(Locale.US, lng)}"
        _uiState.value = state.copy(
            destinationLat = lat,
            destinationLng = lng,
            destinationName = matchingSaved?.name ?: name,
            destinationAddress = address.ifBlank { matchingSaved?.address ?: "" },
            reminderNote = reminderNote ?: (matchingSaved?.reminderNote ?: ""),
            coordinateInputText = coordText,
            distanceToDestination = newDist,
            bearingToDestination = newBearing,
            isOfflineRouteCached = false,
            errorMessage = null
        )
        syncLiveConfigIfNeeded()
    }

    /** Place a destination pin a short distance (e.g. 350m) north-east of the user's current GPS position. */
    fun setDestinationNearUser(offsetMeters: Int = 350) {
        val state = _uiState.value
        if (!LocationUtils.isValidCoordinate(state.currentLat, state.currentLng)) {
            refreshDeviceLocation { lat, lng ->
                val offsetDeg = (offsetMeters / 111320.0)
                selectDestinationFromMapTap(lat + offsetDeg * 0.7, lng + offsetDeg * 0.7)
            }
            return
        }
        val offsetDeg = (offsetMeters / 111320.0)
        selectDestinationFromMapTap(
            state.currentLat + offsetDeg * 0.7,
            state.currentLng + offsetDeg * 0.7
        )
    }

    /** Called when user taps or long-presses on the OSMDroid map. */
    fun selectDestinationFromMapTap(lat: Double, lng: Double, fromUserAction: Boolean = true) {
        val coordLabel = "${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lng)}"
        setDestination(
            lat = lat,
            lng = lng,
            name = "Pinned Stop ($coordLabel)",
            address = "Resolving street name...",
            fromUserAction = fromUserAction
        )
        _uiState.value = _uiState.value.copy(isReverseGeocoding = true)

        reverseGeocodeJob?.cancel()
        reverseGeocodeJob = viewModelScope.launch {
            val resolvedName = LocationUtils.reverseGeocode(appContext, lat, lng)
            val current = _uiState.value
            if (kotlin.math.abs(current.destinationLat - lat) < 0.0001 &&
                kotlin.math.abs(current.destinationLng - lng) < 0.0001
            ) {
                val matchingSaved = savedDestinations.value.firstOrNull {
                    LocationUtils.calculateDistanceMeters(lat, lng, it.latitude, it.longitude) <= 45f
                }
                _uiState.value = current.copy(
                    destinationName = matchingSaved?.name ?: resolvedName,
                    destinationAddress = matchingSaved?.address?.takeIf { it.isNotBlank() }
                        ?: "Lat ${"%.5f".format(Locale.US, lat)}, Lng ${"%.5f".format(Locale.US, lng)}",
                    isReverseGeocoding = false
                )
                syncLiveConfigIfNeeded()
            }
        }
    }

    fun updateCoordinateInputText(text: String) {
        _uiState.value = _uiState.value.copy(coordinateInputText = text, errorMessage = null)
        LocationUtils.parseCoordinates(text)?.let { (lat, lng) ->
            hasUserManuallySelectedDestination = true
            val state = _uiState.value
            val dist = if (LocationUtils.isValidCoordinate(state.currentLat, state.currentLng)) {
                LocationUtils.calculateDistanceMeters(state.currentLat, state.currentLng, lat, lng)
            } else state.distanceToDestination

            val matchingSaved = savedDestinations.value.firstOrNull {
                LocationUtils.calculateDistanceMeters(lat, lng, it.latitude, it.longitude) < 80f
            }

            _uiState.value = state.copy(
                destinationLat = lat,
                destinationLng = lng,
                destinationName = matchingSaved?.name
                    ?: "Coordinates (${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lng)})",
                destinationAddress = matchingSaved?.address ?: "",
                reminderNote = matchingSaved?.reminderNote ?: state.reminderNote,
                distanceToDestination = dist
            )
            syncLiveConfigIfNeeded()
        }
    }

    fun updateDestinationName(name: String) {
        _uiState.value = _uiState.value.copy(destinationName = name)
        syncLiveConfigIfNeeded()
    }

    fun updateReminderNote(note: String) {
        _uiState.value = _uiState.value.copy(reminderNote = note)
        syncLiveConfigIfNeeded()
    }

    fun loadSavedDestination(destination: SavedDestination) {
        hasUserManuallySelectedDestination = true
        val alertType = AlertType.entries.find { it.value == destination.alertType }
            ?: _uiState.value.alertType
        val state = _uiState.value
        val dist = if (LocationUtils.isValidCoordinate(state.currentLat, state.currentLng)) {
            LocationUtils.calculateDistanceMeters(
                state.currentLat, state.currentLng,
                destination.latitude, destination.longitude
            )
        } else state.distanceToDestination

        _uiState.value = state.copy(
            destinationLat = destination.latitude,
            destinationLng = destination.longitude,
            destinationName = destination.name,
            destinationAddress = destination.address,
            reminderNote = destination.reminderNote,
            coordinateInputText = "${"%.5f".format(Locale.US, destination.latitude)}, ${"%.5f".format(Locale.US, destination.longitude)}",
            alertDistanceMeters = destination.alertDistanceMeters,
            alertType = alertType,
            emergencyContact = destination.emergencyContact.ifBlank { state.emergencyContact },
            distanceToDestination = dist,
            errorMessage = null,
            statusBannerMessage = "Loaded \"${destination.name}\""
        )
        syncLiveConfigIfNeeded()
        cacheDestinationOffline(silent = true)
    }

    fun saveCurrentDestination(customName: String? = null, customNote: String? = null) {
        val state = _uiState.value
        if (!LocationUtils.isValidCoordinate(state.destinationLat, state.destinationLng)) {
            _uiState.value = state.copy(errorMessage = "Please select a valid destination first")
            return
        }
        val finalName = customName?.takeIf { it.isNotBlank() }
            ?: state.destinationName.ifBlank {
                "Stop (${"%.4f".format(Locale.US, state.destinationLat)}, ${"%.4f".format(Locale.US, state.destinationLng)})"
            }
        val finalNote = customNote ?: state.reminderNote

        // Check if an existing saved destination matches these coordinates so we preserve its ID & favorite flag
        val existing = savedDestinations.value.firstOrNull {
            LocationUtils.calculateDistanceMeters(
                state.destinationLat,
                state.destinationLng,
                it.latitude,
                it.longitude
            ) <= 45f
        }

        viewModelScope.launch {
            repository.saveDestination(
                SavedDestination(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name = finalName,
                    address = state.destinationAddress,
                    latitude = state.destinationLat,
                    longitude = state.destinationLng,
                    alertDistanceMeters = state.alertDistanceMeters,
                    alertType = state.alertType.value,
                    reminderNote = finalNote,
                    emergencyContact = state.emergencyContact,
                    isFavorite = existing?.isFavorite ?: false
                )
            )
            _uiState.value = _uiState.value.copy(
                destinationName = finalName,
                reminderNote = finalNote,
                statusBannerMessage = "Saved \"$finalName\" to Saved Stops"
            )
        }
    }

    /**
     * Toggles whether the currently selected destination is saved in Saved Stops.
     * If already saved, removes it; if not saved, saves it.
     */
    fun toggleSaveCurrentDestination() {
        val state = _uiState.value
        val currentSaved = savedDestinations.value
        val matching = currentSaved.filter { saved ->
            val dist = LocationUtils.calculateDistanceMeters(
                state.destinationLat,
                state.destinationLng,
                saved.latitude,
                saved.longitude
            )
            dist <= 45f || (dist <= 150f && saved.name.equals(state.destinationName.trim(), ignoreCase = true))
        }
        if (matching.isNotEmpty()) {
            viewModelScope.launch {
                matching.forEach { repository.deleteDestination(it.id) }
                _uiState.value = _uiState.value.copy(
                    statusBannerMessage = "Removed \"${state.destinationName}\" from Saved Stops"
                )
            }
        } else {
            saveCurrentDestination()
        }
    }

    /**
     * Adds a new saved destination or updates an existing saved destination in-place,
     * and automatically syncs with the active Track screen if the edited stop is currently selected.
     */
    fun saveOrUpdateSavedDestination(
        destination: SavedDestination,
        previousDestination: SavedDestination? = null
    ) {
        viewModelScope.launch {
            val cleanDest = destination.copy(
                name = destination.name.trim().ifBlank { "Saved Stop" },
                address = destination.address.trim(),
                reminderNote = destination.reminderNote.trim(),
                alertDistanceMeters = destination.alertDistanceMeters.coerceIn(50, 10000),
                timestamp = System.currentTimeMillis()
            )
            repository.saveDestination(cleanDest)

            val state = _uiState.value
            val matchesPrevious = previousDestination != null && (
                state.destinationName.equals(previousDestination.name, ignoreCase = true) ||
                    LocationUtils.calculateDistanceMeters(
                        state.destinationLat,
                        state.destinationLng,
                        previousDestination.latitude,
                        previousDestination.longitude
                    ) <= 60f
                )
            val matchesCurrent = state.destinationName.equals(cleanDest.name, ignoreCase = true) ||
                LocationUtils.calculateDistanceMeters(
                    state.destinationLat,
                    state.destinationLng,
                    cleanDest.latitude,
                    cleanDest.longitude
                ) <= 60f

            if (matchesPrevious || matchesCurrent) {
                val newAlertType = AlertType.entries.find { it.value == cleanDest.alertType } ?: state.alertType
                val newDist = if (LocationUtils.isValidCoordinate(state.currentLat, state.currentLng)) {
                    LocationUtils.calculateDistanceMeters(
                        state.currentLat, state.currentLng,
                        cleanDest.latitude, cleanDest.longitude
                    )
                } else state.distanceToDestination

                _uiState.value = state.copy(
                    destinationName = cleanDest.name,
                    destinationAddress = cleanDest.address,
                    destinationLat = cleanDest.latitude,
                    destinationLng = cleanDest.longitude,
                    coordinateInputText = "${"%.5f".format(Locale.US, cleanDest.latitude)}, ${"%.5f".format(Locale.US, cleanDest.longitude)}",
                    alertDistanceMeters = cleanDest.alertDistanceMeters,
                    alertType = newAlertType,
                    reminderNote = cleanDest.reminderNote,
                    distanceToDestination = newDist,
                    statusBannerMessage = "Updated \"${cleanDest.name}\""
                )
                syncLiveConfigIfNeeded()
                cacheDestinationOffline(silent = true)
            }
        }
    }

    fun toggleFavoriteDestination(id: String) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
        }
    }

    fun deleteSavedDestination(id: String) {
        viewModelScope.launch {
            repository.deleteDestination(id)
        }
    }

    fun exportBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportBackupJson()
            onReady(json)
        }
    }

    fun importBackupJson(rawJson: String, onResult: ((BackupImportResult) -> Unit)? = null) {
        viewModelScope.launch {
            val result = repository.importBackupJson(rawJson)
            if (result.success) {
                val schedules = repository.getSchedulesSnapshot()
                ScheduleAlarmScheduler.syncAllSchedules(appContext, schedules)
                _uiState.value = _uiState.value.copy(
                    statusBannerMessage = result.message,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.message
                )
            }
            onResult?.invoke(result)
        }
    }

    fun clearTripHistory() {
        viewModelScope.launch {
            repository.clearTripHistory()
        }
    }

    // ── Offline Route & Stop Tile Caching ────────────────────

    /**
     * Pre-fetches and caches OpenStreetMap tiles around the selected destination into persistent offline storage
     * so map visuals and geofencing work seamlessly even with zero mobile data connectivity.
     */
    fun cacheDestinationOffline(silent: Boolean = false) {
        val state = _uiState.value
        val lat = state.destinationLat
        val lng = state.destinationLng
        if (!LocationUtils.isValidCoordinate(lat, lng)) return

        offlineCacheJob?.cancel()
        offlineCacheJob = viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isCachingOffline = true)
            }
            withContext(Dispatchers.IO) {
                try {
                    val tileRoot = File(File(appContext.filesDir, "osmdroid_offline_v5"), "tiles/Mapnik")
                    val zoomLevels = if (silent) listOf(14) else listOf(14, 15)
                    for (zoom in zoomLevels) {
                        val n = 1 shl zoom
                        val centerX = floor((lng + 180.0) / 360.0 * n).toInt()
                        val latRad = Math.toRadians(lat)
                        val centerY = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()

                        for (dx in -1..1) {
                            for (dy in -1..1) {
                                val tx = (centerX + dx).coerceIn(0, n - 1)
                                val ty = (centerY + dy).coerceIn(0, n - 1)
                                val destFile = File(tileRoot, "$zoom/$tx/$ty.png.tile")
                                if (!destFile.exists()) {
                                    destFile.parentFile?.mkdirs()
                                    val url = URL("https://tile.openstreetmap.org/$zoom/$tx/$ty.png")
                                    val conn = (url.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "GET"
                                        connectTimeout = 4000
                                        readTimeout = 4000
                                        setRequestProperty(
                                            "User-Agent",
                                            "StopWake/1.0 (https://github.com/RamuSriram/StopWake; Android)"
                                        )
                                    }
                                    if (conn.responseCode == 200) {
                                        conn.inputStream.use { input ->
                                            destFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                    conn.disconnect()
                                    delay(180L)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            _uiState.value = _uiState.value.copy(
                isCachingOffline = false,
                isOfflineRouteCached = true
            )
        }
    }

    // ── Commute Schedules Management ─────────────────────────

    fun saveSchedule(schedule: CommuteSchedule) {
        viewModelScope.launch {
            val updated = repository.saveSchedule(schedule)
            ScheduleAlarmScheduler.syncAllSchedules(appContext, updated)
        }
    }

    fun toggleScheduleEnabled(id: String) {
        viewModelScope.launch {
            val updated = repository.toggleScheduleEnabled(id)
            ScheduleAlarmScheduler.syncAllSchedules(appContext, updated)
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            ScheduleAlarmScheduler.cancelScheduleAlarms(appContext, id)
            val updated = repository.deleteSchedule(id)
            ScheduleAlarmScheduler.syncAllSchedules(appContext, updated)
        }
    }

    fun runScheduleNow(schedule: CommuteSchedule): Boolean {
        setDestination(
            lat = schedule.destinationLat,
            lng = schedule.destinationLng,
            name = schedule.destinationName,
            reminderNote = schedule.reminderNote,
            fromUserAction = true
        )
        setAlertDistance(schedule.alertDistanceMeters)
        val alertType = AlertType.entries.find { it.value == schedule.alertTypeValue } ?: AlertType.SMART_ADAPTIVE
        setAlertType(alertType)
        return startTracking()
    }

    /**
     * Loads a schedule by ID directly from DataStore (safe on cold app start before StateFlows emit)
     * and starts tracking or invokes [onMissingPermissions] after configuring the schedule stop.
     */
    fun runScheduleById(scheduleId: String, onMissingPermissions: () -> Unit) {
        viewModelScope.launch {
            val targetSchedule = commuteSchedules.value.firstOrNull { it.id == scheduleId }
                ?: repository.getSchedulesSnapshot().firstOrNull { it.id == scheduleId }
                ?: return@launch

            setDestination(
                lat = targetSchedule.destinationLat,
                lng = targetSchedule.destinationLng,
                name = targetSchedule.destinationName,
                reminderNote = targetSchedule.reminderNote,
                fromUserAction = true
            )
            setAlertDistance(targetSchedule.alertDistanceMeters)
            val alertType = AlertType.entries.find { it.value == targetSchedule.alertTypeValue } ?: AlertType.SMART_ADAPTIVE
            setAlertType(alertType)

            if (LocationUtils.hasLocationPermission(appContext)) {
                startTracking()
            } else {
                onMissingPermissions()
            }
        }
    }

    // ── Alert, Personalization & Safety Settings ─────────────

    fun setAlertDistance(meters: Int) {
        val coerced = meters.coerceIn(50, 10000)
        _uiState.value = _uiState.value.copy(alertDistanceMeters = coerced)
        syncLiveConfigIfNeeded()
    }

    fun setAlertLeadTimeMinutes(minutes: Int) {
        val coerced = minutes.coerceIn(0, 30)
        _uiState.value = _uiState.value.copy(alertLeadTimeMinutes = coerced)
        syncLiveConfigIfNeeded()
    }

    fun setAlertType(type: AlertType) {
        _uiState.value = _uiState.value.copy(alertType = type)
        syncLiveConfigIfNeeded()
    }

    fun setAlertSoundOption(option: AlertSoundOption) {
        _uiState.value = _uiState.value.copy(alertSoundOption = option)
        syncLiveConfigIfNeeded()
    }

    fun setCustomRingtone(uriString: String, title: String) {
        _uiState.value = _uiState.value.copy(
            alertSoundOption = AlertSoundOption.CUSTOM_SOUND,
            customRingtoneUri = uriString,
            customRingtoneTitle = title
        )
        syncLiveConfigIfNeeded()
    }

    fun setEmergencyContact(contact: String, name: String = _uiState.value.emergencyContactName) {
        _uiState.value = _uiState.value.copy(
            emergencyContact = contact,
            emergencyContactName = name
        )
        persistCurrentSettings()
        syncLiveConfigIfNeeded()
    }

    /**
     * Validates and adds a phone number (from contact picker or manual entry) to the emergency contacts list (up to 5).
     */
    fun addEmergencyContact(rawNumber: String, name: String = ""): LocationUtils.PhoneValidationResult {
        val validation = LocationUtils.validatePhoneNumber(rawNumber)
        if (!validation.isValid) return validation

        val currentEntries = _uiState.value.emergencyContactsList.toMutableList()
        val newTail = validation.normalizedNumber.filter { it.isDigit() }.takeLast(10)
        val existingIdx = currentEntries.indexOfFirst {
            it.number.filter { c -> c.isDigit() }.takeLast(10) == newTail
        }

        if (existingIdx >= 0) {
            val existing = currentEntries[existingIdx]
            currentEntries[existingIdx] = LocationUtils.EmergencyContactEntry(
                number = validation.normalizedNumber,
                name = name.trim().ifBlank { existing.name }
            )
        } else {
            if (currentEntries.size >= LocationUtils.MAX_EMERGENCY_CONTACTS) {
                return LocationUtils.PhoneValidationResult(
                    isValid = false,
                    errorMessage = "Maximum ${LocationUtils.MAX_EMERGENCY_CONTACTS} emergency contacts reached. Remove one to add another."
                )
            }
            currentEntries.add(
                LocationUtils.EmergencyContactEntry(
                    number = validation.normalizedNumber,
                    name = name.trim()
                )
            )
        }

        val (numbersStr, namesStr) = LocationUtils.serializeEmergencyContacts(currentEntries)
        _uiState.value = _uiState.value.copy(
            emergencyContact = numbersStr,
            emergencyContactName = namesStr
        )
        persistCurrentSettings()
        syncLiveConfigIfNeeded()
        return validation
    }

    /**
     * Removes a single emergency contact by its phone number.
     */
    fun removeEmergencyContact(numberToRemove: String) {
        val targetTail = LocationUtils.normalizePhoneNumberForSms(numberToRemove)
            .filter { it.isDigit() }
            .takeLast(10)
        val updated = _uiState.value.emergencyContactsList.filterNot {
            it.number.filter { c -> c.isDigit() }.takeLast(10) == targetTail
        }
        val (numbersStr, namesStr) = LocationUtils.serializeEmergencyContacts(updated)
        _uiState.value = _uiState.value.copy(
            emergencyContact = numbersStr,
            emergencyContactName = namesStr
        )
        persistCurrentSettings()
        syncLiveConfigIfNeeded()
    }

    /**
     * Clears all saved emergency contacts.
     */
    fun clearEmergencyContacts() {
        _uiState.value = _uiState.value.copy(
            emergencyContact = "",
            emergencyContactName = ""
        )
        persistCurrentSettings()
        syncLiveConfigIfNeeded()
    }

    fun setOvershootMargin(meters: Int) {
        _uiState.value = _uiState.value.copy(overshootMarginMeters = meters.coerceIn(50, 2000))
        persistCurrentSettings()
        syncLiveConfigIfNeeded()
    }

    fun setUnacknowledgedTimeoutMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(unacknowledgedTimeoutMinutes = minutes.coerceIn(1, 30))
        persistCurrentSettings()
        syncLiveConfigIfNeeded()
    }

    fun setSnoozeMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(snoozeDurationMinutes = minutes.coerceIn(1, 30))
        syncLiveConfigIfNeeded()
    }

    fun setHighAccuracyMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            highAccuracyMode = enabled,
            gpsPowerModeLabel = if (enabled) "Adaptive Battery Saver" else "Eco Battery Saver"
        )
        syncLiveConfigIfNeeded()
    }

    // ── Tracking Lifecycle ───────────────────────────────────

    fun startTracking(): Boolean {
        val state = _uiState.value

        val parsed = LocationUtils.parseCoordinates(state.coordinateInputText)
        val destLat = parsed?.first ?: state.destinationLat
        val destLng = parsed?.second ?: state.destinationLng

        if (!LocationUtils.isValidCoordinate(destLat, destLng)) {
            _uiState.value = state.copy(
                errorMessage = "Please enter valid destination coordinates (Lat, Lng) or tap on the map."
            )
            return false
        }

        val sessionConfig = buildSessionConfig(state, destLat, destLng)

        LocationService.activeSessionConfig = sessionConfig
        LocationService.trackingDestLat = destLat
        LocationService.trackingDestLng = destLng

        viewModelScope.launch {
            repository.saveLastSettings(
                alertDistance = state.alertDistanceMeters,
                alertType = state.alertType.value,
                alertLeadTimeMinutes = state.alertLeadTimeMinutes,
                alertSoundOption = state.alertSoundOption.value,
                customRingtoneUri = state.customRingtoneUri,
                customRingtoneTitle = state.customRingtoneTitle,
                emergencyContact = state.emergencyContact.trim(),
                emergencyContactName = state.emergencyContactName.trim(),
                overshootMargin = state.overshootMarginMeters,
                unacknowledgedTimeoutMinutes = state.unacknowledgedTimeoutMinutes,
                snoozeMinutes = state.snoozeDurationMinutes,
                highAccuracyMode = state.highAccuracyMode
            )
        }

        val serviceIntent = Intent(appContext, LocationService::class.java).apply {
            action = LocationService.ACTION_START_TRACKING
        }
        ContextCompat.startForegroundService(appContext, serviceIntent)

        _uiState.value = state.copy(
            destinationLat = destLat,
            destinationLng = destLng,
            trackingState = TrackingState.TRACKING,
            isWithinAlertZone = false,
            errorMessage = null,
            statusBannerMessage = null
        )
        return true
    }

    fun confirmSafeArrival() {
        LocationService.dismissAllAlerts(appContext)
        val confirmIntent = Intent(appContext, LocationService::class.java).apply {
            action = LocationService.ACTION_CONFIRM_ARRIVAL
        }
        try {
            appContext.startService(confirmIntent)
        } catch (_: Exception) {
            appContext.stopService(Intent(appContext, LocationService::class.java))
        }
        _uiState.value = _uiState.value.copy(
            trackingState = TrackingState.IDLE,
            isWithinAlertZone = false,
            isAlarmPlaying = false,
            snoozeEndTime = 0L,
            statusBannerMessage = "Confirmed safe arrival at ${_uiState.value.destinationName}"
        )
    }

    fun stopTracking() {
        LocationService.dismissAllAlerts(appContext)
        val stopIntent = Intent(appContext, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP_TRACKING
        }
        try {
            appContext.startService(stopIntent)
        } catch (_: Exception) {
            appContext.stopService(Intent(appContext, LocationService::class.java))
        }
        _uiState.value = _uiState.value.copy(
            trackingState = TrackingState.IDLE,
            isWithinAlertZone = false,
            isAlarmPlaying = false,
            snoozeEndTime = 0L
        )
    }

    fun snooze(durationMinutes: Int = _uiState.value.snoozeDurationMinutes) {
        setSnoozeMinutes(durationMinutes)
        LocationService.dismissAllAlerts(appContext)
        val snoozeIntent = Intent(appContext, LocationService::class.java).apply {
            action = LocationService.ACTION_SNOOZE
        }
        try {
            appContext.startService(snoozeIntent)
        } catch (_: Exception) {}
    }

    /** Smart Snooze: snoozes for half of the remaining ETA (clamped between 2 and 10 minutes). */
    fun smartSnooze() {
        val state = _uiState.value
        val dist = state.distanceToDestination ?: state.alertDistanceMeters.toFloat()
        val etaMins = LocationUtils.estimateEtaMinutes(dist, state.currentSpeedMps)
        val smartMins = (etaMins / 2).coerceIn(2, 10)
        snooze(smartMins)
    }

    fun dismissActiveAlarm() {
        LocationService.dismissAllAlerts(appContext)
        _uiState.value = _uiState.value.copy(isAlarmPlaying = false)
    }

    fun triggerTestAlert() {
        val state = _uiState.value
        LocationService.triggerTestAlert(
            context = appContext,
            alertTypeValue = state.alertType.value,
            alertDistance = state.alertDistanceMeters,
            alertSoundOption = state.alertSoundOption.value,
            customRingtoneUri = state.customRingtoneUri,
            reminderNote = state.reminderNote
        )
    }

    fun stopTestAlert() {
        LocationService.dismissAllAlerts(appContext)
        _uiState.value = _uiState.value.copy(isAlarmPlaying = false)
    }

    fun triggerManualSosSms(): Boolean {
        val state = _uiState.value
        if (state.emergencyContact.isBlank()) {
            _uiState.value = state.copy(
                errorMessage = "Please add an Emergency Contact number first to send an automatic SOS SMS."
            )
            return false
        }
        val sent = LocationService.sendEmergencySms(
            context = appContext,
            phoneNumber = state.emergencyContact,
            destinationName = state.destinationName,
            currentLat = state.currentLat,
            currentLng = state.currentLng,
            distanceMeters = state.distanceToDestination,
            isManualSos = true
        )
        if (sent) {
            _uiState.value = state.copy(
                emergencySmsSent = true,
                lastEmergencySmsTimeMillis = System.currentTimeMillis(),
                statusBannerMessage = "SOS SMS dispatched to ${state.emergencyContact}"
            )
        } else {
            _uiState.value = state.copy(
                errorMessage = "Could not send SMS automatically. Ensure SMS permission is granted."
            )
        }
        return sent
    }

    // ── Device Location & Search (100% FOSS LocationManager) ──

    @SuppressLint("MissingPermission")
    fun refreshDeviceLocation(onLocated: ((Double, Double) -> Unit)? = null) {
        refreshPermissionsState()
        if (!LocationUtils.hasLocationPermission(appContext)) return

        val lastLoc = LocationUtils.getBestLastKnownLocation(appContext)
        if (lastLoc != null) {
            applyDeviceLocation(lastLoc.latitude, lastLoc.longitude, lastLoc.speed, lastLoc.accuracy)
            onLocated?.invoke(lastLoc.latitude, lastLoc.longitude)
        } else {
            LocationUtils.requestSingleFreshLocation(appContext) { freshLoc ->
                applyDeviceLocation(
                    freshLoc.latitude,
                    freshLoc.longitude,
                    freshLoc.speed,
                    freshLoc.accuracy
                )
                onLocated?.invoke(freshLoc.latitude, freshLoc.longitude)
            }
        }
    }

    private fun applyDeviceLocation(lat: Double, lng: Double, speed: Float, accuracy: Float) {
        val state = _uiState.value

        if (!hasUserManuallySelectedDestination &&
            !hasAutoLocalizedInitialPin &&
            state.trackingState == TrackingState.IDLE &&
            savedDestinations.value.isEmpty()
        ) {
            val distToDefault = LocationUtils.calculateDistanceMeters(
                lat, lng, state.destinationLat, state.destinationLng
            )
            if (distToDefault > 100f) {
                hasAutoLocalizedInitialPin = true
                _uiState.value = state.copy(
                    currentLat = lat,
                    currentLng = lng,
                    destinationLat = lat,
                    destinationLng = lng,
                    currentSpeedMps = speed,
                    currentAccuracyMeters = accuracy
                )
                return
            }
        }

        val dist = if (LocationUtils.isValidCoordinate(state.destinationLat, state.destinationLng)) {
            LocationUtils.calculateDistanceMeters(lat, lng, state.destinationLat, state.destinationLng)
        } else null
        val bearing = if (LocationUtils.isValidCoordinate(state.destinationLat, state.destinationLng)) {
            LocationUtils.calculateBearingDegrees(lat, lng, state.destinationLat, state.destinationLng)
        } else 0f

        _uiState.value = state.copy(
            currentLat = lat,
            currentLng = lng,
            currentSpeedMps = speed,
            currentAccuracyMeters = accuracy,
            distanceToDestination = dist,
            bearingToDestination = bearing
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            // 850ms debounce to strictly respect OpenStreetMap Nominatim's 1 req/s policy
            delay(850L)
            val results = LocationUtils.searchPlaces(appContext, query)
            _uiState.value = _uiState.value.copy(
                searchResults = results,
                isSearching = false
            )
        }
    }

    fun selectSearchResult(result: GeocodingResult) {
        val shortName = result.displayName.split(",").firstOrNull()?.trim()
            ?: result.displayName
        setDestination(
            lat = result.lat,
            lng = result.lon,
            name = shortName,
            address = result.displayName,
            fromUserAction = true
        )
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }

    // ── Permissions & Messages ───────────────────────────────

    fun refreshPermissionsState() {
        _uiState.value = _uiState.value.copy(
            hasLocationPermission = LocationUtils.hasLocationPermission(appContext),
            hasBackgroundLocationPermission = LocationUtils.hasBackgroundLocationPermission(appContext),
            hasSmsPermission = LocationUtils.hasSmsPermission(appContext),
            hasNotificationPermission = LocationUtils.hasNotificationPermission(appContext),
            isBatteryOptimizationIgnored = LocationUtils.isIgnoringBatteryOptimizations(appContext)
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearStatusBanner() {
        _uiState.value = _uiState.value.copy(statusBannerMessage = null)
    }

    private fun buildSessionConfig(
        state: TrackingUiState,
        destLat: Double = state.destinationLat,
        destLng: Double = state.destinationLng
    ): TrackingSessionConfig {
        return TrackingSessionConfig(
            destinationName = state.destinationName.ifBlank {
                "Destination (${"%.4f".format(Locale.US, destLat)}, ${"%.4f".format(Locale.US, destLng)})"
            },
            destinationLat = destLat,
            destinationLng = destLng,
            alertDistanceMeters = state.alertDistanceMeters,
            alertLeadTimeMinutes = state.alertLeadTimeMinutes,
            alertTypeValue = state.alertType.value,
            alertSoundOption = state.alertSoundOption.value,
            customRingtoneUri = state.customRingtoneUri,
            reminderNote = state.reminderNote.trim(),
            emergencyContact = state.emergencyContact.trim(),
            emergencyContactName = state.emergencyContactName.trim(),
            overshootMarginMeters = state.overshootMarginMeters,
            unacknowledgedTimeoutMinutes = state.unacknowledgedTimeoutMinutes,
            snoozeDurationMinutes = state.snoozeDurationMinutes,
            highAccuracyMode = state.highAccuracyMode
        )
    }

    private fun persistCurrentSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveLastSettings(
                alertDistance = state.alertDistanceMeters,
                alertType = state.alertType.value,
                alertLeadTimeMinutes = state.alertLeadTimeMinutes,
                alertSoundOption = state.alertSoundOption.value,
                customRingtoneUri = state.customRingtoneUri,
                customRingtoneTitle = state.customRingtoneTitle,
                emergencyContact = state.emergencyContact.trim(),
                emergencyContactName = state.emergencyContactName.trim(),
                overshootMargin = state.overshootMarginMeters,
                unacknowledgedTimeoutMinutes = state.unacknowledgedTimeoutMinutes,
                snoozeMinutes = state.snoozeDurationMinutes,
                highAccuracyMode = state.highAccuracyMode
            )
        }
    }

    private fun syncLiveConfigIfNeeded() {
        val state = _uiState.value
        if (state.trackingState != TrackingState.IDLE) {
            LocationService.updateLiveConfig(
                context = appContext,
                newConfig = buildSessionConfig(state)
            )
        }
    }
}
