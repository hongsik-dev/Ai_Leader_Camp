package com.catchpro.app.ui.screen.tmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun NaverRouteAddressMap(
    mapState: RouteAddressMapUiState,
    modifier: Modifier = Modifier,
    onPointClick: ((RouteAddressMapPointUiModel) -> Unit)? = null,
) {
    val mapView = rememberNaverMapViewWithLifecycle()
    val naverMapState = remember { mutableStateOf<NaverMap?>(null) }
    val markersByKey = remember { mutableMapOf<String, Marker>() }
    val pathOverlaysByKey = remember { mutableMapOf<String, PathOverlay>() }
    val startCircleOverlay = remember { CircleOverlay() }
    val userInteracting = remember { mutableStateOf(false) }
    val lastCameraSignature = remember { mutableStateOf<String?>(null) }
    val currentMarkerIcon = remember { startMarkerIcon() }
    val nextStopArrowImage = remember { nextStopArrowPatternImage() }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            mapView.apply {
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_POINTER_UP,
                        -> {
                            userInteracting.value = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> {
                            userInteracting.value = false
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }
                getMapAsync { naverMap ->
                    naverMap.configureForRouteAddress()
                    naverMapState.value = naverMap
                }
            }
        },
        update = {
            val naverMap = naverMapState.value ?: return@AndroidView
            updateRouteAddressMap(
                naverMap = naverMap,
                mapState = mapState,
                markersByKey = markersByKey,
                pathOverlaysByKey = pathOverlaysByKey,
                startCircleOverlay = startCircleOverlay,
                currentMarkerIcon = currentMarkerIcon,
                nextStopArrowImage = nextStopArrowImage,
                userInteracting = userInteracting.value,
                lastCameraSignature = lastCameraSignature,
                onPointClick = onPointClick,
            )
        },
    )
}

private fun NaverMap.configureForRouteAddress() {
    mapType = NaverMap.MapType.Navi
    setLayerGroupEnabled(NaverMap.LAYER_GROUP_TRAFFIC, true)
    uiSettings.setAllGesturesEnabled(true)
    uiSettings.isZoomControlEnabled = false
    uiSettings.isScaleBarEnabled = false
    uiSettings.isCompassEnabled = false
    uiSettings.isLocationButtonEnabled = false
    uiSettings.scrollGesturesFriction = 0.15f
    uiSettings.zoomGesturesFriction = 0.15f
    uiSettings.rotateGesturesFriction = 0.15f
    locationOverlay.isVisible = false
    setContentPadding(
        MapContentPaddingHorizontal,
        MapContentPaddingTop,
        MapContentPaddingHorizontal,
        MapContentPaddingBottom,
    )
}

private fun updateRouteAddressMap(
    naverMap: NaverMap,
    mapState: RouteAddressMapUiState,
    markersByKey: MutableMap<String, Marker>,
    pathOverlaysByKey: MutableMap<String, PathOverlay>,
    startCircleOverlay: CircleOverlay,
    currentMarkerIcon: OverlayImage,
    nextStopArrowImage: OverlayImage,
    userInteracting: Boolean,
    lastCameraSignature: androidx.compose.runtime.MutableState<String?>,
    onPointClick: ((RouteAddressMapPointUiModel) -> Unit)?,
) {
    val currentLatLng = mapState.currentLatLng()
    val allBoundsPoints = mutableListOf<LatLng>()
    val desiredMarkerKeys = mutableSetOf<String>()
    val desiredPathKeys = mutableSetOf<String>()

    if (currentLatLng != null) {
        startCircleOverlay.apply {
            center = currentLatLng
            radius = StartCircleRadiusMeters
            color = Color.argb(46, 0, 112, 255)
            outlineColor = Color.rgb(0, 74, 255)
            outlineWidth = 5
            globalZIndex = 1600
            map = naverMap
        }
        naverMap.locationOverlay.apply {
            isVisible = true
            position = currentLatLng
            circleRadius = 45
            circleColor = Color.argb(44, 0, 112, 255)
            globalZIndex = 1000
        }
        desiredMarkerKeys += CurrentMarkerKey
        val currentMarker = markersByKey.getOrPut(CurrentMarkerKey) {
            Marker().apply { icon = currentMarkerIcon }
        }
        currentMarker.apply {
            position = currentLatLng
            icon = currentMarkerIcon
            captionText = "출발점"
            subCaptionText = "현재 위치"
            captionColor = Color.rgb(0, 52, 196)
            captionHaloColor = Color.WHITE
            captionTextSize = 17f
            subCaptionTextSize = 12f
            width = CurrentMarkerWidth
            height = CurrentMarkerHeight
            zIndex = 100
            globalZIndex = 2000
            setForceShowIcon(true)
            setForceShowCaption(true)
            map = naverMap
        }
        allBoundsPoints += currentLatLng
    } else {
        startCircleOverlay.map = null
    }

    mapState.nearestStops.forEach { stop ->
        val routeCoords = stop.routePath.map { LatLng(it.latitude, it.longitude) }
        if (routeCoords.size >= 2) {
            val pathKey = "path-${stop.order}-${stop.sourceIndex}"
            desiredPathKeys += pathKey
            val pathOverlay = pathOverlaysByKey.getOrPut(pathKey) { PathOverlay() }
            pathOverlay.apply {
                coords = routeCoords
                width = 9
                outlineWidth = 3
                color = Color.rgb(0, 112, 255)
                outlineColor = Color.WHITE
                zIndex = 4
                map = naverMap
            }
            allBoundsPoints += routeCoords
        }
    }

    if (currentLatLng != null) {
        val nextStop = mapState.nearestStops.firstOrNull()
        val arrowCoords = nextStop
            ?.routeCoordsFrom(currentLatLng)
            .orEmpty()
        if (arrowCoords.size >= 2) {
            desiredPathKeys += NextStopArrowPathKey
            val arrowOverlay = pathOverlaysByKey.getOrPut(NextStopArrowPathKey) { PathOverlay() }
            arrowOverlay.apply {
                coords = arrowCoords
                width = 13
                outlineWidth = 5
                color = Color.argb(56, 0, 74, 255)
                outlineColor = Color.WHITE
                patternImage = nextStopArrowImage
                patternInterval = NextStopArrowPatternInterval
                zIndex = 30
                map = naverMap
            }
            allBoundsPoints += arrowCoords
        }
    }

    pathOverlaysByKey.removeStaleKeys(desiredPathKeys) { path ->
        path.map = null
    }

    val nearestStopBySourceIndex = mapState.nearestStops.associateBy { it.sourceIndex }
    mapState.points.forEach { point ->
        val pointKey = "point-${point.sourceIndex}"
        desiredMarkerKeys += pointKey
        val latLng = LatLng(point.latitude, point.longitude)
        val overlapsCurrent = currentLatLng
            ?.let { it.distanceMetersTo(latLng) < MarkerOverlapThresholdMeters }
            ?: false
        val nearestStop = nearestStopBySourceIndex[point.sourceIndex]
        val marker = markersByKey.getOrPut(pointKey) { Marker() }
        marker.apply {
            position = latLng
            captionText = buildString {
                if (nearestStop != null) {
                    append(nearestStop.order)
                    append(". ")
                }
                append(point.label)
                point.distanceKmFromCurrentLocation?.let {
                    append(" ")
                    append(it.formatDistanceKm())
                    append("km")
                }
            }
            iconTintColor = Color.rgb(255, 139, 0)
            captionColor = Color.rgb(170, 82, 0)
            captionHaloColor = Color.WHITE
            captionTextSize = 13f
            subCaptionText = when {
                overlapsCurrent -> "출발점 근처"
                nearestStop != null -> "방문 ${nearestStop.order}"
                else -> ""
            }
            subCaptionTextSize = 10f
            zIndex = 8
            setForceShowIcon(true)
            setForceShowCaption(true)
            setOnClickListener {
                onPointClick?.invoke(point)
                true
            }
            map = naverMap
        }
        allBoundsPoints += latLng
    }

    markersByKey.removeStaleKeys(desiredMarkerKeys) { marker ->
        marker.map = null
    }

    val cameraSignature = mapState.cameraSignature(currentLatLng)
    if (cameraSignature != lastCameraSignature.value) {
        if (!userInteracting) {
            when {
                allBoundsPoints.size >= 2 -> {
                    val boundsBuilder = LatLngBounds.Builder()
                    allBoundsPoints.forEach(boundsBuilder::include)
                    naverMap.moveCamera(CameraUpdate.fitBounds(boundsBuilder.build(), 80))
                }
                allBoundsPoints.size == 1 -> {
                    naverMap.moveCamera(CameraUpdate.scrollAndZoomTo(allBoundsPoints.first(), 14.0))
                }
            }
        }
        lastCameraSignature.value = cameraSignature
    }
}

private inline fun <T> MutableMap<String, T>.removeStaleKeys(
    desiredKeys: Set<String>,
    onRemove: (T) -> Unit,
) {
    val staleKeys = keys.filterNot { it in desiredKeys }
    staleKeys.forEach { key ->
        remove(key)?.let(onRemove)
    }
}

@Composable
private fun rememberNaverMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun RouteAddressMapUiState.currentLatLng(): LatLng? {
    val latitude = currentLatitude ?: return null
    val longitude = currentLongitude ?: return null
    return LatLng(latitude, longitude)
}

private fun RouteAddressNearestStopUiModel.routeCoordsFrom(currentLatLng: LatLng): List<LatLng> {
    val routeCoords = routePath.map { LatLng(it.latitude, it.longitude) }
    return routeCoords.takeIf { it.size >= 2 }
        ?: listOf(currentLatLng, LatLng(latitude, longitude))
}

private fun RouteAddressMapUiState.cameraSignature(startLatLng: LatLng?): String =
    buildString {
        append(startLatLng?.latitude?.roundForCameraSignature())
        append(',')
        append(startLatLng?.longitude?.roundForCameraSignature())
        append('|')
        points.joinTo(this, separator = ";") {
            "${it.sourceIndex}:${it.latitude.roundForCameraSignature()},${it.longitude.roundForCameraSignature()}"
        }
        append('|')
        nearestStops.joinTo(this, separator = ";") {
            "${it.order}:${it.sourceIndex}:${it.legDistanceKm?.roundForCameraSignature()}"
        }
    }

private fun Double.roundForCameraSignature(): String =
    "%.5f".format(Locale.US, this)

private fun Double.formatDistanceKm(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        "%.1f".format(Locale.getDefault(), this)
    }
}

private const val CurrentMarkerKey = "current"
private const val NextStopArrowPathKey = "next-stop-arrow"
private const val CurrentMarkerWidth = 118
private const val CurrentMarkerHeight = 150
private const val NextStopArrowPatternWidth = 64
private const val NextStopArrowPatternHeight = 24
private const val NextStopArrowPatternInterval = 54
private const val StartCircleRadiusMeters = 650.0
private const val MapContentPaddingHorizontal = 32
private const val MapContentPaddingTop = 430
private const val MapContentPaddingBottom = 620
private const val MarkerOverlapThresholdMeters = 800.0

private fun LatLng.distanceMetersTo(other: LatLng): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(other.latitude - latitude)
    val longitudeDelta = Math.toRadians(other.longitude - longitude)
    val fromLatitude = Math.toRadians(latitude)
    val toLatitude = Math.toRadians(other.latitude)

    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(fromLatitude) * cos(toLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun startMarkerIcon(): OverlayImage {
    val bitmap = Bitmap.createBitmap(CurrentMarkerWidth, CurrentMarkerHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 74, 255)
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    val point = Path().apply {
        moveTo(CurrentMarkerWidth / 2f, CurrentMarkerHeight - 6f)
        lineTo(CurrentMarkerWidth * 0.22f, CurrentMarkerHeight * 0.56f)
        lineTo(CurrentMarkerWidth * 0.78f, CurrentMarkerHeight * 0.56f)
        close()
    }
    canvas.drawPath(point, fill)
    canvas.drawPath(point, stroke)
    canvas.drawCircle(CurrentMarkerWidth / 2f, CurrentMarkerHeight * 0.36f, 32f, fill)
    canvas.drawCircle(CurrentMarkerWidth / 2f, CurrentMarkerHeight * 0.36f, 32f, stroke)
    return OverlayImage.fromBitmap(bitmap)
}

private fun nextStopArrowPatternImage(): OverlayImage {
    val bitmap = Bitmap.createBitmap(
        NextStopArrowPatternWidth,
        NextStopArrowPatternHeight,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 74, 255)
    }
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    canvas.drawCircle(9f, NextStopArrowPatternHeight / 2f, 4.5f, fill)
    canvas.drawCircle(24f, NextStopArrowPatternHeight / 2f, 4.5f, fill)
    val arrow = Path().apply {
        moveTo(39f, 5f)
        lineTo(57f, NextStopArrowPatternHeight / 2f)
        lineTo(39f, NextStopArrowPatternHeight - 5f)
        close()
    }
    canvas.drawPath(arrow, fill)
    canvas.drawPath(arrow, halo)
    return OverlayImage.fromBitmap(bitmap)
}
