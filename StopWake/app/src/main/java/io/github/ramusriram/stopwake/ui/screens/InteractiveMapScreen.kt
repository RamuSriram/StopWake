package io.github.ramusriram.stopwake.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ramusriram.stopwake.HapticUtils
import io.github.ramusriram.stopwake.LocationUtils
import io.github.ramusriram.stopwake.data.SavedDestination
import io.github.ramusriram.stopwake.ui.components.MapFocusTarget
import io.github.ramusriram.stopwake.ui.components.OsmMapView
import io.github.ramusriram.stopwake.ui.components.ResponsiveOutlinedTextField
import io.github.ramusriram.stopwake.ui.theme.DangerCrimson
import io.github.ramusriram.stopwake.ui.theme.SafeEmerald
import io.github.ramusriram.stopwake.viewmodel.StopWakeViewModel
import io.github.ramusriram.stopwake.viewmodel.TrackingState
import io.github.ramusriram.stopwake.viewmodel.TrackingUiState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-screen interactive OpenStreetMap radar screen for visual destination selection,
 * geofence radius adjustment, and live tracking visualization.
 */
@Composable
fun InteractiveMapScreen(
    uiState: TrackingUiState,
    savedDestinations: List<SavedDestination>,
    viewModel: StopWakeViewModel,
    onRequestPermissionsAndStart: () -> Unit,
    onRequestLocationPermission: (onGranted: () -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()

    var focusTrigger by remember { mutableIntStateOf(0) }
    var focusTarget by remember { mutableStateOf(MapFocusTarget.DESTINATION) }
    var useDarkMapTiles by remember(isDarkTheme) { mutableStateOf(isDarkTheme) }
    val isTracking = uiState.trackingState != TrackingState.IDLE
    val isPlaceSaved = uiState.isSavedIn(savedDestinations)

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-Screen OSMDroid Map
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
            cameraFocusRequest = focusTrigger,
            focusTarget = focusTarget,
            onMapTap = { lat, lng ->
                viewModel.selectDestinationFromMapTap(lat, lng)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Floating Search Overlay + Quick Near Me Pill
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                ResponsiveOutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(text = "Search stop or tap map")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.isSearching || uiState.isReverseGeocoding) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    HapticUtils.lightTick(context, view)
                                    viewModel.clearSearch()
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search Results Dropdown (Zero text truncation!)
            AnimatedVisibility(visible = uiState.searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        uiState.searchResults.forEachIndexed { index, res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        HapticUtils.pinDrop(context, view)
                                        viewModel.selectSearchResult(res)
                                        focusTarget = MapFocusTarget.DESTINATION
                                        focusTrigger++
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${"%.5f".format(Locale.US, res.lat)}, ${"%.5f".format(Locale.US, res.lon)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (index < uiState.searchResults.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Floating Map Control FABs (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            SmallFloatingActionButton(
                onClick = {
                    HapticUtils.lightTick(context, view)
                    useDarkMapTiles = !useDarkMapTiles
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (useDarkMapTiles) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Map Style"
                )
            }

            SmallFloatingActionButton(
                onClick = {
                    HapticUtils.lightTick(context, view)
                    val centerAction = {
                        viewModel.refreshDeviceLocation { _, _ ->
                            focusTarget = MapFocusTarget.USER_LOCATION
                            focusTrigger++
                        }
                    }
                    if (uiState.hasLocationPermission) {
                        centerAction()
                    } else {
                        onRequestLocationPermission(centerAction)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My GPS Location")
            }

            SmallFloatingActionButton(
                onClick = {
                    HapticUtils.lightTick(context, view)
                    focusTarget = MapFocusTarget.DESTINATION
                    focusTrigger++
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center on Destination")
            }

            SmallFloatingActionButton(
                onClick = {
                    HapticUtils.lightTick(context, view)
                    focusTarget = MapFocusTarget.FIT_BOTH
                    focusTrigger++
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit Route")
            }
        }

        // 4. Bottom Interactive Geofence & Destination Control Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.destinationName.ifBlank { "Tap on map to pick stop" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lat ${"%.5f".format(Locale.US, uiState.destinationLat)}, Lng ${"%.5f".format(Locale.US, uiState.destinationLng)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    uiState.distanceToDestination?.let { dist ->
                        Surface(
                            color = if (uiState.isWithinAlertZone) SafeEmerald
                            else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = LocationUtils.formatDistance(dist),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.isWithinAlertZone) Color.White
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Live Geofence Radius Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${uiState.alertDistanceMeters}m zone",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.width(94.dp)
                    )
                    Slider(
                        value = uiState.alertDistanceMeters.toFloat().coerceIn(50f, 5000f),
                        onValueChange = { value ->
                            val rounded = ((value / 25f).roundToInt() * 25).coerceIn(50, 5000)
                            if (rounded != uiState.alertDistanceMeters) {
                                HapticUtils.lightTick(context, view)
                                viewModel.setAlertDistance(rounded)
                            }
                        },
                        valueRange = 50f..5000f,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Save/Unsave Toggle & Start/Stop Tracking Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticUtils.confirmAction(context, view)
                            viewModel.toggleSaveCurrentDestination()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPlaceSaved) SafeEmerald.copy(alpha = 0.14f) else Color.Transparent,
                            contentColor = if (isPlaceSaved) SafeEmerald else MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPlaceSaved) SafeEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(
                            imageVector = if (isPlaceSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaceSaved) "Saved" else "Save Place",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    if (!isTracking) {
                        Button(
                            onClick = {
                                HapticUtils.confirmAction(context, view)
                                if (uiState.hasLocationPermission && uiState.hasNotificationPermission) {
                                    viewModel.startTracking()
                                } else {
                                    onRequestPermissionsAndStart()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafeEmerald,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Tracking", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    } else {
                        Button(
                            onClick = {
                                HapticUtils.confirmAction(context, view)
                                viewModel.stopTracking()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DangerCrimson,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Tracking", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
