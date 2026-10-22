package io.github.ramusriram.stopwake

import io.github.ramusriram.stopwake.data.SavedDestination
import io.github.ramusriram.stopwake.data.StopWakeBackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseCoordinates_handlesCommaSeparatedAndGeoUrls() {
        val standard = LocationUtils.parseCoordinates("17.5947, 78.4856")
        assertNotNull(standard)
        assertEquals(17.5947, standard!!.first, 0.0001)
        assertEquals(78.4856, standard.second, 0.0001)

        val spaceSeparated = LocationUtils.parseCoordinates("17.4344 78.5013")
        assertNotNull(spaceSeparated)
        assertEquals(17.4344, spaceSeparated!!.first, 0.0001)
        assertEquals(78.5013, spaceSeparated.second, 0.0001)

        val geoUri = LocationUtils.parseCoordinates("geo:17.3780,78.4801")
        assertNotNull(geoUri)
        assertEquals(17.3780, geoUri!!.first, 0.0001)

        val invalid = LocationUtils.parseCoordinates("invalid_coords")
        assertNull(invalid)

        val outOfRange = LocationUtils.parseCoordinates("125.0, 200.0")
        assertNull(outOfRange)
    }

    @Test
    fun formatDistance_formatsMetersAndKilometersCorrectly() {
        assertEquals("250 m", LocationUtils.formatDistance(250.2f))
        assertEquals("1.45 km", LocationUtils.formatDistance(1450f))
        assertEquals("14.2 km", LocationUtils.formatDistance(14230f))
    }

    @Test
    fun bearingToCompass_returnsExpectedOctants() {
        assertEquals("N", LocationUtils.bearingToCompass(0f))
        assertEquals("NE", LocationUtils.bearingToCompass(45f))
        assertEquals("E", LocationUtils.bearingToCompass(90f))
        assertEquals("S", LocationUtils.bearingToCompass(180f))
        assertEquals("W", LocationUtils.bearingToCompass(270f))
    }

    @Test
    fun estimateEtaMinutes_calculatesReasonableMinutes() {
        assertEquals(0, LocationUtils.estimateEtaMinutes(0f, 10f))
        // 3000m at 10 m/s = 300s = 5 min
        assertEquals(5, LocationUtils.estimateEtaMinutes(3000f, 10f))
    }

    @Test
    fun backupPayload_storesSavedDestinationsCleanly() {
        val sampleStop = SavedDestination(
            name = "Central Station",
            address = "Main Transit Hub",
            latitude = 40.7527,
            longitude = -73.9772,
            alertDistanceMeters = 350,
            isFavorite = true
        )
        assertTrue(LocationUtils.isValidCoordinate(sampleStop.latitude, sampleStop.longitude))
        assertFalse(LocationUtils.isValidCoordinate(0.0, 0.0))

        val payload = StopWakeBackupPayload(savedDestinations = listOf(sampleStop))
        assertEquals("StopWake", payload.app)
        assertEquals(1, payload.savedDestinations.size)
        assertEquals("Central Station", payload.savedDestinations.first().name)
    }

    @Test
    fun routePacks_deserializeSavedDestinationsAndSchedulesCleanly() {
        val gson = com.google.gson.Gson()
        val legacyFormatJson = """
            {
              "schemaVersion": 1,
              "appName": "StopWake",
              "destinations": [
                {
                  "id": "hyd_stop_1",
                  "name": "Raidurg Metro Station",
                  "latitude": 17.442246,
                  "longitude": 78.377213,
                  "alertDistanceMeters": 500,
                  "alertTypeValue": 4
                }
              ],
              "schedules": [
                {
                  "id": "hyd_sched_1",
                  "label": "Morning Metro",
                  "destinationName": "Raidurg Metro Station",
                  "destinationLat": 17.442246,
                  "destinationLng": 78.377213,
                  "alertDistanceMeters": 500,
                  "alertTypeValue": 4
                }
              ]
            }
        """.trimIndent()
        val parsed = gson.fromJson(legacyFormatJson, StopWakeBackupPayload::class.java)
        assertEquals(1, parsed.savedDestinations.size)
        assertEquals("Raidurg Metro Station", parsed.savedDestinations.first().name)
        assertEquals(4, parsed.savedDestinations.first().alertType)
        assertEquals(1, parsed.commuteSchedules.size)
    }

    @Test
    fun validatePhoneNumber_acceptsValidE164AndNationalFormatsAndRejectsInvalid() {
        val validIntl = LocationUtils.validatePhoneNumber("+91 98765 43210")
        assertTrue(validIntl.isValid)
        assertEquals("+919876543210", validIntl.normalizedNumber)
        assertEquals("+91 98765 43210", validIntl.formattedNumber)

        val validNational = LocationUtils.validatePhoneNumber("98765-43210")
        assertTrue(validNational.isValid)
        assertEquals("9876543210", validNational.normalizedNumber)
        assertEquals("98765 43210", validNational.formattedNumber)

        val tooShort = LocationUtils.validatePhoneNumber("12345")
        assertFalse(tooShort.isValid)
        assertNotNull(tooShort.errorMessage)

        val hasLetters = LocationUtils.validatePhoneNumber("98765abc10")
        assertFalse(hasLetters.isValid)

        val misplacedPlus = LocationUtils.validatePhoneNumber("91+9876543210")
        assertFalse(misplacedPlus.isValid)
    }

    @Test
    fun emergencyContacts_parseAndSerializeMultipleContactsAndDeduplicate() {
        val entries = listOf(
            LocationUtils.EmergencyContactEntry(number = "+919876543210", name = "Mom"),
            LocationUtils.EmergencyContactEntry(number = "+919123456789", name = "Dad")
        )
        val (nums, names) = LocationUtils.serializeEmergencyContacts(entries)
        val roundTrip = LocationUtils.parseEmergencyContacts(nums, names)
        assertEquals(2, roundTrip.size)
        assertEquals("Mom", roundTrip[0].name)
        assertEquals("+91 98765 43210", roundTrip[0].displayNumber)
        assertEquals("Dad", roundTrip[1].name)
        assertEquals("+91 91234 56789", roundTrip[1].displayNumber)

        // Duplicate last-10-digits should deduplicate cleanly
        val deduped = LocationUtils.parseEmergencyContacts("+919876543210, 9876543210", "Mom|Mom Dup")
        assertEquals(1, deduped.size)
    }
}