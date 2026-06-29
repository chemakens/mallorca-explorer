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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.ItineraryStop
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.attributionText
import androidx.compose.ui.res.stringResource
import com.mallorca.explorer.feature.itinerary.R
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
fun ItineraryDetailScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSaveToTrip: () -> Unit,
    onViewOnMap: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ItineraryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> ItineraryLoadingSkeleton()
        uiState.itinerary != null -> {
            ItineraryDetailContent(
                itinerary = uiState.itinerary!!,
                visitedPlaceIds = uiState.visitedPlaceIds,
                supStatus = uiState.supStatus,
                weatherIsStale = uiState.weatherIsStale,
                weatherLoadFailed = uiState.weatherLoadFailed,
                onStopToggle = viewModel::toggleStop,
                onBack = onBack,
                onPlaceClick = onPlaceClick,
                onSaveToTrip = onSaveToTrip,
                onViewOnMap = onViewOnMap,
                modifier = modifier,
            )
            if (uiState.showQrWelcome) {
                QrWelcomeSheet(
                    supStatus = uiState.supStatus,
                    onDismiss = viewModel::dismissQrWelcome,
                    onSaveRoute = { viewModel.dismissQrWelcome(); onSaveToTrip() },
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
                "🏄 Ruta SUP · Cala Sant Vicenç",
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
                "Guarda esta ruta para consultarla sin conexión o volver mañana.",
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
    weatherIsStale: Boolean = false,
    weatherLoadFailed: Boolean = false,
    onStopToggle: (String) -> Unit,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSaveToTrip: () -> Unit,
    onViewOnMap: () -> Unit,
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
                        SubcomposeAsyncImage(
                            model = photos[page].url,
                            contentDescription = itinerary.title,
                            contentScale = ContentScale.Crop,
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
                    Text(itinerary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("⏱ ${itinerary.durationDays}d", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        Text("📍 ${itinerary.places.size} stops", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f))
                        itinerary.difficulty?.let { Text("🥾 ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f)) }
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
                // ── BLOQUE 1: Alerta climática SUP (solo si es ruta QR con weather_config) ──
                if (itinerary.isQrLanding) {
                    when {
                        supStatus != null -> SupWeatherAlertCard(
                            supStatus = supStatus,
                            tramuntanaNoteEs = itinerary.weatherConfig?.tramuntanaNoteEs ?: "",
                            caveEntryMaxWaveM = itinerary.weatherConfig?.caveEntryMaxWaveM,
                            isStale = weatherIsStale,
                        )
                        weatherLoadFailed -> OfflineWeatherWarning()
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Tags/badges
                if (itinerary.bestSeasons.isNotEmpty() || itinerary.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itinerary.bestSeasons.forEach { SuggestionChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Description
                if (itinerary.description.isNotEmpty()) {
                    Text(itinerary.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                }

                // Progress bar
                val totalStops = itinerary.places.size
                val visitedCount = visitedPlaceIds.size
                if (totalStops > 0) {
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

                // ── BLOQUE 2: Timeline de ruta SUP (reemplaza stops genéricos si hay waypoints) ──
                if (itinerary.routeWaypoints.any { it.name.isNotBlank() }) {
                    Text(stringResource(R.string.itinerary_step_by_step), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    SupRouteTimeline(waypoints = itinerary.routeWaypoints)
                    Spacer(Modifier.height(16.dp))
                }

                // Stops by day
                val stopsByDay = itinerary.places.groupBy { it.dayNumber }
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
                itinerary.commercialBlock?.let { block ->
                    Spacer(Modifier.height(8.dp))
                    SupCommercialBlock(block = block)
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
                        "${stop.place.category.emoji} ${stop.place.name}",
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
