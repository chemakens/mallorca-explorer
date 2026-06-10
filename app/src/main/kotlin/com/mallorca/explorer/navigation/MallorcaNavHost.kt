package com.mallorca.explorer.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mallorca.explorer.connectivity.ConnectivityViewModel
import com.mallorca.explorer.core.ui.component.OfflineBanner
import com.mallorca.explorer.core.ui.transition.LocalAnimatedContentScope
import com.mallorca.explorer.feature.explore.ExploreScreen
import com.mallorca.explorer.feature.gems.GemsScreen
import com.mallorca.explorer.onboarding.OnboardingScreen
import com.mallorca.explorer.feature.favorites.FavoritesScreen
import com.mallorca.explorer.feature.itinerary.detail.ItineraryDetailScreen
import com.mallorca.explorer.feature.itinerary.list.ItineraryListScreen
import com.mallorca.explorer.feature.map.MapScreen
import com.mallorca.explorer.feature.place.PlaceDetailScreen
import com.mallorca.explorer.feature.settings.SettingsScreen
import com.mallorca.explorer.feature.trips.builder.TripBuilderScreen
import com.mallorca.explorer.feature.trips.list.TripListScreen
import com.mallorca.explorer.feature.trips.map.TripMapScreen
import com.mallorca.explorer.feature.trips.selector.TripSelectorScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.mallorca.explorer.R
import com.mallorca.explorer.feature.place.PlaceDetailViewModel
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable data class MapRoute(val itineraryId: String? = null)
@Serializable object ExploreRoute
@Serializable object TripsRoute
@Serializable object FavoritesRoute
@Serializable data class PlaceDetailRoute(val placeId: String)
@Serializable data class ItineraryDetailRoute(val itineraryId: String)
@Serializable data class ItineraryListRoute(val category: String? = null)
@Serializable data class TripBuilderRoute(val tripId: String? = null)
@Serializable data class TripMapRoute(val tripId: String)
@Serializable data class TripSelectorRoute(val placeId: String)
@Serializable object GemsRoute
@Serializable object SettingsRoute

private data class TopLevelRoute<T : Any>(
    val route: T,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MallorcaNavHost(
    showOnboarding: Boolean = false,
    onNavControllerReady: (androidx.navigation.NavController) -> Unit = {},
) {
    val navController = rememberNavController()
    androidx.compose.runtime.LaunchedEffect(navController) { onNavControllerReady(navController) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
    val isOffline by connectivityViewModel.isOffline.collectAsStateWithLifecycle()

    val topLevelRoutes = listOf(
        TopLevelRoute(MapRoute(), stringResource(R.string.nav_map), Icons.Outlined.Map),
        TopLevelRoute(ExploreRoute, stringResource(R.string.nav_explore), Icons.Outlined.Explore),
        TopLevelRoute(TripsRoute, stringResource(R.string.nav_trips), Icons.Outlined.ListAlt),
        TopLevelRoute(FavoritesRoute, stringResource(R.string.nav_favorites), Icons.Outlined.FavoriteBorder),
        TopLevelRoute(GemsRoute, stringResource(R.string.nav_gems), Icons.Outlined.Diamond),
    )

    val showBottomBar = topLevelRoutes.any { tlr ->
        currentDestination?.hierarchy?.any { it.hasRoute(tlr.route::class) } == true
    }

    Scaffold(
        topBar = { OfflineBanner(isOffline = isOffline) },
        bottomBar = {
            if (showBottomBar) {
                // Superficie flotante con esquinas redondeadas
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 12.dp,
                    tonalElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
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
                                label = { Text(tlr.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = if (showOnboarding) OnboardingRoute else MapRoute(),
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable<OnboardingRoute> {
                        OnboardingScreen(
                            onComplete = {
                                navController.navigate(MapRoute()) {
                                    popUpTo(OnboardingRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<MapRoute> { backStack ->
                        val route = backStack.toRoute<MapRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            MapScreen(
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onItineraryClick = { id -> navController.navigate(ItineraryDetailRoute(id)) },
                                onSettingsClick = { navController.navigate(SettingsRoute) },
                                itineraryId = route.itineraryId,
                            )
                        }
                    }
                    composable<ExploreRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            ExploreScreen(
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onItineraryClick = { id -> navController.navigate(ItineraryDetailRoute(id)) },
                                onCategoryClick = { cat -> navController.navigate(ItineraryListRoute(cat)) }
                            )
                        }
                    }
                    composable<TripsRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            TripListScreen(
                                onTripClick = { id -> navController.navigate(TripBuilderRoute(id)) },
                                onNewTrip = { navController.navigate(TripBuilderRoute()) }
                            )
                        }
                    }
                    composable<FavoritesRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            FavoritesScreen(
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) }
                            )
                        }
                    }
                    composable<GemsRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            GemsScreen(
                                onGemClick = { id -> navController.navigate(PlaceDetailRoute(id)) }
                            )
                        }
                    }
                    composable<PlaceDetailRoute> { backStack ->
                        android.util.Log.e("NavHost", ">>> Entrando en ruta de detalle")
                        val route = backStack.toRoute<PlaceDetailRoute>()
                        val viewModel: PlaceDetailViewModel = hiltViewModel()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            PlaceDetailScreen(
                                viewModel = viewModel,
                                placeId = route.placeId,
                                onBack = { navController.popBackStack() },
                                onAddToTrip = { navController.navigate(TripSelectorRoute(route.placeId)) }
                            )
                        }
                    }
                    composable<ItineraryDetailRoute>(
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "mallorca://itinerary?itineraryId={itineraryId}" },
                            navDeepLink { uriPattern = "https://chemakens.github.io/mallorca-explorer-web/itinerary/?itineraryId={itineraryId}" },
                        )
                    ) { backStack ->
                        val route = backStack.toRoute<ItineraryDetailRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            ItineraryDetailScreen(
                                itineraryId = route.itineraryId,
                                onBack = { navController.popBackStack() },
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onSaveToTrip = {
                                    navController.navigate(TripsRoute) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onViewOnMap = {
                                    navController.navigate(MapRoute(itineraryId = route.itineraryId)) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                },
                            )
                        }
                    }
                    composable<ItineraryListRoute> { backStack ->
                        val route = backStack.toRoute<ItineraryListRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            ItineraryListScreen(
                                category = route.category,
                                onItineraryClick = { id -> navController.navigate(ItineraryDetailRoute(id)) },
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable<TripBuilderRoute> { backStack ->
                        val route = backStack.toRoute<TripBuilderRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            TripBuilderScreen(
                                tripId = route.tripId,
                                onBack = { navController.popBackStack() },
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onViewMap = { route.tripId?.let { navController.navigate(TripMapRoute(it)) } },
                            )
                        }
                    }
                    composable<TripMapRoute> { backStack ->
                        val route = backStack.toRoute<TripMapRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            TripMapScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<TripSelectorRoute> { backStack ->
                        val route = backStack.toRoute<TripSelectorRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            TripSelectorScreen(
                                onBack = { navController.popBackStack() },
                                onTripSelected = { tripId ->
                                    navController.navigate(TripBuilderRoute(tripId)) {
                                        popUpTo(TripSelectorRoute(route.placeId)) { inclusive = true }
                                    }
                                },
                            )
                        }
                    }
                    composable<SettingsRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
