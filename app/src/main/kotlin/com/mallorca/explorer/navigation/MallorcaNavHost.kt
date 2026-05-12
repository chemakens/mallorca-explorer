package com.mallorca.explorer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mallorca.explorer.feature.explore.ExploreScreen
import com.mallorca.explorer.feature.favorites.FavoritesScreen
import com.mallorca.explorer.feature.itinerary.detail.ItineraryDetailScreen
import com.mallorca.explorer.feature.itinerary.list.ItineraryListScreen
import com.mallorca.explorer.feature.map.MapScreen
import com.mallorca.explorer.feature.place.PlaceDetailScreen
import com.mallorca.explorer.feature.trips.builder.TripBuilderScreen
import com.mallorca.explorer.feature.trips.list.TripListScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Map
import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable object ExploreRoute
@Serializable object TripsRoute
@Serializable object FavoritesRoute
@Serializable data class PlaceDetailRoute(val placeId: String)
@Serializable data class ItineraryDetailRoute(val itineraryId: String)
@Serializable data class ItineraryListRoute(val category: String? = null)
@Serializable data class TripBuilderRoute(val tripId: String? = null)

private data class TopLevelRoute<T : Any>(
    val route: T,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun MallorcaNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topLevelRoutes = listOf(
        TopLevelRoute(MapRoute, "Map", Icons.Outlined.Map),
        TopLevelRoute(ExploreRoute, "Explore", Icons.Outlined.Explore),
        TopLevelRoute(TripsRoute, "My Trips", Icons.Outlined.ListAlt),
        TopLevelRoute(FavoritesRoute, "Favorites", Icons.Outlined.FavoriteBorder),
    )

    val showBottomBar = topLevelRoutes.any { tlr ->
        currentDestination?.hierarchy?.any { it.hasRoute(tlr.route::class) } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelRoutes.forEach { tlr ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(tlr.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tlr.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tlr.icon, contentDescription = tlr.label) },
                            label = { Text(tlr.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MapRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<MapRoute> {
                MapScreen(
                    onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) }
                )
            }
            composable<ExploreRoute> {
                ExploreScreen(
                    onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                    onItineraryClick = { id -> navController.navigate(ItineraryDetailRoute(id)) },
                    onCategoryClick = { cat -> navController.navigate(ItineraryListRoute(cat)) }
                )
            }
            composable<TripsRoute> {
                TripListScreen(
                    onTripClick = { id -> navController.navigate(TripBuilderRoute(id)) },
                    onNewTrip = { navController.navigate(TripBuilderRoute()) }
                )
            }
            composable<FavoritesRoute> {
                FavoritesScreen(
                    onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) }
                )
            }
            composable<PlaceDetailRoute> { backStack ->
                val route = backStack.toRoute<PlaceDetailRoute>()
                PlaceDetailScreen(
                    placeId = route.placeId,
                    onBack = { navController.popBackStack() },
                    onAddToTrip = { navController.navigate(TripBuilderRoute()) }
                )
            }
            composable<ItineraryDetailRoute> { backStack ->
                val route = backStack.toRoute<ItineraryDetailRoute>()
                ItineraryDetailScreen(
                    itineraryId = route.itineraryId,
                    onBack = { navController.popBackStack() },
                    onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                    onSaveToTrip = { navController.navigate(TripBuilderRoute()) }
                )
            }
            composable<ItineraryListRoute> { backStack ->
                val route = backStack.toRoute<ItineraryListRoute>()
                ItineraryListScreen(
                    category = route.category,
                    onItineraryClick = { id -> navController.navigate(ItineraryDetailRoute(id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<TripBuilderRoute> { backStack ->
                val route = backStack.toRoute<TripBuilderRoute>()
                TripBuilderScreen(
                    tripId = route.tripId,
                    onBack = { navController.popBackStack() },
                    onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) }
                )
            }
        }
    }
}
