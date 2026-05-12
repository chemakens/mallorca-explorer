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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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

@Composable
fun ItineraryDetailScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSaveToTrip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItineraryDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.itinerary != null -> ItineraryDetailContent(
            itinerary = uiState.itinerary!!,
            onBack = onBack,
            onPlaceClick = onPlaceClick,
            onSaveToTrip = onSaveToTrip,
            modifier = modifier,
        )
    }
}

@Composable
private fun ItineraryDetailContent(
    itinerary: Itinerary,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onSaveToTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero section
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(itinerary.category.emoji, style = MaterialTheme.typography.displayLarge)
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
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
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

            Column(modifier = Modifier.padding(16.dp)) {
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

                // Stops by day
                val stopsByDay = itinerary.places.groupBy { it.dayNumber }
                stopsByDay.entries.sortedBy { it.key }.forEach { (day, stops) ->
                    Text("Day $day", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    stops.sortedBy { it.order }.forEachIndexed { idx, stop ->
                        TimelineItem(
                            stop = stop,
                            number = idx + 1,
                            isLast = idx == stops.lastIndex,
                            onClick = { onPlaceClick(stop.place.id) },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(72.dp))
            }
        }

        // FAB
        ExtendedFloatingActionButton(
            onClick = onSaveToTrip,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Outlined.BookmarkAdd, null) },
            text = { Text("Save to My Trips") },
        )
    }
}

@Composable
private fun TimelineItem(
    stop: ItineraryStop,
    number: Int,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(bottom = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
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
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    "${stop.place.category.emoji} ${stop.place.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
        }
    }
}
