package io.github.ramusriram.stopwake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ramusriram.stopwake.ui.components.StopWakeBrandLogo
import io.github.ramusriram.stopwake.ui.screens.GuideAndAboutScreen
import io.github.ramusriram.stopwake.ui.screens.InteractiveMapScreen
import io.github.ramusriram.stopwake.ui.screens.SavedPlacesScreen
import io.github.ramusriram.stopwake.ui.screens.SchedulesScreen
import io.github.ramusriram.stopwake.ui.screens.TrackingDashboardScreen
import io.github.ramusriram.stopwake.ui.theme.StopWakeBlue
import io.github.ramusriram.stopwake.ui.theme.StopWakeBlueBright
import io.github.ramusriram.stopwake.ui.theme.StopWakeIndigo
import io.github.ramusriram.stopwake.ui.theme.StopWakeTheme
import io.github.ramusriram.stopwake.ui.theme.DangerCrimson
import io.github.ramusriram.stopwake.ui.theme.SafeEmerald
import io.github.ramusriram.stopwake.ui.theme.WarningAmber
import io.github.ramusriram.stopwake.viewmodel.StopWakeViewModel
import io.github.ramusriram.stopwake.viewmodel.TrackingState

class MainActivity : ComponentActivity() {

    private val viewModel: StopWakeViewModel by viewModels()

    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var contactPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var ringtonePickerLauncher: ActivityResultLauncher<Intent>
    private var shouldStartTrackingOnGrant = false
    private var pendingGrantCallback: (() -> Unit)? = null
    private var pendingRequireLocation = false
    private var pendingRequireBackgroundLocation = false
    private var pendingRequireNotification = false
    private var pendingRequireSms = false
    private var settingsFallbackPermissionName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        LocationService.createNotificationChannels(this)

        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            viewModel.refreshPermissionsState()
            val hasLoc = LocationUtils.hasLocationPermission(this)
            val hasBgLoc = LocationUtils.hasBackgroundLocationPermission(this)
            val hasNotif = LocationUtils.hasNotificationPermission(this)
            val hasSms = LocationUtils.hasSmsPermission(this)

            if (hasLoc) {
                viewModel.refreshDeviceLocation()
            }

            val callback = pendingGrantCallback
            pendingGrantCallback = null

            if (shouldStartTrackingOnGrant) {
                shouldStartTrackingOnGrant = false
                if (hasLoc) {
                    viewModel.startTracking()
                } else {
                    handlePermissionDenied(
                        permissionLabel = "Location",
                        manifestPerm = Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            } else if (pendingRequireBackgroundLocation) {
                if (hasBgLoc) {
                    callback?.invoke()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Toast.makeText(
                        this,
                        "Select 'Allow all the time' so schedules can start automatically in the background.",
                        Toast.LENGTH_LONG
                    ).show()
                    openAppPermissionSettings()
                }
            } else if (pendingRequireLocation) {
                if (hasLoc) {
                    callback?.invoke()
                } else {
                    handlePermissionDenied(
                        permissionLabel = "Location",
                        manifestPerm = Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            } else if (pendingRequireSms) {
                if (hasSms) {
                    callback?.invoke()
                } else {
                    handlePermissionDenied(
                        permissionLabel = "SMS (Emergency Safety Net)",
                        manifestPerm = Manifest.permission.SEND_SMS
                    )
                }
            } else if (pendingRequireNotification) {
                callback?.invoke()
                if (!hasNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    handlePermissionDenied(
                        permissionLabel = "Notifications",
                        manifestPerm = Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }

            pendingRequireLocation = false
            pendingRequireBackgroundLocation = false
            pendingRequireNotification = false
            pendingRequireSms = false
        }

        contactPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val contactUri = result.data?.data ?: return@registerForActivityResult
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        if (number.isNotBlank()) {
                            val validation = viewModel.addEmergencyContact(number, name)
                            if (validation.isValid) {
                                val displayLabel = if (name.isNotBlank()) "$name (${validation.formattedNumber})" else validation.formattedNumber
                                Toast.makeText(this, "Added $displayLabel", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    validation.errorMessage ?: "Invalid phone number",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        ringtonePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (pickedUri != null) {
                    val title = try {
                        RingtoneManager.getRingtone(this, pickedUri)?.getTitle(this) ?: "Custom Tone"
                    } catch (_: Exception) {
                        "Custom Tone"
                    }
                    viewModel.setCustomRingtone(pickedUri.toString(), title)
                }
            }
        }

        if (LocationUtils.hasLocationPermission(this)) {
            viewModel.refreshDeviceLocation()
        }
        handleIncomingIntent(intent)

        setContent {
            StopWakeTheme {
                StopWakeMainShell(
                    viewModel = viewModel,
                    settingsFallbackPermissionName = settingsFallbackPermissionName,
                    onDismissSettingsFallback = { settingsFallbackPermissionName = null },
                    onOpenAppSettings = {
                        settingsFallbackPermissionName = null
                        openAppPermissionSettings()
                    },
                    onRequestPermissionsAndStart = {
                        requestAppPermissions(startTrackingAfter = true)
                    },
                    onRequestPermissionsOnly = {
                        requestAppPermissions(startTrackingAfter = false)
                    },
                    onRequestLocationPermission = { onGranted ->
                        requestSpecificPermissions(
                            requireLocation = true,
                            requireNotification = false,
                            requireSms = false,
                            onGranted = onGranted
                        )
                    },
                    onRequestBackgroundLocationPermission = {
                        requestBackgroundLocationPermission()
                    },
                    onRequestNotificationPermission = { onGranted ->
                        requestSpecificPermissions(
                            requireLocation = false,
                            requireNotification = true,
                            requireSms = false,
                            onGranted = onGranted
                        )
                    },
                    onRequestSmsPermission = {
                        requestSpecificPermissions(
                            requireLocation = false,
                            requireNotification = false,
                            requireSms = true
                        )
                    },
                    onRequestIgnoreBatteryOptimizations = {
                        requestIgnoreBatteryOptimizations()
                    },
                    onPickEmergencyContact = {
                        launchContactPhonePicker()
                    },
                    onPickCustomRingtone = {
                        launchCustomRingtonePicker()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionsState()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val scheduleId = intent?.getStringExtra(ScheduleReceiver.EXTRA_AUTO_START_SCHEDULE_ID)
        if (!scheduleId.isNullOrBlank()) {
            intent.removeExtra(ScheduleReceiver.EXTRA_AUTO_START_SCHEDULE_ID)
            ScheduleReceiver.dismissSchedulePromptNotification(this)
            viewModel.runScheduleById(
                scheduleId = scheduleId,
                onMissingPermissions = { requestAppPermissions(startTrackingAfter = true) }
            )
            return
        }

        if (intent?.getBooleanExtra(ScheduleReceiver.EXTRA_AUTO_START_ACTIVE_CONFIG, false) == true) {
            intent.removeExtra(ScheduleReceiver.EXTRA_AUTO_START_ACTIVE_CONFIG)
            ScheduleReceiver.dismissSchedulePromptNotification(this)
            val cfg = LocationService.activeSessionConfig
            if (LocationUtils.isValidCoordinate(cfg.destinationLat, cfg.destinationLng)) {
                viewModel.setDestination(
                    lat = cfg.destinationLat,
                    lng = cfg.destinationLng,
                    name = cfg.destinationName,
                    reminderNote = cfg.reminderNote,
                    fromUserAction = true
                )
                viewModel.setAlertDistance(cfg.alertDistanceMeters)
                if (LocationUtils.hasLocationPermission(this)) {
                    viewModel.startTracking()
                } else {
                    requestAppPermissions(startTrackingAfter = true)
                }
            }
        }
    }

    private fun handlePermissionDenied(permissionLabel: String, manifestPerm: String) {
        val canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, manifestPerm)
        if (!canShowRationale) {
            settingsFallbackPermissionName = permissionLabel
        } else {
            Toast.makeText(
                this,
                "$permissionLabel permission is required for this feature.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openAppPermissionSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Open System Settings > Apps > StopWake > Permissions",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestAppPermissions(startTrackingAfter: Boolean) {
        shouldStartTrackingOnGrant = startTrackingAfter
        pendingRequireLocation = true
        pendingRequireBackgroundLocation = false
        pendingRequireNotification = true
        pendingRequireSms = false
        permissionsLauncher.launch(LocationUtils.getRequiredPermissions(includeSms = false))
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (LocationUtils.hasBackgroundLocationPermission(this)) return

        if (!LocationUtils.hasLocationPermission(this)) {
            requestSpecificPermissions(
                requireLocation = true,
                requireNotification = true,
                requireSms = false,
                onGranted = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !LocationUtils.hasBackgroundLocationPermission(this)) {
                        shouldStartTrackingOnGrant = false
                        pendingRequireBackgroundLocation = true
                        permissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    }
                }
            )
            return
        }

        shouldStartTrackingOnGrant = false
        pendingRequireLocation = false
        pendingRequireBackgroundLocation = true
        pendingRequireNotification = false
        pendingRequireSms = false
        permissionsLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    private fun requestSpecificPermissions(
        requireLocation: Boolean,
        requireNotification: Boolean,
        requireSms: Boolean,
        onGranted: (() -> Unit)? = null
    ) {
        val permsToRequest = mutableListOf<String>()
        if (requireLocation && !LocationUtils.hasLocationPermission(this)) {
            permsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (requireNotification && !LocationUtils.hasNotificationPermission(this) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (requireSms && !BuildConfig.IS_PLAY_STORE_BUILD && !LocationUtils.hasSmsPermission(this)) {
            permsToRequest.add(Manifest.permission.SEND_SMS)
        }

        if (permsToRequest.isEmpty()) {
            onGranted?.invoke()
            return
        }

        shouldStartTrackingOnGrant = false
        pendingGrantCallback = onGranted
        pendingRequireLocation = requireLocation
        pendingRequireBackgroundLocation = false
        pendingRequireNotification = requireNotification
        pendingRequireSms = requireSms && !BuildConfig.IS_PLAY_STORE_BUILD
        permissionsLauncher.launch(permsToRequest.toTypedArray())
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (BuildConfig.IS_PLAY_STORE_BUILD) {
            Toast.makeText(
                this,
                "Select Battery > Unrestricted so StopWake can wake you while your screen is off",
                Toast.LENGTH_LONG
            ).show()
            openAppPermissionSettings()
            return
        }
        try {
            if (!LocationUtils.isIgnoringBatteryOptimizations(this)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Open System Settings > Apps > StopWake > Battery > Unrestricted",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun launchContactPhonePicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No Contacts app available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCustomRingtonePicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select StopWake Wake-Up Sound")
                val existing = viewModel.uiState.value.customRingtoneUri
                if (existing.isNotBlank()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
                }
            }
            ringtonePickerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "System sound picker unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val CHANNEL_ID = LocationService.CHANNEL_ID
    }
}

private enum class MainTab(val title: String, val icon: ImageVector) {
    TRACK("Track", Icons.Default.Navigation),
    MAP("Map", Icons.Default.Map),
    SCHEDULE("Schedule", Icons.Default.Schedule),
    SAVED("Saved", Icons.Default.Bookmarks)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopWakeMainShell(
    viewModel: StopWakeViewModel,
    settingsFallbackPermissionName: String? = null,
    onDismissSettingsFallback: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRequestPermissionsAndStart: () -> Unit,
    onRequestPermissionsOnly: () -> Unit,
    onRequestLocationPermission: (onGranted: () -> Unit) -> Unit = {},
    onRequestBackgroundLocationPermission: () -> Unit = {},
    onRequestNotificationPermission: (onGranted: () -> Unit) -> Unit = {},
    onRequestSmsPermission: () -> Unit = {},
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onPickEmergencyContact: () -> Unit,
    onPickCustomRingtone: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val uiState by viewModel.uiState.collectAsState()
    val savedDestinations by viewModel.savedDestinations.collectAsState()
    val commuteSchedules by viewModel.commuteSchedules.collectAsState()
    val tripHistory by viewModel.tripHistory.collectAsState()

    var selectedTabOrdinal by rememberSaveable { mutableIntStateOf(MainTab.TRACK.ordinal) }
    var showHelpScreen by rememberSaveable { mutableStateOf(false) }
    var showPermissionDisclosure by remember { mutableStateOf(false) }
    var startTrackingAfterDisclosure by remember { mutableStateOf(false) }
    val selectedTab = MainTab.entries.getOrElse(selectedTabOrdinal) { MainTab.TRACK }

    fun triggerPermissionFlow(startTrackingAfter: Boolean) {
        if (!uiState.hasLocationPermission) {
            startTrackingAfterDisclosure = startTrackingAfter
            showPermissionDisclosure = true
        } else if (startTrackingAfter) {
            onRequestPermissionsAndStart()
        } else {
            onRequestPermissionsOnly()
        }
    }

    if (showPermissionDisclosure) {
        PermissionDisclosureDialog(
            onConfirm = {
                HapticUtils.confirmAction(context, view)
                showPermissionDisclosure = false
                if (startTrackingAfterDisclosure) {
                    onRequestPermissionsAndStart()
                } else {
                    onRequestPermissionsOnly()
                }
            },
            onDismiss = {
                HapticUtils.lightTick(context, view)
                showPermissionDisclosure = false
            }
        )
    }

    if (settingsFallbackPermissionName != null) {
        PermissionSettingsFallbackDialog(
            permissionName = settingsFallbackPermissionName,
            onOpenSettings = {
                HapticUtils.confirmAction(context, view)
                onOpenAppSettings()
            },
            onDismiss = {
                HapticUtils.lightTick(context, view)
                onDismissSettingsFallback()
            }
        )
    }

    BackHandler(enabled = showHelpScreen) {
        HapticUtils.lightTick(context, view)
        showHelpScreen = false
    }

    fun switchToTab(tab: MainTab) {
        showHelpScreen = false
        if (selectedTabOrdinal != tab.ordinal) {
            HapticUtils.tabSwitch(context, view)
            selectedTabOrdinal = tab.ordinal
        } else {
            HapticUtils.lightTick(context, view)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (showHelpScreen) {
                            IconButton(
                                onClick = {
                                    HapticUtils.lightTick(context, view)
                                    showHelpScreen = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!showHelpScreen) {
                                StopWakeBrandLogo(size = 36.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (showHelpScreen) "Help & About" else "StopWake",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (!showHelpScreen && !uiState.isAlarmPlaying) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TrackingStatusBadge(state = uiState.trackingState)
                                    }
                                }
                                Text(
                                    text = if (showHelpScreen) {
                                        "User Guide & Creator Profile"
                                    } else {
                                        "GPS Stop & Wake Alarm"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.isAlarmPlaying) {
                            Button(
                                onClick = {
                                    HapticUtils.confirmAction(context, view)
                                    viewModel.dismissActiveAlarm()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DangerCrimson,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlarmOff,
                                    contentDescription = "Dismiss Alert",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Silence",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        if (!showHelpScreen) {
                            IconButton(
                                onClick = {
                                    HapticUtils.tabSwitch(context, view)
                                    showHelpScreen = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Help & About",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                )
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = !showHelpScreen && selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { switchToTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = {
                                Text(
                                    text = tab.title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showHelpScreen) {
                GuideAndAboutScreen(
                    currentAlertDistance = uiState.alertDistanceMeters
                )
            } else {
                when (selectedTab) {
                    MainTab.TRACK -> TrackingDashboardScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestPermissionsAndStart = { triggerPermissionFlow(startTrackingAfter = true) },
                        onRequestPermissionsOnly = { triggerPermissionFlow(startTrackingAfter = false) },
                        onRequestLocationPermission = onRequestLocationPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations,
                        onPickEmergencyContact = onPickEmergencyContact,
                        onPickCustomRingtone = onPickCustomRingtone,
                        onOpenFullMap = { switchToTab(MainTab.MAP) },
                        onOpenSchedules = { switchToTab(MainTab.SCHEDULE) }
                    )
                    MainTab.MAP -> InteractiveMapScreen(
                        uiState = uiState,
                        savedDestinations = savedDestinations,
                        viewModel = viewModel,
                        onRequestPermissionsAndStart = { triggerPermissionFlow(startTrackingAfter = true) },
                        onRequestLocationPermission = onRequestLocationPermission
                    )
                    MainTab.SCHEDULE -> SchedulesScreen(
                        schedules = commuteSchedules,
                        savedDestinations = savedDestinations,
                        uiState = uiState,
                        viewModel = viewModel,
                        onRunScheduleAndNavigateToTrack = { schedule ->
                            switchToTab(MainTab.TRACK)
                            if (uiState.hasLocationPermission && uiState.hasNotificationPermission) {
                                viewModel.runScheduleNow(schedule)
                            } else {
                                viewModel.setDestination(
                                    lat = schedule.destinationLat,
                                    lng = schedule.destinationLng,
                                    name = schedule.destinationName
                                )
                                viewModel.setAlertDistance(schedule.alertDistanceMeters)
                                triggerPermissionFlow(startTrackingAfter = true)
                            }
                        },
                        onRequestPermissionsForSchedule = {
                            triggerPermissionFlow(startTrackingAfter = false)
                        },
                        onRequestBackgroundLocationPermission = onRequestBackgroundLocationPermission
                    )
                    MainTab.SAVED -> SavedPlacesScreen(
                        savedDestinations = savedDestinations,
                        tripHistory = tripHistory,
                        viewModel = viewModel,
                        onSelectAndNavigateToDashboard = {
                            switchToTab(MainTab.TRACK)
                        },
                        onSelectAndStartTracking = {
                            switchToTab(MainTab.TRACK)
                            if (uiState.hasLocationPermission && uiState.hasNotificationPermission) {
                                viewModel.startTracking()
                            } else {
                                triggerPermissionFlow(startTrackingAfter = true)
                            }
                        },
                        onRequestLocationPermission = onRequestLocationPermission
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionSettingsFallbackDialog(
    permissionName: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val isSmsRestrictedOnAndroid13Plus = permissionName.contains("SMS", ignoreCase = true) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSmsRestrictedOnAndroid13Plus) {
                    "Unlock Android Restricted Settings for Auto-SMS"
                } else {
                    "Enable $permissionName in Settings"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            if (isSmsRestrictedOnAndroid13Plus) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "On Android 13+, sideloaded apps (installed via APK) have background SMS locked under \"Restricted settings\" by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = SafeEmerald.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Good news: 1-Tap Emergency SMS Alerts already work right now without this permission! Only unlock Restricted Settings if you want 100% unattended background Auto-SMS while asleep.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "How to enable unattended Auto-SMS (3 steps):\n" +
                            "1. Tap \"Open App Info\" below.\n" +
                            "2. Tap the ⋮ (three-dot menu) in the top-right corner of StopWake's App Info screen (or scroll to the bottom on Samsung/Xiaomi) and tap \"Allow restricted settings\".\n" +
                            "3. Tap Permissions → SMS → Allow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = "Android blocked the permission prompt because $permissionName access was previously denied. Please tap \"Open App Info\" and enable $permissionName under Permissions so StopWake can run this feature.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open App Info", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isSmsRestrictedOnAndroid13Plus) "Use 1-Tap Mode" else "Not Now")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun PermissionDisclosureDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Location & Wake-Up Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "StopWake needs your permission to monitor your trip and wake you up before your stop:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Foreground & Background GPS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "StopWake uses location data while a trip is active (including when the screen is locked) to calculate your distance to your stop. Your location stays on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = SafeEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Arrival Notifications & Alarm",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Displays live trip distance in your status bar and triggers your arrival wake-up alarm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun TrackingStatusBadge(state: TrackingState) {
    val (label, bgColor, contentColor) = when (state) {
        TrackingState.IDLE -> Triple(
            "Ready",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        TrackingState.TRACKING -> Triple(
            "Tracking",
            StopWakeBlueBright.copy(alpha = 0.18f),
            StopWakeBlueBright
        )
        TrackingState.ARRIVED -> Triple(
            "Arriving!",
            SafeEmerald.copy(alpha = 0.2f),
            SafeEmerald
        )
        TrackingState.SNOOZED -> Triple(
            "Snoozed",
            WarningAmber.copy(alpha = 0.22f),
            WarningAmber
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(0.8.dp, contentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Legacy shared state holder retained for backwards compatibility.
 */
object LocationData {
    var destinationLat: Double by mutableStateOf(0.0)
    var destinationLng: Double by mutableStateOf(0.0)
    var distanceToDestination: Float? by mutableStateOf(null)
    var notificationOption: Int by mutableStateOf(1)
    var isWithin100Meters: Boolean by mutableStateOf(false)
    var emergencyContact: String by mutableStateOf("")
    var alertDistance: Int by mutableStateOf(100)
}