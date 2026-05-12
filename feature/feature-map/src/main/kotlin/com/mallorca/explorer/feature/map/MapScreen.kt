package com.mallorca.explorer.feature.map

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.ui.component.OfflineBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (uiState.selectedPlace != null) SheetValue.PartiallyExpanded else SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    LaunchedEffect(uiState.selectedPlace) {
        if (uiState.selectedPlace != null) sheetState.partialExpand()
        else sheetState.hide()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 160.dp,
        sheetContent = {
            uiState.selectedPlace?.let { place ->
                PlaceBottomSheet(
                    place = place,
                    onViewDetails = { onPlaceClick(place.id) },
                    onFavoriteClick = { viewModel.onFavoriteToggled(place.id) },
                )
            }
        },
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Map view using AndroidView for MapLibre
            MallorcaMapView(
                places = uiState.places,
                activeCategory = uiState.activeCategory,
                onMarkerClick = { viewModel.onMarkerTapped(it) },
                modifier = Modifier.fillMaxSize(),
            )

            OfflineBanner(
                isOffline = uiState.isOffline,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Category filter chips
            CategoryFilterBar(
                activeCategory = uiState.activeCategory,
                onCategorySelected = viewModel::onCategorySelected,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = if (uiState.selectedPlace != null) 180.dp else 16.dp),
            )
        }
    }
}

@Composable
private fun MallorcaMapView(
    places: kotlinx.collections.immutable.ImmutableList<Place>,
    activeCategory: Category?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // MapLibre integration via AndroidView
    AndroidView(
        factory = { ctx ->
            // In a real build, create MapLibreMap here
            FrameLayout(ctx).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#C8DFED"))
            }
        },
        update = { /* Update markers when places change */ },
        modifier = modifier,
    )
}

@Composable
private fun CategoryFilterBar(
    activeCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(Category.entries) { category ->
            FilterChip(
                selected = activeCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text("${category.emoji} ${category.displayName}") },
            )
        }
    }
}

@Composable
private fun PlaceBottomSheet(
    place: Place,
    onViewDetails: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = place.thumbnailUrl.ifEmpty { null },
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${place.category.emoji} ${place.category.displayName} · ${place.municipality}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                place.rating?.let {
                    Text("★ ${"%.1f".format(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (place.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (place.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onViewDetails, modifier = Modifier.fillMaxWidth()) {
            Text("View Details")
        }
        Spacer(Modifier.height(8.dp))
    }
}
