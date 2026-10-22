package io.github.ramusriram.stopwake.ui.screens

import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ramusriram.stopwake.HapticUtils
import io.github.ramusriram.stopwake.data.CommuteSchedule
import io.github.ramusriram.stopwake.data.SavedDestination
import io.github.ramusriram.stopwake.ui.components.ResponsiveOutlinedTextField
import io.github.ramusriram.stopwake.ui.theme.StopWakeBlue
import io.github.ramusriram.stopwake.ui.theme.StopWakeIndigo
import io.github.ramusriram.stopwake.ui.theme.SafeEmerald
import io.github.ramusriram.stopwake.viewmodel.AlertType
import io.github.ramusriram.stopwake.viewmodel.StopWakeViewModel
import io.github.ramusriram.stopwake.viewmodel.TrackingUiState
import java.util.UUID

/**
 * Screen for managing automated recurring commute schedules (auto-start and auto-stop tracking
 * on specific days and times, such as Monday–Saturday college commutes).
 */
@Composable
fun SchedulesScreen(
    schedules: List<CommuteSchedule>,
    savedDestinations: List<SavedDestination>,
    uiState: TrackingUiState,
    viewModel: StopWakeViewModel,
    onRunScheduleAndNavigateToTrack: (CommuteSchedule) -> Unit,
    onRequestPermissionsForSchedule: () -> Unit = {},
    onRequestBackgroundLocationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    var editingSchedule by remember { mutableStateOf<CommuteSchedule?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Explanation Banner
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(StopWakeBlue, StopWakeIndigo)))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Commute Schedules",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Set your commute days and hours so StopWake starts and stops tracking automatically.",
                                color = Color.White.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(
                        onClick = {
                            HapticUtils.confirmAction(context, view)
                            editingSchedule = CommuteSchedule(
                                label = "Daily Commute",
                                destinationName = uiState.destinationName,
                                destinationLat = uiState.destinationLat,
                                destinationLng = uiState.destinationLng,
                                alertDistanceMeters = uiState.alertDistanceMeters,
                                alertTypeValue = uiState.alertType.value,
                                reminderNote = uiState.reminderNote,
                                startHour = 8,
                                startMinute = 0,
                                endHour = 9,
                                endMinute = 30,
                                daysOfWeek = setOf(1, 2, 3, 4, 5, 6), // Mon-Sat default
                                isEnabled = true
                            )
                            showEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = StopWakeBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Auto-Tracking Schedule",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 1b. Android 10+ Background Auto-Start Permission Card (only shown when an enabled schedule needs 'Allow all the time')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            schedules.any { it.isEnabled } &&
            !uiState.hasBackgroundLocationPermission
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, StopWakeBlue.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable Zero-Tap Background Start",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "To let Android start GPS tracking automatically while your phone is locked, set Location permission to 'Allow all the time'. Otherwise, StopWake will send a 1-tap start reminder at your scheduled time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                HapticUtils.confirmAction(context, view)
                                onRequestBackgroundLocationPermission()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Allow All the Time",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Empty State or List of Schedules
        if (schedules.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = cardBorder,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Schedules Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create a schedule for your daily commute (for example, Monday to Saturday, 8:00 AM to 9:30 AM) so tracking starts automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(schedules, key = { it.id }) { schedule ->
                ScheduleItemCard(
                    schedule = schedule,
                    onToggleEnabled = {
                        HapticUtils.confirmAction(context, view)
                        val turningOn = !schedule.isEnabled
                        viewModel.toggleScheduleEnabled(schedule.id)
                        if (turningOn) {
                            if (!uiState.hasLocationPermission || !uiState.hasNotificationPermission) {
                                onRequestPermissionsForSchedule()
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.hasBackgroundLocationPermission) {
                                onRequestBackgroundLocationPermission()
                            }
                        }
                    },
                    onToggleDay = { day ->
                        HapticUtils.lightTick(context, view)
                        val updatedDays = if (day in schedule.daysOfWeek) {
                            schedule.daysOfWeek - day
                        } else {
                            schedule.daysOfWeek + day
                        }
                        viewModel.saveSchedule(schedule.copy(daysOfWeek = updatedDays))
                    },
                    onEdit = {
                        HapticUtils.lightTick(context, view)
                        editingSchedule = schedule
                        showEditDialog = true
                    },
                    onDelete = {
                        HapticUtils.lightTick(context, view)
                        viewModel.deleteSchedule(schedule.id)
                    },
                    onRunNow = {
                        HapticUtils.confirmAction(context, view)
                        onRunScheduleAndNavigateToTrack(schedule)
                    }
                )
            }
        }
    }

    if (showEditDialog && editingSchedule != null) {
        ScheduleEditorDialog(
            initialSchedule = editingSchedule!!,
            currentStopName = uiState.destinationName,
            currentStopLat = uiState.destinationLat,
            currentStopLng = uiState.destinationLng,
            savedDestinations = savedDestinations,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                HapticUtils.confirmAction(context, view)
                viewModel.saveSchedule(updated)
                showEditDialog = false
                if (updated.isEnabled) {
                    if (!uiState.hasLocationPermission || !uiState.hasNotificationPermission) {
                        onRequestPermissionsForSchedule()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !uiState.hasBackgroundLocationPermission) {
                        onRequestBackgroundLocationPermission()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleItemCard(
    schedule: CommuteSchedule,
    onToggleEnabled: () -> Unit,
    onToggleDay: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRunNow: () -> Unit
) {
    val borderColor = if (schedule.isEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (schedule.isEnabled) 1.5.dp else 1.dp, borderColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Full-width Title & Destination on the left + Switch on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = schedule.destinationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            // Row 2: Clean Time Window & Wrapped Metadata Pills Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Time Window + Status Pill
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
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${schedule.formattedStartTime()}  →  ${schedule.formattedEndTime()}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = if (schedule.isEnabled) SafeEmerald.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                0.8.dp,
                                if (schedule.isEnabled) SafeEmerald.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = if (schedule.isEnabled) "Active" else "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (schedule.isEnabled) SafeEmerald
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Alert Distance & Alert Mode Pills Row
                    val modeName = AlertType.entries.find { it.value == schedule.alertTypeValue }?.shortLabel
                        ?: "Vibration"

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${schedule.alertDistanceMeters}m before stop",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = modeName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (schedule.reminderNote.isNotBlank()) {
                        Text(
                            text = "Note: ${schedule.reminderNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Row 3: Interactive 7-Day Selector (Mon..Sun)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = schedule.formattedDaysSummary(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..7).forEach { day ->
                        val isSelected = day in schedule.daysOfWeek
                        val isSunday = day == 7
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onToggleDay(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = CommuteSchedule.dayShortName(day).take(2),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else if (isSunday) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Row 4: Edit / Delete / Start Now Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }

                Button(
                    onClick = onRunNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeEmerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Now", fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Schedule",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditorDialog(
    initialSchedule: CommuteSchedule,
    currentStopName: String,
    currentStopLat: Double,
    currentStopLng: Double,
    savedDestinations: List<SavedDestination>,
    onDismiss: () -> Unit,
    onSave: (CommuteSchedule) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var label by remember { mutableStateOf(initialSchedule.label) }
    var destName by remember { mutableStateOf(initialSchedule.destinationName) }
    var destLat by remember { mutableStateOf(initialSchedule.destinationLat) }
    var destLng by remember { mutableStateOf(initialSchedule.destinationLng) }
    var reminderNote by remember { mutableStateOf(initialSchedule.reminderNote) }
    var alertRadius by remember { mutableIntStateOf(initialSchedule.alertDistanceMeters) }
    var alertTypeVal by remember { mutableIntStateOf(initialSchedule.alertTypeValue) }
    var startHour by remember { mutableIntStateOf(initialSchedule.startHour) }
    var startMinute by remember { mutableIntStateOf(initialSchedule.startMinute) }
    var endHour by remember { mutableIntStateOf(initialSchedule.endHour) }
    var endMinute by remember { mutableIntStateOf(initialSchedule.endMinute) }
    var selectedDays by remember { mutableStateOf(initialSchedule.daysOfWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure Schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ResponsiveOutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Schedule Name") },
                    placeholder = { Text("e.g., Morning Bus") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Destination Selector Chips (Wrapped cleanly with FlowRow)
                Text(
                    text = "Destination: $destName",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            destName = currentStopName
                            destLat = currentStopLat
                            destLng = currentStopLng
                        },
                        color = if (destName == currentStopName) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Current: $currentStopName",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    savedDestinations.forEach { saved ->
                        Surface(
                            onClick = {
                                HapticUtils.lightTick(context, view)
                                destName = saved.name
                                destLat = saved.latitude
                                destLng = saved.longitude
                                alertRadius = saved.alertDistanceMeters
                                if (saved.reminderNote.isNotBlank()) {
                                    reminderNote = saved.reminderNote
                                }
                            },
                            color = if (destName == saved.name) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = saved.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                ResponsiveOutlinedTextField(
                    value = reminderNote,
                    onValueChange = { reminderNote = it },
                    label = { Text("Arrival Reminder Note (Optional)") },
                    placeholder = { Text("e.g., Exit via Gate 2 • Carry ID") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Active Days with Quick Presets
                Text(
                    text = "Active Days",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val monSat = setOf(1, 2, 3, 4, 5, 6)
                    val monFri = setOf(1, 2, 3, 4, 5)
                    val allDays = setOf(1, 2, 3, 4, 5, 6, 7)

                    Surface(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            selectedDays = monSat
                        },
                        color = if (selectedDays == monSat) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text(
                            text = "Mon–Sat",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }

                    Surface(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            selectedDays = monFri
                        },
                        color = if (selectedDays == monFri) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Mon–Fri",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }

                    Surface(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            selectedDays = allDays
                        },
                        color = if (selectedDays == allDays) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Every Day",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..7).forEach { day ->
                        val isSelected = day in selectedDays
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    HapticUtils.lightTick(context, view)
                                    selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = CommuteSchedule.dayShortName(day).take(2),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Start Time & End Time Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    startHour = h
                                    startMinute = m
                                },
                                startHour,
                                startMinute,
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Auto-Start", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(
                                text = CommuteSchedule.formatTime(startHour, startMinute),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    endHour = h
                                    endMinute = m
                                },
                                endHour,
                                endMinute,
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Auto-Stop", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(
                                text = CommuteSchedule.formatTime(endHour, endMinute),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Alert Type Choice (Concise 1-word labels)
                Text(
                    text = "Alert Mode",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modeOptions = listOf(
                        4 to "Smart",
                        3 to "Alarm",
                        2 to "Vibrate",
                        1 to "Silent"
                    )
                    modeOptions.forEach { (valInt, shortName) ->
                        val selected = alertTypeVal == valInt
                        Surface(
                            onClick = {
                                HapticUtils.lightTick(context, view)
                                alertTypeVal = valInt
                            },
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = shortName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialSchedule.copy(
                            id = initialSchedule.id.ifBlank { UUID.randomUUID().toString() },
                            label = label.trim().ifBlank { "Daily Commute" },
                            destinationName = destName.ifBlank { "Selected Stop" },
                            destinationLat = destLat,
                            destinationLng = destLng,
                            alertDistanceMeters = alertRadius,
                            alertTypeValue = alertTypeVal,
                            reminderNote = reminderNote.trim(),
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            daysOfWeek = if (selectedDays.isEmpty()) setOf(1, 2, 3, 4, 5, 6) else selectedDays,
                            isEnabled = true
                        )
                    )
                }
            ) {
                Text("Save Schedule", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

