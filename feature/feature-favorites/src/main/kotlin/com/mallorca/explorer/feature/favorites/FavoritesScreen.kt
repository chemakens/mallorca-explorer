package com.mallorca.explorer.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mallorca.explorer.core.ui.component.EmptyState
import com.mallorca.explorer.core.ui.component.PlaceCard
import com.mallorca.explorer.feature.favorites.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_favorites)) }) },
        modifier = modifier,
    ) { padding ->
        if (uiState.places.isEmpty() && !uiState.isLoading) {
            EmptyState(
                emoji = "💙",
                title = stringResource(R.string.favorites_empty_title),
                subtitle = stringResource(R.string.favorites_empty_subtitle),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(uiState.places, key = { it.id }) { place ->
                    PlaceCard(
                        place = place,
                        onClick = { onPlaceClick(place.id) },
                        locale = uiState.locale,
                    )
                }
            }
        }
    }
}
