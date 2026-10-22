package io.github.ramusriram.stopwake

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.gson.Gson
import io.github.ramusriram.stopwake.viewmodel.TrackingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Configuration snapshot passed to [LocationService] when starting or updating tracking.
 */
data class TrackingSessionConfig(
    val destinationName: String = "Selected Destination",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val alertDistanceMeters: Int = 250,
    val alertLeadTimeMinutes: Int = 0, // 0 = distance only; >0 = also alert when ETA <= N mins
    val alertTypeValue: Int = 4, // 1=Silent, 2=Vibrate, 3=Alarm, 4=Smart Adaptive
    val alertSoundOption: Int = 1, // 1=Alarm Siren, 2=Ringtone, 3=Gentle Chime, 4=Custom URI
    val customRingtoneUri: String = "",
    val reminderNote: String = "",
    val emergencyContact: String = "",
    val emergencyContactName: String = "",
    val overshootMarginMeters: Int = 100,
    val unacknowledgedTimeoutMinutes: Int = 2,
    val snoozeDurationMinutes: Int = 5,
    val highAccuracyMode: Boolean = true
)

/**
 * Live telemetry snapshot emitted by [LocationService] for UI observation.
 */
data class ServiceTrackingSnapshot(
    val isServiceRunning: Boolean = false,
    val trackingState: TrackingState = TrackingState.IDLE,
    val config: TrackingSessionConfig = TrackingSessionConfig(),
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val currentAccuracyMeters: Float = 0f,
    val initialDistanceMeters: Float? = null,
    val distanceToDestination: Float? = null,
    val minDistanceReachedMeters: Float? = null,
    val bearingToDestination: Float = 0f,
    val isWithinAlertZone: Boolean = false,
    val hasEnteredAlertZone: Boolean = false,
    val isAlarmPlaying: Boolean = false,
    val snoozeEndTimeMillis: Long = 0L,
    val emergencySmsSent: Boolean = false,
    val lastEmergencySmsTimeMillis: Long? = null,
    val startedAtMillis: Long = 0L,
    val gpsPowerModeLabel: String = "Adaptive GPS",
    val pathPoints: List<Pair<Double, Double>> = emptyList()
)

/**
 * Completed session summary emitted when tracking stops so ViewModel can log trip history.
 */
data class CompletedSessionEvent(
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val alertDistanceMeters: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val outcome: String,
    val minDistanceReachedMeters: Float?
)

private enum class GpsPollingTier(
    val intervalMs: Long,
    val minDistanceMeters: Float,
    val label: String
) {
    ECO_FAR(10000L, 10f, "Eco Battery Saver (10s)"),
    BALANCED_MID(4000L, 3f, "Balanced GPS (4s)"),
    HIGH_PRECISION_NEAR(1500L, 1f, "High-Precision GPS (1.5s)")
}

/**
 * Autonomous foreground service for continuous battery-optimized GPS tracking, geofence & lead-time
 * proximity detection, activity-aware wake-up alarms, heads-up pop-up banners, arrival confirmation,
 * smart snooze, and unacknowledged-alert / overshoot emergency SMS protection.
 */
class LocationService : Service() {

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var lastAcceptedLocation: Location? = null
    private var snoozeCheckRunnable: Runnable? = null
    private var currentGpsTier: GpsPollingTier? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONFIRM_ARRIVAL -> {
                stopTrackingInternal(reason = "Confirmed Safe Arrival", forceConfirmedOutcome = true)
                return START_NOT_STICKY
            }
            ACTION_STOP_TRACKING -> {
                stopTrackingInternal(reason = "Stopped by user")
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val mins = _trackingSnapshot.value.config.snoozeDurationMinutes.coerceAtLeast(1)
                snoozeInternal(mins)
                return START_STICKY
            }
            ACTION_DISMISS_ALERT -> {
                dismissAllAlerts(this)
                if (_trackingSnapshot.value.isServiceRunning) {
                    updateForegroundNotification(null)
                    return START_STICKY
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_UPDATE_CONFIG -> {
                persistActiveSession(this, activeSessionConfig)
                val snap = _trackingSnapshot.value
                if (snap.isServiceRunning && LocationUtils.isValidCoordinate(snap.currentLat, snap.currentLng)) {
                    val loc = Location("manual").apply {
                        latitude = snap.currentLat
                        longitude = snap.currentLng
                        speed = snap.currentSpeedMps
                        accuracy = snap.currentAccuracyMeters
                    }
                    processLocationUpdate(loc)
                }
                return START_STICKY
            }
        }

        // Starting or restarting tracking session (including OS START_STICKY process recreation)
        var config = activeSessionConfig
        if (config.destinationLat == 0.0 && config.destinationLng == 0.0) {
            val restored = restorePersistedActiveSession(this)
            if (restored != null) {
                config = restored
                activeSessionConfig = restored
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            persistActiveSession(this, config)
        }

        trackingDestLat = config.destinationLat
        trackingDestLng = config.destinationLng
        currentGpsTier = null
        lastAcceptedLocation = null

        val now = System.currentTimeMillis()
        _trackingSnapshot.value = ServiceTrackingSnapshot(
            isServiceRunning = true,
            trackingState = TrackingState.TRACKING,
            config = config,
            startedAtMillis = now,
            gpsPowerModeLabel = if (config.highAccuracyMode) "Adaptive Battery Saver" else "Eco Battery Saver"
        )

        val foregroundNotif = buildForegroundNotification(null)
        try {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                foregroundNotif,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
            ScheduleReceiver.dismissSchedulePromptNotification(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground; posting one-tap commute fallback notification", e)
            _trackingSnapshot.value = ServiceTrackingSnapshot()
            clearPersistedActiveSession(this)
            val schedId = intent?.getStringExtra(ScheduleAlarmScheduler.EXTRA_SCHEDULE_ID)
            val schedLabel = intent?.getStringExtra(ScheduleReceiver.EXTRA_SCHEDULE_LABEL)
                ?.takeIf { it.isNotBlank() } ?: "Scheduled Commute"
            ScheduleReceiver.postScheduledCommuteFallbackNotification(
                context = this,
                scheduleId = schedId,
                label = schedLabel,
                destinationName = config.destinationName,
                alertDistanceMeters = config.alertDistanceMeters
            )
            stopSelf()
            return START_NOT_STICKY
        }

        applyGpsTier(GpsPollingTier.BALANCED_MID)
        startSnoozeWatcher()
        return START_STICKY
    }

    private fun applyGpsTier(tier: GpsPollingTier) {
        if (currentGpsTier == tier && locationListener != null) return
        currentGpsTier = tier

        val lm = locationManager ?: (getSystemService(Context.LOCATION_SERVICE) as? LocationManager).also {
            locationManager = it
        }
        if (lm == null) return

        locationListener?.let {
            try {
                lm.removeUpdates(it)
            } catch (_: Exception) {}
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (LocationUtils.isBetterLocation(location, lastAcceptedLocation)) {
                    lastAcceptedLocation = location
                    processLocationUpdate(location)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locationListener = listener

        try {
            if (LocationUtils.hasLocationPermission(this)) {
                val providers = buildList {
                    add(LocationManager.GPS_PROVIDER)
                    add(LocationManager.NETWORK_PROVIDER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(LocationManager.FUSED_PROVIDER)
                    }
                }
                for (provider in providers) {
                    try {
                        if (lm.allProviders.contains(provider)) {
                            lm.requestLocationUpdates(
                                provider,
                                tier.intervalMs,
                                tier.minDistanceMeters,
                                listener,
                                Looper.getMainLooper()
                            )
                        }
                    } catch (_: Exception) {}
                }

                LocationUtils.getBestLastKnownLocation(this)?.let { lastLoc ->
                    if (_trackingSnapshot.value.isServiceRunning) {
                        lastAcceptedLocation = lastLoc
                        processLocationUpdate(lastLoc)
                    }
                }
            } else {
                Log.e(TAG, "Location permission missing, stopping service")
                stopTrackingInternal("Permission missing")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            stopTrackingInternal("Permission error")
        }
    }

    private fun updateAdaptiveGpsPollingIfNeeded(distanceMeters: Float, config: TrackingSessionConfig) {
        val desiredTier = when {
            !config.highAccuracyMode && distanceMeters > config.alertDistanceMeters * 2 -> GpsPollingTier.ECO_FAR
            distanceMeters <= 1000f || distanceMeters <= config.alertDistanceMeters * 2.2f -> GpsPollingTier.HIGH_PRECISION_NEAR
            distanceMeters <= 3200f -> GpsPollingTier.BALANCED_MID
            else -> GpsPollingTier.ECO_FAR
        }
        if (desiredTier != currentGpsTier) {
            applyGpsTier(desiredTier)
        }
    }

    private fun startSnoozeWatcher() {
        snoozeCheckRunnable?.let { sharedHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val snap = _trackingSnapshot.value
                if (!snap.isServiceRunning) return
                if (snap.trackingState == TrackingState.SNOOZED &&
                    System.currentTimeMillis() >= snap.snoozeEndTimeMillis
                ) {
                    _trackingSnapshot.value = snap.copy(
                        trackingState = TrackingState.TRACKING,
                        isWithinAlertZone = false,
                        snoozeEndTimeMillis = 0L
                    )
                    if (LocationUtils.isValidCoordinate(snap.currentLat, snap.currentLng)) {
                        val loc = Location("snooze_resume").apply {
                            latitude = snap.currentLat
                            longitude = snap.currentLng
                            speed = snap.currentSpeedMps
                            accuracy = snap.currentAccuracyMeters
                        }
                        processLocationUpdate(loc)
                    } else {
                        updateForegroundNotification(null)
                    }
                }
                sharedHandler.postDelayed(this, 2000L)
            }
        }
        snoozeCheckRunnable = runnable
        sharedHandler.postDelayed(runnable, 2000L)
    }

    private fun processLocationUpdate(location: Location) {
        _locationUpdates.tryEmit(location)
        val snap = _trackingSnapshot.value
        if (!snap.isServiceRunning) return

        val config = snap.config
        val dist = LocationUtils.calculateDistanceMeters(
            location.latitude,
            location.longitude,
            config.destinationLat,
            config.destinationLng
        )
        val bearing = LocationUtils.calculateBearingDegrees(
            location.latitude,
            location.longitude,
            config.destinationLat,
            config.destinationLng
        )

        // Dynamically adjust GPS polling interval to save battery when far and lock high-precision when near
        updateAdaptiveGpsPollingIfNeeded(dist, config)

        val initialDist = snap.initialDistanceMeters ?: dist
        val minDist = if (snap.minDistanceReachedMeters == null) {
            dist
        } else {
            kotlin.math.min(snap.minDistanceReachedMeters, dist)
        }

        val currentPath = snap.pathPoints
        val updatedPath = if (currentPath.isEmpty() ||
            LocationUtils.calculateDistanceMeters(
                currentPath.last().first,
                currentPath.last().second,
                location.latitude,
                location.longitude
            ) >= 8f
        ) {
            (currentPath + Pair(location.latitude, location.longitude)).takeLast(200)
        } else {
            currentPath
        }

        var nextState = snap.trackingState
        var withinZone = snap.isWithinAlertZone
        var enteredZoneOnce = snap.hasEnteredAlertZone
        var emergencySent = snap.emergencySmsSent
        var emergencyTime = snap.lastEmergencySmsTimeMillis

        if (nextState == TrackingState.SNOOZED && System.currentTimeMillis() >= snap.snoozeEndTimeMillis) {
            nextState = TrackingState.TRACKING
            withinZone = false
        }

        val etaMinutes = LocationUtils.estimateEtaMinutes(dist, location.speed)
        val isWithinDistanceZone = dist <= config.alertDistanceMeters
        val isWithinLeadTimeZone = config.alertLeadTimeMinutes > 0 &&
            dist <= 5000f &&
            etaMinutes <= config.alertLeadTimeMinutes

        if (nextState != TrackingState.SNOOZED) {
            // 1. Entering destination alert radius OR lead-time threshold
            if ((isWithinDistanceZone || isWithinLeadTimeZone) && !withinZone) {
                withinZone = true
                enteredZoneOnce = true
                nextState = TrackingState.ARRIVED
                triggerProximityAlert(config)
            }
            // 2. Overshoot / moving away after entering the alert zone -> Emergency SMS
            else if (enteredZoneOnce && dist > config.alertDistanceMeters + config.overshootMarginMeters) {
                withinZone = false
                nextState = TrackingState.TRACKING
                dismissAllAlerts(this)

                if (config.emergencyContact.isNotBlank() && !emergencySent) {
                    emergencySent = true
                    emergencyTime = System.currentTimeMillis()
                    sendEmergencySms(
                        context = this,
                        phoneNumber = config.emergencyContact,
                        destinationName = config.destinationName,
                        currentLat = location.latitude,
                        currentLng = location.longitude,
                        distanceMeters = dist,
                        reasonText = "moved past destination"
                    )
                    sendEmergencyAlertNotification(
                        contact = config.emergencyContact,
                        destinationName = config.destinationName,
                        reason = "You moved past ${config.destinationName}."
                    )
                }
            }
        }

        _trackingSnapshot.value = _trackingSnapshot.value.copy(
            trackingState = nextState,
            currentLat = location.latitude,
            currentLng = location.longitude,
            currentSpeedMps = location.speed,
            currentAccuracyMeters = location.accuracy,
            initialDistanceMeters = initialDist,
            distanceToDestination = dist,
            minDistanceReachedMeters = minDist,
            bearingToDestination = bearing,
            isWithinAlertZone = withinZone,
            hasEnteredAlertZone = enteredZoneOnce,
            emergencySmsSent = emergencySent,
            lastEmergencySmsTimeMillis = emergencyTime,
            gpsPowerModeLabel = currentGpsTier?.label ?: "Adaptive GPS",
            pathPoints = updatedPath
        )

        updateForegroundNotification(location)
    }

    private fun snoozeInternal(durationMinutes: Int) {
        dismissAllAlerts(this)
        val endTime = System.currentTimeMillis() + durationMinutes * 60_000L
        val snap = _trackingSnapshot.value
        _trackingSnapshot.value = snap.copy(
            trackingState = TrackingState.SNOOZED,
            snoozeEndTimeMillis = endTime,
            isAlarmPlaying = false
        )
        updateForegroundNotification(null)
    }

    private fun updateForegroundNotification(location: Location?) {
        val notification = buildForegroundNotification(location)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(location: Location?): Notification {
        val snap = _trackingSnapshot.value
        val config = snap.config

        val confirmIntent = Intent(this, StopTrackingReceiver::class.java).apply {
            action = ACTION_CONFIRM_ARRIVAL
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            this, 9, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StopTrackingReceiver::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 10, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, StopTrackingReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, 11, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this, 12, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dist = snap.distanceToDestination ?: location?.let {
            LocationUtils.calculateDistanceMeters(
                it.latitude, it.longitude,
                config.destinationLat, config.destinationLng
            )
        }

        val destLabel = config.destinationName.ifBlank { "Destination" }
        val titleText = when (snap.trackingState) {
            TrackingState.ARRIVED -> "Arriving: $destLabel"
            TrackingState.SNOOZED -> "Snoozed: $destLabel"
            else -> "Heading to $destLabel"
        }

        val contentText = when {
            snap.trackingState == TrackingState.SNOOZED -> {
                val remMin = ((snap.snoozeEndTimeMillis - System.currentTimeMillis()) / 60000L)
                    .coerceAtLeast(1L)
                val distPart = dist?.let { "${LocationUtils.formatDistance(it)} away • " } ?: ""
                "${distPart}Snoozed for ~$remMin min"
            }
            dist != null -> {
                val eta = LocationUtils.estimateEtaMinutes(dist, snap.currentSpeedMps)
                "${LocationUtils.formatDistance(dist)} away • Alert at ${config.alertDistanceMeters}m (~$eta min)"
            }
            else -> "Getting GPS signal for $destLabel..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(titleText)
                    .bigText(contentText)
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(launchPendingIntent)
            .addAction(R.drawable.ic_stop, "Confirm", confirmPendingIntent)
            .addAction(R.drawable.ic_stop, "Snooze ${config.snoozeDurationMinutes}m", snoozePendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ── Proximity Wake-Up Alert Methods ──────────────────────

    private fun triggerProximityAlert(config: TrackingSessionConfig) {
        if (_trackingSnapshot.value.isAlarmPlaying) return
        _trackingSnapshot.value = _trackingSnapshot.value.copy(isAlarmPlaying = true)
        _isAnyAlertActive.value = true

        sendAlertNotification(config)
        activateAlertHardware(
            context = this,
            alertTypeValue = config.alertTypeValue,
            alertSoundOption = config.alertSoundOption,
            customRingtoneUri = config.customRingtoneUri
        )

        // Start Safety-Net Unacknowledged Arrival Alert Timer if Emergency Contact is set
        scheduleUnacknowledgedAlertSafetyNet(config)
    }

    private fun scheduleUnacknowledgedAlertSafetyNet(config: TrackingSessionConfig) {
        unacknowledgedAlertRunnable?.let { sharedHandler.removeCallbacks(it) }
        if (config.emergencyContact.isBlank()) return

        val timeoutMs = (config.unacknowledgedTimeoutMinutes.coerceIn(1, 30)) * 60_000L
        acquireAlertWakeLock(this, timeoutMs + 60_000L)
        val runnable = Runnable {
            val snap = _trackingSnapshot.value
            if (snap.isServiceRunning && snap.isAlarmPlaying && !snap.emergencySmsSent) {
                sendEmergencySms(
                    context = this@LocationService,
                    phoneNumber = config.emergencyContact,
                    destinationName = config.destinationName,
                    currentLat = snap.currentLat,
                    currentLng = snap.currentLng,
                    distanceMeters = snap.distanceToDestination,
                    reasonText = "did not respond to arrival alert within ${config.unacknowledgedTimeoutMinutes} min"
                )
                _trackingSnapshot.value = snap.copy(
                    emergencySmsSent = true,
                    lastEmergencySmsTimeMillis = System.currentTimeMillis()
                )
                sendEmergencyAlertNotification(
                    contact = config.emergencyContact,
                    destinationName = config.destinationName,
                    reason = "No response to arrival alert within ${config.unacknowledgedTimeoutMinutes} min."
                )
            }
        }
        unacknowledgedAlertRunnable = runnable
        sharedHandler.postDelayed(runnable, timeoutMs)
    }

    private fun sendAlertNotification(config: TrackingSessionConfig) {
        val snap = _trackingSnapshot.value
        val distText = snap.distanceToDestination?.let { LocationUtils.formatDistance(it) }
            ?: "${config.alertDistanceMeters}m"

        val confirmIntent = Intent(this, StopTrackingReceiver::class.java).apply {
            action = ACTION_CONFIRM_ARRIVAL
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            this, 19, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, StopTrackingReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, 21, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, StopTrackingReceiver::class.java).apply {
            action = ACTION_DISMISS_ALERT
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this, 22, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FULL_SCREEN_ALARM, true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 23, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (config.alertTypeValue == 1) SILENT_ALERT_CHANNEL_ID else ACTIVE_ALARM_CHANNEL_ID
        val noteSuffix = if (config.reminderNote.isNotBlank()) {
            "\nNote: ${config.reminderNote}"
        } else {
            ""
        }
        val alertTitle = "Approaching ${config.destinationName}"
        val bodyText = "Within $distText of ${config.destinationName}. Tap to silence or confirm arrival.$noteSuffix"

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alertTitle)
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(alertTitle)
                    .bigText(bodyText)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVibrate(if (config.alertTypeValue == 1) longArrayOf(0L, 1L) else longArrayOf(0L, 500L, 200L, 500L))
            .setOnlyAlertOnce(false)
            // Tapping the notification body OR swiping it away immediately dismisses the alert!
            .setContentIntent(dismissPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stop, "Confirm", confirmPendingIntent)
            .addAction(R.drawable.ic_stop, "Snooze ${config.snoozeDurationMinutes}m", snoozePendingIntent)
            .addAction(R.drawable.ic_stop, "Dismiss", dismissPendingIntent)

        if (config.alertTypeValue != 1) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        // Cancel first so Android SystemUI always animates a fresh Heads-Up Pop-Up Banner from the top!
        manager.cancel(ALERT_NOTIFICATION_ID)
        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    private fun sendEmergencyAlertNotification(contact: String, destinationName: String, reason: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this, 24, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snap = _trackingSnapshot.value
        val mapsLink = if (LocationUtils.isValidCoordinate(snap.currentLat, snap.currentLng)) {
            "https://maps.google.com/?q=${"%.5f".format(Locale.US, snap.currentLat)},${"%.5f".format(Locale.US, snap.currentLng)}"
        } else {
            ""
        }
        val smsBody = buildString {
            append("StopWake Safety Alert: I may have missed my stop ($destinationName). ")
            if (mapsLink.isNotBlank()) {
                append("Live GPS location: $mapsLink")
            }
        }
        val parsedContacts = LocationUtils.parseEmergencyContacts(contact)
        val smstoRecipients = if (parsedContacts.isNotEmpty()) {
            parsedContacts.joinToString(";") { it.number }
        } else {
            LocationUtils.normalizePhoneNumberForSms(contact).ifBlank { contact.trim() }
        }
        val displayRecipients = if (parsedContacts.isNotEmpty()) {
            parsedContacts.joinToString(", ") { it.displayNumber }
        } else {
            contact
        }

        val manualSmsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(smstoRecipients)}")).apply {
            putExtra("sms_body", smsBody)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val manualSmsPendingIntent = PendingIntent.getActivity(
            this, 25, manualSmsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val autoSent = snap.emergencySmsSent && LocationUtils.hasSmsPermission(this)
        val body = if (autoSent) {
            "$reason Emergency SMS dispatched to $displayRecipients for $destinationName."
        } else {
            "$reason Tap 'Send Emergency SMS' below to alert $displayRecipients with your live GPS location."
        }
        val notification = NotificationCompat.Builder(this, ACTIVE_ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Emergency Safety Alert ($destinationName)")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVibrate(longArrayOf(0L, 400L, 200L, 400L))
            .setContentIntent(launchPendingIntent)
            .addAction(R.drawable.ic_notification, "Send Emergency SMS", manualSmsPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(EMERGENCY_NOTIFICATION_ID)
        manager.notify(EMERGENCY_NOTIFICATION_ID, notification)
    }

    private fun stopTrackingInternal(reason: String, forceConfirmedOutcome: Boolean = false) {
        val snap = _trackingSnapshot.value
        clearPersistedActiveSession(this)
        dismissAllAlerts(this)
        snoozeCheckRunnable?.let { sharedHandler.removeCallbacks(it) }
        locationListener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (_: Exception) {}
        }
        locationListener = null
        lastAcceptedLocation = null
        currentGpsTier = null
        _locationUpdates.tryEmit(null)

        if (snap.isServiceRunning && snap.startedAtMillis > 0L) {
            val outcome = when {
                forceConfirmedOutcome -> "Confirmed Safe Arrival"
                snap.emergencySmsSent -> "Emergency Alert Sent"
                snap.hasEnteredAlertZone -> "Arrived at Destination"
                else -> reason
            }
            _completedSessions.tryEmit(
                CompletedSessionEvent(
                    destinationName = snap.config.destinationName,
                    destinationLat = snap.config.destinationLat,
                    destinationLng = snap.config.destinationLng,
                    alertDistanceMeters = snap.config.alertDistanceMeters,
                    startedAtMillis = snap.startedAtMillis,
                    endedAtMillis = System.currentTimeMillis(),
                    outcome = outcome,
                    minDistanceReachedMeters = snap.minDistanceReachedMeters
                )
            )
        }

        _trackingSnapshot.value = ServiceTrackingSnapshot(isServiceRunning = false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        snoozeCheckRunnable?.let { sharedHandler.removeCallbacks(it) }
        locationListener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (_: Exception) {}
        }
        dismissAllAlerts(this)
    }

    companion object {
        const val TAG = "LocationService"
        private const val ACTIVE_SESSION_PREFS = "stopwake_active_service_session"
        private const val KEY_ACTIVE_SESSION_JSON = "active_session_config_json"
        private const val WAKE_LOCK_TAG = "StopWake::AlertEscalationWakeLock"

        const val CHANNEL_ID = "stopwake_tracking_channel_v5"
        const val SILENT_ALERT_CHANNEL_ID = "stopwake_heads_up_banner_v5"
        const val ACTIVE_ALARM_CHANNEL_ID = "stopwake_heads_up_alarm_v5"
        const val EXTRA_FULL_SCREEN_ALARM = "io.github.ramusriram.stopwake.EXTRA_FULL_SCREEN_ALARM"

        // Legacy constant alias
        const val ALERT_CHANNEL_ID = ACTIVE_ALARM_CHANNEL_ID

        const val FOREGROUND_NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2002
        const val EMERGENCY_NOTIFICATION_ID = 2003
        const val TEST_NOTIFICATION_ID = 2004

        const val ACTION_START_TRACKING = "io.github.ramusriram.stopwake.START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.github.ramusriram.stopwake.STOP_TRACKING"
        const val ACTION_CONFIRM_ARRIVAL = "io.github.ramusriram.stopwake.CONFIRM_ARRIVAL"
        const val ACTION_SNOOZE = "io.github.ramusriram.stopwake.SNOOZE"
        const val ACTION_DISMISS_ALERT = "io.github.ramusriram.stopwake.DISMISS_ALERT"
        const val ACTION_UPDATE_CONFIG = "io.github.ramusriram.stopwake.UPDATE_CONFIG"
        const val STOP_TRACKING_BROADCAST = ACTION_STOP_TRACKING

        var trackingDestLat: Double = 0.0
        var trackingDestLng: Double = 0.0

        @Volatile
        var activeSessionConfig: TrackingSessionConfig = TrackingSessionConfig()

        private val _trackingSnapshot = MutableStateFlow(ServiceTrackingSnapshot())
        val trackingSnapshot: StateFlow<ServiceTrackingSnapshot> = _trackingSnapshot.asStateFlow()

        private val _isAnyAlertActive = MutableStateFlow(false)
        val isAnyAlertActive: StateFlow<Boolean> = _isAnyAlertActive.asStateFlow()

        private val _locationUpdates = MutableSharedFlow<Location?>(replay = 1)
        val locationUpdates = _locationUpdates.asSharedFlow()

        private val _completedSessions = MutableSharedFlow<CompletedSessionEvent>(extraBufferCapacity = 5)
        val completedSessions = _completedSessions.asSharedFlow()

        private val sharedHandler = Handler(Looper.getMainLooper())
        private val gson = Gson()
        private var activeAlertWakeLock: PowerManager.WakeLock? = null
        private var activeVibrator: Vibrator? = null
        private var vibrationKeepAliveRunnable: Runnable? = null
        private var adaptiveEscalationRunnable: Runnable? = null
        private var unacknowledgedAlertRunnable: Runnable? = null
        private var activeMediaPlayer: MediaPlayer? = null
        private var fallbackRingtone: Ringtone? = null

        private fun persistActiveSession(context: Context, config: TrackingSessionConfig) {
            if (!LocationUtils.isValidCoordinate(config.destinationLat, config.destinationLng) ||
                (config.destinationLat == 0.0 && config.destinationLng == 0.0)
            ) return
            try {
                context.applicationContext
                    .getSharedPreferences(ACTIVE_SESSION_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ACTIVE_SESSION_JSON, gson.toJson(config))
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist active session config", e)
            }
        }

        private fun restorePersistedActiveSession(context: Context): TrackingSessionConfig? {
            return try {
                val json = context.applicationContext
                    .getSharedPreferences(ACTIVE_SESSION_PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ACTIVE_SESSION_JSON, null)
                    ?: return null
                val restored = gson.fromJson(json, TrackingSessionConfig::class.java)
                if (restored != null &&
                    LocationUtils.isValidCoordinate(restored.destinationLat, restored.destinationLng) &&
                    (restored.destinationLat != 0.0 || restored.destinationLng != 0.0)
                ) {
                    restored
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore persisted session config", e)
                null
            }
        }

        private fun clearPersistedActiveSession(context: Context) {
            try {
                context.applicationContext
                    .getSharedPreferences(ACTIVE_SESSION_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_ACTIVE_SESSION_JSON)
                    .apply()
            } catch (_: Exception) {}
        }

        private fun acquireAlertWakeLock(context: Context, timeoutMs: Long = 10 * 60_000L) {
            try {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                val wl = activeAlertWakeLock ?: pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                ).apply {
                    setReferenceCounted(false)
                    activeAlertWakeLock = this
                }
                wl.acquire(timeoutMs)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to acquire alert wake lock", e)
            }
        }

        private fun releaseAlertWakeLock() {
            try {
                activeAlertWakeLock?.let { wl ->
                    if (wl.isHeld) {
                        wl.release()
                    }
                }
            } catch (_: Exception) {}
        }

        /**
         * Update live config if service is already running.
         */
        fun updateLiveConfig(context: Context, newConfig: TrackingSessionConfig) {
            activeSessionConfig = newConfig
            trackingDestLat = newConfig.destinationLat
            trackingDestLng = newConfig.destinationLng
            persistActiveSession(context, newConfig)
            if (_trackingSnapshot.value.isServiceRunning) {
                _trackingSnapshot.value = _trackingSnapshot.value.copy(config = newConfig)
                val intent = Intent(context, LocationService::class.java).apply {
                    action = ACTION_UPDATE_CONFIG
                }
                context.startService(intent)
            }
        }

        /**
         * Preview the selected alert mode (1 = Silent Banner, 2 = Continuous Vibration, 3 = Loud Alarm, 4 = Smart Adaptive).
         * Pops down a real Heads-Up Notification Banner at the top of the screen and continues until dismissed!
         */
        fun triggerTestAlert(
            context: Context,
            alertTypeValue: Int,
            alertDistance: Int,
            alertSoundOption: Int = 1,
            customRingtoneUri: String = "",
            reminderNote: String = ""
        ) {
            val appContext = context.applicationContext
            createNotificationChannels(appContext)
            dismissAllAlerts(appContext)

            _isAnyAlertActive.value = true
            _trackingSnapshot.value = _trackingSnapshot.value.copy(isAlarmPlaying = true)

            val dismissIntent = Intent(appContext, StopTrackingReceiver::class.java).apply {
                action = ACTION_DISMISS_ALERT
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                appContext,
                99,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val fullScreenIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FULL_SCREEN_ALARM, true)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                98,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val modeTitle = when (alertTypeValue) {
                1 -> "Destination Alert (Silent Banner)"
                2 -> "Destination Alert (Continuous Vibration)"
                4 -> "Smart Mode Alert Active"
                else -> "Destination Wake-Up Alarm"
            }
            val notePart = if (reminderNote.isNotBlank()) "\nNote: $reminderNote" else ""
            val modeBody = when (alertTypeValue) {
                1 -> "Approaching stop (${alertDistance}m zone). Tap this banner to dismiss.$notePart"
                2 -> "Vibrating continuously (${alertDistance}m zone). Tap this banner to stop.$notePart"
                4 -> "Smart alert (${alertDistance}m zone): gentle vibration first, escalates to full alarm if unacknowledged.$notePart"
                else -> "Playing wake-up alarm & vibrating (${alertDistance}m zone). Tap here to silence.$notePart"
            }

            val channelId = if (alertTypeValue == 1) SILENT_ALERT_CHANNEL_ID else ACTIVE_ALARM_CHANNEL_ID

            val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(modeTitle)
                .setContentText(modeBody)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(modeTitle)
                        .bigText(modeBody)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setVibrate(if (alertTypeValue == 1) longArrayOf(0L, 1L) else longArrayOf(0L, 500L, 200L, 500L))
                .setOnlyAlertOnce(false)
                // Tapping the notification body OR swiping it away immediately dismisses the test alert!
                .setContentIntent(dismissPendingIntent)
                .setDeleteIntent(dismissPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_stop, "Dismiss", dismissPendingIntent)

            if (alertTypeValue != 1) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
            }

            val manager = appContext.getSystemService(NotificationManager::class.java)
            // Cancel any previous test notification first so Android SystemUI always animates a fresh Heads-Up Banner!
            manager.cancel(TEST_NOTIFICATION_ID)
            manager.notify(TEST_NOTIFICATION_ID, builder.build())

            activateAlertHardware(
                context = appContext,
                alertTypeValue = alertTypeValue,
                alertSoundOption = alertSoundOption,
                customRingtoneUri = customRingtoneUri
            )
        }

        private fun activateAlertHardware(
            context: Context,
            alertTypeValue: Int,
            alertSoundOption: Int,
            customRingtoneUri: String
        ) {
            val appContext = context.applicationContext
            acquireAlertWakeLock(appContext, 10 * 60_000L)
            when (alertTypeValue) {
                1 -> {
                    // Mode 1: Silent Banner Only — heads-up pop-up banner with zero continuous vibration or sound.
                }
                2 -> {
                    // Mode 2: Continuous Vibration until dismissed.
                    startContinuousVibration(appContext, gentle = false)
                }
                4 -> {
                    // Mode 4: Smart Activity-Aware Alert Intensity!
                    // Check if the user is actively awake & using their unlocked screen:
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    val isScreenInteractiveAndUnlocked = (pm?.isInteractive == true) && (km?.isKeyguardLocked == false)

                    if (!isScreenInteractiveAndUnlocked) {
                        // Screen is off or locked (commuter may be asleep) -> Immediate full wake-up alarm + vibration!
                        startContinuousVibration(appContext, gentle = false)
                        startContinuousAlarmAudio(appContext, alertSoundOption, customRingtoneUri)
                    } else {
                        // User is awake & using phone -> Start with gentle vibration first, then escalate after 12s if not dismissed!
                        startContinuousVibration(appContext, gentle = true)
                        adaptiveEscalationRunnable?.let { sharedHandler.removeCallbacks(it) }
                        val escalate = Runnable {
                            if (_isAnyAlertActive.value) {
                                startContinuousVibration(appContext, gentle = false)
                                startContinuousAlarmAudio(appContext, alertSoundOption, customRingtoneUri)
                            }
                        }
                        adaptiveEscalationRunnable = escalate
                        sharedHandler.postDelayed(escalate, 12_000L)
                    }
                }
                else -> {
                    // Mode 3: Full Wake-Up Alarm (Continuous Vibration + Continuous Looping Alarm Sound).
                    startContinuousVibration(appContext, gentle = false)
                    startContinuousAlarmAudio(appContext, alertSoundOption, customRingtoneUri)
                }
            }
        }

        fun stopTestAlert(context: Context) {
            dismissAllAlerts(context)
        }

        /**
         * Immediately stops all active vibrations, alarm audio, escalation timers, wake locks, and alert notifications.
         */
        fun dismissAllAlerts(context: Context) {
            _isAnyAlertActive.value = false
            _trackingSnapshot.value = _trackingSnapshot.value.copy(isAlarmPlaying = false)

            vibrationKeepAliveRunnable?.let { sharedHandler.removeCallbacks(it) }
            vibrationKeepAliveRunnable = null

            adaptiveEscalationRunnable?.let { sharedHandler.removeCallbacks(it) }
            adaptiveEscalationRunnable = null

            unacknowledgedAlertRunnable?.let { sharedHandler.removeCallbacks(it) }
            unacknowledgedAlertRunnable = null

            releaseAlertWakeLock()

            try {
                activeVibrator?.cancel()
            } catch (_: Exception) {}
            activeVibrator = null

            try {
                activeMediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                }
            } catch (_: Exception) {}
            activeMediaPlayer = null

            try {
                fallbackRingtone?.stop()
            } catch (_: Exception) {}
            fallbackRingtone = null

            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(ALERT_NOTIFICATION_ID)
                manager.cancel(TEST_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }

        /**
         * Starts a continuous, repeating hardware vibration waveform that stays alive until [dismissAllAlerts] is called.
         */
        private fun startContinuousVibration(context: Context, gentle: Boolean = false) {
            val vib = getVibratorCompat(context) ?: return
            if (!vib.hasVibrator()) return
            activeVibrator = vib

            val pattern = if (gentle) {
                longArrayOf(0L, 280L, 500L, 280L, 900L)
            } else {
                longArrayOf(0L, 650L, 250L, 650L, 450L)
            }
            val alarmAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            fun pulseWave() {
                if (!_isAnyAlertActive.value) return
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        @Suppress("DEPRECATION")
                        vib.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttrs)
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(pattern, 0)
                    }
                } catch (_: Exception) {}
            }

            pulseWave()

            // Re-affirm waveform every 2.0 seconds in case the OEM OS interrupts repeating waveforms
            vibrationKeepAliveRunnable?.let { sharedHandler.removeCallbacks(it) }
            val keepAlive = object : Runnable {
                override fun run() {
                    if (!_isAnyAlertActive.value) return
                    pulseWave()
                    sharedHandler.postDelayed(this, 2000L)
                }
            }
            vibrationKeepAliveRunnable = keepAlive
            sharedHandler.postDelayed(keepAlive, 2000L)
        }

        /**
         * Starts a continuous looping alarm sound over the ALARM audio stream using the user's selected sound.
         */
        private fun startContinuousAlarmAudio(
            context: Context,
            alertSoundOption: Int = 1,
            customRingtoneUri: String = ""
        ) {
            val preferredUri: Uri? = when (alertSoundOption) {
                2 -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                3 -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                4 -> customRingtoneUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val uri = preferredUri ?: fallbackUri ?: return

            try {
                activeMediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Exception) {}

            try {
                val player = MediaPlayer().apply {
                    setDataSource(context.applicationContext, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                activeMediaPlayer = player
            } catch (e: Exception) {
                Log.w(TAG, "Custom/primary URI failed, falling back to default alarm Ringtone", e)
                val safeUri = fallbackUri ?: return
                try {
                    val rt = RingtoneManager.getRingtone(context.applicationContext, safeUri)?.apply {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = true
                        }
                        play()
                    }
                    fallbackRingtone = rt
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to play alarm audio", ex)
                }
            }
        }

        /**
         * Send an emergency SMS with the user's current coordinates and Google Maps link.
         */
        fun sendEmergencySms(
            context: Context,
            phoneNumber: String,
            destinationName: String,
            currentLat: Double,
            currentLng: Double,
            distanceMeters: Float? = null,
            isManualSos: Boolean = false,
            reasonText: String = "moved away from destination"
        ): Boolean {
            if (phoneNumber.isBlank()) return false
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "SEND_SMS permission not granted")
                return false
            }

            return try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                } ?: return false

                val hasValidCoords = LocationUtils.isValidCoordinate(currentLat, currentLng)
                val mapsLink = if (hasValidCoords) {
                    " https://maps.google.com/?q=${"%.5f".format(Locale.US, currentLat)},${"%.5f".format(Locale.US, currentLng)}"
                } else {
                    ""
                }
                val distInfo = distanceMeters?.let { " (${LocationUtils.formatDistance(it)} from $destinationName)" } ?: ""

                val message = if (isManualSos) {
                    "🚨 STOPWAKE SOS: I need assistance near $destinationName$distInfo.$mapsLink"
                } else {
                    "StopWake Safety Alert: User $reasonText ($destinationName)$distInfo.$mapsLink"
                }

                val recipients = LocationUtils.parseEmergencyContacts(phoneNumber)
                    .map { it.number }
                    .ifEmpty {
                        listOfNotNull(LocationUtils.normalizePhoneNumberForSms(phoneNumber).takeIf { it.isNotBlank() })
                    }
                if (recipients.isEmpty()) return false

                val parts = smsManager.divideMessage(message)
                var anySent = false
                for (recipient in recipients) {
                    try {
                        if (parts.size > 1) {
                            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
                        } else {
                            smsManager.sendTextMessage(recipient, null, message, null, null)
                        }
                        anySent = true
                        Log.d(TAG, "Emergency SMS sent to $recipient")
                    } catch (recipientErr: Exception) {
                        Log.e(TAG, "Failed to send emergency SMS to $recipient", recipientErr)
                    }
                }
                anySent
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send emergency SMS", e)
                false
            }
        }

        fun getVibratorCompat(context: Context): Vibrator? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return

                // Delete all legacy channels so cached OS settings never block heads-up banners
                try {
                    listOf(
                        "stopwake_tracking_channel",
                        "stopwake_alert_channel",
                        "stopwake_tracking_channel_v3",
                        "stopwake_silent_alert_v3",
                        "stopwake_active_alarm_v3",
                        "stopwake_heads_up_banner_v4",
                        "stopwake_heads_up_alarm_v4"
                    ).forEach { manager.deleteNotificationChannel(it) }
                } catch (_: Exception) {}

                val trackingChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Live Trip Distance",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows live distance to your stop while tracking is active"
                    enableVibration(false)
                    setSound(null, null)
                }

                // Channel for Mode 1 (Silent Banner): High importance + 1ms micro-vibration so Android
                // SystemUI's HeadsUpManager always pops down the banner at the top of the screen!
                val silentAlertChannel = NotificationChannel(
                    SILENT_ALERT_CHANNEL_ID,
                    "Pop-Up Destination Banners (Silent)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Heads-up pop-up banner at the top of the screen without loud sound"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 1L)
                    setSound(null, null)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

                // Channel for Modes 2, 3 & 4 (Vibration / Loud Alarm / Smart Adaptive):
                // High importance + vibration enabled so Android always pops down a Heads-Up Banner!
                val activeAlarmChannel = NotificationChannel(
                    ACTIVE_ALARM_CHANNEL_ID,
                    "Wake-Up Destination Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High-priority heads-up wake-up notifications with continuous vibration and alarm"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 500L, 200L, 500L)
                    setSound(null, null)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

                manager.createNotificationChannel(trackingChannel)
                manager.createNotificationChannel(silentAlertChannel)
                manager.createNotificationChannel(activeAlarmChannel)
            }
        }
    }
}