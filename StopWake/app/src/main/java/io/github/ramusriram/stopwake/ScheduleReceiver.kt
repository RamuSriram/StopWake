package io.github.ramusriram.stopwake

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.ramusriram.stopwake.data.CommuteSchedule
import io.github.ramusriram.stopwake.data.DestinationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that executes scheduled auto-start and auto-stop commute tracking
 * and re-registers alarms after device reboot or time changes.
 * Includes Android 14/15 background FGS protection with a high-priority one-tap commute prompt fallback.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = DestinationRepository(appContext)
                val allSchedules = repo.getSchedulesSnapshot()

                when (action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        ScheduleAlarmScheduler.syncAllSchedules(appContext, allSchedules)
                    }

                    ScheduleAlarmScheduler.ACTION_SCHEDULE_START -> {
                        val scheduleId = intent.getStringExtra(ScheduleAlarmScheduler.EXTRA_SCHEDULE_ID)
                        val matching = allSchedules.find { it.id == scheduleId }

                        if (matching != null && matching.isEnabled) {
                            // Re-schedule next occurrence immediately
                            ScheduleAlarmScheduler.scheduleNextStartAndStop(appContext, matching)

                            val config = TrackingSessionConfig(
                                destinationName = matching.destinationName,
                                destinationLat = matching.destinationLat,
                                destinationLng = matching.destinationLng,
                                alertDistanceMeters = matching.alertDistanceMeters,
                                alertLeadTimeMinutes = repo.lastAlertLeadTimeMinutes.first(),
                                alertTypeValue = matching.alertTypeValue,
                                alertSoundOption = repo.lastAlertSoundOption.first(),
                                customRingtoneUri = repo.lastCustomRingtoneUri.first(),
                                reminderNote = matching.reminderNote,
                                emergencyContact = repo.lastEmergencyContact.first(),
                                emergencyContactName = repo.lastEmergencyContactName.first(),
                                overshootMarginMeters = repo.lastOvershootMargin.first(),
                                unacknowledgedTimeoutMinutes = repo.lastUnacknowledgedTimeoutMinutes.first(),
                                snoozeDurationMinutes = repo.lastSnoozeMinutes.first(),
                                highAccuracyMode = repo.highAccuracyMode.first()
                            )
                            LocationService.activeSessionConfig = config
                            LocationService.trackingDestLat = config.destinationLat
                            LocationService.trackingDestLng = config.destinationLng

                            var startedDirectly = false
                            // On Android 10+ (and especially Android 14/15), starting a LOCATION foreground service
                            // from a background alarm broadcast requires ACCESS_BACKGROUND_LOCATION ("Allow all the time").
                            // Without it, startForegroundService() succeeds in the receiver, but ServiceCompat.startForeground()
                            // throws a SecurityException inside LocationService.
                            val canStartLocationFgsInBackground =
                                LocationUtils.hasBackgroundLocationPermission(appContext) &&
                                    LocationUtils.hasForegroundLocationServicePermission(appContext)

                            if (canStartLocationFgsInBackground) {
                                val serviceIntent = Intent(appContext, LocationService::class.java).apply {
                                    this.action = LocationService.ACTION_START_TRACKING
                                    putExtra(ScheduleAlarmScheduler.EXTRA_SCHEDULE_ID, matching.id)
                                    putExtra(EXTRA_SCHEDULE_LABEL, matching.label)
                                }
                                try {
                                    ContextCompat.startForegroundService(appContext, serviceIntent)
                                    startedDirectly = true
                                    Log.d("ScheduleReceiver", "Auto-started scheduled commute: ${matching.label}")
                                } catch (e: Exception) {
                                    Log.w("ScheduleReceiver", "Background FGS start restricted by OS; posting one-tap commute notification", e)
                                }
                            }

                            if (!startedDirectly) {
                                postScheduledCommutePromptNotification(appContext, matching)
                            }
                        }
                    }

                    ScheduleAlarmScheduler.ACTION_SCHEDULE_STOP -> {
                        val scheduleId = intent.getStringExtra(ScheduleAlarmScheduler.EXTRA_SCHEDULE_ID)
                        val matching = allSchedules.find { it.id == scheduleId }
                        if (matching != null && matching.isEnabled) {
                            ScheduleAlarmScheduler.scheduleNextStartAndStop(appContext, matching)
                        }

                        // Dismiss any pending schedule prompt notification and stop tracking if running
                        dismissSchedulePromptNotification(appContext)
                        LocationService.dismissAllAlerts(appContext)
                        if (LocationService.trackingSnapshot.value.isServiceRunning) {
                            val stopIntent = Intent(appContext, LocationService::class.java).apply {
                                this.action = LocationService.ACTION_STOP_TRACKING
                            }
                            try {
                                appContext.startService(stopIntent)
                            } catch (_: Exception) {
                                appContext.stopService(Intent(appContext, LocationService::class.java))
                            }
                            Log.d("ScheduleReceiver", "Auto-stopped scheduled commute")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ScheduleReceiver", "Error handling schedule broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postScheduledCommutePromptNotification(context: Context, schedule: CommuteSchedule) {
        postScheduledCommuteFallbackNotification(
            context = context,
            scheduleId = schedule.id,
            label = schedule.label,
            destinationName = schedule.destinationName,
            alertDistanceMeters = schedule.alertDistanceMeters
        )
    }

    companion object {
        const val EXTRA_AUTO_START_SCHEDULE_ID = "io.github.ramusriram.stopwake.EXTRA_AUTO_START_SCHEDULE_ID"
        const val EXTRA_SCHEDULE_LABEL = "io.github.ramusriram.stopwake.EXTRA_SCHEDULE_LABEL"
        const val SCHEDULE_PROMPT_NOTIFICATION_ID = 2005

        fun dismissSchedulePromptNotification(context: Context) {
            try {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.cancel(SCHEDULE_PROMPT_NOTIFICATION_ID)
            } catch (_: Exception) {}
        }

        fun postScheduledCommuteFallbackNotification(
            context: Context,
            scheduleId: String?,
            label: String,
            destinationName: String,
            alertDistanceMeters: Int
        ) {
            LocationService.createNotificationChannels(context)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!scheduleId.isNullOrBlank()) {
                    putExtra(EXTRA_AUTO_START_SCHEDULE_ID, scheduleId)
                } else {
                    putExtra(EXTRA_AUTO_START_ACTIVE_CONFIG, true)
                }
            }
            val requestCode = scheduleId?.hashCode() ?: SCHEDULE_PROMPT_NOTIFICATION_ID
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "🚌 Time for $label"
            val body = "Tap to start tracking your commute to $destinationName (${alertDistanceMeters}m alert zone)."

            val notification = NotificationCompat.Builder(context, LocationService.ACTIVE_ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(launchPendingIntent)
                .addAction(R.drawable.ic_notification, "Start Tracking", launchPendingIntent)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.notify(SCHEDULE_PROMPT_NOTIFICATION_ID, notification)
        }

        const val EXTRA_AUTO_START_ACTIVE_CONFIG = "io.github.ramusriram.stopwake.EXTRA_AUTO_START_ACTIVE_CONFIG"
    }
}
