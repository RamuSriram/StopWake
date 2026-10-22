package io.github.ramusriram.stopwake.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.ramusriram.stopwake.HapticUtils
import io.github.ramusriram.stopwake.LocationUtils
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Configure OSMDroid with a compliant application User-Agent and a clean cache directory,
 * purging any legacy cached error/watermarked tiles.
 */
fun ensureOsmConfigured(context: Context) {
    val appCtx = context.applicationContext
    val config = Configuration.getInstance()
    config.userAgentValue =
        "StopWake/1.0 (https://github.com/RamuSriram/StopWake; Android)"

    // Purge legacy cache directories that contained 403 or watermarked tiles
    try {
        listOf("osmdroid", "osmdroid_v2", "osmdroid_v3").forEach { oldName ->
            val oldDir = File(appCtx.cacheDir, oldName)
            if (oldDir.exists()) {
                oldDir.deleteRecursively()
            }
        }
    } catch (_: Exception) {}

    val baseDir = File(appCtx.filesDir, "osmdroid_offline_v5").apply { mkdirs() }
    val tileDir = File(baseDir, "tiles").apply { mkdirs() }
    config.osmdroidBasePath = baseDir
    config.osmdroidTileCache = tileDir
    config.tileFileSystemCacheMaxBytes = 150L * 1024L * 1024L
    config.tileFileSystemCacheTrimBytes = 120L * 1024L * 1024L
    config.expirationOverrideDuration = 1000L * 60L * 60L * 24L * 30L // 30-day offline tile retention
}

/**
 * Dark-slate color matrix filter applied to OSM street tiles when dark map mode is active.
 */
private val DARK_MAP_COLOR_FILTER: ColorMatrixColorFilter by lazy {
    val invertAndTintMatrix = ColorMatrix(
        floatArrayOf(
            -0.82f, 0f, 0f, 0f, 225f,
            0f, -0.80f, 0f, 0f, 232f,
            0f, 0f, -0.72f, 0f, 248f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    ColorMatrixColorFilter(invertAndTintMatrix)
}

/**
 * Draw a crisp, modern teardrop destination pin marker bitmap.
 */
private fun createDestinationPinDrawable(context: Context, isArrived: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (42 * density).toInt().coerceAtLeast(48)
    val height = (54 * density).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val cy = width / 2f
    val radius = width * 0.38f
    val tipY = height - (4 * density)

    val mainColor = if (isArrived) AndroidColor.rgb(16, 185, 129) else AndroidColor.rgb(59, 130, 246)

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, tipY, 4.5f * density, shadowPaint)

    val path = Path().apply {
        moveTo(cx, tipY)
        quadTo(cx - radius * 1.05f, cy + radius * 0.55f, cx - radius, cy)
        arcTo(cx - radius, cy - radius, cx + radius, cy + radius, 180f, 180f, false)
        quadTo(cx + radius * 1.05f, cy + radius * 0.55f, cx, tipY)
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mainColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
    }
    canvas.drawPath(path, strokePaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius * 0.38f, dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Draw a modern navigation puck for the user's current GPS position.
 */
private fun createUserLocationDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt().coerceAtLeast(40)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(75, 16, 185, 129)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, size * 0.46f, haloPaint)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, size * 0.30f, ringPaint)

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(16, 185, 129)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(c, c, size * 0.21f, dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMapView(
    destinationLat: Double,
    destinationLng: Double,
    destinationName: String,
    alertDistanceMeters: Int,
    currentLat: Double,
    currentLng: Double,
    isWithinAlertZone: Boolean,
    pathPoints: List<Pair<Double, Double>> = emptyList(),
    interactive: Boolean = true,
    useDarkTiles: Boolean = false,
    cameraFocusRequest: Int = 0,
    focusTarget: MapFocusTarget = MapFocusTarget.DESTINATION,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapTap by rememberUpdatedState(onMapTap)

    remember(context) {
        ensureOsmConfigured(context)
        true
    }

    val destPinNormal = remember(context) { createDestinationPinDrawable(context, isArrived = false) }
    val destPinArrived = remember(context) { createDestinationPinDrawable(context, isArrived = true) }
    val userDotDrawable = remember(context) { createUserLocationDrawable(context) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var lastDestination by remember { mutableStateOf(Pair(0.0, 0.0)) }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(cameraFocusRequest, focusTarget) {
        val map = mapViewRef ?: return@LaunchedEffect
        if (cameraFocusRequest <= 0) return@LaunchedEffect

        val hasDest = LocationUtils.isValidCoordinate(destinationLat, destinationLng)
        val hasCurr = LocationUtils.isValidCoordinate(currentLat, currentLng)

        when (focusTarget) {
            MapFocusTarget.DESTINATION -> {
                if (hasDest) {
                    map.controller.animateTo(
                        GeoPoint(destinationLat, destinationLng),
                        getZoomForRadius(alertDistanceMeters),
                        600L
                    )
                }
            }
            MapFocusTarget.USER_LOCATION -> {
                if (hasCurr) {
                    map.controller.animateTo(GeoPoint(currentLat, currentLng), 16.0, 600L)
                } else if (hasDest) {
                    map.controller.animateTo(GeoPoint(destinationLat, destinationLng), 15.0, 600L)
                }
            }
            MapFocusTarget.FIT_BOTH -> {
                if (hasDest && hasCurr) {
                    val minLat = kotlin.math.min(destinationLat, currentLat) - 0.004
                    val maxLat = kotlin.math.max(destinationLat, currentLat) + 0.004
                    val minLng = kotlin.math.min(destinationLng, currentLng) - 0.004
                    val maxLng = kotlin.math.max(destinationLng, currentLng) + 0.004
                    try {
                        map.zoomToBoundingBox(BoundingBox(maxLat, maxLng, minLat, minLng), true, 100)
                    } catch (_: Exception) {
                        map.controller.animateTo(GeoPoint(destinationLat, destinationLng), 14.0, 600L)
                    }
                } else if (hasDest) {
                    map.controller.animateTo(
                        GeoPoint(destinationLat, destinationLng),
                        getZoomForRadius(alertDistanceMeters),
                        600L
                    )
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ensureOsmConfigured(ctx)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                minZoomLevel = 3.0
                maxZoomLevel = 19.5

                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

                val initPoint = if (LocationUtils.isValidCoordinate(destinationLat, destinationLng)) {
                    GeoPoint(destinationLat, destinationLng)
                } else if (LocationUtils.isValidCoordinate(currentLat, currentLng)) {
                    GeoPoint(currentLat, currentLng)
                } else {
                    GeoPoint(20.5937, 78.9629)
                }
                controller.setZoom(getZoomForRadius(alertDistanceMeters))
                controller.setCenter(initPoint)
                lastDestination = Pair(destinationLat, destinationLng)
                mapViewRef = this
            }
        },
        update = { map ->
            // Apply dark or light styling to the map tile layer
            if (useDarkTiles) {
                map.overlayManager.tilesOverlay.setColorFilter(DARK_MAP_COLOR_FILTER)
                map.overlayManager.tilesOverlay.loadingBackgroundColor = AndroidColor.rgb(13, 19, 33)
                map.overlayManager.tilesOverlay.loadingLineColor = AndroidColor.rgb(30, 41, 59)
            } else {
                map.overlayManager.tilesOverlay.setColorFilter(null)
                map.overlayManager.tilesOverlay.loadingBackgroundColor = AndroidColor.rgb(241, 245, 249)
                map.overlayManager.tilesOverlay.loadingLineColor = AndroidColor.rgb(203, 213, 225)
            }

            map.overlays.clear()

            // 1. Tap / Long-Press overlay with tactile pinDrop haptic feedback
            val receiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        HapticUtils.pinDrop(map.context, map)
                        currentOnMapTap(p.latitude, p.longitude)
                        return true
                    }
                    return false
                }

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) {
                        HapticUtils.pinDrop(map.context, map)
                        currentOnMapTap(p.latitude, p.longitude)
                        return true
                    }
                    return false
                }
            }
            map.overlays.add(MapEventsOverlay(receiver))

            val hasDest = LocationUtils.isValidCoordinate(destinationLat, destinationLng)
            val hasCurr = LocationUtils.isValidCoordinate(currentLat, currentLng)

            // 2. Geofence radius circle around destination
            if (hasDest) {
                val destGeo = GeoPoint(destinationLat, destinationLng)
                val circlePolygon = Polygon(map).apply {
                    points = Polygon.pointsAsCircle(destGeo, alertDistanceMeters.toDouble().coerceAtLeast(30.0))
                    fillPaint.color = if (isWithinAlertZone) {
                        AndroidColor.argb(65, 16, 185, 129)
                    } else {
                        AndroidColor.argb(55, 59, 130, 246)
                    }
                    fillPaint.style = Paint.Style.FILL
                    outlinePaint.color = if (isWithinAlertZone) {
                        AndroidColor.rgb(16, 185, 129)
                    } else {
                        AndroidColor.rgb(59, 130, 246)
                    }
                    outlinePaint.strokeWidth = 5.5f
                }
                map.overlays.add(circlePolygon)
            }

            // 3. Direct route line connecting User -> Destination
            if (hasDest && hasCurr) {
                val routeLine = Polyline(map).apply {
                    setPoints(
                        listOf(
                            GeoPoint(currentLat, currentLng),
                            GeoPoint(destinationLat, destinationLng)
                        )
                    )
                    outlinePaint.color = AndroidColor.argb(200, 96, 165, 250)
                    outlinePaint.strokeWidth = 6.5f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                map.overlays.add(routeLine)
            }

            // 4. Traveled breadcrumb path
            if (pathPoints.size >= 2) {
                val trailLine = Polyline(map).apply {
                    setPoints(pathPoints.map { GeoPoint(it.first, it.second) })
                    outlinePaint.color = AndroidColor.argb(230, 16, 185, 129)
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                map.overlays.add(trailLine)
            }

            // 5. Current user GPS puck
            if (hasCurr) {
                val userMarker = Marker(map).apply {
                    position = GeoPoint(currentLat, currentLng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = userDotDrawable
                    infoWindow = null
                }
                map.overlays.add(userMarker)
            }

            // 6. Custom Destination Pin Marker
            if (hasDest) {
                val destGeo = GeoPoint(destinationLat, destinationLng)
                val destMarker = Marker(map).apply {
                    position = destGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = if (isWithinAlertZone) destPinArrived else destPinNormal
                    title = destinationName.ifBlank { "Destination" }
                    infoWindow = null
                }
                map.overlays.add(destMarker)

                if (kotlin.math.abs(lastDestination.first - destinationLat) > 0.0001 ||
                    kotlin.math.abs(lastDestination.second - destinationLng) > 0.0001
                ) {
                    lastDestination = Pair(destinationLat, destinationLng)
                    map.controller.animateTo(destGeo, getZoomForRadius(alertDistanceMeters), 550L)
                }
            }

            map.invalidate()
        },
        onRelease = { map ->
            map.onDetach()
        }
    )
}

enum class MapFocusTarget {
    DESTINATION,
    USER_LOCATION,
    FIT_BOTH
}

private fun getZoomForRadius(radiusMeters: Int): Double {
    return when {
        radiusMeters <= 150 -> 16.5
        radiusMeters <= 350 -> 15.6
        radiusMeters <= 700 -> 14.8
        radiusMeters <= 1500 -> 13.8
        radiusMeters <= 3000 -> 12.8
        else -> 11.8
    }
}
