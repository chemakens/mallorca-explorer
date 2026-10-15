package com.mallorca.explorer.feature.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import kotlinx.collections.immutable.persistentListOf
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
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.MarkerType
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.WaypointRole
import com.mallorca.explorer.core.domain.model.localizedName
import com.mallorca.explorer.core.domain.model.localizedLabel
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
import org.maplibre.android.style.sources.GeoJsonOptions
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
import androidx.annotation.DrawableRes


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

// Datos de un marcador de parada/punto para construir su feature en el mapa.
private data class MarkerData(
    val location: LatLng,
    val name: String,
    val order: Int,
    val role: WaypointRole,
    val type: com.mallorca.explorer.core.domain.model.WaypointType = com.mallorca.explorer.core.domain.model.WaypointType.OTHER,
)

// Capa de iconos (emoji) sobre los marcadores de senderismo: salida/llegada/giro/cima.
private const val STOPS_GLYPH_LAYER = "itinerary-stops-glyph"

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

// Category symbol icons (los mismos conceptos que la pantalla Explorar)
private fun categoryIconId(c: Category) = "cat-" + c.name.lowercase()

// marker_type symbol pins (CHURCH, MONUMENT, ARCHAEOLOGY, CASTLE, LIGHTHOUSE, MUSEUM)
// v78: marker_type ya NO cambia el icono del mapa (todos los lugares con marker_type usan el
// icono de CULTURE). Se conserva en Firestore, seed y modelo. createMarkerTypeBitmap/markerIconId
// y MARKER_LAYER_ID se mantienen aquí por si se quiere recuperar el dibujo por marker_type.
@Suppress("unused")
private const val MARKER_LAYER_ID       = "marker-type-symbol"
@Suppress("unused")
private fun markerIconId(type: MarkerType) = "mt-" + type.name.lowercase()

/**
 * Silueta blanca del símbolo de un marker_type, pensada para añadirse como imagen SDF
 * (style.addImage(id, bitmap, true)) y recolorearse en el mapa con el color de la categoría
 * del lugar (iconColor) y un borde blanco (iconHalo). Formas simples, legibles a poco zoom.
 */
@Suppress("unused")
private fun createMarkerTypeBitmap(type: MarkerType, size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = Paint.Style.FILL
    }
    val s = size.toFloat()
    val pad = s * 0.20f           // margen para que el borde/halo quepa
    val l = pad; val r = s - pad; val t = pad; val b = s - pad
    val w = r - l; val h = b - t; val cx = s / 2f
    val path = android.graphics.Path()
    when (type) {
        MarkerType.CHURCH -> {
            // nave + tejado triangular + cruz
            val bodyTop = t + h * 0.42f
            c.drawRect(l + w * 0.18f, bodyTop, r - w * 0.18f, b, white)
            path.moveTo(l + w * 0.10f, bodyTop)
            path.lineTo(cx, t + h * 0.18f)
            path.lineTo(r - w * 0.10f, bodyTop)
            path.close(); c.drawPath(path, white)
            val cw = w * 0.06f
            c.drawRect(cx - cw, t, cx + cw, t + h * 0.20f, white)       // cruz vertical
            c.drawRect(cx - cw * 2.4f, t + h * 0.05f, cx + cw * 2.4f, t + h * 0.11f, white) // cruz horiz
        }
        MarkerType.MONUMENT -> {
            // columna: capitel + fuste + base
            c.drawRect(l + w * 0.15f, t, r - w * 0.15f, t + h * 0.12f, white)   // capitel
            c.drawRect(l + w * 0.32f, t + h * 0.12f, r - w * 0.32f, b - h * 0.14f, white) // fuste
            c.drawRect(l + w * 0.10f, b - h * 0.14f, r - w * 0.10f, b, white)   // base
        }
        MarkerType.ARCHAEOLOGY -> {
            // talaiot: tronco de cono de piedra (trapecio) con dintel
            path.moveTo(l + w * 0.06f, b)
            path.lineTo(l + w * 0.26f, t + h * 0.10f)
            path.lineTo(r - w * 0.26f, t + h * 0.10f)
            path.lineTo(r - w * 0.06f, b)
            path.close(); c.drawPath(path, white)
        }
        MarkerType.CASTLE -> {
            // torre almenada
            val top = t + h * 0.30f
            c.drawRect(l + w * 0.14f, top, r - w * 0.14f, b, white)
            val merlonW = w * 0.72f / 5f
            var x = l + w * 0.14f
            for (i in 0 until 3) {
                c.drawRect(x, t + h * 0.14f, x + merlonW, top, white)
                x += merlonW * 2
            }
        }
        MarkerType.LIGHTHOUSE -> {
            // faro: torre troncocónica + linterna
            path.moveTo(l + w * 0.34f, t + h * 0.28f)
            path.lineTo(l + w * 0.28f, b)
            path.lineTo(r - w * 0.28f, b)
            path.lineTo(r - w * 0.34f, t + h * 0.28f)
            path.close(); c.drawPath(path, white)
            c.drawRect(l + w * 0.37f, t + h * 0.12f, r - w * 0.37f, t + h * 0.28f, white) // linterna
        }
        MarkerType.MUSEUM -> {
            // frontón sobre columnas
            path.moveTo(l, t + h * 0.34f)
            path.lineTo(cx, t + h * 0.08f)
            path.lineTo(r, t + h * 0.34f)
            path.close(); c.drawPath(path, white)       // frontón
            c.drawRect(l + w * 0.06f, t + h * 0.34f, r - w * 0.06f, t + h * 0.44f, white) // arquitrabe
            val colW = w * 0.10f
            for (i in 0 until 4) {
                val x = l + w * 0.10f + i * (w * 0.80f / 3f) - colW / 2f
                c.drawRect(x, t + h * 0.46f, x + colW, b - h * 0.06f, white)   // columnas
            }
            c.drawRect(l, b - h * 0.06f, r, b, white)   // base
        }
    }
    return bmp
}

// PNG ilustrado a color de cada categoría (los mismos dibujos de Gemini que la cuadrícula de
// Explorar), copiados a feature-map/res/drawable-nodpi. v80: el mapa usa estos dibujos.
@DrawableRes
private fun categoryDrawableRes(category: Category): Int = when (category) {
    Category.BEACH -> R.drawable.ic_cat_beach
    Category.HIKING -> R.drawable.ic_cat_hiking
    Category.CULTURE -> R.drawable.ic_cat_culture
    Category.GASTRONOMY -> R.drawable.ic_cat_gastronomy
    Category.TOWN -> R.drawable.ic_cat_town
    Category.VIEWPOINT -> R.drawable.ic_cat_viewpoint
    Category.SUP -> R.drawable.ic_cat_sup
}

/**
 * Marcador de mapa A COLOR (NO SDF): círculo blanco con sombra suave y borde de 3dp del color de
 * la categoría (Category.routeColor()), con el PNG ilustrado de Gemini centrado dentro (~70% del
 * círculo). El bitmap se crea a la densidad real del dispositivo (px físicos) y se añade con
 * addImage SIN sdf; así iconSize≈1 lo muestra nítido en pantallas de alta densidad (mismo enfoque
 * probado que el pin de gema). `diameterDp` = diámetro del círculo en dp.
 */
// Dibuja un emoji en un bitmap transparente para usarlo como icono de marcador de senderismo.
private fun createGlyphBitmap(emoji: String, context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (30f * density).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * density
        textAlign = Paint.Align.CENTER
    }
    val fm = paint.fontMetrics
    canvas.drawText(emoji, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
    return bmp
}

private fun createCategoryMarkerBitmap(category: Category, context: Context, diameterDp: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    val diameter = diameterDp * density
    val border = 3f * density
    val shadowPad = 4f * density
    val sizePx = (diameter + shadowPad * 2f).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = sizePx / 2f
    val r = diameter / 2f

    // Sombra suave (círculo negro difuminado, ligeramente desplazado hacia abajo)
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(density * 2.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    c.drawCircle(cx, cx + density * 1.2f, r - border, shadow)

    // Relleno blanco
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; style = Paint.Style.FILL
    }
    c.drawCircle(cx, cx, r - border / 2f, fill)

    // Borde del color de la categoría
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = border
        color = android.graphics.Color.parseColor(category.routeColor())
    }
    c.drawCircle(cx, cx, r - border / 2f, stroke)

    // PNG ilustrado centrado (~70% del diámetro), con filtrado para que no se vea dentado
    val png = android.graphics.BitmapFactory.decodeResource(context.resources, categoryDrawableRes(category))
    val target = (diameter * 0.70f).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(png, target, target, true)
    val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    c.drawBitmap(scaled, cx - target / 2f, cx - target / 2f, imgPaint)
    return bmp
}

/**
 * Faceted gem artwork (res/drawable-nodpi/gem_mystery_base.png) with a solid "?" overlaid —
 * mystery pin for hidden gems. The source PNG's transparent margin is trimmed so the gem's
 * ink fills the icon box the same way the previous circle did, at the same 32dp footprint.
 */
private fun createGemMysteryBitmap(w: Int, context: Context): Bitmap {
    val source = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.gem_mystery_base)

    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    var minX = source.width; var maxX = 0; var minY = source.height; var maxY = 0
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            if ((pixels[y * source.width + x] ushr 24) and 0xFF > 10) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    val trimmed = Bitmap.createBitmap(source, minX, minY, maxX - minX + 1, maxY - minY + 1)

    val bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = w / 2f

    // Scale so the gem's ink fills ~90% of the box — same visual weight as the original circle.
    val fillRatio = 0.90f
    val scale = (w * fillRatio) / maxOf(trimmed.width, trimmed.height).toFloat()
    val destW = (trimmed.width * scale).toInt()
    val destH = (trimmed.height * scale).toInt()
    val scaledGem = Bitmap.createScaledBitmap(trimmed, destW, destH, true)
    canvas.drawBitmap(scaledGem, r - destW / 2f, r - destH / 2f, Paint(Paint.ANTI_ALIAS_FLAG))

    // Solid black "?" directly on the artwork, no badge behind it
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0A0A0A")
        textSize = w * 0.38f
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
    waypointOrder: Int? = null,
    initialCategory: String? = null,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val categorySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchText by remember { mutableStateOf("") }
    val isSearching = remember { mutableStateOf(false) }
    LaunchedEffect(itineraryId) { viewModel.setActiveItinerary(itineraryId) }
    // Si se llega desde "Lugares en el camino", seleccionar ese punto (muestra su burbuja).
    LaunchedEffect(itineraryId, waypointOrder) { if (waypointOrder != null) viewModel.selectWaypoint(waypointOrder) }
    // Si se llega desde Explorar con una categoría (p. ej. Hiking), abrir el mapa ya filtrado
    // por esa categoría (misma hoja de rutas que al tocarla en el mapa).
    LaunchedEffect(initialCategory) {
        initialCategory?.let { name -> runCatching { Category.valueOf(name) }.getOrNull()?.let { viewModel.openCategory(it) } }
    }
    // El idioma lo gobierna la fuente única (LocaleSource, vía uiState.locale). NO lo
    // sobrescribimos con el idioma del sistema aquí: hacerlo pisaba el elegido en Ajustes.

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleReady = remember { mutableStateOf(false) }

    // Variante de trazado resaltada en la leyenda (null = todas al mismo nivel). Al cambiar de
    // itinerario activo se reinicia. Un segundo toque sobre el mismo chip vuelve a null.
    var highlightedVariantId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.activeItinerary?.id) { highlightedVariantId = null }

    val onPlaceClickState = rememberUpdatedState(onPlaceClick)
    val onItineraryClickState = rememberUpdatedState(onItineraryClick)

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
                            // Mismo grosor para todas las variantes. La opacidad va por feature
                            // ("opacity") para poder atenuar las variantes no resaltadas.
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineOpacity(Expression.get("opacity")),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    }
                )

                // ── Place pins (clustering) ──────────────────────────────────
                // Fuente con clustering: a zoom bajo los lugares se agrupan en círculos con
                // recuento; a partir de clusterMaxZoom se muestran los iconos sueltos.
                // v80: iconos más grandes → clusterMaxZoom 12 y clusterRadius 75 para que a zoom
                // medio sigan agrupados y los marcadores sueltos no se solapen.
                // Las gemas van en su propia fuente (GEMS_SOURCE_ID) y NO se agrupan aquí.
                style.addSource(
                    GeoJsonSource(
                        PLACES_SOURCE_ID,
                        FeatureCollection.fromFeatures(emptyList()),
                        GeoJsonOptions()
                            .withCluster(true)
                            .withClusterMaxZoom(12)   // por encima de este zoom, iconos sueltos
                            .withClusterRadius(75),
                    )
                )

                // Marcadores ilustrados A COLOR por categoría (círculo blanco + borde de color +
                // sombra + PNG de Gemini dentro). NO son SDF (no recoloreables). Base 44dp.
                Category.entries.forEach { cat ->
                    style.addImage(categoryIconId(cat), createCategoryMarkerBitmap(cat, context, 44f))
                }

                // Círculo de clúster (color neutro de la app, tamaño creciente por recuento)
                style.addLayer(
                    CircleLayer(CLUSTER_LAYER_ID, PLACES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.circleColor("#546E7A"),   // azul grisáceo neutro
                            PropertyFactory.circleOpacity(0.92f),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(1.5f),
                            PropertyFactory.circleRadius(
                                Expression.step(
                                    Expression.get("point_count"),
                                    Expression.literal(13f),
                                    Expression.literal(10), Expression.literal(17f),
                                    Expression.literal(25), Expression.literal(21f),
                                    Expression.literal(50), Expression.literal(26f),
                                    Expression.literal(100), Expression.literal(32f),
                                )
                            ),
                        )
                        setFilter(Expression.has("point_count"))
                    }
                )

                // Número de lugares dentro del clúster
                style.addLayer(
                    SymbolLayer(CLUSTER_COUNT_LAYER, PLACES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                            PropertyFactory.textSize(12f),
                            PropertyFactory.textColor("#FFFFFF"),
                            PropertyFactory.textIgnorePlacement(true),
                            PropertyFactory.textAllowOverlap(true),
                        )
                        setFilter(Expression.has("point_count"))
                    }
                )

                // Marcadores sueltos ilustrados a color. El icono (bitmap ya coloreado) se elige
                // por "icon"; los lugares con marker_type reciben el icono de CULTURE (ver
                // LaunchedEffect de uiState.places). Sin iconColor/halo: el color va en el bitmap.
                style.addLayer(
                    SymbolLayer(UNCLUSTERED_LAYER_ID, PLACES_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            // Colisión: evita apilamiento. Prioridad por valoración (sortKey menor = antes).
                            PropertyFactory.iconAllowOverlap(false),
                            PropertyFactory.iconIgnorePlacement(false),
                            PropertyFactory.symbolSortKey(Expression.get("sortKey")),
                            // Tamaño (base 44dp): ~44dp a zoom ≥13, ~34dp a zoom 11-12, nunca < ~0.7 (≈31dp).
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(9,  Expression.literal(0.70f)),
                                    Expression.stop(12, Expression.literal(0.77f)),
                                    Expression.stop(13, Expression.literal(1.0f)),
                                    Expression.stop(16, Expression.literal(1.0f)),
                                )
                            ),
                        )
                        setFilter(Expression.not(Expression.has("point_count")))
                    }
                )

                // Lugar seleccionado: dibuja el MISMO marcador a 1,2× encima de todo, con
                // allowOverlap=true, para que siempre se resalte y nunca lo oculte la colisión.
                style.addSource(GeoJsonSource(SELECTED_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                style.addLayer(
                    SymbolLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("icon")),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(9,  Expression.literal(0.84f)),
                                    Expression.stop(12, Expression.literal(0.92f)),
                                    Expression.stop(13, Expression.literal(1.2f)),
                                    Expression.stop(16, Expression.literal(1.2f)),
                                )
                            ),
                        )
                    }
                )
                // ── Hidden gem mystery pins ──────────────────────────────────
                style.addSource(GeoJsonSource(GEMS_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                // v80: base 44dp y misma curva de tamaño que los marcadores para que las gemas
                // no se vean ni más grandes ni más pequeñas que los nuevos iconos ilustrados.
                val gemW = (context.resources.displayMetrics.density * 44).toInt()
                style.addImage(GEM_ICON_ID, createGemMysteryBitmap(gemW, context))
                style.addLayer(
                    SymbolLayer(GEM_MYSTERY_LAYER_ID, GEMS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.iconImage(GEM_ICON_ID),
                            PropertyFactory.iconSize(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(9,  Expression.literal(0.70f)),
                                    Expression.stop(12, Expression.literal(0.77f)),
                                    Expression.stop(13, Expression.literal(1.0f)),
                                    Expression.stop(16, Expression.literal(1.0f)),
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
                // Iconos de senderismo (salida/llegada/giro/cima) encima de los círculos.
                mapOf(
                    "wp-start" to "🚩", "wp-meta" to "🏁", "wp-turn" to "↩️",
                    "wp-summit" to "⛰️", "wp-loop" to "🔁",
                ).forEach { (id, emoji) -> style.addImage(id, createGlyphBitmap(emoji, context)) }
                style.addLayer(
                    SymbolLayer(STOPS_GLYPH_LAYER, STOPS_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get("glyphIcon")),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconOpacity(Expression.get("opacity")),
                        )
                        setFilter(Expression.has("glyphIcon"))
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

                // 2. Cluster tap → acercar el mapa a esa zona (zoom de expansión del clúster)
                val clusterFeatures = map.queryRenderedFeatures(tapPoint, CLUSTER_LAYER_ID)
                if (clusterFeatures.isNotEmpty()) {
                    val cluster = clusterFeatures[0]
                    val source = map.style?.getSourceAs<GeoJsonSource>(PLACES_SOURCE_ID)
                    val geom = cluster.geometry()
                    if (source != null && geom is Point) {
                        val expansionZoom = source.getClusterExpansionZoom(cluster).toDouble()
                        map.easeCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                org.maplibre.android.geometry.LatLng(geom.latitude(), geom.longitude()),
                                expansionZoom,
                            ),
                            500,
                        )
                        return@addOnMapClickListener true
                    }
                }

                // 3. Individual place tap → show detail card (icono suelto por categoría)
                val placeFeatures = map.queryRenderedFeatures(tapPoint, UNCLUSTERED_LAYER_ID)
                if (placeFeatures.isNotEmpty()) {
                    val placeId = placeFeatures[0].getStringProperty("placeId")
                    if (placeId != null) {
                        viewModel.onMarkerTapped(placeId)
                        return@addOnMapClickListener true
                    }
                }

                // 4. Punto de ruta de senderismo → burbuja con su nombre y nota
                val wpFeatures = map.queryRenderedFeatures(tapPoint, STOPS_LAYER, STOPS_GLYPH_LAYER)
                if (wpFeatures.isNotEmpty()) {
                    val order = wpFeatures[0].getNumberProperty("waypointOrder")?.toInt()
                    if (order != null) {
                        viewModel.onWaypointTapped(order)
                        return@addOnMapClickListener true
                    }
                }

                // Nothing found → dismiss bottom sheet + burbuja
                viewModel.onBottomSheetDismissed()
                false
            }
        }
    }

    // Update itinerary stop markers + route line when itinerary changes.
    // Se vuelve a ejecutar también al resaltar/soltar una variante (highlightedVariantId).
    LaunchedEffect(styleReady.value, uiState.allItineraryRoutes, uiState.activeItinerary, highlightedVariantId) {
        val map = mapRef.value ?: return@LaunchedEffect
        if (!styleReady.value) return@LaunchedEffect

        val stopsSource = map.style?.getSourceAs<GeoJsonSource>(STOPS_SOURCE_ID) ?: return@LaunchedEffect
        val routeSource = map.style?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@LaunchedEffect

        val activeId = uiState.activeItinerary?.id

        // Stop markers - only show for the active itinerary
        val activeRoute = uiState.allItineraryRoutes.find { it.id == activeId }
        val variants = activeRoute?.variants ?: persistentListOf()
        val hasVariants = variants.size >= 2
        val highlighted = highlightedVariantId?.takeIf { id -> variants.any { it.id == id } }

        // Variante más cercana a un punto (por distancia mínima a sus vértices). Sirve para
        // atenuar un punto de interés junto a la variante a la que pertenece.
        fun nearestVariantId(p: LatLng): String? = variants.minByOrNull { v ->
            v.coords.minOfOrNull { c ->
                val dLat = c.latitude - p.latitude; val dLng = c.longitude - p.longitude
                dLat * dLat + dLng * dLng
            } ?: Double.MAX_VALUE
        }?.id

        val features = if (activeRoute != null) {
            // Prefer waypoints over coords for marker placement
            val routeType = uiState.activeItinerary?.routeType
            // Los iconos (bandera/cima/giro) son SOLO de senderismo; SUP conserva sus círculos.
            val glyphsForHiking = uiState.activeItinerary?.category == Category.HIKING
            val markersData = if (activeRoute.waypoints.isNotEmpty()) {
                activeRoute.waypoints.map { wp -> MarkerData(wp.location, wp.name, wp.order, wp.role, wp.type) }
            } else {
                activeRoute.coords.mapIndexed { index, coord ->
                    val role = when (index) {
                        0 -> WaypointRole.LAUNCH
                        activeRoute.coords.lastIndex -> WaypointRole.FINISH
                        else -> WaypointRole.WAYPOINT
                    }
                    MarkerData(coord, "", index + 1, role)
                }
            }

            markersData.map { (location, name, order, role, type) ->
                val isStart = role == WaypointRole.LAUNCH
                val isFinish = role == WaypointRole.FINISH
                val isSummit = type == com.mallorca.explorer.core.domain.model.WaypointType.SUMMIT
                // Icono de senderismo: cima manda; luego salida (circular = salida y llegada);
                // luego llegada (ida y vuelta = punto de giro, resto = meta). POI normal: sin icono.
                val glyphIcon = if (!glyphsForHiking) null else when {
                    isSummit -> "wp-summit"
                    isStart -> if (routeType == com.mallorca.explorer.core.domain.model.RouteType.CIRCULAR) "wp-loop" else "wp-start"
                    isFinish -> if (routeType == com.mallorca.explorer.core.domain.model.RouteType.OUT_AND_BACK) "wp-turn" else "wp-meta"
                    else -> null
                }
                // Salida (verde) y llegada (rojo) siempre visibles. Los POI toman el color de su
                // variante (si hay variantes) y se atenúan cuando otra variante está resaltada.
                val assignedVariant = if (hasVariants && !isStart && !isFinish && !isSummit) nearestVariantId(location) else null
                val markerColor = when {
                    isStart -> "#2E7D32"
                    isFinish -> "#C62828"
                    isSummit -> "#5D4037"
                    assignedVariant != null -> variants.first { it.id == assignedVariant }.color
                    else -> activeRoute.color
                }
                // Opacidad: salida/llegada siempre 1.0. POI: 1.0 salvo que haya una variante
                // resaltada y este punto pertenezca a otra → se atenúa.
                val markerOpacity = when {
                    isStart || isFinish || isSummit -> 1.0
                    highlighted == null -> 1.0
                    assignedVariant == highlighted -> 1.0
                    else -> 0.3
                }
                val big = isStart || isFinish || isSummit
                Feature.fromGeometry(
                    Point.fromLngLat(location.longitude, location.latitude),
                    JsonObject().apply {
                        addProperty("itineraryId", activeRoute.id)
                        addProperty("color", markerColor)
                        addProperty("radius", if (big) 9.0 else 7.0)
                        addProperty("haloRadius", if (big) 12.0 else 10.0)
                        addProperty("opacity", markerOpacity)
                        addProperty("shadowOpacity", if (markerOpacity < 1.0) 0.05 else 0.15)
                        addProperty("waypointName", name)
                        addProperty("waypointOrder", order)
                        if (glyphIcon != null) addProperty("glyphIcon", glyphIcon)
                    }
                )
            }
        } else {
            emptyList()
        }
        stopsSource.setGeoJson(FeatureCollection.fromFeatures(features))

        // Route line: con variantes dibuja una línea por variante (color propio + opacidad según
        // resaltado); sin variantes, la polilínea única de siempre.
        // drawLine=false (SUP sin track real) → no se pinta línea; los marcadores siguen.
        if (activeRoute != null && activeRoute.drawLine) {
            val lineFeatures = if (hasVariants) {
                variants.filter { it.coords.size >= 2 }.map { v ->
                    val opacity = when {
                        highlighted == null -> 0.85
                        v.id == highlighted -> 0.95
                        else -> 0.22
                    }
                    Feature.fromGeometry(
                        LineString.fromLngLats(v.coords.map { Point.fromLngLat(it.longitude, it.latitude) }),
                        JsonObject().apply {
                            addProperty("color", v.color)
                            addProperty("opacity", opacity)
                            addProperty("variantId", v.id)
                        }
                    )
                }
            } else if (activeRoute.coords.size >= 2) {
                listOf(
                    Feature.fromGeometry(
                        LineString.fromLngLats(activeRoute.coords.map { Point.fromLngLat(it.longitude, it.latitude) }),
                        JsonObject().apply {
                            addProperty("color", activeRoute.color)
                            addProperty("opacity", 0.85)
                        }
                    )
                )
            } else {
                emptyList()
            }
            routeSource.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
        } else {
            routeSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        // SUP → línea discontinua; resto de itinerarios → línea continua.
        (map.style?.getLayerAs<LineLayer>(ROUTE_LAYER_ID))?.setProperties(
            if (uiState.activeItinerary?.category == Category.SUP)
                PropertyFactory.lineDasharray(arrayOf(2f, 1.5f))
            else
                PropertyFactory.lineDasharray(arrayOf(1f, 0f)),
        )

        // Camera bounds: con variantes, encuadra la unión de todas; si no, la polilínea única.
        // Solo al cambiar de itinerario (no al resaltar una variante), para no mover la cámara.
        if (highlightedVariantId == null) {
            val boundCoords = if (hasVariants) variants.flatMap { it.coords } else activeRoute?.coords ?: emptyList()
            if (activeId != null && boundCoords.size >= 2) {
                val builder = LatLngBounds.Builder()
                boundCoords.forEach {
                    builder.include(org.maplibre.android.geometry.LatLng(it.latitude, it.longitude))
                }
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80), 1200)
            }
        }
    }

    // Al llegar desde "Lugares en el camino" (waypointOrder), centrar la cámara en ese punto.
    LaunchedEffect(styleReady.value, waypointOrder, uiState.activeItinerary?.id) {
        if (!styleReady.value || waypointOrder == null) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val wp = uiState.activeItinerary?.routeWaypoints?.firstOrNull { it.order == waypointOrder } ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(wp.lat, wp.lng), 15.0),
            700,
        )
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
            // Mismo icono que en la capa de lugares (marker_type → CULTURE), para resaltarlo a 1,2×.
            val iconKey = if (selected.markerType != null) {
                categoryIconId(Category.CULTURE)
            } else {
                categoryIconId(selected.category)
            }
            source.setGeoJson(FeatureCollection.fromFeatures(listOf(
                Feature.fromGeometry(
                    Point.fromLngLat(selected.location.longitude, selected.location.latitude),
                    JsonObject().apply { addProperty("icon", iconKey) }
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
            // Icono por categoría; los lugares con marker_type usan el icono de CULTURE (v78).
            val iconKey = if (place.markerType != null) {
                categoryIconId(Category.CULTURE)
            } else {
                categoryIconId(place.category)
            }
            Feature.fromGeometry(
                Point.fromLngLat(place.location.longitude, place.location.latitude),
                JsonObject().apply {
                    addProperty("placeId", place.id)
                    addProperty("icon", iconKey)
                    addProperty("color", place.category.routeColor())
                    // Prioridad de colisión: mayor valoración se dibuja primero (sortKey menor).
                    addProperty("sortKey", -(place.rating ?: 0f))
                    place.markerType?.let { addProperty("markerType", it.name) }
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
                    if (searchText.isEmpty()) stringResource(R.string.map_search_hint) else searchText,
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
                                        val displayName = place.localizedName(uiState.locale)
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
            // ¿Se dibuja línea para este itinerario? (SUP solo con track real; resto, sí.)
            val hasRouteLine = uiState.allItineraryRoutes
                .find { it.id == itin.id }
                ?.let { it.drawLine && it.coords.size >= 2 } ?: false
            // Punto representativo para "Abrir en mapas": salida del trazado o primera parada.
            val launch = itin.routePolylinePoints.firstOrNull()
                ?: itin.routeWaypoints.minByOrNull { it.order }
                    ?.let { com.mallorca.explorer.core.domain.model.LatLng(it.lat, it.lng) }
                ?: itin.places.minByOrNull { it.order }?.place?.location
            ItineraryRouteBanner(
                title = itin.title,
                stopCount = if (itin.routeWaypoints.isNotEmpty()) itin.routeWaypoints.size else itin.places.size,
                category = itin.category,
                hasRouteLine = hasRouteLine,
                // Sin línea no prometemos ruta: ocultamos "Abrir en mapas".
                onOpenInMaps = if (hasRouteLine) {
                    launch?.let { pt -> { openInMaps(context, pt.latitude, pt.longitude, itin.title) } }
                } else {
                    null
                },
                onDismiss = { viewModel.setActiveItinerary(null) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (uiState.isOffline) 40.dp else 8.dp),
            )

            // Leyenda de variantes (solo si la ruta tiene >=2): chips con color + etiqueta +
            // distancia + duración/dificultad. Toca un chip para resaltar su variante.
            val route = uiState.allItineraryRoutes.find { it.id == itin.id }
            if (route != null && route.variants.size >= 2) {
                VariantLegend(
                    variants = route.variants,
                    labels = route.variants.associate { v ->
                        v.id to (itin.routeVariants.find { it.id == v.id }?.localizedLabel(uiState.locale) ?: v.id)
                    },
                    locale = uiState.locale,
                    highlightedId = highlightedVariantId,
                    onToggle = { id -> highlightedVariantId = if (highlightedVariantId == id) null else id },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 92.dp),
                )
            }
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

        // Burbuja del punto de senderismo seleccionado (icono + nombre + nota localizada).
        uiState.selectedWaypointOrder?.let { order ->
            uiState.activeItinerary?.routeWaypoints?.firstOrNull { it.order == order }?.let { wp ->
                WaypointBubble(
                    waypoint = wp,
                    locale = uiState.locale,
                    onDismiss = viewModel::onWaypointDismissed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 80.dp),
                )
            }
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
                places = uiState.categorySheetPlaces,
                // SUP y senderismo listan las RUTAS en la hoja (en SUP los lugares sup-* están
                // ocultos; en senderismo, las fichas que tienen ruta se ocultan de la lista).
                routes = when (uiState.activeCategory) {
                    Category.SUP -> uiState.allItineraryRoutes.filter { it.category == Category.SUP }
                    // Senderismo: se ocultan los itinerarios antiguos solapados (datos intactos).
                    Category.HIKING -> uiState.allItineraryRoutes.filter {
                        it.category == Category.HIKING && it.id !in com.mallorca.explorer.core.domain.model.HIDDEN_HIKING_ITINERARY_IDS
                    }
                    else -> emptyList()
                },
                locale = uiState.locale,
                onPlaceClick = { placeId ->
                    viewModel.onCategorySelected(uiState.activeCategory)
                    onPlaceClick(placeId)
                },
                onRouteClick = { itineraryId ->
                    viewModel.onCategorySelected(uiState.activeCategory)
                    onItineraryClickState.value(itineraryId)
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
                    stringResource(R.string.map_gem_locked_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onViewDetail, modifier = Modifier.padding(0.dp)) {
                    Text(stringResource(R.string.map_go_to_place), style = MaterialTheme.typography.labelMedium, color = gemGold)
                }
            }
        }
    }
}

@Composable
private fun WaypointBubble(
    waypoint: com.mallorca.explorer.core.domain.model.RouteWaypoint,
    locale: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emoji = when (waypoint.type) {
        com.mallorca.explorer.core.domain.model.WaypointType.START -> "🚩"
        com.mallorca.explorer.core.domain.model.WaypointType.END -> "🏁"
        com.mallorca.explorer.core.domain.model.WaypointType.PARKING -> "🅿️"
        com.mallorca.explorer.core.domain.model.WaypointType.WATER -> "💧"
        com.mallorca.explorer.core.domain.model.WaypointType.VIEWPOINT -> "🔭"
        com.mallorca.explorer.core.domain.model.WaypointType.HERITAGE -> "🏛️"
        com.mallorca.explorer.core.domain.model.WaypointType.CAVE -> "🕳️"
        com.mallorca.explorer.core.domain.model.WaypointType.BEACH -> "🏖️"
        com.mallorca.explorer.core.domain.model.WaypointType.TECHNICAL -> "🧗"
        com.mallorca.explorer.core.domain.model.WaypointType.WARNING -> "⚠️"
        com.mallorca.explorer.core.domain.model.WaypointType.BUS -> "🚌"
        com.mallorca.explorer.core.domain.model.WaypointType.PHOTO -> "📸"
        com.mallorca.explorer.core.domain.model.WaypointType.SUMMIT -> "⛰️"
        com.mallorca.explorer.core.domain.model.WaypointType.OTHER -> "📍"
    }
    val name = when (locale) {
        "es" -> waypoint.nameEs.ifBlank { waypoint.name }
        "de" -> waypoint.nameDe.ifBlank { waypoint.name }
        else -> waypoint.name
    }
    val note = when (locale) {
        "es" -> waypoint.noteEs.ifBlank { waypoint.note }
        "de" -> waypoint.noteDe.ifBlank { waypoint.note }
        else -> waypoint.note
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.isNotBlank()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
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
    val displayName = place.localizedName(locale)
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

/** Abre el punto de salida de la ruta en una app de mapas externa (intent geo:, con respaldo web). Robusto: si no hay app, no falla. */
private fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
    val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
            )
        } catch (_: Exception) { /* sin app de mapas ni navegador: no hacer nada */ }
    }
}

@Composable
private fun VariantLegend(
    variants: kotlinx.collections.immutable.ImmutableList<RouteVariantUi>,
    labels: Map<String, String>,
    locale: String,
    highlightedId: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Más de tres variantes → la leyenda arranca plegada para no tapar el mapa.
    var expanded by remember(variants) { mutableStateOf(variants.size <= 3) }
    Surface(
        modifier = modifier.padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Cabecera con el título y, si hay >3, botón para plegar/desplegar.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (variants.size > 3) Modifier.clickable { expanded = !expanded } else Modifier,
            ) {
                Text(
                    stringResource(R.string.map_variants_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263238),
                    modifier = Modifier.weight(1f),
                )
                if (variants.size > 3) {
                    Text(
                        if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF607D8B),
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    variants.forEach { v ->
                        val label = labels[v.id] ?: v.id
                        val selected = highlightedId == v.id
                        val dimmed = highlightedId != null && !selected
                        val color = runCatching { Color(android.graphics.Color.parseColor(v.color)) }
                            .getOrDefault(Color(0xFF1565C0))
                        // Detalle: distancia + duración o dificultad (si existen).
                        val diffText = v.difficulty?.let {
                            when (it) {
                                com.mallorca.explorer.core.domain.model.Difficulty.EASY -> stringResource(R.string.map_variant_diff_easy)
                                com.mallorca.explorer.core.domain.model.Difficulty.MODERATE -> stringResource(R.string.map_variant_diff_moderate)
                                com.mallorca.explorer.core.domain.model.Difficulty.HARD -> stringResource(R.string.map_variant_diff_hard)
                            }
                        }
                        val detail = listOfNotNull(
                            v.distanceKm?.let { String.format(java.util.Locale(locale), "%.1f km", it) },
                            v.durationHoursText?.takeIf { it.isNotBlank() } ?: diffText,
                        ).joinToString(" · ")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) color.copy(alpha = 0.12f) else Color(0xFFF5F5F5),
                            border = androidx.compose.foundation.BorderStroke(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) color else Color(0x1A000000),
                            ),
                            modifier = Modifier.clickable { onToggle(v.id) }.alpha(if (dimmed) 0.55f else 1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Box(Modifier.size(11.dp).clip(CircleShape).background(color))
                                Column {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF263238),
                                        maxLines = 1,
                                    )
                                    if (detail.isNotBlank()) {
                                        Text(
                                            detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF607D8B),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryRouteBanner(
    title: String,
    stopCount: Int,
    category: Category,
    hasRouteLine: Boolean,
    onOpenInMaps: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Aviso de "ruta orientativa" SOLO cuando hay línea dibujada; sin línea no prometemos recorrido.
    val advisory = if (hasRouteLine) {
        when (category) {
            Category.SUP -> stringResource(R.string.map_route_advisory_sup)
            Category.HIKING -> stringResource(R.string.map_route_advisory_hiking)
            else -> null
        }
    } else {
        null
    }
    Surface(modifier = modifier.padding(horizontal = 12.dp), shape = RoundedCornerShape(20.dp), color = Color(0xFF1565C0), shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🗺", style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    // Con línea: "N paradas · ruta trazada". Sin línea: solo "N paradas".
                    val stopsText = when {
                        hasRouteLine && stopCount == 1 -> stringResource(R.string.map_route_stop_singular_banner)
                        hasRouteLine -> stringResource(R.string.map_route_stops_banner, stopCount)
                        stopCount == 1 -> stringResource(R.string.map_route_stop_singular_no_line)
                        else -> stringResource(R.string.map_route_stops_no_line, stopCount)
                    }
                    Text(stopsText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
                if (onOpenInMaps != null) {
                    TextButton(
                        onClick = onOpenInMaps,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(stringResource(R.string.map_route_open_in_maps), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White)
                }
            }
            if (advisory != null) {
                Text(
                    "⚠ $advisory",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                )
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
    routes: List<ItineraryRoute> = emptyList(),
    onRouteClick: (String) -> Unit = {},
) {
    // SUP y senderismo muestran tarjetas de ruta. En senderismo, además, pueden quedar fichas
    // de lugar sin ruta (se muestran debajo); las fichas que SÍ tienen ruta ya vienen filtradas.
    val showRoutes = category == Category.SUP || category == Category.HIKING
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
                val routesLabel = "${routes.size} ruta${if (routes.size != 1) "s" else ""}"
                val placesLabel = "${places.size} lugar${if (places.size != 1) "es" else ""}"
                val countLabel = when {
                    showRoutes && routes.isNotEmpty() && places.isNotEmpty() -> "$routesLabel · $placesLabel"
                    showRoutes && routes.isNotEmpty() -> routesLabel
                    else -> placesLabel
                }
                Text(
                    countLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (routes.isEmpty() && places.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.map_no_places_category), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Rutas (SUP y senderismo): tarjetas pequeñas con foto; al pulsar abren el detalle.
                items(routes, key = { "route-" + it.id }) { route ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onRouteClick(route.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = route.coverPhotoUrl.ifEmpty { null },
                                contentDescription = route.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    route.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${category.emoji} ${category.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Lugares sin ruta (p. ej. fichas de senderismo sin GPX). En SUP la lista va vacía.
                items(places, key = { "place-" + it.id }) { place ->
                    val displayName = place.localizedName(locale)
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
