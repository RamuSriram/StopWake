package io.github.ramusriram.stopwake.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ramusriram.stopwake.BuildConfig
import io.github.ramusriram.stopwake.HapticUtils
import io.github.ramusriram.stopwake.LocationUtils
import io.github.ramusriram.stopwake.data.CommuteSchedule
import io.github.ramusriram.stopwake.data.SavedDestination
import io.github.ramusriram.stopwake.ui.components.MapFocusTarget
import io.github.ramusriram.stopwake.ui.components.OsmMapView
import io.github.ramusriram.stopwake.ui.components.ResponsiveOutlinedTextField
import io.github.ramusriram.stopwake.ui.theme.StopWakeBlue
import io.github.ramusriram.stopwake.ui.theme.StopWakeIndigo
import io.github.ramusriram.stopwake.ui.theme.DangerCrimson
import io.github.ramusriram.stopwake.ui.theme.SafeEmerald
import io.github.ramusriram.stopwake.ui.theme.WarningAmber
import io.github.ramusriram.stopwake.viewmodel.AlertSoundOption
import io.github.ramusriram.stopwake.viewmodel.AlertType
import io.github.ramusriram.stopwake.viewmodel.StopWakeViewModel
import io.github.ramusriram.stopwake.viewmodel.GeocodingResult
import io.github.ramusriram.stopwake.viewmodel.TrackingState
import io.github.ramusriram.stopwake.viewmodel.TrackingUiState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main Track screen organized around a clear, zero-overlap 3-step commuter journey:
 * - Live Status, Arrival Confirmation & Start/Stop Tracking
 * - Step 1: Where is your stop? (Search, Quick Stops, Reminder Note, Interactive Map, Save/Unsave Toggle, Offline Cache)
 * - Step 2: How & when should we alert you? (Alert Distance, Advance Lead-Time, 4 Alert Modes in 2x2 Grid, Custom Sounds, Smart Snooze)
 * - Step 3: Automation, Battery Optimization & Safety Net (Daily Schedule, Adaptive GPS, Emergency Contact & Unacknowledged Alert Window)
 */
@Composable
fun TrackingDashboardScreen(
    uiState: TrackingUiState,
    viewModel: StopWakeViewModel,
    onRequestPermissionsAndStart: () -> Unit,
    onRequestPermissionsOnly: () -> Unit,
    onRequestLocationPermission: (onGranted: () -> Unit) -> Unit = {},
    onRequestNotificationPermission: (onGranted: () -> Unit) -> Unit = {},
    onRequestSmsPermission: () -> Unit = {},
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onPickEmergencyContact: () -> Unit,
    onPickCustomRingtone: () -> Unit,
    onOpenFullMap: () -> Unit,
    onOpenSchedules: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()

    val savedDestinations by viewModel.savedDestinations.collectAsState()
    val schedules by viewModel.commuteSchedules.collectAsState()

    var mapFocusTrigger by remember { mutableIntStateOf(0) }
    var mapFocusTarget by remember { mutableStateOf(MapFocusTarget.DESTINATION) }
    var useDarkMapTiles by remember(isDarkTheme) { mutableStateOf(isDarkTheme) }
    var showCoordinateEditor by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val isPlaceSaved = uiState.isSavedIn(savedDestinations)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Permission Warning Banner (if needed)
        AnimatedVisibility(
            visible = !uiState.hasLocationPermission || !uiState.hasNotificationPermission
        ) {
            PermissionBanner(
                hasLocation = uiState.hasLocationPermission,
                hasNotification = uiState.hasNotificationPermission,
                onGrantClick = {
                    HapticUtils.confirmAction(context, view)
                    onRequestPermissionsOnly()
                }
            )
        }

        // 2. Active Alert / Arrival Confirmation & Smart Snooze Banner
        AnimatedVisibility(
            visible = uiState.isAlarmPlaying || uiState.trackingState == TrackingState.ARRIVED
        ) {
            ActiveAlertControlBanner(
                destinationName = uiState.destinationName,
                reminderNote = uiState.reminderNote,
                alertModeName = uiState.alertType.label,
                isTrackingArrived = uiState.trackingState == TrackingState.ARRIVED,
                unacknowledgedTimeoutMinutes = uiState.unacknowledgedTimeoutMinutes,
                emergencyContactCount = uiState.emergencyContactsList.size,
                onConfirmArrival = {
                    HapticUtils.confirmAction(context, view)
                    viewModel.confirmSafeArrival()
                },
                onDismissAlert = {
                    HapticUtils.confirmAction(context, view)
                    viewModel.dismissActiveAlarm()
                },
                onSnoozeMinutes = { mins ->
                    HapticUtils.lightTick(context, view)
                    viewModel.snooze(mins)
                },
                onSmartSnooze = {
                    HapticUtils.lightTick(context, view)
                    viewModel.smartSnooze()
                }
            )
        }

        // 3. Top Trip Summary & Primary Start/Stop/Confirm Arrival Card
        TripSummaryHeroCard(
            uiState = uiState,
            onStartTracking = {
                HapticUtils.confirmAction(context, view)
                if (uiState.hasLocationPermission && uiState.hasNotificationPermission) {
                    viewModel.startTracking()
                } else {
                    onRequestPermissionsAndStart()
                }
            },
            onStopTracking = {
                HapticUtils.confirmAction(context, view)
                viewModel.stopTracking()
            },
            onConfirmArrival = {
                HapticUtils.confirmAction(context, view)
                viewModel.confirmSafeArrival()
            },
            onSmartSnooze = {
                HapticUtils.lightTick(context, view)
                viewModel.smartSnooze()
            },
            onShareTrip = {
                HapticUtils.lightTick(context, view)
                val hasLiveGps = uiState.currentLat != 0.0 || uiState.currentLng != 0.0
                val shareLat = if (hasLiveGps) uiState.currentLat else uiState.destinationLat
                val shareLng = if (hasLiveGps) uiState.currentLng else uiState.destinationLng
                val mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", shareLat, shareLng)
                val distInfo = uiState.distanceToDestination?.let {
                    " (${LocationUtils.formatDistance(it)} away)"
                } ?: ""
                val shareText = buildString {
                    append("Heading to ${uiState.destinationName.ifBlank { "my stop" }}$distInfo.\n")
                    append("Live location: $mapsUrl\n\n")
                    append("Tracked with StopWake: https://github.com/RamuSriram/StopWake")
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Live Trip Status (StopWake)")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Live Trip"))
            }
        )

        // 4. STEP 1: Where is your stop? (Search + Quick Stops + Reminder Note + Map + Save/Unsave Toggle + Offline Cache)
        Step1DestinationAndMapCard(
            uiState = uiState,
            savedDestinations = savedDestinations,
            isPlaceSaved = isPlaceSaved,
            useDarkMapTiles = useDarkMapTiles,
            mapFocusTrigger = mapFocusTrigger,
            mapFocusTarget = mapFocusTarget,
            showCoordinateEditor = showCoordinateEditor,
            onToggleDarkMap = {
                HapticUtils.lightTick(context, view)
                useDarkMapTiles = !useDarkMapTiles
            },
            onCenterUserLocation = {
                HapticUtils.lightTick(context, view)
                val centerAction = {
                    viewModel.refreshDeviceLocation { _, _ ->
                        mapFocusTarget = MapFocusTarget.USER_LOCATION
                        mapFocusTrigger++
                    }
                }
                if (uiState.hasLocationPermission) {
                    centerAction()
                } else {
                    onRequestLocationPermission(centerAction)
                }
            },
            onCenterDestination = {
                HapticUtils.lightTick(context, view)
                mapFocusTarget = MapFocusTarget.DESTINATION
                mapFocusTrigger++
            },
            onOpenFullMap = onOpenFullMap,
            onMapTap = { lat, lng ->
                viewModel.selectDestinationFromMapTap(lat, lng)
            },
            onBookmarkCurrentStop = {
                HapticUtils.confirmAction(context, view)
                viewModel.toggleSaveCurrentDestination()
            },
            onCacheRouteOffline = {
                HapticUtils.confirmAction(context, view)
                viewModel.cacheDestinationOffline()
            },
            onToggleCoordinateEditor = {
                HapticUtils.lightTick(context, view)
                showCoordinateEditor = !showCoordinateEditor
            },
            onSelectSaved = { dest ->
                HapticUtils.lightTick(context, view)
                viewModel.loadSavedDestination(dest)
                mapFocusTarget = MapFocusTarget.DESTINATION
                mapFocusTrigger++
            },
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            onClearSearch = {
                HapticUtils.lightTick(context, view)
                viewModel.clearSearch()
            },
            onSelectSearchResult = { res ->
                HapticUtils.pinDrop(context, view)
                viewModel.selectSearchResult(res)
                mapFocusTarget = MapFocusTarget.DESTINATION
                mapFocusTrigger++
            },
            onNameChange = { viewModel.updateDestinationName(it) },
            onCoordinateTextChange = { viewModel.updateCoordinateInputText(it) },
            onReminderNoteChange = { viewModel.updateReminderNote(it) }
        )

        // 5. STEP 2: How & when should we alert you? (Distance + Advance Timing + 4 Modes + Custom Sounds + Test/Dismiss)
        Step2AlertSettingsCard(
            uiState = uiState,
            onDistanceChange = { newDist ->
                if (newDist != uiState.alertDistanceMeters) {
                    HapticUtils.lightTick(context, view)
                    viewModel.setAlertDistance(newDist)
                }
            },
            onLeadTimeChange = { mins ->
                HapticUtils.lightTick(context, view)
                viewModel.setAlertLeadTimeMinutes(mins)
            },
            onSelectAlertType = { type ->
                HapticUtils.lightTick(context, view)
                viewModel.setAlertType(type)
            },
            onSelectSoundOption = { soundOption ->
                HapticUtils.lightTick(context, view)
                viewModel.setAlertSoundOption(soundOption)
                if (soundOption == AlertSoundOption.CUSTOM_SOUND && uiState.customRingtoneUri.isBlank()) {
                    onPickCustomRingtone()
                }
            },
            onPickCustomRingtone = {
                HapticUtils.lightTick(context, view)
                onPickCustomRingtone()
            },
            onTestAlert = {
                HapticUtils.confirmAction(context, view)
                if (uiState.hasNotificationPermission) {
                    viewModel.triggerTestAlert()
                } else {
                    onRequestNotificationPermission {
                        viewModel.triggerTestAlert()
                    }
                }
            },
            onDismissAlert = {
                HapticUtils.confirmAction(context, view)
                viewModel.dismissActiveAlarm()
            }
        )

        // 6. STEP 3: Daily Commute Automation, Battery-Optimized GPS & Safety Net
        Step3AutomationAndSafetyCard(
            uiState = uiState,
            schedules = schedules,
            onOpenSchedules = onOpenSchedules,
            onToggleBatteryOptimizedGps = { enabled ->
                HapticUtils.lightTick(context, view)
                viewModel.setHighAccuracyMode(enabled)
            },
            onRequestIgnoreBatteryOptimizations = {
                HapticUtils.confirmAction(context, view)
                onRequestIgnoreBatteryOptimizations()
            },
            onAddManualContact = { rawNumber ->
                HapticUtils.confirmAction(context, view)
                viewModel.addEmergencyContact(rawNumber)
            },
            onRemoveContact = { numberToRemove ->
                HapticUtils.lightTick(context, view)
                viewModel.removeEmergencyContact(numberToRemove)
            },
            onPickContact = {
                HapticUtils.lightTick(context, view)
                onPickEmergencyContact()
            },
            onRequestSmsPermission = {
                HapticUtils.confirmAction(context, view)
                onRequestSmsPermission()
            },
            onClearAllContacts = {
                HapticUtils.lightTick(context, view)
                viewModel.clearEmergencyContacts()
            },
            onSelectTimeoutMinutes = { mins ->
                HapticUtils.lightTick(context, view)
                viewModel.setUnacknowledgedTimeoutMinutes(mins)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionBanner(
    hasLocation: Boolean,
    hasNotification: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = WarningAmber.copy(alpha = 0.14f)
        ),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions Needed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val missing = buildList {
                    if (!hasLocation) add("Location")
                    if (!hasNotification) add("Notifications")
                }.joinToString(" & ")
                Text(
                    text = "Allow $missing so StopWake can track your trip and pop up arrival alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningAmber,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Allow", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActiveAlertControlBanner(
    destinationName: String,
    reminderNote: String,
    alertModeName: String,
    isTrackingArrived: Boolean,
    unacknowledgedTimeoutMinutes: Int,
    emergencyContactCount: Int,
    onConfirmArrival: () -> Unit,
    onDismissAlert: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onSmartSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alertPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = DangerCrimson.copy(alpha = 0.14f)
        ),
        border = BorderStroke(1.5.dp, DangerCrimson),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = DangerCrimson,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTrackingArrived) "Approaching $destinationName!"
                        else "Alert Active ($alertModeName)",
                        style = MaterialTheme.typography.titleMedium,
                        color = DangerCrimson,
                        fontWeight = FontWeight.ExtraBold
                    )
                    val contactWord = if (emergencyContactCount > 1) "contacts" else "contact"
                    Text(
                        text = if (isTrackingArrived && emergencyContactCount > 0 && unacknowledgedTimeoutMinutes > 0) {
                            "Confirm arrival within ${unacknowledgedTimeoutMinutes}m or StopWake will notify your emergency $contactWord."
                        } else {
                            "Confirm your safe arrival or silence the alert below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (reminderNote.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: $reminderNote",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Primary Arrival Confirmation & Dismiss Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isTrackingArrived) {
                    Button(
                        onClick = onConfirmArrival,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafeEmerald,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm Arrival", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }

                Button(
                    onClick = onDismissAlert,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerCrimson,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Silence Alert", fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }

            // Smart Snooze Options when Tracking Arrived
            if (isTrackingArrived) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Snooze:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(2 to "2m", 5 to "5m", 10 to "10m").forEach { (mins, label) ->
                        Surface(
                            onClick = { onSnoozeMinutes(mins) },
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = onSmartSnooze,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text(
                            text = "Smart",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSummaryHeroCard(
    uiState: TrackingUiState,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onConfirmArrival: () -> Unit,
    onSmartSnooze: () -> Unit,
    onShareTrip: () -> Unit
) {
    val isTracking = uiState.trackingState != TrackingState.IDLE
    val gradientColors = when {
        uiState.isWithinAlertZone -> listOf(Color(0xFF059669), Color(0xFF047857))
        uiState.trackingState == TrackingState.TRACKING -> listOf(StopWakeBlue, StopWakeIndigo)
        uiState.trackingState == TrackingState.SNOOZED -> listOf(Color(0xFFD97706), Color(0xFFB45309))
        else -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Header Row: Status Label on the left + Live Distance Badge & Share Button on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (uiState.trackingState) {
                        TrackingState.IDLE -> "SELECTED DESTINATION"
                        TrackingState.TRACKING -> "TRACKING IN PROGRESS"
                        TrackingState.ARRIVED -> "ARRIVING AT STOP"
                        TrackingState.SNOOZED -> "ALERTS PAUSED (SNOOZED)"
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.distanceToDestination?.let {
                                LocationUtils.formatDistance(it)
                            } ?: "GPS...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (uiState.isWithinAlertZone) "• In Zone" else "• Away",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    onClick = onShareTrip,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Live Trip",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Full-Width Destination Title (Zero truncation — full name is always readable!)
            Text(
                text = uiState.destinationName.ifBlank { "Tap map or search a stop" },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth()
            )

            // Full-Width Reminder Note (Zero truncation — entire note wraps cleanly across full width!)
            if (uiState.reminderNote.isNotBlank()) {
                Surface(
                    color = Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = uiState.reminderNote,
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            uiState.distanceToDestination?.let { dist ->
                val progress = if (dist <= uiState.alertDistanceMeters) {
                    1f
                } else {
                    (1f - ((dist - uiState.alertDistanceMeters) / 5000f)).coerceIn(0.08f, 0.95f)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (uiState.isWithinAlertZone) Color(0xFF34D399) else Color.White,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
            }

            val etaLabel = uiState.distanceToDestination?.let { dist ->
                if (dist <= uiState.alertDistanceMeters) "Now"
                else {
                    val mins = LocationUtils.estimateEtaMinutes(dist, uiState.currentSpeedMps)
                    when {
                        mins <= 1 -> "< 1 min"
                        mins < 60 -> "~$mins min"
                        mins < 600 -> {
                            val hrs = mins / 60
                            val remMins = mins % 60
                            if (remMins == 0) "~${hrs}h" else "~${hrs}h ${remMins}m"
                        }
                        else -> "~${mins / 60} hrs"
                    }
                }
            } ?: "GPS..."

            val triggerLabel = if (uiState.alertLeadTimeMinutes > 0) {
                "${uiState.alertDistanceMeters}m / ${uiState.alertLeadTimeMinutes}m"
            } else {
                "${uiState.alertDistanceMeters} m"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill(
                    label = "Trigger",
                    value = triggerLabel,
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    label = "Mode",
                    value = uiState.alertType.shortLabel,
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    label = "Est. Time",
                    value = etaLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!isTracking) {
                Button(
                    onClick = onStartTracking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeEmerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 13.dp, horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Destination Tracking",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else if (uiState.trackingState == TrackingState.ARRIVED) {
                Button(
                    onClick = onConfirmArrival,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF047857)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm Safe Arrival",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSmartSnooze,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snooze", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onStopTracking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerCrimson,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                        modifier = Modifier.weight(1.15f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Tracking", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1DestinationAndMapCard(
    uiState: TrackingUiState,
    savedDestinations: List<SavedDestination>,
    isPlaceSaved: Boolean,
    useDarkMapTiles: Boolean,
    mapFocusTrigger: Int,
    mapFocusTarget: MapFocusTarget,
    showCoordinateEditor: Boolean,
    onToggleDarkMap: () -> Unit,
    onCenterUserLocation: () -> Unit,
    onCenterDestination: () -> Unit,
    onOpenFullMap: () -> Unit,
    onMapTap: (Double, Double) -> Unit,
    onBookmarkCurrentStop: () -> Unit,
    onCacheRouteOffline: () -> Unit,
    onToggleCoordinateEditor: () -> Unit,
    onSelectSaved: (SavedDestination) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectSearchResult: (GeocodingResult) -> Unit,
    onNameChange: (String) -> Unit,
    onCoordinateTextChange: (String) -> Unit,
    onReminderNoteChange: (String) -> Unit
) {
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    val favoriteDestinations = remember(savedDestinations) {
        savedDestinations.filter { it.isFavorite }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Title + Coordinate Editor Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StepNumberBadge(number = "1")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Where is your stop?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onToggleCoordinateEditor,
                    color = if (showCoordinateEditor) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocationAlt,
                            contentDescription = null,
                            tint = if (showCoordinateEditor) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showCoordinateEditor) "Hide Coords" else "Lat / Lng",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (showCoordinateEditor) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search Bar (ResponsiveOutlinedTextField preserves cursor & selection)
            ResponsiveOutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(text = "Search stop or address")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Search Autocomplete Results (Zero text truncation!)
            AnimatedVisibility(visible = uiState.searchResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        uiState.searchResults.forEachIndexed { index, place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSearchResult(place) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${"%.5f".format(Locale.US, place.lat)}, ${"%.5f".format(Locale.US, place.lon)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (index < uiState.searchResults.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }

            // Quick-Pick Chips: Only Favorite Stops (★)
            if (favoriteDestinations.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    favoriteDestinations.forEach { dest ->
                        val isSelected = uiState.destinationName == dest.name
                        Surface(
                            onClick = { onSelectSaved(dest) },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = dest.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Destination-Specific Reminder Note Field (multi-line wrap so text never hides off the right edge!)
            ResponsiveOutlinedTextField(
                value = uiState.reminderNote,
                onValueChange = onReminderNoteChange,
                placeholder = {
                    Text(text = "Arrival note (optional)")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                },
                trailingIcon = {
                    if (uiState.reminderNote.isNotEmpty()) {
                        IconButton(onClick = { onReminderNoteChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear note")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Expandable Manual Coordinate & Stop Label Editor
            AnimatedVisibility(visible = showCoordinateEditor) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ResponsiveOutlinedTextField(
                        value = uiState.destinationName,
                        onValueChange = onNameChange,
                        label = { Text("Stop Name") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ResponsiveOutlinedTextField(
                        value = uiState.coordinateInputText,
                        onValueChange = onCoordinateTextChange,
                        label = { Text("Latitude, Longitude") },
                        placeholder = { Text("18.5917, 73.7448") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Clean Instruction Row Above Map
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (uiState.isReverseGeocoding) "Finding street name..."
                    else "Tap anywhere on map to move stop pin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Embedded Interactive Map Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                OsmMapView(
                    destinationLat = uiState.destinationLat,
                    destinationLng = uiState.destinationLng,
                    destinationName = uiState.destinationName,
                    alertDistanceMeters = uiState.alertDistanceMeters,
                    currentLat = uiState.currentLat,
                    currentLng = uiState.currentLng,
                    isWithinAlertZone = uiState.isWithinAlertZone,
                    pathPoints = uiState.pathPoints,
                    interactive = true,
                    useDarkTiles = useDarkMapTiles,
                    cameraFocusRequest = mapFocusTrigger,
                    focusTarget = mapFocusTarget,
                    onMapTap = onMapTap,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-Right Floating Map Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FloatingMapActionButton(
                        icon = if (useDarkMapTiles) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Switch Map Theme",
                        onClick = onToggleDarkMap
                    )
                    FloatingMapActionButton(
                        icon = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        onClick = onCenterUserLocation
                    )
                    FloatingMapActionButton(
                        icon = Icons.Default.CenterFocusStrong,
                        contentDescription = "Center on Stop",
                        onClick = onCenterDestination
                    )
                    FloatingMapActionButton(
                        icon = Icons.Default.OpenInFull,
                        contentDescription = "Open Full Map",
                        onClick = onOpenFullMap
                    )
                }

                // Bottom Floating Coordinates & Save/Unsave Toggle Button
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "${"%.4f".format(Locale.US, uiState.destinationLat)}, ${"%.4f".format(Locale.US, uiState.destinationLng)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        onClick = onBookmarkCurrentStop,
                        color = if (isPlaceSaved) SafeEmerald.copy(alpha = 0.95f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlaceSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                tint = if (isPlaceSaved) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlaceSaved) "Saved" else "Save Place",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = if (isPlaceSaved) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Offline Route & Map Tile Caching Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (uiState.isOfflineRouteCached) Icons.Default.CloudDone
                            else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (uiState.isOfflineRouteCached) SafeEmerald
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isCachingOffline) {
                                "Caching offline tiles..."
                            } else if (uiState.isOfflineRouteCached) {
                                "Offline map tiles cached"
                            } else {
                                "Offline cache ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isOfflineRouteCached) SafeEmerald
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = onCacheRouteOffline,
                        color = if (uiState.isOfflineRouteCached) SafeEmerald.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (uiState.isOfflineRouteCached) "Cached ✓" else "Cache",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isOfflineRouteCached) SafeEmerald
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step2AlertSettingsCard(
    uiState: TrackingUiState,
    onDistanceChange: (Int) -> Unit,
    onLeadTimeChange: (Int) -> Unit,
    onSelectAlertType: (AlertType) -> Unit,
    onSelectSoundOption: (AlertSoundOption) -> Unit,
    onPickCustomRingtone: () -> Unit,
    onTestAlert: () -> Unit,
    onDismissAlert: () -> Unit
) {
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    val presetDistances = listOf(100, 250, 500, 1000, 2000)
    // Ultra-concise 3-6 character labels so 4 chips fit any phone screen with zero clipping!
    val presetLeadTimes = listOf(0 to "Off", 2 to "2 min", 5 to "5 min", 10 to "10 min")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section Title + Single-Line Distance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StepNumberBadge(number = "2")
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Alert Distance & Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (uiState.alertLeadTimeMinutes > 0) {
                                "Alerts at ${uiState.alertDistanceMeters}m or ${uiState.alertLeadTimeMinutes} min early"
                            } else {
                                "Alerts ${uiState.alertDistanceMeters}m before your stop"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${uiState.alertDistanceMeters} m",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Distance Slider
            Slider(
                value = uiState.alertDistanceMeters.toFloat().coerceIn(50f, 5000f),
                onValueChange = { value ->
                    val rounded = ((value / 25f).roundToInt() * 25).coerceIn(50, 5000)
                    onDistanceChange(rounded)
                },
                valueRange = 50f..5000f,
                modifier = Modifier.fillMaxWidth()
            )

            // Quick Distance Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetDistances.forEach { preset ->
                    val isSelected = uiState.alertDistanceMeters == preset
                    Surface(
                        onClick = { onDistanceChange(preset) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (preset >= 1000) "${preset / 1000} km" else "$preset m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Adjustable Advance Alert Timing (Lead-Time) Chips ("Off", "2 min", "5 min", "10 min")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Early Time Trigger (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetLeadTimes.forEach { (mins, label) ->
                        val isSelected = uiState.alertLeadTimeMinutes == mins
                        Surface(
                            onClick = { onLeadTimeChange(mins) },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // 4 Alert Mode Tiles in a Clean 2x2 Grid with Short, Full-Width Labels (Zero Clipping!)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertModeTile(
                        title = "Smart",
                        subtitle = "Auto-escalate",
                        icon = Icons.Default.AutoAwesome,
                        selected = uiState.alertType == AlertType.SMART_ADAPTIVE,
                        onClick = { onSelectAlertType(AlertType.SMART_ADAPTIVE) },
                        modifier = Modifier.weight(1f)
                    )
                    AlertModeTile(
                        title = "Alarm",
                        subtitle = "Loud ring",
                        icon = Icons.Default.Alarm,
                        selected = uiState.alertType == AlertType.WITH_VIBRATION_AND_RINGTONE,
                        onClick = { onSelectAlertType(AlertType.WITH_VIBRATION_AND_RINGTONE) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertModeTile(
                        title = "Vibrate",
                        subtitle = "Buzz only",
                        icon = Icons.Default.Vibration,
                        selected = uiState.alertType == AlertType.WITH_VIBRATION,
                        onClick = { onSelectAlertType(AlertType.WITH_VIBRATION) },
                        modifier = Modifier.weight(1f)
                    )
                    AlertModeTile(
                        title = "Silent",
                        subtitle = "Banner only",
                        icon = Icons.Default.Notifications,
                        selected = uiState.alertType == AlertType.NOTIFICATION_ONLY,
                        onClick = { onSelectAlertType(AlertType.NOTIFICATION_ONLY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Custom Alert Sound Selector (visible whenever Smart or Alarm is selected)
            AnimatedVisibility(
                visible = uiState.alertType == AlertType.SMART_ADAPTIVE ||
                    uiState.alertType == AlertType.WITH_VIBRATION_AND_RINGTONE
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Wake-Up Sound",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (uiState.alertSoundOption == AlertSoundOption.CUSTOM_SOUND) {
                            Surface(
                                onClick = onPickCustomRingtone,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Pick Tone",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundOptionChip(
                            label = "Alarm Siren",
                            selected = uiState.alertSoundOption == AlertSoundOption.ALARM_SIREN,
                            onClick = { onSelectSoundOption(AlertSoundOption.ALARM_SIREN) },
                            modifier = Modifier.weight(1f)
                        )
                        SoundOptionChip(
                            label = "Ringtone",
                            selected = uiState.alertSoundOption == AlertSoundOption.PHONE_RINGTONE,
                            onClick = { onSelectSoundOption(AlertSoundOption.PHONE_RINGTONE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundOptionChip(
                            label = "Gentle Chime",
                            selected = uiState.alertSoundOption == AlertSoundOption.GENTLE_CHIME,
                            onClick = { onSelectSoundOption(AlertSoundOption.GENTLE_CHIME) },
                            modifier = Modifier.weight(1f)
                        )
                        SoundOptionChip(
                            label = "Custom Tone",
                            selected = uiState.alertSoundOption == AlertSoundOption.CUSTOM_SOUND,
                            onClick = {
                                if (uiState.alertSoundOption == AlertSoundOption.CUSTOM_SOUND) {
                                    onPickCustomRingtone()
                                } else {
                                    onSelectSoundOption(AlertSoundOption.CUSTOM_SOUND)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Full-width Explanation of Selected Mode + Full-Width Test / Dismiss Button
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = uiState.alertType.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.alertType.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.isAlarmPlaying) {
                        Button(
                            onClick = onDismissAlert,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DangerCrimson,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dismiss Active Alert",
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTestAlert,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test ${uiState.alertType.shortLabel} Alert",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun Step3AutomationAndSafetyCard(
    uiState: TrackingUiState,
    schedules: List<CommuteSchedule>,
    onOpenSchedules: () -> Unit,
    onToggleBatteryOptimizedGps: (Boolean) -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onAddManualContact: (String) -> LocationUtils.PhoneValidationResult,
    onRemoveContact: (String) -> Unit,
    onPickContact: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onClearAllContacts: () -> Unit,
    onSelectTimeoutMinutes: (Int) -> Unit
) {
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    val activeSchedules = schedules.filter { it.isEnabled }
    val contactsList = uiState.emergencyContactsList
    val isContactSaved = contactsList.isNotEmpty()
    val isSmsReady = isContactSaved && uiState.hasSmsPermission
    val canAddMoreContacts = contactsList.size < LocationUtils.MAX_EMERGENCY_CONTACTS
    val timeoutOptions = listOf(
        2 to "2m",
        5 to "5m",
        10 to "10m",
        15 to "15m",
        30 to "30m"
    )
    var showManualPhoneInput by remember { mutableStateOf(false) }
    var manualPhoneText by remember { mutableStateOf("") }
    var phoneValidationError by remember { mutableStateOf<String?>(null) }

    fun submitManualPhone() {
        val res = onAddManualContact(manualPhoneText)
        if (res.isValid) {
            manualPhoneText = ""
            phoneValidationError = null
            showManualPhoneInput = false
        } else {
            phoneValidationError = res.errorMessage
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepNumberBadge(number = "3")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Automation, Battery & Safety Net",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Part A: Daily Commute Automation (Compact single-row card)
            Surface(
                onClick = onOpenSchedules,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EventRepeat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeSchedules.isNotEmpty()) {
                                "${activeSchedules.size} Daily Schedule Active"
                            } else {
                                "Automatic GPS Schedule"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (activeSchedules.isNotEmpty()) {
                                val first = activeSchedules.first()
                                "${first.formattedDaysSummary()} • ${first.formattedStartTime()}–${first.formattedEndTime()}"
                            } else {
                                "Auto-start tracking on commute days"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = onOpenSchedules,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (activeSchedules.isNotEmpty()) "Manage" else "Set Up",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Part B: Battery-Optimized Adaptive GPS + Background Sleep Protection
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatterySaver,
                            contentDescription = null,
                            tint = SafeEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Adaptive Battery GPS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Polls every 10s far away, 1.5s near stop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.highAccuracyMode,
                            onCheckedChange = onToggleBatteryOptimizedGps
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

                    if (uiState.isBatteryOptimizationIgnored) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SafeEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unrestricted background run active (screen-off safe)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Prevent battery saver from pausing GPS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = onRequestIgnoreBatteryOptimizations,
                                color = WarningAmber.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, WarningAmber),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Allow",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WarningAmber,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Part C: Emergency Safety Net (Streamlined Multi-Contact + Safety Window)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = when {
                                isSmsReady -> SafeEmerald
                                isContactSaved -> StopWakeBlue
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isContactSaved) {
                                "Emergency Contacts (${contactsList.size}/${LocationUtils.MAX_EMERGENCY_CONTACTS})"
                            } else {
                                "Emergency Contacts"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isContactSaved) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = {
                                if (!uiState.hasSmsPermission && !BuildConfig.IS_PLAY_STORE_BUILD) {
                                    onRequestSmsPermission()
                                }
                            },
                            color = if (isSmsReady) SafeEmerald.copy(alpha = 0.16f)
                            else StopWakeBlue.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (isSmsReady) "Auto-SMS On" else "1-Tap Ready",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                color = if (isSmsReady) SafeEmerald else StopWakeBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Alerts your trusted contacts with a live Google Maps link if you miss your stop or don't respond to the arrival alarm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Slim, scannable contact rows
                if (contactsList.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        contactsList.forEach { entry ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (entry.name.isNotBlank()) {
                                            Text(
                                                text = entry.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = entry.displayNumber,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (entry.name.isBlank()) FontWeight.Bold else FontWeight.Medium,
                                            color = if (entry.name.isBlank()) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveContact(entry.number) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove ${entry.displayLabel}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (contactsList.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DangerCrimson,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onClearAllContacts() }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Compact Action Row: Pick Contact + Toggle Manual Number Input (Progressive Disclosure)
                if (canAddMoreContacts) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickContact,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            modifier = Modifier.weight(1.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (contactsList.isEmpty()) "Pick Contact" else "Add Contact",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                showManualPhoneInput = !showManualPhoneInput
                                if (!showManualPhoneInput) {
                                    phoneValidationError = null
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (showManualPhoneInput) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    Color.Transparent
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            modifier = Modifier.weight(0.95f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContactPhone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showManualPhoneInput) "Hide Keypad" else "Type Number",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // Expandable Single-Line Manual Phone Input with Integrated Add Button
                    AnimatedVisibility(visible = showManualPhoneInput) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ResponsiveOutlinedTextField(
                                value = manualPhoneText,
                                onValueChange = {
                                    manualPhoneText = it
                                    if (phoneValidationError != null) phoneValidationError = null
                                },
                                placeholder = {
                                    Text(
                                        text = "Phone (e.g. +91 98765 43210)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ContactPhone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (manualPhoneText.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    manualPhoneText = ""
                                                    phoneValidationError = null
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                onClick = { submitManualPhone() },
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Add",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitManualPhone() }
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (phoneValidationError != null) {
                                Text(
                                    text = phoneValidationError!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DangerCrimson,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Include country code (e.g. +91) or 10-digit mobile number.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Compact Single-Line Auto-SMS Upgrade Row (only when contacts exist & SMS not yet granted)
                if (isContactSaved && !uiState.hasSmsPermission && !BuildConfig.IS_PLAY_STORE_BUILD) {
                    Surface(
                        color = StopWakeBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, StopWakeBlue.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "1-Tap SMS active • Want background Auto-SMS?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = onRequestSmsPermission,
                                color = StopWakeBlue,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Enable",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Single-Row Safety Window Selector (2m, 5m, 10m, 15m, 30m)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Safety Window",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${uiState.unacknowledgedTimeoutMinutes} min after alarm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        timeoutOptions.forEach { (mins, label) ->
                            val isSelected = uiState.unacknowledgedTimeoutMinutes == mins
                            Surface(
                                onClick = { onSelectTimeoutMinutes(mins) },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.emergencySmsSent) {
                Surface(
                    color = DangerCrimson.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = DangerCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency SMS alert triggered with your live Google Maps location.",
                            style = MaterialTheme.typography.labelSmall,
                            color = DangerCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNumberBadge(number: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun FloatingMapActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 4.dp,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * 2-column Alert Mode Tile where the icon and short 1-word title sit on the top row,
 * and the subtitle spans the full width of the tile underneath — guaranteeing zero text clipping!
 */
@Composable
private fun AlertModeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        label = "tileBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        label = "tileBorder"
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

