package com.mallorca.explorer.feature.itinerary.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.WorkspacePremium
import com.mallorca.explorer.core.ui.component.PlaceCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mallorca.explorer.core.domain.model.Itinerary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryListScreen(
    category: String?,
    onItineraryClick: (String) -> Unit,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ItineraryListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.category?.displayName ?: "All Itineraries") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Itineraries section
            if (uiState.itineraries.isNotEmpty()) {
                if (uiState.places.isNotEmpty()) {
                    item {
                        Text(
                            "Rutas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                items(uiState.itineraries, key = { it.id }) { itin ->
                    ItineraryListItem(itinerary = itin, onClick = { onItineraryClick(itin.id) })
                }
            }

            // Places section
            if (uiState.places.isNotEmpty()) {
                item {
                    Text(
                        "Lugares",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = if (uiState.itineraries.isNotEmpty()) 8.dp else 0.dp, bottom = 4.dp),
                    )
                }
                items(uiState.places, key = { it.id }) { place ->
                    PlaceCard(place = place, onClick = { onPlaceClick(place.id) })
                }
            }
        }
    }
}

@Composable
private fun ItineraryListItem(
    itinerary: Itinerary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPremium = itinerary.tags.any { it == "luxury" || it == "premium" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box {
            // Hero image — full bleed
            if (itinerary.coverPhotoUrl.isNotEmpty()) {
                AsyncImage(
                    model = itinerary.coverPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(itinerary.category.emoji, style = MaterialTheme.typography.displayLarge)
                }
            }

            // Gradient scrim — transparent top → dark bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.15f),
                                1f to Color.Black.copy(alpha = 0.80f),
                            )
                        )
                    )
            )

            // Premium badge
            if (isPremium) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFFD700))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFF5C3D00),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        "PREMIUM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF5C3D00),
                    )
                }
            }

            // Text overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    itinerary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "⏱ ${itinerary.durationDays}d",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Text(
                        "📍 ${itinerary.places.size} stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    itinerary.difficulty?.let {
                        Text(
                            "🥾 ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }
    }
}
