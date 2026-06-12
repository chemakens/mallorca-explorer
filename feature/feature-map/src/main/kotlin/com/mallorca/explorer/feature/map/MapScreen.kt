package com.mallorca.explorer.feature.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.ui.component.OfflineBanner
import com.mallorca.explorer.core.ui.theme.Azure
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.widget.addTextChangedListener


/**
 * Interpolates a feature property (target radius at zoom 14) across zoom levels.
 * Shrinks markers at overview zoom to avoid obscuring the coastline.
 */
private fun zoomScaledRadius(property: String): Expression =
    Expression.interpolate(
        Expression.linear(), Expression.zoom(),
        Expression.stop(7,  Expression.product(Expression.get(property), Expression.literal(0.35f))),
        Expression.stop(10, Expression.product(Expression.get(property), Expression.literal(0.55f))),
        Expression.stop(13, Expression.product(Expression.get(property), Expression.literal(0.80f))),
        Expression.stop(14, Expression.get(property)),
        Expression.stop(17, Expression.product(Expression.get(property), Expression.literal(1.25f))),
    )

// MapView con soporte nativo para scroll de ratón (rueda)
private class ScrollableMapView(context: Context) : MapView(context) {
    var onScrollZoom: ((scrollUp: Boolean) -> Unit)? = null

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (axis != 0f) {
                onScrollZoom?.invoke(axis > 0)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}

private val MALLORCA_CENTER = org.maplibre.android.geometry.LatLng(39.6953, 3.0176)
private const val DEFAULT_ZOOM = 8.5
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"

// Itinerary stop layers
private const val STOPS_SOURCE_ID    = "itinerary-stops-source"
private const val STOPS_SHADOW_LAYER = "itinerary-stops-shadow"
private const val STOPS_HALO_LAYER   = "itinerary-stops-halo"
private const val STOPS_LAYER        = "itinerary-stops"

// Route line layer
private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID  = "route-line"

// Place clustering layers
private const val PLACES_SOURCE_ID      = "places-source"
private const val CLUSTER_LAYER_ID      = "cluster-circle"
private const val CLUSTER_COUNT_LAYER   = "cluster-count"
private const val UNCLUSTERED_LAYER_ID  = "unclustered-place"

// Selected-place overlay
private const val SELECTED_SOURCE_ID    = "selected-place-source"
private const val SELECTED_LAYER_ID     = "selected-place-layer"

// Hidden gem mystery pins
private const val GEMS_SOURCE_ID        = "hidden-gems-source"
private const val GEM_MYSTERY_LAYER_ID  = "hidden-gems-mystery"
private const val GEM_ICON_ID           = "gem-mystery-pin"

/**
 * Compact gold circle with white border and "?" label — mystery pin for hidden gems.
 */
private fun createGemMysteryBitmap(w: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = w / 2f
    val inner = r * 0.88f
    // Gold fill
    canvas.drawCircle(r, r, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F9A825")
        style = Paint.Style.FILL
    })
    // White border for contrast against any map background
    canvas.drawCircle(r, r, inner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = r * 0.18f
    })
    // Dark "?" for legibility on gold
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A0030")
        textSize = w * 0.48f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val bounds = android.graphics.Rect()
    textPaint.getTextBounds("?", 0, 1, bounds)
    canvas.drawText("?", r, r - bounds.exactCenterY(), textPaint)
    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onPlaceClick: (String) -> Unit,
    onItineraryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    itineraryId: String? = null,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val categorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchText by remember { mutableStateOf("") }
    val isSearching = remember { mutableStateOf(false) }
    LaunchedEffect(itineraryId) { viewModel.setActiveItinerary(itineraryId) }
    val locale = LocalConfiguration.current.locales[0].language
    LaunchedEffect(locale) { viewModel.setLocale(locale) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleReady = remember { mutableStateOf(false) }

    val onPlaceClickState = rememberUpdatedState(onPlaceClick)
    val onItineraryClickState = rememberUpdatedState(onItineraryClick)
    val currentRoutesState = rememberUpdatedState(uiState.allItineraryRoutes)

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) centerOnUserLocation(context, mapRef.value)
    }

    val mapView = remember {
        MapLibre.getInstance(context, "", WellKnownTileServer.MapLibre)
        ScrollableMapView(context).apply {
            onCreate(null)
            onScrollZoom = { scrollUp ->
                mapRef.value?.animateCamera(
                    if (scrollUp) CameraUpdateFactory.zoomIn() else CameraUpdateFactory.zoomOut(), 150,
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapRef.value = map

            map.uiSettings.apply {
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
            }

            map.setStyle(MAP_STYLE_URL) { style ->

                // ── Route line ──────────────────────────────────────────────
                style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.lineColor(Expression.get("color")),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineOpacity(0.85f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    }
                )

                // ── Place pins ───────────────────────────────────────────────
                style.addSource(GeoJsonSource(PLACES_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(
                    CircleLayer(UNCLUSTERED_LAYER_ID, PLACES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleColor(Expression.get("color")),
                            PropertyFactory.circleRadius(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(5,  Expression.literal(4f)),
                                    Expression.stop(10, Expression.literal(5.5f)),
                                    Expression.stop(14, Expression.literal(7f)),
                                    Expression.stop(18, Expression.literal(9f)),
                                )
                            ),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(1.5f),
                        )
                    }
                )

                style.addSource(GeoJsonSource(SELECTED_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(
                    CircleLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleColor("#FFFFFF"),
                            PropertyFactory.circleRadius(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(5,  Expression.literal(6f)),
                                    Expression.stop(10, Expression.literal(7.5f)),
                                    Expression.stop(14, Expression.literal(9f)),
                                    Expression.stop(18, Expression.literal(11f)),
                                )
                            ),
                            PropertyFactory.circleStrokeColor("#1565C0"),
                            PropertyFactory.circleStrokeWidth(3f),
                        )
                    }
                )
                // ── Hidden gem mystery pins ──────────────────────────────────
                style.addSource(GeoJsonSource(GEMS_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                val gemW = (context.resources.displayMetrics.density * 32).toInt()
                style.addImage(GEM_ICON_ID, createGemMysteryBitmap(gemW))
                style.addLayer(
                    SymbolLayer(GEM_MYSTERY_LAYER_ID, GEMS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.iconImage(GEM_ICON_ID),
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(5,  Expression.literal(0.65f)),
                                    Expression.stop(10, Expression.literal(0.90f)),
                                    Expression.stop(13, Expression.literal(1.05f)),
                                    Expression.stop(18, Expression.literal(1.15f)),
                                )
                            ),
                            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                        )
                    }
                )

                // ── Itinerary stop layers (above places) — zoom-driven scaling ──
                // Feature property "radius" = target radius at zoom 14.
                // At lower zooms: shrink to avoid covering coastline features.
                // At higher zooms: grow slightly for tap precision.
                style.addSource(GeoJsonSource(STOPS_SOURCE_ID))
                style.addLayer(
                    CircleLayer(STOPS_SHADOW_LAYER, STOPS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleColor("#000000"),
                            PropertyFactory.circleRadius(zoomScaledRadius("haloRadius")),
                            PropertyFactory.circleBlur(1.2f),
                            PropertyFactory.circleOpacity(Expression.get("shadowOpacity")),
                        )
                    }
                )
                style.addLayer(
                    CircleLayer(STOPS_HALO_LAYER, STOPS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleColor("#FFFFFF"),
                            PropertyFactory.circleRadius(zoomScaledRadius("haloRadius")),
                            PropertyFactory.circleOpacity(Expression.get("opacity")),
                        )
                    }
                )
                style.addLayer(
                    CircleLayer(STOPS_LAYER, STOPS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleColor(Expression.get("color")),
                            PropertyFactory.circleRadius(zoomScaledRadius("radius")),
                            PropertyFactory.circleOpacity(Expression.get("opacity")),
                        )
                    }
                )

                map.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(MALLORCA_CENTER).zoom(DEFAULT_ZOOM).build()
                    )
                )
                styleReady.value = true
            }

            map.addOnMapClickListener { latLng ->
                val tapScreen = map.projection.toScreenLocation(latLng)
                val tapPoint = android.graphics.PointF(tapScreen.x, tapScreen.y)

                // 1. Hidden gem tap → show mystery card (no info revealed)
                val gemFeatures = map.queryRenderedFeatures(tapPoint, GEM_MYSTERY_LAYER_ID)
                if (gemFeatures.isNotEmpty()) {
                    val placeId = gemFeatures[0].getStringProperty("placeId")
                    if (placeId != null) {
                        viewModel.onGemMarkerTapped(placeId)
                        return@addOnMapClickListener true
                    }
                }

                // 2. Individual place tap → show detail card
                val placeFeatures = map.queryRenderedFeatures(tapPoint, UNCLUSTERED_LAYER_ID)
                if (placeFeatures.isNotEmpty()) {
                    val placeId = placeFeatures[0].getStringProperty("placeId")
                    if (placeId != null) {
                        viewModel.onMarkerTapped(placeId)
                        return@addOnMapClickListener true
                    }
                }

                // 3. Itinerary stop tap → navigate to itinerary detail
                val hitRadiusPx = 80f
                var bestId: String? = null
                var bestDistSq = hitRadiusPx * hitRadiusPx
                var nearbyCount = 0

                for (route in currentRoutesState.value) {
                    for (coord in route.coords) {
                        val markerScreen = map.projection.toScreenLocation(
                            org.maplibre.android.geometry.LatLng(coord.latitude, coord.longitude)
                        )
                        val dx = tapScreen.x - markerScreen.x
                        val dy = tapScreen.y - markerScreen.y
                        val distSq = dx * dx + dy * dy
                        if (distSq < bestDistSq) { bestDistSq = distSq; bestId = route.id }
                        if (distSq < 120f * 120f) nearbyCount++
                    }
                }

                when {
                    bestId != null -> {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(latLng, maxOf(map.cameraPosition.zoom, 12.0)), 350,
                        )
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            onItineraryClickState.value(bestId)
                        }, 200)
                        true
                    }
                    nearbyCount >= 2 && map.cameraPosition.zoom < 13 -> {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(latLng, map.cameraPosition.zoom + 2.5), 600,
                        )
                        true
                    }
                    else -> {
                        viewModel.onBottomSheetDismissed()
                        false
                    }
                }
            }
        }
    }

    // Update itinerary stop markers + route line when itinerary changes
    LaunchedEffect(styleReady.value, uiState.allItineraryRoutes, uiState.activeItinerary) {
        val map = mapRef.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect

        val stopsSource = map.style?.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID) ?: return@LaunchedEffect
        val routeSource = map.style?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@LaunchedEffect

        val activeId = uiState.activeItinerary?.id
        val isAnyActive = activeId != null

        // Stop markers
        val features = uiState.allItineraryRoutes.flatMap { route ->
            val isActive = route.id == activeId
            // Base radius at zoom 14 — zoomScaledRadius() scales these down at overview
            val innerRadius = if (isActive) 7.0 else 4.5
            val haloRadius  = if (isActive) 10.0 else 7.0
            val opacity = if (!isAnyActive) 0.0 else if (isActive) 1.0 else 0.25
            route.coords.map { coord ->
                Feature.fromGeometry(
                    Point.fromLngLat(coord.longitude, coord.latitude),
                    JsonObject().apply {
                        addProperty("itineraryId", route.id)
                        addProperty("color", route.color)
                        addProperty("radius", innerRadius)
                        addProperty("haloRadius", haloRadius)
                        addProperty("opacity", opacity)
                        addProperty("shadowOpacity", opacity * 0.15)
                    }
                )
            }
        }
        stopsSource.setGeoJson(FeatureCollection.fromFeatures(features))

        // Route line: draw ordered polyline for active itinerary
        val activeRoute = uiState.allItineraryRoutes.find { it.id == activeId }
        if (activeRoute != null && activeRoute.coords.size >= 2) {
            val lineCoords = activeRoute.coords.map { Point.fromLngLat(it.longitude, it.latitude) }
            val routeFeature = Feature.fromGeometry(
                LineString.fromLngLats(lineCoords),
                JsonObject().apply { addProperty("color", activeRoute.color) }
            )
            routeSource.setGeoJson(FeatureCollection.fromFeatures(listOf(routeFeature)))
        } else {
            routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        // Camera bounds for active itinerary
        if (activeId != null && activeRoute != null && activeRoute.coords.size >= 2) {
            val builder = LatLngBounds.Builder()
            activeRoute.coords.forEach {
                builder.include(org.maplibre.android.geometry.LatLng(it.latitude, it.longitude))
            }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80), 1200)
        }
    }

    // Update selected-place pin whenever the selection changes
    LaunchedEffect(styleReady.value, uiState.selectedPlace) {
        if (!styleReady.value) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val source = map.style?.getSourceAs<GeoJsonSource>(SELECTED_SOURCE_ID) ?: return@LaunchedEffect
        val selected = uiState.selectedPlace
        if (selected == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(listOf(
                Feature.fromGeometry(
                    Point.fromLngLat(selected.location.longitude, selected.location.latitude),
                )
            )))
        }
    }

    // Update place clustering source when places change
    LaunchedEffect(styleReady.value, uiState.places) {
        if (!styleReady.value) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val source = map.style?.getSourceAs<GeoJsonSource>(PLACES_SOURCE_ID) ?: return@LaunchedEffect
        val features = uiState.places.map { place ->
            Feature.fromGeometry(
                Point.fromLngLat(place.location.longitude, place.location.latitude),
                JsonObject().apply {
                    addProperty("placeId", place.id)
                    addProperty("color", place.category.routeColor())
                }
            )
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Update hidden gem mystery pins when gems change
    LaunchedEffect(styleReady.value, uiState.hiddenGems) {
        if (!styleReady.value) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val source = map.style?.getSourceAs<GeoJsonSource>(GEMS_SOURCE_ID) ?: return@LaunchedEffect
        val features = uiState.hiddenGems.map { gem ->
            Feature.fromGeometry(
                Point.fromLngLat(gem.location.longitude, gem.location.latitude),
                JsonObject().apply { addProperty("placeId", gem.id) }
            )
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        OfflineBanner(isOffline = uiState.isOffline, modifier = Modifier.align(Alignment.TopCenter))
        // ── Search bar: visual-only tap target ────────────────────────
        // The real TextField lives in the overlay below. Keeping the
        // TextField here causes MapLibre's AndroidView to intercept
        // touch events before Compose can grant keyboard focus.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = if (uiState.isOffline) 114.dp else 74.dp, start = 16.dp, end = 16.dp)
                .height(54.dp)
                .clickable { isSearching.value = true },
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🔍", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (searchText.isEmpty()) "Buscar lugares secretos..." else searchText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (searchText.isEmpty()) Color.Gray else Color(0xFF1C1B1F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Search Dialog: native EditText inside its own Android Window ──
        // Compose TextField shares the IME focus chain with MapLibre's
        // AndroidView even inside a Dialog. A native EditText + direct
        // InputMethodManager.showSoftInput() bypasses Compose's focus
        // layer entirely and always opens the keyboard reliably.
        if (isSearching.value) {
            Dialog(
                onDismissRequest = {
                    isSearching.value = false
                    searchText = ""
                    viewModel.clearSearch()
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true,
                ),
            ) {
                val dialogView = LocalView.current
                SideEffect {
                    val win = (dialogView.parent as? DialogWindowProvider)?.window
                    // ADJUST_NOTHING prevents the dialog window from resizing/shifting
                    // when the keyboard appears, which avoids a black background below the IME.
                    win?.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    )
                    // Let the system draw the dim; clear any opaque window background.
                    win?.setBackgroundDrawableResource(android.R.color.transparent)
                    win?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    win?.setDimAmount(0.4f)
                }
                // Transparent dismiss area — dimming is handled by the window above.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            isSearching.value = false
                            searchText = ""
                            viewModel.clearSearch()
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(
                                top = if (uiState.isOffline) 114.dp else 74.dp,
                                start = 16.dp,
                                end = 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // ── Input field ──────────────────────────────────────
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clickable(enabled = false) {},
                            shape = RoundedCornerShape(28.dp),
                            color = Color.White,
                            shadowElevation = 8.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AndroidView(
                                    modifier = Modifier.weight(1f),
                                    factory = { ctx ->
                                        EditText(ctx).apply {
                                            hint = ctx.getString(R.string.map_search_hint)
                                            background = null
                                            isFocusableInTouchMode = true
                                            addTextChangedListener { text ->
                                                val q = text.toString()
                                                searchText = q
                                                viewModel.onSearchQueryChanged(q)
                                            }
                                            postDelayed({
                                                requestFocus()
                                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE)
                                                        as InputMethodManager
                                                imm.showSoftInput(this, InputMethodManager.SHOW_FORCED)
                                            }, 150)
                                        }
                                    },
                                    update = { editText ->
                                        if (editText.text.toString() != searchText) {
                                            editText.setText(searchText)
                                            editText.setSelection(searchText.length)
                                        }
                                    },
                                )
                                IconButton(onClick = {
                                    isSearching.value = false
                                    searchText = ""
                                    viewModel.clearSearch()
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.map_search_close_cd))
                                }
                            }
                        }

                        // ── Results dropdown ─────────────────────────────────
                        if (searchResults.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = false) {},
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 6.dp,
                            ) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 320.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp),
                                ) {
                                    itemsIndexed(searchResults, key = { _, p -> p.id }) { index, place ->
                                        val displayName = when (uiState.locale) {
                                            "es" -> place.nameEs.ifEmpty { place.name }
                                            "de" -> place.nameDe.ifEmpty { place.name }
                                            "ru" -> place.nameRu.ifEmpty { place.name }
                                            "zh" -> place.nameZh.ifEmpty { place.name }
                                            else -> place.name
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    isSearching.value = false
                                                    searchText = ""
                                                    viewModel.clearSearch()
                                                    viewModel.onMarkerTapped(place.id)
                                                    mapRef.value?.animateCamera(
                                                        CameraUpdateFactory.newCameraPosition(
                                                            CameraPosition.Builder()
                                                                .target(org.maplibre.android.geometry.LatLng(
                                                                    place.location.latitude,
                                                                    place.location.longitude,
                                                                ))
                                                                .zoom(14.0)
                                                                .build()
                                                        ),
                                                        600,
                                                    )
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Text(
                                                place.category.emoji,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    place.municipality,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (index < searchResults.lastIndex) {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BrandBadge(modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 12.dp))

        SettingsButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp))

        uiState.activeItinerary?.let { itin ->
            ItineraryRouteBanner(
                title = itin.title,
                stopCount = itin.places.size,
                onDismiss = { viewModel.setActiveItinerary(null) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (uiState.isOffline) 40.dp else 8.dp),
            )
        }

        ZoomControls(
            onZoomIn = { mapRef.value?.animateCamera(CameraUpdateFactory.zoomIn(), 200) },
            onZoomOut = { mapRef.value?.animateCamera(CameraUpdateFactory.zoomOut(), 200) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        )

        MyLocationButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) centerOnUserLocation(context, mapRef.value)
                else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 80.dp),
        )

        CategoryFilterBar(
            activeCategory = uiState.activeCategory,
            onCategorySelected = viewModel::onCategorySelected,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp),
        )

        // Mystery gem card (when tapping a hidden gem pin — no info revealed)
        uiState.selectedGem?.let { gem ->
            GemMysteryCard(
                municipality = gem.municipality,
                onDismiss = viewModel::onGemDismissed,
                onViewDetail = { onPlaceClick(gem.id) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp),
            )
        }

        // Selected place card (when tapping a normal map marker)
        uiState.selectedPlace?.let { place ->
            SelectedPlaceCard(
                place = place,
                locale = uiState.locale,
                onDismiss = viewModel::onBottomSheetDismissed,
                onViewDetail = { onPlaceClick(place.id) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 80.dp),
            )
        }
    }

    // Category places bottom sheet
    if (uiState.activeCategory != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onCategorySelected(uiState.activeCategory) },
            sheetState = categorySheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CategoryPlacesSheet(
                category = uiState.activeCategory!!,
                places = uiState.places,
                locale = uiState.locale,
                onPlaceClick = { placeId ->
                    viewModel.onCategorySelected(uiState.activeCategory)
                    onPlaceClick(placeId)
                },
            )
        }
    }
}

@Suppress("MissingPermission", "DEPRECATION")
private fun centerOnUserLocation(context: Context, map: MapLibreMap?) {
    map ?: return
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lm.removeUpdates(this)
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(location.latitude, location.longitude), 14.0
                    ), 800,
                )
            }
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider != null) {
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } else {
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstNotNullOfOrNull { lm.getLastKnownLocation(it) } ?: return
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    org.maplibre.android.geometry.LatLng(last.latitude, last.longitude), 14.0
                ), 800,
            )
        }
    } catch (_: Exception) {}
}

// ─── Composables ─────────────────────────────────────────────────────────────

@Composable
private fun GemMysteryCard(
    municipality: String,
    onDismiss: () -> Unit,
    onViewDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gemGold = Color(0xFFF9A825)
    val gemDark = Color(0xFF1A0030)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        color = gemDark,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D1B4E)),
                contentAlignment = Alignment.Center,
            ) {
                Text("💎", style = MaterialTheme.typography.headlineMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gema oculta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = gemGold,
                )
                if (municipality.isNotEmpty()) {
                    Text(
                        "📍 $municipality",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Text(
                    "Visítala para descubrirla",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onViewDetail, modifier = Modifier.padding(0.dp)) {
                    Text("Ir al lugar", style = MaterialTheme.typography.labelMedium, color = gemGold)
                }
            }
        }
    }
}

@Composable
private fun SelectedPlaceCard(
    place: Place,
    locale: String,
    onDismiss: () -> Unit,
    onViewDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = when (locale) {
        "es" -> place.nameEs.ifEmpty { place.name }
        "de" -> place.nameDe.ifEmpty { place.name }
        "ru" -> place.nameRu.ifEmpty { place.name }
        "zh" -> place.nameZh.ifEmpty { place.name }
        else -> place.name
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = place.thumbnailUrl.ifEmpty { null },
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${place.category.emoji} ${place.municipality}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                place.rating?.let {
                    Text(
                        "★ ${"%.1f".format(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.map_place_close_cd), modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onViewDetail, modifier = Modifier.padding(0.dp)) {
                    Text(stringResource(R.string.map_place_view_detail), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.width(40.dp), shape = RoundedCornerShape(8.dp), color = Color.White, shadowElevation = 4.dp) {
        Column {
            IconButton(onClick = onZoomIn, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Add, stringResource(R.string.map_zoom_in_cd), tint = Color(0xFF555555)) }
            HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
            IconButton(onClick = onZoomOut, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Remove, stringResource(R.string.map_zoom_out_cd), tint = Color(0xFF555555)) }
        }
    }
}

@Composable
private fun MyLocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(44.dp), shape = CircleShape, containerColor = Color.White, contentColor = Color(0xFF1A73E8)) {
        Icon(Icons.Outlined.MyLocation, stringResource(R.string.map_my_location_cd), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BrandBadge(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.92f), shadowElevation = 4.dp) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Azure), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Explore, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Mallorca Explorer", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                Text(stringResource(R.string.map_brand_subtitle), style = MaterialTheme.typography.labelSmall, color = Color(0xFF79747E))
            }
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(onClick = onClick, modifier = modifier.size(44.dp), shape = CircleShape, containerColor = Color.White, contentColor = Color(0xFF555555)) {
        Icon(Icons.Outlined.Settings, stringResource(R.string.map_settings_cd), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ItineraryRouteBanner(title: String, stopCount: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.padding(horizontal = 12.dp), shape = RoundedCornerShape(20.dp), color = Color(0xFF1565C0), shadowElevation = 4.dp) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🗺", style = MaterialTheme.typography.bodyMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text("$stopCount stops · ruta trazada", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CategoryPlacesSheet(
    category: Category,
    places: kotlinx.collections.immutable.ImmutableList<com.mallorca.explorer.core.domain.model.Place>,
    locale: String,
    onPlaceClick: (String) -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(category.emoji, style = MaterialTheme.typography.titleLarge)
            Column {
                Text(
                    category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${places.size} lugar${if (places.size != 1) "es" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (places.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.map_no_places_category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(places, key = { it.id }) { place ->
                    val displayName = when (locale) {
                        "es" -> place.nameEs.ifEmpty { place.name }
                        "de" -> place.nameDe.ifEmpty { place.name }
                        "ru" -> place.nameRu.ifEmpty { place.name }
                        "zh" -> place.nameZh.ifEmpty { place.name }
                        else -> place.name
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPlaceClick(place.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = place.thumbnailUrl.ifEmpty { null },
                                contentDescription = displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    place.municipality,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                place.rating?.let {
                                    Text(
                                        "★ ${"%.1f".format(it)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                "›",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterBar(activeCategory: Category?, onCategorySelected: (Category?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        items(Category.entries) { category ->
            FilterChip(
                selected = activeCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text("${category.emoji} ${category.displayName}") },
            )
        }
    }
}
