package io.github.ramusriram.stopwake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.ramusriram.stopwake.data.CommuteSchedule
import java.util.Calendar

/**
 * Manages OS-level [AlarmManager] triggers for recurring [CommuteSchedule] start and end times.
 */
object ScheduleAlarmScheduler {
    private const val TAG = "ScheduleAlarmScheduler"

    const val ACTION_SCHEDULE_START = "io.github.ramusriram.stopwake.SCHEDULE_AUTO_START"
    const val ACTION_SCHEDULE_STOP = "io.github.ramusriram.stopwake.SCHEDULE_AUTO_STOP"

    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_SCHEDULE_LABEL = "extra_schedule_label"
    const val EXTRA_DEST_NAME = "extra_dest_name"
    const val EXTRA_DEST_LAT = "extra_dest_lat"
    const val EXTRA_DEST_LNG = "extra_dest_lng"
    const val EXTRA_ALERT_DISTANCE = "extra_alert_distance"
    const val EXTRA_ALERT_TYPE = "extra_alert_type"

    /**
     * Synchronize all schedules with Android's [AlarmManager].
     */
    fun syncAllSchedules(context: Context, schedules: List<CommuteSchedule>) {
        schedules.forEach { schedule ->
            if (schedule.isEnabled && schedule.daysOfWeek.isNotEmpty()) {
                scheduleNextStartAndStop(context, schedule)
            } else {
                cancelScheduleAlarms(context, schedule.id)
            }
        }
    }

    /**
     * Schedule the next upcoming Start and Stop alarms for a single [CommuteSchedule].
     */
    fun scheduleNextStartAndStop(context: Context, schedule: CommuteSchedule) {
        if (!schedule.isEnabled || schedule.daysOfWeek.isEmpty()) {
            cancelScheduleAlarms(context, schedule.id)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        val nextStartMillis = computeNextTriggerMillis(
            hour = schedule.startHour,
            minute = schedule.startMinute,
            daysOfWeek = schedule.daysOfWeek
        )
        val nextStopMillis = computeNextTriggerMillis(
            hour = schedule.endHour,
            minute = schedule.endMinute,
            daysOfWeek = schedule.daysOfWeek
        )

        if (nextStartMillis != null) {
            val startPi = buildPendingIntent(
                context = context,
                schedule = schedule,
                action = ACTION_SCHEDULE_START,
                requestCode = startRequestCode(schedule.id)
            )
            setReliableAlarm(alarmManager, nextStartMillis, startPi)
            Log.d(TAG, "Scheduled auto-start for '${schedule.label}' at $nextStartMillis")
        }

        if (nextStopMillis != null) {
            val stopPi = buildPendingIntent(
                context = context,
                schedule = schedule,
                action = ACTION_SCHEDULE_STOP,
                requestCode = stopRequestCode(schedule.id)
            )
            setReliableAlarm(alarmManager, nextStopMillis, stopPi)
            Log.d(TAG, "Scheduled auto-stop for '${schedule.label}' at $nextStopMillis")
        }
    }

    /**
     * Cancel both Start and Stop alarms for the given schedule ID.
     */
    fun cancelScheduleAlarms(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        val startIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_START
        }
        val startPi = PendingIntent.getBroadcast(
            context,
            startRequestCode(scheduleId),
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(startPi)

        val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_STOP
        }
        val stopPi = PendingIntent.getBroadcast(
            context,
            stopRequestCode(scheduleId),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(stopPi)
    }

    /**
     * Compute the next epoch milliseconds for a given (hour, minute) matching one of [daysOfWeek]
     * where 1 = Monday .. 7 = Sunday.
     */
    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        daysOfWeek: Set<Int>,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (daysOfWeek.isEmpty()) return null

        for (dayOffset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val isoDay = calendarDayToIsoDay(candidate.get(Calendar.DAY_OF_WEEK))
            if (isoDay in daysOfWeek && candidate.timeInMillis > nowMillis + 2000L) {
                return candidate.timeInMillis
            }
        }
        return null
    }

    /**
     * Convert `Calendar.DAY_OF_WEEK` (Sunday=1..Saturday=7) to ISO day (Monday=1..Sunday=7).
     */
    fun calendarDayToIsoDay(calDay: Int): Int = when (calDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    private fun buildPendingIntent(
        context: Context,
        schedule: CommuteSchedule,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(EXTRA_SCHEDULE_LABEL, schedule.label)
            putExtra(EXTRA_DEST_NAME, schedule.destinationName)
            putExtra(EXTRA_DEST_LAT, schedule.destinationLat)
            putExtra(EXTRA_DEST_LNG, schedule.destinationLng)
            putExtra(EXTRA_ALERT_DISTANCE, schedule.alertDistanceMeters)
            putExtra(EXTRA_ALERT_TYPE, schedule.alertTypeValue)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setReliableAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun startRequestCode(id: String): Int = (id.hashCode() and 0x0FFFFFFF) + 1000
    private fun stopRequestCode(id: String): Int = (id.hashCode() and 0x0FFFFFFF) + 2000
}
