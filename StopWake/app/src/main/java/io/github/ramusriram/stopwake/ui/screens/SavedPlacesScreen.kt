package io.github.ramusriram.stopwake.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import io.github.ramusriram.stopwake.HapticUtils
import io.github.ramusriram.stopwake.LocationUtils
import io.github.ramusriram.stopwake.data.SavedDestination
import io.github.ramusriram.stopwake.data.TripHistoryEntry
import io.github.ramusriram.stopwake.ui.components.ResponsiveOutlinedTextField
import io.github.ramusriram.stopwake.ui.theme.DangerCrimson
import io.github.ramusriram.stopwake.ui.theme.SafeEmerald
import io.github.ramusriram.stopwake.ui.theme.WarningAmber
import io.github.ramusriram.stopwake.viewmodel.AlertType
import io.github.ramusriram.stopwake.viewmodel.StopWakeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Screen for viewing, searching, adding, sharing, importing/exporting, and directly editing
 * Saved Destinations, Favorites, Destination Reminder Notes, and Trip History.
 */
@Composable
fun SavedPlacesScreen(
    savedDestinations: List<SavedDestination>,
    tripHistory: List<TripHistoryEntry>,
    viewModel: StopWakeViewModel,
    onSelectAndNavigateToDashboard: () -> Unit,
    onSelectAndStartTracking: () -> Unit,
    onRequestLocationPermission: ((onGranted: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val commuteSchedules by viewModel.commuteSchedules.collectAsState()

    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingDestination by remember { mutableStateOf<SavedDestination?>(null) }
    var showExportImportDialog by remember { mutableStateOf(false) }

    val filteredDestinations = remember(savedDestinations, searchQuery, showFavoritesOnly) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        savedDestinations.filter { dest ->
            val matchesFav = !showFavoritesOnly || dest.isFavorite
            val matchesQuery = q.isEmpty() ||
                dest.name.lowercase(Locale.getDefault()).contains(q) ||
                dest.address.lowercase(Locale.getDefault()).contains(q) ||
                dest.reminderNote.lowercase(Locale.getDefault()).contains(q)
            matchesFav && matchesQuery
        }
    }

    val filteredHistory = remember(tripHistory, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) {
            tripHistory
        } else {
            tripHistory.filter { entry ->
                entry.destinationName.lowercase(Locale.getDefault()).contains(q) ||
                    entry.outcome.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSection,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedSection == 0,
                onClick = {
                    HapticUtils.tabSwitch(context, view)
                    selectedSection = 0
                },
                text = {
                    Text(text = "Saved Stops (${savedDestinations.size})")
                },
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = {
                    HapticUtils.tabSwitch(context, view)
                    selectedSection = 1
                },
                text = {
                    Text(text = "Trip History (${tripHistory.size})")
                },
                icon = { Icon(Icons.Default.History, contentDescription = null) }
            )
        }

        // Persistent Search & Filter Bar for Saved Stops & Trip History
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResponsiveOutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (selectedSection == 0) {
                                "Search saved stops..."
                            } else {
                                "Search past trips..."
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    HapticUtils.lightTick(context, view)
                                    searchQuery = ""
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedSection == 0 && savedDestinations.isNotEmpty()) {
                    val favCount = savedDestinations.count { it.isFavorite }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                HapticUtils.lightTick(context, view)
                                showFavoritesOnly = false
                            },
                            color = if (!showFavoritesOnly) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(
                                1.dp,
                                if (!showFavoritesOnly) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "All Stops (${savedDestinations.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (!showFavoritesOnly) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            onClick = {
                                HapticUtils.lightTick(context, view)
                                showFavoritesOnly = !showFavoritesOnly
                            },
                            color = if (showFavoritesOnly) WarningAmber.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(
                                1.dp,
                                if (showFavoritesOnly) WarningAmber
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Favorites ($favCount)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (showFavoritesOnly) WarningAmber
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )

        when (selectedSection) {
            0 -> SavedDestinationsList(
                allSavedCount = savedDestinations.size,
                filteredDestinations = filteredDestinations,
                searchQuery = searchQuery,
                showFavoritesOnly = showFavoritesOnly,
                onClearFilters = {
                    HapticUtils.lightTick(context, view)
                    searchQuery = ""
                    showFavoritesOnly = false
                },
                onAddClick = {
                    HapticUtils.lightTick(context, view)
                    editingDestination = null
                    showEditorDialog = true
                },
                onEditClick = { dest ->
                    HapticUtils.lightTick(context, view)
                    editingDestination = dest
                    showEditorDialog = true
                },
                onShareSingleStop = { dest ->
                    HapticUtils.confirmAction(context, view)
                    val singleJson = GsonBuilder().setPrettyPrinting().create().toJson(dest)
                    val mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f", dest.latitude, dest.longitude)
                    val shareText = buildString {
                        append("StopWake Stop: ${dest.name}\n")
                        if (dest.address.isNotBlank()) append("Area: ${dest.address}\n")
                        append("Alert Zone: ${dest.alertDistanceMeters}m\n")
                        if (dest.reminderNote.isNotBlank()) append("Note: ${dest.reminderNote}\n")
                        append("Map: $mapsUrl\n")
                        append("Get StopWake: https://github.com/RamuSriram/StopWake\n\n")
                        append("Copy & import this stop in StopWake (Saved -> Share / Import -> Paste Shared Config):\n")
                        append(singleJson)
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "StopWake Stop: ${dest.name}")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Stop"))
                },
                onExportImportClick = {
                    HapticUtils.lightTick(context, view)
                    showExportImportDialog = true
                },
                onToggleFavorite = {
                    HapticUtils.lightTick(context, view)
                    viewModel.toggleFavoriteDestination(it)
                },
                onDelete = {
                    HapticUtils.lightTick(context, view)
                    viewModel.deleteSavedDestination(it)
                },
                onSelect = { dest ->
                    HapticUtils.lightTick(context, view)
                    viewModel.loadSavedDestination(dest)
                    onSelectAndNavigateToDashboard()
                },
                onStartNow = { dest ->
                    HapticUtils.confirmAction(context, view)
                    viewModel.loadSavedDestination(dest)
                    onSelectAndStartTracking()
                }
            )
            1 -> TripHistoryList(
                allHistoryCount = tripHistory.size,
                filteredHistory = filteredHistory,
                searchQuery = searchQuery,
                onClearSearch = {
                    HapticUtils.lightTick(context, view)
                    searchQuery = ""
                },
                onClearHistory = {
                    HapticUtils.confirmAction(context, view)
                    viewModel.clearTripHistory()
                },
                onRepeatTrip = { entry ->
                    HapticUtils.lightTick(context, view)
                    viewModel.setDestination(
                        lat = entry.latitude,
                        lng = entry.longitude,
                        name = entry.destinationName
                    )
                    viewModel.setAlertDistance(entry.alertDistanceMeters)
                    onSelectAndNavigateToDashboard()
                }
            )
        }
    }

    if (showEditorDialog) {
        AddOrEditDestinationDialog(
            existingDestination = editingDestination,
            currentMapName = uiState.destinationName,
            currentMapAddress = uiState.destinationAddress,
            currentMapLat = uiState.destinationLat,
            currentMapLng = uiState.destinationLng,
            currentAlertDistance = uiState.alertDistanceMeters,
            currentAlertType = uiState.alertType.value,
            hasLocationPermission = uiState.hasLocationPermission,
            onRequestUseLiveGps = { onGpsCoordinates ->
                if (uiState.hasLocationPermission) {
                    viewModel.refreshDeviceLocation { lat, lng ->
                        onGpsCoordinates(lat, lng)
                    }
                } else if (onRequestLocationPermission != null) {
                    onRequestLocationPermission {
                        viewModel.refreshDeviceLocation { lat, lng ->
                            onGpsCoordinates(lat, lng)
                        }
                    }
                } else {
                    Toast.makeText(context, "Please allow Location permission first", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showEditorDialog = false
                editingDestination = null
            },
            onSave = { updatedDest ->
                HapticUtils.confirmAction(context, view)
                viewModel.saveOrUpdateSavedDestination(
                    destination = updatedDest,
                    previousDestination = editingDestination
                )
                showEditorDialog = false
                editingDestination = null
            }
        )
    }

    if (showExportImportDialog) {
        ExportImportStopsDialog(
            savedStopsCount = savedDestinations.size,
            schedulesCount = commuteSchedules.size,
            viewModel = viewModel,
            onDismiss = { showExportImportDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedDestinationsList(
    allSavedCount: Int,
    filteredDestinations: List<SavedDestination>,
    searchQuery: String,
    showFavoritesOnly: Boolean,
    onClearFilters: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (SavedDestination) -> Unit,
    onShareSingleStop: (SavedDestination) -> Unit,
    onExportImportClick: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (SavedDestination) -> Unit,
    onStartNow: (SavedDestination) -> Unit
) {
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Stop")
                }
                OutlinedButton(
                    onClick = onExportImportClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ImportExport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Import")
                }
            }
        }

        if (allSavedCount == 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = cardBorder,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Saved Stops Yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Bookmark your frequent transit, college, or work stops for one-tap tracking, or import stops shared by a friend.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (filteredDestinations.isEmpty()) {
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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No stops matching \"$searchQuery\""
                            } else {
                                "No favorite stops yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (showFavoritesOnly && searchQuery.isBlank()) {
                                "Tap the star icon on any saved stop to mark it as a favorite."
                            } else {
                                "Try a different keyword or clear the search filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = onClearFilters,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Show All Saved Stops")
                        }
                    }
                }
            }
        } else {
            items(filteredDestinations, key = { it.id }) { dest ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = cardBorder,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row 1: Favorite Star + Title/Address + Share, Edit & Delete Icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            IconButton(
                                onClick = { onToggleFavorite(dest.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (dest.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (dest.isFavorite) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onEditClick(dest) }
                            ) {
                                Text(
                                    text = dest.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (dest.address.isNotBlank()) {
                                    Text(
                                        text = dest.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onShareSingleStop(dest) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Stop",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onEditClick(dest) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Stop",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onDelete(dest.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Stop",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Optional Reminder Note Banner (clickable to edit)
                        if (dest.reminderNote.isNotBlank()) {
                            Surface(
                                onClick = { onEditClick(dest) },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dest.reminderNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Row 2: Metadata Badges + Coordinates (FlowRow prevents any horizontal clipping)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${dest.alertDistanceMeters}m zone",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            val alertLabel = AlertType.entries.find { it.value == dest.alertType }?.shortLabel
                                ?: "Smart"
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = alertLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${"%.4f".format(Locale.US, dest.latitude)}, ${"%.4f".format(Locale.US, dest.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Row 3: Direct Edit, Load, and Track Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEditClick(dest) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
                                modifier = Modifier.weight(0.85f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                            OutlinedButton(
                                onClick = { onSelect(dest) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
                                modifier = Modifier.weight(0.85f)
                            ) {
                                Text("Load")
                            }
                            Button(
                                onClick = { onStartNow(dest) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafeEmerald,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Track", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripHistoryList(
    allHistoryCount: Int,
    filteredHistory: List<TripHistoryEntry>,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onClearHistory: () -> Unit,
    onRepeatTrip: (TripHistoryEntry) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (allHistoryCount > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClearHistory) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear History")
                    }
                }
            }
        }

        if (allHistoryCount == 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = cardBorder,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Past Trips Recorded",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Your completed tracking sessions, arrival confirmations, and safety alerts will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (filteredHistory.isEmpty()) {
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
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No trips matching \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = onClearSearch) {
                            Text("Clear Search")
                        }
                    }
                }
            }
        } else {
            items(filteredHistory, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = cardBorder,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.destinationName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val badgeColor = when {
                                item.outcome.contains("Arrived", ignoreCase = true) ||
                                    item.outcome.contains("Confirmed", ignoreCase = true) -> SafeEmerald
                                item.outcome.contains("Emergency", ignoreCase = true) -> DangerCrimson
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                color = badgeColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = item.outcome,
                                    color = badgeColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "${dateFormat.format(Date(item.endedAt))} • Zone: ${item.alertDistanceMeters}m" +
                                (item.minDistanceReachedMeters?.let { " • Closest: ${LocationUtils.formatDistance(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${"%.4f".format(Locale.US, item.latitude)}, ${"%.4f".format(Locale.US, item.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { onRepeatTrip(item) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Use Again")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding a new Saved Stop or editing an existing Saved Stop in-place.
 */
@Composable
private fun AddOrEditDestinationDialog(
    existingDestination: SavedDestination?,
    currentMapName: String,
    currentMapAddress: String,
    currentMapLat: Double,
    currentMapLng: Double,
    currentAlertDistance: Int,
    currentAlertType: Int,
    hasLocationPermission: Boolean,
    onRequestUseLiveGps: ((Double, Double) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (SavedDestination) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isEditing = existingDestination != null

    var name by remember(existingDestination) {
        mutableStateOf(existingDestination?.name ?: "")
    }
    var address by remember(existingDestination) {
        mutableStateOf(existingDestination?.address ?: "")
    }
    var latText by remember(existingDestination) {
        mutableStateOf(
            existingDestination?.let { "%.5f".format(Locale.US, it.latitude) }
                ?: "%.5f".format(Locale.US, currentMapLat)
        )
    }
    var lngText by remember(existingDestination) {
        mutableStateOf(
            existingDestination?.let { "%.5f".format(Locale.US, it.longitude) }
                ?: "%.5f".format(Locale.US, currentMapLng)
        )
    }
    var alertRadius by remember(existingDestination) {
        mutableIntStateOf(existingDestination?.alertDistanceMeters ?: currentAlertDistance)
    }
    var alertTypeVal by remember(existingDestination) {
        mutableIntStateOf(existingDestination?.alertType ?: currentAlertType)
    }
    var reminderNote by remember(existingDestination) {
        mutableStateOf(existingDestination?.reminderNote ?: "")
    }
    var isFavorite by remember(existingDestination) {
        mutableStateOf(existingDestination?.isFavorite ?: false)
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Saved Stop" else "Add Saved Stop",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        HapticUtils.lightTick(context, view)
                        isFavorite = !isFavorite
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isEditing && LocationUtils.isValidCoordinate(currentMapLat, currentMapLng)) {
                        Surface(
                            onClick = {
                                HapticUtils.lightTick(context, view)
                                if (name.isBlank()) name = currentMapName
                                if (address.isBlank()) address = currentMapAddress
                                latText = "%.5f".format(Locale.US, currentMapLat)
                                lngText = "%.5f".format(Locale.US, currentMapLng)
                            },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Map Pin",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            HapticUtils.lightTick(context, view)
                            onRequestUseLiveGps { lat, lng ->
                                latText = "%.5f".format(Locale.US, lat)
                                lngText = "%.5f".format(Locale.US, lng)
                                errorText = null
                            }
                        },
                        color = SafeEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, SafeEmerald.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = SafeEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasLocationPermission) "Use Live GPS" else "Allow GPS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafeEmerald
                            )
                        }
                    }
                }

                ResponsiveOutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Stop Name") },
                    placeholder = { Text("e.g., Central Bus / Metro Stop") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                ResponsiveOutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Area (Optional)") },
                    placeholder = { Text("e.g., Main Street / Platform 2") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                ResponsiveOutlinedTextField(
                    value = reminderNote,
                    onValueChange = { reminderNote = it },
                    label = { Text("Arrival Note (Optional)") },
                    placeholder = { Text("e.g., Exit Gate 2 • Check bag") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResponsiveOutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            errorText = null
                        },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    ResponsiveOutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            lngText = it
                            errorText = null
                        },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Alert Distance Slider + Presets
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alert Radius",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$alertRadius m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = alertRadius.toFloat().coerceIn(50f, 5000f),
                        onValueChange = { v ->
                            alertRadius = ((v / 25f).roundToInt() * 25).coerceIn(50, 5000)
                        },
                        valueRange = 50f..5000f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(100, 250, 500, 1000, 2000).forEach { preset ->
                            val selected = alertRadius == preset
                            Surface(
                                onClick = {
                                    HapticUtils.lightTick(context, view)
                                    alertRadius = preset
                                },
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (preset >= 1000) "${preset / 1000}km" else "${preset}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Alert Mode Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Alert Mode",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            4 to "Smart",
                            3 to "Alarm",
                            2 to "Vibrate",
                            1 to "Silent"
                        ).forEach { (modeInt, modeLabel) ->
                            val selected = alertTypeVal == modeInt
                            Surface(
                                onClick = {
                                    HapticUtils.lightTick(context, view)
                                    alertTypeVal = modeInt
                                },
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = modeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                errorText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.trim().toDoubleOrNull()
                    val lng = lngText.trim().toDoubleOrNull()
                    if (name.isBlank()) {
                        errorText = "Please enter a stop name"
                    } else if (lat == null || lng == null || !LocationUtils.isValidCoordinate(lat, lng)) {
                        errorText = "Please enter valid Latitude (-90..90) and Longitude (-180..180)"
                    } else {
                        onSave(
                            SavedDestination(
                                id = existingDestination?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                address = address.trim(),
                                latitude = lat,
                                longitude = lng,
                                alertDistanceMeters = alertRadius.coerceIn(50, 10000),
                                alertType = alertTypeVal,
                                reminderNote = reminderNote.trim(),
                                emergencyContact = existingDestination?.emergencyContact ?: "",
                                isFavorite = isFavorite
                            )
                        )
                    }
                }
            ) {
                Text(if (isEditing) "Save Changes" else "Save Stop", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for exporting, sharing, and importing Saved Stops & Commute Schedules via JSON files or chat snippets.
 */
@Composable
private fun ExportImportStopsDialog(
    savedStopsCount: Int,
    schedulesCount: Int,
    viewModel: StopWakeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var showPasteField by remember { mutableStateOf(false) }
    var pastedJson by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupJson { jsonContent ->
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                    isErrorStatus = false
                    statusMessage = "Saved backup file ($savedStopsCount stops, $schedulesCount schedules)."
                    Toast.makeText(context, "Backup file saved successfully", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    isErrorStatus = true
                    statusMessage = "Could not write backup file to the selected location."
                }
            }
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: ""
                viewModel.importBackupJson(content) { result ->
                    isErrorStatus = !result.success
                    statusMessage = result.message
                    if (result.success) {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                isErrorStatus = true
                statusMessage = "Could not read the selected file."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ImportExport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share, Export & Import",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Share your configured stops and schedules with friends on the same transit route, or back up and restore your data across devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current Data Summary Pill
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ready to Export:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$savedStopsCount Stops • $schedulesCount Schedules",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 1. Export & Share Section
                Text(
                    text = "Export & Share",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        HapticUtils.confirmAction(context, view)
                        if (savedStopsCount == 0 && schedulesCount == 0) {
                            isErrorStatus = true
                            statusMessage = "Add at least one stop or schedule before sharing."
                        } else {
                            viewModel.exportBackupJson { jsonContent ->
                                val shareText = buildString {
                                    append("Here are my StopWake transit stops ($savedStopsCount stops, $schedulesCount schedules).\n")
                                    append("Get StopWake: https://github.com/RamuSriram/StopWake\n")
                                    append("In StopWake, open Saved -> Share / Import -> Paste Shared Config to import them:\n\n")
                                    append(jsonContent)
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "StopWake Stops & Schedules")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Stops with Friends"))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Stops with Friends", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        HapticUtils.confirmAction(context, view)
                        if (savedStopsCount == 0 && schedulesCount == 0) {
                            isErrorStatus = true
                            statusMessage = "Add at least one stop or schedule before exporting a file."
                        } else {
                            saveFileLauncher.launch("stopwake_stops_backup.json")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Backup File (.json)", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                // 2. Import Section
                Text(
                    text = "Import Stops & Schedules",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = {
                        HapticUtils.lightTick(context, view)
                        openFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import from .json File", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        HapticUtils.lightTick(context, view)
                        showPasteField = !showPasteField
                        if (showPasteField && pastedJson.isBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                            if (clipText.contains("{") && clipText.contains("}")) {
                                pastedJson = clipText
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showPasteField) "Hide Paste Box" else "Paste Shared Config",
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(visible = showPasteField) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResponsiveOutlinedTextField(
                            value = pastedJson,
                            onValueChange = {
                                pastedJson = it
                                statusMessage = null
                            },
                            label = { Text("Shared StopWake JSON") },
                            placeholder = { Text("Paste the shared stop message or JSON here...") },
                            singleLine = false,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                HapticUtils.confirmAction(context, view)
                                viewModel.importBackupJson(pastedJson) { result ->
                                    isErrorStatus = !result.success
                                    statusMessage = result.message
                                    if (result.success) {
                                        pastedJson = ""
                                        showPasteField = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafeEmerald,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import Pasted Stops", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Surface(
                        color = if (isErrorStatus) DangerCrimson.copy(alpha = 0.14f)
                        else SafeEmerald.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isErrorStatus) DangerCrimson else SafeEmerald,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

