package io.github.ramusriram.stopwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver to handle Dismiss Alert, Snooze, and Stop Tracking actions
 * directly and immediately from notifications (body tap, action button, or swipe-to-dismiss).
 */
class StopTrackingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext

        when (action) {
            LocationService.ACTION_DISMISS_ALERT -> {
                // Immediately stop any active vibration, alarm audio, and alert/test notifications
                // without needing to start a service from the background.
                LocationService.dismissAllAlerts(appContext)
            }

            LocationService.ACTION_SNOOZE -> {
                LocationService.dismissAllAlerts(appContext)
                if (LocationService.trackingSnapshot.value.isServiceRunning) {
                    val serviceIntent = Intent(appContext, LocationService::class.java).apply {
                        this.action = LocationService.ACTION_SNOOZE
                    }
                    try {
                        appContext.startService(serviceIntent)
                    } catch (_: Exception) {}
                }
            }

            LocationService.ACTION_CONFIRM_ARRIVAL -> {
                LocationService.dismissAllAlerts(appContext)
                val serviceIntent = Intent(appContext, LocationService::class.java).apply {
                    this.action = LocationService.ACTION_CONFIRM_ARRIVAL
                }
                try {
                    appContext.startService(serviceIntent)
                } catch (_: Exception) {
                    appContext.stopService(Intent(appContext, LocationService::class.java))
                }
            }

            LocationService.ACTION_STOP_TRACKING -> {
                LocationService.dismissAllAlerts(appContext)
                val serviceIntent = Intent(appContext, LocationService::class.java).apply {
                    this.action = LocationService.ACTION_STOP_TRACKING
                }
                try {
                    appContext.startService(serviceIntent)
                } catch (_: Exception) {
                    appContext.stopService(Intent(appContext, LocationService::class.java))
                }
            }
        }
    }
}