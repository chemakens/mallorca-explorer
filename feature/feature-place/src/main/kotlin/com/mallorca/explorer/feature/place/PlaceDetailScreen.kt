package com.mallorca.explorer.feature.place

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mallorca.explorer.core.domain.model.Place

@Composable
fun PlaceDetailScreen(
    placeId: String,
    onBack: () -> Unit,
    onAddToTrip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.place != null -> PlaceDetailContent(
            place = uiState.place!!,
            onBack = onBack,
            onFavoriteToggle = viewModel::onFavoriteToggled,
            onAddToTrip = onAddToTrip,
            modifier = modifier,
        )
    }
}

@Composable
private fun PlaceDetailContent(
    place: Place,
    onBack: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Photo header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) {
            if (place.photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = place.photoUrls.first(),
                    contentDescription = place.name,
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
                    Text(place.category.emoji, style = MaterialTheme.typography.displayLarge)
                }
            }
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        listOf(Color.Black.copy(0.3f), Color.Transparent, Color.Transparent)
                    ))
            )
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.4f)),
            ) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
            }
            // Favorite button
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.4f)),
            ) {
                Icon(
                    if (place.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    "Favorite",
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            // Title row
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("📍 ${place.municipality}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                place.priceLevel?.let {
                    androidx.compose.material3.Badge(
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(it.name) }
                }
            }

            // Rating
            place.rating?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★★★★★".take((it + 0.5f).toInt().coerceIn(0, 5)), color = Color(0xFFF9A825))
                    Text(
                        " ${"%.1f".format(it)} (${place.reviewCount} reviews)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Description
            if (place.description.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(place.description, style = MaterialTheme.typography.bodyMedium)
            }

            // Info card
            if (place.address != null || place.tips.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        place.address?.let {
                            Row(verticalAlignment = Alignment.Top) {
                                Text("📍", modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text("Address", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (place.tips.isNotEmpty()) Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        place.tips.forEachIndexed { i, tip ->
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Row {
                                Text("💡", modifier = Modifier.size(20.dp))
                                Text(tip, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            // Directions button
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val uri = Uri.parse("geo:${place.location.latitude},${place.location.longitude}?q=${Uri.encode(place.name)}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Directions, null)
                Spacer(Modifier.size(8.dp))
                Text("Get Directions")
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAddToTrip,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) {
                Text("+ Add to Trip")
            }
        }
    }
}

private fun Color(value: Long) = Color(value.toULong())
