package com.mallorca.explorer.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mallorca.explorer.R
import com.mallorca.explorer.feature.favorites.FavoritesScreen
import com.mallorca.explorer.feature.trips.list.TripListScreen

@Composable
fun MyMallorcaScreen(
    onPlaceClick: (String) -> Unit,
    onTripClick: (String) -> Unit,
    onNewTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.nav_favorites)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.nav_trips)) },
            )
        }
        when (selectedTab) {
            0 -> FavoritesScreen(
                onPlaceClick = onPlaceClick,
                modifier = Modifier.fillMaxSize(),
            )
            1 -> TripListScreen(
                onTripClick = onTripClick,
                onNewTrip = onNewTrip,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
