# Privacy Policy for StopWake

**Effective Date:** October 19, 2026  
**Developer:** Ramu Sriram  
**Application ID:** `io.github.ramusriram.stopwake`

StopWake is a free, open-source Android application licensed under the Apache License 2.0. It is designed from the ground up to operate locally on your device without collecting, storing, or transmitting personal data to the developer or any third-party analytics/advertising servers.

---

## 1. Data Stored Locally on Your Device
All user configurations—including saved transit stops, recurring commute schedules, trip history, custom alarm preferences, and optional emergency contact phone numbers—are stored **exclusively on your device** using Android Jetpack DataStore.
- StopWake has **no user accounts**, **no cloud databases**, **no analytics SDKs**, and **no advertising trackers**.

## 2. Location Data (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` & optional `ACCESS_BACKGROUND_LOCATION`)
- StopWake uses your device's native GPS and network location providers inside a foreground service (`LocationService`) solely to calculate the real-time distance and estimated arrival time between your current position and your selected transit stop.
- If you turn on weekday commute schedules, StopWake may ask for "Allow all the time" location (`ACCESS_BACKGROUND_LOCATION`). It is used only so a scheduled commute can start tracking while the app is closed, and the location is processed on your phone in the same way. If you don't grant it, schedules show a one-tap "Start Tracking" notification instead.
- Your live location coordinates are processed in memory on your phone and are never uploaded to any server owned by the developer.
- When you search for a stop by name or tap the interactive map, StopWake queries the public OpenStreetMap Nominatim API (`nominatim.openstreetmap.org`) and fetches map tiles from OpenStreetMap (`tile.openstreetmap.org`) under their respective public usage policies.

## 3. Optional Emergency Safety Net (`SEND_SMS` / SMS Composer)
- If you explicitly configure an Emergency Contact in the app and either overshoot your stop or fail to acknowledge the arrival alarm within your configured safety timeout:
  - On the **FOSS (GitHub / F-Droid)** edition, if you have granted the optional `SEND_SMS` permission, StopWake sends an automated text message directly from your device to your chosen emergency contact containing your stop name and a coordinate map link.
  - On the **Google Play** edition (or if `SEND_SMS` is not granted), StopWake posts a high-priority notification with a one-tap **Send SMS** action that opens your default SMS app pre-filled with the alert message.
- StopWake never reads your incoming SMS messages or contacts list in the background.

## 4. Data Sharing & Backup (`.json` Export / Import)
- You can optionally export your saved stops and commute schedules as a `.json` file or plain text to back up your data or share routes with friends. This action is 100% user-initiated.

## 5. Contact & Source Code
You can inspect the complete source code or report issues at:  
https://github.com/RamuSriram/StopWake
