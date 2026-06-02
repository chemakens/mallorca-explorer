package com.mallorca.explorer.feature.trips.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"
private const val STOPS_SOURCE  = "trip-stops"
private const val STOPS_LAYER   = "trip-stops-icons"
private const val ROUTE_SOURCE  = "trip-route"
private const val ROUTE_LAYER   = "trip-route-line"

// Marker geometry (pixels, designed for ~2x screens)
private const val MARKER_SIZE   = 120   // total canvas
private const val M_CX          = 60f   // centre X/Y
private const val M_CIRCLE_R    = 46f   // photo circle radius
private const val M_BORDER_W    = 7f    // border ring width
private const val M_BADGE_R     = 20f   // number badge radius
private const val M_BADGE_CX    = 90f   // badge centre X (bottom-right overlap)
private const val M_BADGE_CY    = 90f   // badge centre Y

/**
 * Creates a circular photo marker with a numbered badge at the bottom-right.
 * Falls back to a solid-colour circle when the URL is blank or the load fails.
 */
private suspend fun makeStopMarker(
    context: Context,
    thumbnailUrl: String,
    number: Int,
    primaryColor: Int,
    onPrimaryColor: Int,
): Bitmap {
    val bmp = Bitmap.createBitmap(MARKER_SIZE, MARKER_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Photo circle ──────────────────────────────────────────────────────────
    var photoLoaded = false
    if (thumbnailUrl.isNotBlank()) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(MARKER_SIZE, MARKER_SIZE)
                .allowHardware(false)
                .build()
        )
        if (result is SuccessResult) {
            val photoBmp = Bitmap.createBitmap(MARKER_SIZE, MARKER_SIZE, Bitmap.Config.ARGB_8888)
            val pCanvas = Canvas(photoBmp)
            result.drawable.setBounds(0, 0, MARKER_SIZE, MARKER_SIZE)
            result.drawable.draw(pCanvas)

            val shader = BitmapShader(photoBmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            paint.shader = shader
            canvas.drawCircle(M_CX, M_CX, M_CIRCLE_R, paint)
            paint.shader = null
            photoBmp.recycle()
            photoLoaded = true
        }
    }
    if (!photoLoaded) {
        // Solid primary-colour fill as fallback
        paint.color = primaryColor
        paint.alpha = 90
        canvas.drawCircle(M_CX, M_CX, M_CIRCLE_R, paint)
        paint.alpha = 255
    }

    // ── Border ring ───────────────────────────────────────────────────────────
    paint.shader = null
    paint.color = primaryColor
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = M_BORDER_W
    canvas.drawCircle(M_CX, M_CX, M_CIRCLE_R, paint)
    paint.style = Paint.Style.FILL

    // ── Number badge ─────────────────────────────────────────────────────────
    paint.color = primaryColor
    canvas.drawCircle(M_BADGE_CX, M_BADGE_CY, M_BADGE_R, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    canvas.drawCircle(M_BADGE_CX, M_BADGE_CY, M_BADGE_R, paint)
    paint.style = Paint.Style.FILL

    val label = "$number"
    paint.color = onPrimaryColor
    paint.textSize = if (number < 10) 20f else 15f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    val bounds = Rect()
    paint.getTextBounds(label, 0, label.length, bounds)
    canvas.drawText(label, M_BADGE_CX, M_BADGE_CY + bounds.height() / 2f, paint)

    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TripMapViewModel = hiltViewModel(),
) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val stops = trip?.stops ?: emptyList()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val primaryColorInt   = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColorInt = MaterialTheme.colorScheme.onPrimary.toArgb()

    val mapRef   = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }

    val scaffoldState = rememberBottomSheetScaffoldState()

    val mapView = remember {
        MapLibre.getInstance(context, "", WellKnownTileServer.MapLibre)
        MapView(context).apply { onCreate(null) }
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
                isZoomGesturesEnabled    = true
                isScrollGesturesEnabled  = true
                isTiltGesturesEnabled    = true
                isRotateGesturesEnabled  = true
                isCompassEnabled         = false
                isLogoEnabled            = false
                isAttributionEnabled     = false
            }
            map.setStyle(MAP_STYLE_URL) { style ->
                styleRef.value = style

                // Route line — dashed
                style.addSource(GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(LineLayer(ROUTE_LAYER, ROUTE_SOURCE).apply {
                    setProperties(
                        PropertyFactory.lineColor(Expression.get("lineColor")),
                        PropertyFactory.lineWidth(3.5f),
                        PropertyFactory.lineOpacity(0.85f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineDasharray(arrayOf(0f, 2.5f)),
                    )
                })

                // Stop icons
                style.addSource(GeoJsonSource(STOPS_SOURCE, FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(SymbolLayer(STOPS_LAYER, STOPS_SOURCE).apply {
                    setProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                        PropertyFactory.iconSize(1f),
                    )
                })
            }
        }
    }

    // Reload markers and camera whenever trip data or map style becomes ready
    LaunchedEffect(trip, styleRef.value) {
        val t       = trip ?: return@LaunchedEffect
        val style   = styleRef.value ?: return@LaunchedEffect
        val map     = mapRef.value ?: return@LaunchedEffect
        val stopsNow = t.stops
        if (stopsNow.isEmpty()) return@LaunchedEffect

        // Build / cache marker bitmaps (loads photos from network)
        stopsNow.forEachIndexed { idx, stop ->
            val iconId = "stop-${idx + 1}"
            if (style.getImage(iconId) == null) {
                val bmp = makeStopMarker(context, stop.place.thumbnailUrl, idx + 1, primaryColorInt, onPrimaryColorInt)
                style.addImage(iconId, bmp)
            }
        }

        // Update stops GeoJson
        val features = stopsNow.mapIndexed { idx, stop ->
            Feature.fromGeometry(
                Point.fromLngLat(stop.place.location.longitude, stop.place.location.latitude)
            ).apply {
                addStringProperty("icon", "stop-${idx + 1}")
                addStringProperty("placeId", stop.place.id)
            }
        }
        (style.getSource(STOPS_SOURCE) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))

        // Update route GeoJson
        val coords = stopsNow.map { Point.fromLngLat(it.place.location.longitude, it.place.location.latitude) }
        val routeFeature = Feature.fromGeometry(LineString.fromLngLats(coords)).apply {
            addStringProperty("lineColor", String.format("#%06X", 0xFFFFFF and primaryColorInt))
        }
        (style.getSource(ROUTE_SOURCE) as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(routeFeature)))

        // Fit bounds — extra bottom padding for bottom sheet
        if (stopsNow.size == 1) {
            val s = stopsNow[0]
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(s.place.location.latitude, s.place.location.longitude), 14.0,
                )
            )
        } else {
            val builder = LatLngBounds.Builder()
            stopsNow.forEach { s ->
                builder.include(LatLng(s.place.location.latitude, s.place.location.longitude))
            }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80, 80, 80, 300))
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 80.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContent = {
            StopsBottomSheet(
                stops = stops,
                onStopClick = { stop ->
                    mapRef.value?.animateCamera(
                        CameraUpdateFactory.newLatLng(
                            LatLng(stop.place.location.latitude, stop.place.location.longitude)
                        )
                    )
                    coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() }
                },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        trip?.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            )

            // ── Zoom buttons ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 12.dp,
                        bottom = padding.calculateBottomPadding() + 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ZoomButton(icon = Icons.Outlined.Add, description = "Acercar") {
                    mapRef.value?.animateCamera(CameraUpdateFactory.zoomIn(), 180)
                }
                ZoomButton(icon = Icons.Outlined.Remove, description = "Alejar") {
                    mapRef.value?.animateCamera(CameraUpdateFactory.zoomOut(), 180)
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StopsBottomSheet(
    stops: List<com.mallorca.explorer.core.domain.model.UserTripStop>,
    onStopClick: (com.mallorca.explorer.core.domain.model.UserTripStop) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Paradas del viaje",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (stops.isNotEmpty()) {
                Text(
                    "${stops.size} ${if (stops.size == 1) "parada" else "paradas"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        if (stops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sin paradas todavía",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(stops) { idx, stop ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStopClick(stop) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stop.place.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${stop.place.category.emoji} ${stop.place.municipality}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (idx < stops.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 62.dp, end = 20.dp),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}
