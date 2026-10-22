package io.github.ramusriram.stopwake

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.util.LruCache
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ramusriram.stopwake.viewmodel.GeocodingResult
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Location calculation, FOSS platform LocationManager helpers, permission checking,
 * battery-optimization verification, coordinate parsing, and rate-limited Nominatim geocoding.
 */
object LocationUtils {

    private const val NOMINATIM_USER_AGENT = "StopWake/1.0 (https://github.com/RamuSriram/StopWake; Android)"
    private const val NOMINATIM_MIN_INTERVAL_MS = 1050L

    private val nominatimMutex = Mutex()
    @Volatile
    private var lastNominatimRequestMillis: Long = 0L

    private val searchCache = LruCache<String, List<GeocodingResult>>(50)
    private val reverseGeocodeCache = LruCache<String, String>(80)

    /**
     * Check if fine or coarse location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Check if foreground service location permission is granted (Android 14+ / API 34+).
     */
    fun hasForegroundLocationServicePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if background ("Allow all the time") location permission is granted (Android 10+ / API 29+).
     * Required on Android 14/15 to start a location foreground service from a background schedule alarm
     * without user interaction.
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (!hasLocationPermission(context)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if notification permission is granted (Android 13+ / API 33+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Check if SMS sending permission is granted (or not required on Google Play flavor where smsto: intent is used).
     */
    fun hasSmsPermission(context: Context): Boolean {
        if (BuildConfig.IS_PLAY_STORE_BUILD) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if the app is exempted from OEM/Doze battery restrictions so background tracking isn't killed.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Build the array of runtime permissions to request before starting tracking.
     * Note: SEND_SMS is requested separately when the user explicitly opts into unattended background Auto-SMS,
     * so Android 13+ Restricted Settings on sideloaded APKs never block starting GPS tracking.
     */
    fun getRequiredPermissions(includeSms: Boolean = false): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            list.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (includeSms && !BuildConfig.IS_PLAY_STORE_BUILD) {
            list.add(Manifest.permission.SEND_SMS)
        }
        return list.toTypedArray()
    }

    const val MAX_EMERGENCY_CONTACTS = 5

    data class EmergencyContactEntry(
        val number: String,
        val name: String = ""
    ) {
        val displayNumber: String
            get() = formatPhoneNumberForDisplay(number)

        val displayLabel: String
            get() = if (name.isNotBlank()) "$name ($displayNumber)" else displayNumber
    }

    data class PhoneValidationResult(
        val isValid: Boolean,
        val normalizedNumber: String = "",
        val formattedNumber: String = "",
        val errorMessage: String? = null
    )

    /**
     * Normalizes a phone number string for SMS transmission (retains optional leading '+' and digits only).
     */
    fun normalizePhoneNumberForSms(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""
        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Formats a phone number for clean human-readable display (e.g. "+91 98765 43210" or "98765 43210").
     */
    fun formatPhoneNumberForDisplay(raw: String): String {
        val normalized = normalizePhoneNumberForSms(raw)
        if (normalized.isEmpty()) return raw.trim()
        val hasPlus = normalized.startsWith("+")
        val digits = normalized.removePrefix("+")
        return when {
            hasPlus && digits.length == 12 && digits.startsWith("91") ->
                "+91 ${digits.substring(2, 7)} ${digits.substring(7)}"
            hasPlus && digits.length == 11 && digits.startsWith("1") ->
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            hasPlus && digits.length in 10..15 -> {
                val ccLen = (digits.length - 10).coerceIn(1, 3)
                val cc = digits.substring(0, ccLen)
                val local = digits.substring(ccLen)
                if (local.length == 10) {
                    "+$cc ${local.substring(0, 5)} ${local.substring(5)}"
                } else {
                    "+$cc $local"
                }
            }
            !hasPlus && digits.length == 10 ->
                "${digits.substring(0, 5)} ${digits.substring(5)}"
            !hasPlus && digits.length == 11 && digits.startsWith("0") ->
                "0${digits.substring(1, 6)} ${digits.substring(6)}"
            else -> normalized
        }
    }

    /**
     * Validates a user-entered or picked phone number according to ITU-T E.164 / national mobile rules
     * (7 to 15 digits, optional '+' prefix, spaces/dashes/parentheses permitted).
     */
    fun validatePhoneNumber(raw: String): PhoneValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Please enter a phone number or pick a contact."
            )
        }
        // Reject letters or unexpected symbols (allow digits, +, spaces, hyphens, parentheses, dots)
        val allowedFormattingRegex = Regex("^[+0-9\\s\\-().]+$")
        if (!allowedFormattingRegex.matches(trimmed)) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Phone number can only contain digits, '+', spaces, or dashes."
            )
        }
        // '+' if present may only appear at index 0
        if (trimmed.indexOf('+', startIndex = 1) != -1) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Country code '+' must be at the very beginning (e.g. +91 98765 43210)."
            )
        }
        val normalized = normalizePhoneNumberForSms(trimmed)
        val digitsCount = normalized.count { it.isDigit() }
        if (digitsCount < 7) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Number is too short ($digitsCount digits). Enter a 7–15 digit phone number."
            )
        }
        if (digitsCount > 15) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Number is too long ($digitsCount digits). Max 15 digits (ITU E.164)."
            )
        }
        return PhoneValidationResult(
            isValid = true,
            normalizedNumber = normalized,
            formattedNumber = formatPhoneNumberForDisplay(normalized)
        )
    }

    /**
     * Parses stored emergency contact string(s) and name(s) into a deduplicated list of [EmergencyContactEntry].
     * Backward-compatible with single-contact strings and multi-contact strings separated by ',' or ';'.
     */
    fun parseEmergencyContacts(rawNumbers: String, rawNames: String = ""): List<EmergencyContactEntry> {
        if (rawNumbers.isBlank()) return emptyList()
        val numberTokens = rawNumbers.split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val nameTokens = if (rawNames.contains("|")) {
            rawNames.split('|').map { it.trim() }
        } else if (numberTokens.size == 1) {
            listOf(rawNames.trim())
        } else {
            rawNames.split(',', ';').map { it.trim() }
        }

        val entries = mutableListOf<EmergencyContactEntry>()
        for ((index, rawNum) in numberTokens.withIndex()) {
            val normalized = normalizePhoneNumberForSms(rawNum)
            if (normalized.count { it.isDigit() } < 7) continue
            val tailKey = normalized.filter { it.isDigit() }.takeLast(10)
            if (entries.any { it.number.filter { c -> c.isDigit() }.takeLast(10) == tailKey }) {
                continue
            }
            val contactName = nameTokens.getOrNull(index)?.trim().orEmpty()
            entries.add(EmergencyContactEntry(number = normalized, name = contactName))
            if (entries.size >= MAX_EMERGENCY_CONTACTS) break
        }
        return entries
    }

    /**
     * Serializes a list of [EmergencyContactEntry] into (emergencyContact, emergencyContactName) strings.
     */
    fun serializeEmergencyContacts(entries: List<EmergencyContactEntry>): Pair<String, String> {
        val capped = entries.take(MAX_EMERGENCY_CONTACTS)
        if (capped.isEmpty()) return "" to ""
        val numbersStr = capped.joinToString(", ") { normalizePhoneNumberForSms(it.number) }
        val namesStr = if (capped.all { it.name.isBlank() }) {
            ""
        } else {
            capped.joinToString("|") { it.name.replace("|", " ").trim() }
        }
        return numbersStr to namesStr
    }

    /**
     * Returns the freshest, most accurate last-known [Location] across all platform providers
     * (GPS, Fused, Network, Passive) without requiring proprietary Google Play Services.
     */
    @SuppressLint("MissingPermission")
    fun getBestLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            } ?: continue

            if (bestLocation == null || isBetterLocation(loc, bestLocation)) {
                bestLocation = loc
            }
        }
        return bestLocation
    }

    /**
     * Requests a single fresh location fix from platform [LocationManager] when no cached location is available.
     */
    @SuppressLint("MissingPermission")
    fun requestSingleFreshLocation(context: Context, onLocation: (Location) -> Unit) {
        if (!hasLocationPermission(context)) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        val candidateProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        val provider = candidateProviders.firstOrNull() ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(context)
                ) { loc ->
                    if (loc != null) onLocation(loc)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            lm.removeUpdates(this)
                        } catch (_: Exception) {}
                        onLocation(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (_: Exception) {}
    }

    /**
     * Evaluates whether [candidate] is significantly fresher or more accurate than [currentBest].
     */
    fun isBetterLocation(candidate: Location, currentBest: Location?): Boolean {
        if (currentBest == null) return true
        val timeDelta = candidate.time - currentBest.time
        val isSignificantlyNewer = timeDelta > 15_000L
        val isSignificantlyOlder = timeDelta < -15_000L
        val isNewer = timeDelta > 0L

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val accuracyDelta = (candidate.accuracy - currentBest.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 60

        return when {
            isMoreAccurate -> true
            isNewer && !isLessAccurate -> true
            isNewer && !isSignificantlyLessAccurate -> true
            else -> false
        }
    }

    /**
     * Compute distance in meters between two Lat/Lng points.
     */
    fun calculateDistanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }

    /**
     * Compute initial bearing in degrees [0, 360) from start to destination.
     */
    fun calculateBearingDegrees(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val results = FloatArray(2)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return (results[1] + 360f) % 360f
    }

    /**
     * Convert bearing in degrees to an 8-point compass direction string.
     */
    fun bearingToCompass(bearing: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return directions[index]
    }

    /**
     * Format distance in meters into a clean human-readable string.
     */
    fun formatDistance(meters: Float): String {
        return when {
            meters < 1000f -> "${meters.roundToInt()} m"
            meters < 10000f -> "%.2f km".format(Locale.US, meters / 1000f)
            else -> "%.1f km".format(Locale.US, meters / 1000f)
        }
    }

    /**
     * Estimate ETA in minutes based on distance and current speed (fallback to ~30 km/h commuter transit).
     */
    fun estimateEtaMinutes(distanceMeters: Float, speedMetersPerSecond: Float): Int {
        if (distanceMeters <= 0f) return 0
        val effectiveSpeed = if (speedMetersPerSecond >= 1.2f) {
            speedMetersPerSecond
        } else {
            8.33f // ~30 km/h typical bus/metro average
        }
        val seconds = distanceMeters / effectiveSpeed
        return (seconds / 60f).roundToInt().coerceAtLeast(1)
    }

    /**
     * Parse flexible coordinate strings such as:
     * - "17.5947, 78.4856"
     * - "17.5947 78.4856"
     * - "geo:17.5947,78.4856"
     * - "...?q=17.5947,78.4856"
     */
    fun parseCoordinates(input: String): Pair<Double, Double>? {
        val cleaned = input.trim()
            .removePrefix("geo:")
            .substringAfter("q=", missingDelimiterValue = input.trim().removePrefix("geo:"))
            .substringBefore("&")
            .trim()
            .removeSurrounding("(", ")")

        val parts = if (cleaned.contains(",")) {
            cleaned.split(",")
        } else {
            cleaned.split(Regex("\\s+"))
        }

        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lng = parts[1].trim().toDoubleOrNull() ?: return null
        if (!isValidCoordinate(lat, lng)) return null
        return Pair(lat, lng)
    }

    fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)
    }

    /**
     * Enforces OpenStreetMap Nominatim's strict max 1 request/second rate-limit policy.
     */
    private suspend fun awaitNominatimRateLimitSlot() {
        nominatimMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastNominatimRequestMillis
            if (elapsed in 0 until NOMINATIM_MIN_INTERVAL_MS) {
                delay(NOMINATIM_MIN_INTERVAL_MS - elapsed)
            }
            lastNominatimRequestMillis = System.currentTimeMillis()
        }
    }

    /**
     * Search for places matching [query] using:
     * 1. In-memory LRU cache
     * 2. Direct coordinate parsing if query is Lat,Lng
     * 3. Local preset destinations (offline-ready)
     * 4. Android Geocoder (if present)
     * 5. Rate-limited OpenStreetMap Nominatim API fallback
     */
    suspend fun searchPlaces(context: Context, query: String): List<GeocodingResult> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext emptyList()

            val cacheKey = trimmed.lowercase(Locale.US)
            searchCache.get(cacheKey)?.let { cached ->
                if (cached.isNotEmpty()) return@withContext cached
            }

            val results = mutableListOf<GeocodingResult>()

            // 1. Direct coordinate match
            parseCoordinates(trimmed)?.let { (lat, lng) ->
                results.add(
                    GeocodingResult(
                        displayName = "Coordinates (${"%.5f".format(Locale.US, lat)}, ${"%.5f".format(Locale.US, lng)})",
                        lat = lat,
                        lon = lng
                    )
                )
            }

            // 2. Try Android platform Geocoder
            if (Geocoder.isPresent()) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses: List<Address>? = geocoder.getFromLocationName(trimmed, 5)
                    addresses?.forEach { addr ->
                        if (addr.hasLatitude() && addr.hasLongitude()) {
                            val label = buildAddressLabel(addr, trimmed)
                            if (results.none { kotlin.math.abs(it.lat - addr.latitude) < 0.0005 && kotlin.math.abs(it.lon - addr.longitude) < 0.0005 }) {
                                results.add(
                                    GeocodingResult(
                                        displayName = label,
                                        lat = addr.latitude,
                                        lon = addr.longitude
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to Nominatim below
                }
            }

            // 3. Query OpenStreetMap Nominatim HTTP API (respecting 1 req/s policy) if we need more results
            if (results.size < 4) {
                try {
                    awaitNominatimRateLimitSlot()
                    val encoded = URLEncoder.encode(trimmed, "UTF-8")
                    val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("User-Agent", NOMINATIM_USER_AGENT)
                        setRequestProperty("Accept-Language", "en")
                    }
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = JsonParser.parseString(responseText).asJsonArray
                        for (element in jsonArray) {
                            val obj = element.asJsonObject
                            val displayName = obj.get("display_name")?.asString ?: continue
                            val lat = obj.get("lat")?.asString?.toDoubleOrNull() ?: continue
                            val lon = obj.get("lon")?.asString?.toDoubleOrNull() ?: continue
                            if (results.none { kotlin.math.abs(it.lat - lat) < 0.0005 && kotlin.math.abs(it.lon - lon) < 0.0005 }) {
                                results.add(GeocodingResult(displayName = displayName, lat = lat, lon = lon))
                            }
                        }
                    }
                    connection.disconnect()
                } catch (_: Exception) {
                    // Offline or network unavailable; coordinates still returned
                }
            }

            val finalResults = results.take(8)
            if (finalResults.isNotEmpty()) {
                searchCache.put(cacheKey, finalResults)
            }
            finalResults
        }

    /**
     * Reverse-geocode coordinates into a friendly location name when tapping on the map.
     */
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) {
            val cacheKey = "${"%.4f".format(Locale.US, lat)},${"%.4f".format(Locale.US, lng)}"
            reverseGeocodeCache.get(cacheKey)?.let { return@withContext it }

            // Try Android Geocoder
            if (Geocoder.isPresent()) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(lat, lng, 1)
                    val addr = list?.firstOrNull()
                    if (addr != null) {
                        val parts = listOfNotNull(
                            addr.featureName?.takeIf { it != addr.subAdminArea && it != addr.locality },
                            addr.subLocality,
                            addr.locality ?: addr.subAdminArea
                        ).distinct()
                        if (parts.isNotEmpty()) {
                            val resolved = parts.joinToString(", ")
                            reverseGeocodeCache.put(cacheKey, resolved)
                            return@withContext resolved
                        }
                    }
                } catch (_: Exception) {}
            }

            // Try Nominatim reverse geocoding (rate-limited to 1 req/s)
            try {
                awaitNominatimRateLimitSlot()
                val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=16")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", NOMINATIM_USER_AGENT)
                    setRequestProperty("Accept-Language", "en")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JsonParser.parseString(body).asJsonObject
                    val name = obj.get("name")?.asString?.takeIf { it.isNotBlank() }
                    val displayName = obj.get("display_name")?.asString
                    conn.disconnect()
                    if (!name.isNullOrBlank()) {
                        reverseGeocodeCache.put(cacheKey, name)
                        return@withContext name
                    }
                    if (!displayName.isNullOrBlank()) {
                        val shortDisplay = displayName.split(",").take(2).joinToString(",").trim()
                        reverseGeocodeCache.put(cacheKey, shortDisplay)
                        return@withContext shortDisplay
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}

            "Pinned Location (${"%.4f".format(Locale.US, lat)}, ${"%.4f".format(Locale.US, lng)})"
        }

    private fun buildAddressLabel(addr: Address, fallback: String): String {
        val fullLine = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }
            .joinToString(", ")
        if (fullLine.isNotBlank()) return fullLine
        val parts = listOfNotNull(addr.featureName, addr.subLocality, addr.locality, addr.adminArea)
            .distinct()
        return if (parts.isNotEmpty()) parts.joinToString(", ") else fallback
    }
}
