package com.mallorca.explorer.feature.itinerary.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.ItineraryStop
import com.mallorca.explorer.core.domain.model.durationLabel
import com.mallorca.explorer.core.domain.model.localizedCaption
import com.mallorca.explorer.core.domain.model.localizedName
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.RouteType
import com.mallorca.explorer.core.domain.model.localizedAccessWarning
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.ui.component.SupConditionsCard
import com.mallorca.explorer.core.ui.component.labelRes
import com.mallorca.explorer.core.domain.model.attributionText
import androidx.compose.ui.res.stringResource
import com.mallorca.explorer.feature.itinerary.R
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

// The commercial block CTA has no real, non-placeholder partner data behind it yet
// (the only seeded entry uses a dummy phone number), so it's hidden for now.
// itinerary.commercialBlock is kept intact in seed_data.json for when this is reactivated.
private const val SHOW_COMMERCIAL_BLOCK = false

@Composable
fun ItineraryDetailScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSaveToTrip: () -> Unit,
    onViewOnMap: () -> Unit = {},
    onWaypointOnMap: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ItineraryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasLoggedView by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.itinerary) {
        if (uiState.itinerary != null && !hasLoggedView) {
            viewModel.onItineraryViewed()
            hasLoggedView = true
        }
    }

    val handleSaveToTrip = { viewModel.onSaveToTripClicked(); onSaveToTrip() }

    when {
        uiState.isLoading -> ItineraryLoadingSkeleton()
        uiState.itinerary != null -> {
            ItineraryDetailContent(
                itinerary = uiState.itinerary!!,
                visitedPlaceIds = uiState.visitedPlaceIds,
                supStatus = uiState.supStatus,
                airTempC = uiState.weather?.tempC,
                weatherIsStale = uiState.weatherIsStale,
                weatherLoadFailed = uiState.weatherLoadFailed,
                onStopToggle = viewModel::toggleStop,
                onBack = onBack,
                onPlaceClick = onPlaceClick,
                onSaveToTrip = handleSaveToTrip,
                onViewOnMap = onViewOnMap,
                onWaypointOnMap = onWaypointOnMap,
                modifier = modifier,
            )
            if (uiState.showQrWelcome) {
                QrWelcomeSheet(
                    routeName = uiState.itinerary?.qrEntryPoint?.sourceBeach.orEmpty(),
                    supStatus = uiState.supStatus,
                    onDismiss = viewModel::dismissQrWelcome,
                    onSaveRoute = { viewModel.dismissQrWelcome(); handleSaveToTrip() },
                )
            }
        }
        else -> {
            androidx.compose.foundation.layout.Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    Text("🗺️", style = MaterialTheme.typography.displayLarge)
                    Text(
                        stringResource(R.string.itinerary_not_found_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.itinerary_not_found_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onBack) { Text(stringResource(R.string.itinerary_back_to_map)) }
                }
            }
        }
    }
}

@Composable
private fun ItineraryLoadingSkeleton() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🏄", style = MaterialTheme.typography.displayLarge)
            Text(
                stringResource(R.string.itinerary_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.width(200.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrWelcomeSheet(
    routeName: String,
    supStatus: SUPWeatherStatus?,
    onDismiss: () -> Unit,
    onSaveRoute: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (routeName.isBlank()) "🏄 Ruta SUP" else "🏄 Ruta SUP · $routeName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            supStatus?.let { status ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(status.trafficLight.emoji, style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text(status.trafficLight.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(status.trafficLight.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                stringResource(R.string.itinerary_save_offline_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSaveRoute,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.itinerary_save_for_later))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.itinerary_not_now))
            }
        }
    }
}

@Composable
private fun ItineraryDetailContent(
    itinerary: Itinerary,
    visitedPlaceIds: ImmutableSet<String>,
    supStatus: SUPWeatherStatus? = null,
    airTempC: Float? = null,
    weatherIsStale: Boolean = false,
    weatherLoadFailed: Boolean = false,
    onStopToggle: (String) -> Unit,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSaveToTrip: () -> Unit,
    onViewOnMap: () -> Unit,
    onWaypointOnMap: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val photos = itinerary.galleryPhotos.ifEmpty {
        if (itinerary.coverPhoto.url.isNotEmpty()) listOf(itinerary.coverPhoto) else emptyList()
    }
    val pagerState = rememberPagerState { photos.size.coerceAtLeast(1) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Hero carousel (outside scroll so horizontal swipe works) ──
            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    if (photos.isNotEmpty()) {
                        // Mismo enfoque que la ficha de lugar: si la foto trae heroFocalY,
                        // se alinea el recorte vertical a ese punto (0=arriba, 1=abajo); si no, centro.
                        val heroFocalY = photos[page].heroFocalY
                        val heroAlignment = if (heroFocalY != null) {
                            BiasAlignment(horizontalBias = 0f, verticalBias = 2f * heroFocalY - 1f)
                        } else {
                            Alignment.Center
                        }
                        SubcomposeAsyncImage(
                            model = photos[page].url,
                            contentDescription = itinerary.title,
                            contentScale = ContentScale.Crop,
                            alignment = heroAlignment,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty ->
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(itinerary.category.emoji, style = MaterialTheme.typography.displayLarge) }
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Text(itinerary.category.emoji, style = MaterialTheme.typography.displayLarge) }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.2f), Color.Transparent, Color.Black.copy(0.5f)))),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
                        .clip(CircleShape).background(Color.Black.copy(0.4f)),
                ) {
                    Icon(Icons.Outlined.ArrowBack, stringResource(R.string.itinerary_back_cd), tint = Color.White)
                }
                // Pie de foto (es/en/de): chip arriba-centro, para no tapar el título (abajo),
                // los puntos del carrusel (abajo-centro), la atribución (abajo-dcha) ni el sujeto.
                photos.getOrNull(pagerState.currentPage)
                    ?.localizedCaption(com.mallorca.explorer.core.ui.locale.LocalAppLocale.current)
                    ?.let { caption ->
                        Text(
                            text = caption,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 14.dp, start = 56.dp, end = 56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                // Attribution overlay (AETIB requirement)
                photos.getOrNull(pagerState.currentPage)?.attributionText()?.let { attribution ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = attribution,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                // Dot indicators
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(photos.size) { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (pagerState.currentPage == i) Color.White else Color.White.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                ) {
                    // Ruta de senderismo con trazado: una sola vista, puntos de interés y dificultad localizada.
                    val isHikingRoute = itinerary.category == Category.HIKING && itinerary.routePolylinePoints.isNotEmpty()
                    Text(itinerary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("⏱ ${itinerary.durationLabel()}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        if (isHikingRoute) {
                            val poi = itinerary.routeWaypoints.count { it.name.isNotBlank() }
                            Text("📍 " + stringResource(R.string.itinerary_points_of_interest, poi), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        } else {
                            val stopCount = if (itinerary.routeWaypoints.isNotEmpty()) itinerary.routeWaypoints.size else itinerary.places.size
                            Text("📍 $stopCount stops", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        }
                        itinerary.difficulty?.let {
                            // SUP: sin icono, la dificultad como palabra. Senderismo: 🥾 + dificultad
                            // localizada (es/en/de). Resto: 🥾 + enum.
                            val diffText = when {
                                itinerary.category == Category.SUP -> stringResource(it.labelRes())
                                isHikingRoute -> "🥾 " + stringResource(it.labelRes())
                                else -> "🥾 ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}"
                            }
                            Text(diffText, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        }
                    }
                }
            }

            // ── Scrollable content ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // ── Senderismo: aviso de acceso (zona con permiso / cerrada) ──
                val localeLang = LocalConfiguration.current.locales[0].language
                // Ruta de senderismo con trazado: una sola vista (sin "Día 1" ni barra de progreso).
                val isHikingRoute = itinerary.category == Category.HIKING && itinerary.routePolylinePoints.isNotEmpty()
                itinerary.localizedAccessWarning(localeLang)?.let { warn ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️", style = MaterialTheme.typography.titleMedium)
                            Column {
                                Text(
                                    stringResource(R.string.itinerary_access_notice),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(warn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Senderismo: fila de datos clave (distancia · tipo · desnivel ↑/↓) ──
                if (itinerary.routeType != null || itinerary.ascentM != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        itinerary.totalDistanceKm?.let {
                            RouteFactItem(stringResource(R.string.itinerary_distance), "%.1f km".format(it))
                        }
                        itinerary.routeType?.let {
                            val rt = when (it) {
                                RouteType.CIRCULAR -> R.string.itinerary_route_type_loop
                                RouteType.LINEAR -> R.string.itinerary_route_type_oneway
                                RouteType.OUT_AND_BACK -> R.string.itinerary_route_type_outandback
                            }
                            RouteFactItem(stringResource(R.string.itinerary_route_type_label), stringResource(rt))
                        }
                        itinerary.ascentM?.let { RouteFactItem(stringResource(R.string.itinerary_ascent), "↑ $it m") }
                        itinerary.descentM?.let { RouteFactItem(stringResource(R.string.itinerary_descent), "↓ $it m") }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Botón "Cómo llegar al inicio/salida" (navegación al punto de salida) ──
                // Senderismo con trazado y rutas SUP: navega al waypoint LAUNCH (o 1er punto del
                // trazado). Si no hay ni LAUNCH ni polilínea, no se muestra.
                if (isHikingRoute || itinerary.isSUPRoute) {
                    val startWp = itinerary.routeWaypoints.firstOrNull { it.role == com.mallorca.explorer.core.domain.model.WaypointRole.LAUNCH }
                    val startLat = startWp?.lat ?: itinerary.routePolylinePoints.firstOrNull()?.latitude
                    val startLng = startWp?.lng ?: itinerary.routePolylinePoints.firstOrNull()?.longitude
                    if (startLat != null && startLng != null) {
                        val ctx = LocalContext.current
                        val startName = startWp?.let {
                            when (localeLang) { "es" -> it.nameEs.ifBlank { it.name }; "de" -> it.nameDe.ifBlank { it.name }; else -> it.name }
                        }
                        val startLabel = if (itinerary.isSUPRoute) R.string.itinerary_get_to_start_sup else R.string.itinerary_get_to_start
                        Button(
                            onClick = { openDirections(ctx, startLat, startLng) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(startLabel))
                        }
                        if (!startName.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "📍 $startName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── BLOQUE 1: Estado del mar SUP (tarjeta unificada) ──
                // Se muestra siempre que la ruta tenga weather_config (isSUPRoute), sin exigir
                // qr_entry_point. La hoja de bienvenida QR sigue dependiendo solo de isQrLanding.
                if (itinerary.isSUPRoute) {
                    val cfg = itinerary.weatherConfig
                    when {
                        supStatus != null -> {
                            val loc = LocalConfiguration.current.locales[0].language
                            val tramuntana = cfg?.let {
                                when (loc) {
                                    "de" -> it.tramuntanaNoteDe.ifBlank { it.tramuntanaNoteEs.ifBlank { it.tramuntanaNote } }
                                    "es" -> it.tramuntanaNoteEs.ifBlank { it.tramuntanaNote }
                                    else -> it.tramuntanaNote.ifBlank { it.tramuntanaNoteEs }
                                }
                            }
                            SupConditionsCard(
                                status = supStatus,
                                airTempC = airTempC,
                                isStale = weatherIsStale,
                                tramuntanaText = tramuntana,
                                caveEntryMaxWaveM = cfg?.caveEntryMaxWaveM,
                                windMapLat = cfg?.fetchLat,
                                windMapLng = cfg?.fetchLng,
                            )
                        }
                        weatherLoadFailed -> OfflineWeatherWarning()
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Tags/badges
                if (itinerary.bestSeasons.isNotEmpty() || itinerary.tags.isNotEmpty()) {
                    val locale = LocalConfiguration.current.locales[0].language
                    val translateSeason: (String) -> String = { season ->
                        when (locale) {
                            "es" -> when (season) {
                                "Spring" -> "Primavera"
                                "Summer" -> "Verano"
                                "Autumn" -> "Otoño"
                                "Winter" -> "Invierno"
                                else -> season
                            }
                            "de" -> when (season) {
                                "Spring" -> "Frühling"
                                "Summer" -> "Sommer"
                                "Autumn" -> "Herbst"
                                "Winter" -> "Winter"
                                else -> season
                            }
                            else -> season // EN por defecto
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itinerary.bestSeasons.forEach { season -> 
                            SuggestionChip(onClick = {}, label = { Text(translateSeason(season), style = MaterialTheme.typography.labelSmall) }) 
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Description
                if (itinerary.description.isNotEmpty()) {
                    Text(itinerary.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                }

                // En rutas SUP no se muestra "paso a paso": ni barra de progreso, ni waypoints
                // (BLOQUE 2), ni paradas por día (BLOQUE 3). Los waypoints se conservan en los
                // datos y en el mapa (marcadores + línea). Más adelante se añadirán puntos
                // importantes desde el KML. Senderismo y demás categorías no cambian.
                val isSup = itinerary.category == Category.SUP

                // Progress bar
                val totalStops = itinerary.places.size
                val visitedCount = visitedPlaceIds.size
                if (totalStops > 0 && !isSup && !isHikingRoute) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { visitedCount.toFloat() / totalStops },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Text(
                            "$visitedCount/$totalStops",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── BLOQUE 2: Timeline de puntos (oculto en SUP; ver nota arriba) ──
                // Senderismo: sección "Lugares en el camino" solo con los puntos que tienen
                // descripción (los puntos sin nota —Inicio, Fuente, etc.— quedan solo como
                // iconos en el mapa). Resto de categorías: comportamiento previo ("Ruta paso a paso").
                val isHiking = itinerary.category == Category.HIKING
                val timelineWaypoints = if (isHiking) {
                    itinerary.routeWaypoints.filter {
                        it.note.isNotBlank() || it.noteEs.isNotBlank() || it.noteDe.isNotBlank()
                    }
                } else {
                    itinerary.routeWaypoints
                }
                if (!isSup && timelineWaypoints.any { it.name.isNotBlank() }) {
                    val header = if (isHiking) R.string.itinerary_places_along_way else R.string.itinerary_step_by_step
                    Text(stringResource(header), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    // Senderismo: al pulsar un punto se abre el mapa centrado en él.
                    SupRouteTimeline(
                        waypoints = timelineWaypoints,
                        onWaypointClick = if (isHikingRoute) onWaypointOnMap else null,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // Stops by day (oculto en SUP y en rutas de senderismo con trazado)
                val stopsByDay = if (isSup || isHikingRoute) emptyMap<Int, List<ItineraryStop>>() else itinerary.places.groupBy { it.dayNumber }
                stopsByDay.entries.sortedBy { it.key }.forEach { (day, stops) ->
                    Text(stringResource(R.string.itinerary_day, day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    stops.sortedBy { it.order }.forEachIndexed { idx, stop ->
                        TimelineItem(
                            stop = stop,
                            number = idx + 1,
                            isLast = idx == stops.lastIndex,
                            isVisited = stop.place.id in visitedPlaceIds,
                            onToggleVisited = { onStopToggle(stop.place.id) },
                            onClick = { onPlaceClick(stop.place.id) },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── BLOQUE 3: CTA comercial (alquiler SUP) ──
                if (SHOW_COMMERCIAL_BLOCK) {
                    itinerary.commercialBlock?.let { block ->
                        Spacer(Modifier.height(8.dp))
                        SupCommercialBlock(block = block)
                    }
                }

                // Senderismo: enlace discreto a la ficha de lugar (la ruta conserva su ficha).
                if (isHikingRoute) {
                    itinerary.places.firstOrNull()?.place?.id?.let { placeId ->
                        TextButton(onClick = { onPlaceClick(placeId) }) {
                            Text(stringResource(R.string.itinerary_view_place))
                        }
                    }
                }

                Spacer(Modifier.height(72.dp))
            }
        }

        // FABs
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = onViewOnMap,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = { Icon(Icons.Outlined.Map, null) },
                text = { Text(stringResource(R.string.itinerary_view_on_map)) },
            )
            ExtendedFloatingActionButton(
                onClick = onSaveToTrip,
                icon = { Icon(Icons.Outlined.BookmarkAdd, null) },
                text = { Text(stringResource(R.string.itinerary_save_to_trips)) },
            )
        }
    }
}

@Composable
private fun TimelineItem(
    stop: ItineraryStop,
    number: Int,
    isLast: Boolean,
    isVisited: Boolean,
    onToggleVisited: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(bottom = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(if (isVisited) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (!isLast) {
                Spacer(modifier = Modifier.width(2.dp).height(16.dp).background(MaterialTheme.colorScheme.outline))
            }
        }
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(1.dp),
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${stop.place.category.emoji} ${stop.place.localizedName(com.mallorca.explorer.core.ui.locale.LocalAppLocale.current)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isVisited) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "⏱ ~${stop.suggestedDurationMinutes / 60}h ${stop.suggestedDurationMinutes % 60}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stop.note?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onToggleVisited, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isVisited) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.itinerary_toggle_visited_cd),
                        tint = if (isVisited) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteFactItem(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun openDirections(context: android.content.Context, lat: Double, lng: Double) {
    // Navegación turn-by-turn si hay app de mapas; si no, la web de Google Maps.
    try {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=$lat,$lng")))
    } catch (e: android.content.ActivityNotFoundException) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")))
        } catch (_: Exception) { /* sin app de mapas ni navegador */ }
    }
}
