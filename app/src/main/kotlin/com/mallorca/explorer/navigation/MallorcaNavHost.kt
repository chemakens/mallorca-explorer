package com.mallorca.explorer.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
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
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.ui.component.OfflineBanner
import com.mallorca.explorer.core.ui.transition.LocalAnimatedContentScope
import com.mallorca.explorer.feature.explore.EventsScreen
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.mallorca.explorer.R
import com.mallorca.explorer.feature.place.PlaceDetailViewModel
import kotlinx.serialization.Serializable

// Compatibilidad SUP: los antiguos lugares sup-* ya no tienen ficha propia (cada zona SUP
// es ahora una RUTA/itinerario). Un favorito o enlace antiguo a un lugar sup-* se redirige
// a su ruta equivalente. La fila sup-* sigue en el seed (oculta por categoría y usada como
// stop por algunos itinerarios); esto solo cambia la navegación, no borra nada.
val SUP_PLACE_TO_ROUTE: Map<String, String> = mapOf(
    "sup-santa-ponsa" to "itin-sup-santa-ponsa",
    "sup-portals-vells" to "itin-sup-portals-vells",
    "sup-cala-major" to "itin-sup-cala-major",
    "sup-soller" to "itin-sup-soller",
    "sup-port-adriano" to "itin-sup-port-adriano",
    "sup-portocolom" to "itin-sup-portocolom",
    "sup-sant-vicenc" to "itin-sup-sant-vicenc",
    "sup-cala-ratjada" to "itin-sup-cala-ratjada",
    "sup-camp-de-mar" to "itin-sup-camp-de-mar",
    "sup-port-pollenca" to "itin-sup-formentor",
    "sup-cala-punta-negra-calvia" to "itin-sup-cala-punta-negra",
    "sup-cala-romantica" to "itin-sup-cala-romantica",
    "sup-sa-cova-baixa" to "itin-sup-sa-cova-baixa",
)

// Alias de itinerarios fusionados: una ruta que se absorbió en otra como VARIANTE deja aquí su
// id antiguo → id de la ruta que la contiene, para que deeplinks y favoritos antiguos sigan
// abriendo la ruta correcta. La ruta vieja se quita del seed y de DELETED_ITINERARY_IDS.
val ITINERARY_ALIAS: Map<String, String> = mapOf(
    // Portals Vells norte (Cala Falcó) pasó a ser la variante "norte" de SUP Portals Vells.
    "itin-sup-portals-vells-nord" to "itin-sup-portals-vells",
)

// Senderismo (GPX): cada lugar HIKING que ahora tiene una RUTA (itinerario con trazado) se
// enlaza aquí con ella. A diferencia de SUP, la ficha de lugar SE CONSERVA (con botón "Ver
// ruta"); este mapa solo sirve para ese enlace y para ocultar la ficha de la lista HIKING del
// mapa (la ruta ya aparece en esa lista). No redirige automáticamente.
val HIKING_PLACE_TO_ROUTE: Map<String, String> = mapOf(
    "avenc-de-son-pou" to "itin-hike-avenc-de-son-pou",
    "barranc-biniaraix" to "itin-hike-barranc-biniaraix",
    "cala-en-basset" to "itin-hike-cala-en-basset",
    "cami-arxiduc" to "itin-hike-cami-arxiduc",
    "cami-des-correu" to "itin-hike-cami-des-correu",
    "castell-alaro" to "itin-hike-castell-alaro",
    "faro-ses-salines" to "itin-hike-faro-ses-salines",
    "hike-port-des-canonge" to "itin-hike-port-des-canonge",
    "puig-tomir" to "itin-hike-puig-tomir",
    "puig-ofre" to "itin-hike-puig-ofre",
    "ruta-coll-baix" to "itin-hike-ruta-coll-baix",
    "ruta-cala-estreta" to "itin-hike-ruta-cala-estreta",
    "salt-des-freu" to "itin-hike-salt-des-freu",
    "ruta-cala-boquer" to "itin-hike-ruta-cala-boquer",
    "cami-vell-de-lluc" to "itin-hike-cami-vell-de-lluc",
    "fonts-ufanes" to "itin-hike-fonts-ufanes",
    "la-comuna-bunyola" to "itin-hike-la-comuna-bunyola",
    "ruta-cala-tuent" to "itin-hike-ruta-cala-tuent",
    "ruta-cala-varques" to "itin-hike-ruta-cala-varques",
    "illa-sa-dragonera" to "itin-hike-illa-sa-dragonera",
)

@Serializable object OnboardingRoute
@Serializable data class MapRoute(val itineraryId: String? = null, val waypointOrder: Int? = null, val category: String? = null)
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
@Serializable data class EventsRoute(val filter: String? = null)
@Serializable object MyMallorcaRoute
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
        TopLevelRoute(GemsRoute, stringResource(R.string.nav_gems), Icons.Outlined.Diamond),
        TopLevelRoute(EventsRoute(), stringResource(R.string.nav_events), Icons.Outlined.CalendarMonth),
        TopLevelRoute(MyMallorcaRoute, stringResource(R.string.nav_my_mallorca), Icons.Outlined.BookmarkBorder),
    )

    val showBottomBar = topLevelRoutes.any { tlr ->
        currentDestination?.hierarchy?.any { it.hasRoute(tlr.route::class) } == true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { OfflineBanner(isOffline = isOffline) },
        bottomBar = {
            if (showBottomBar) {
                // Superficie flotante con esquinas redondeadas
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
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
                                waypointOrder = route.waypointOrder,
                                initialCategory = route.category,
                            )
                        }
                    }
                    composable<ExploreRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            ExploreScreen(
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onItineraryClick = { id -> navController.navigate(ItineraryDetailRoute(id)) },
                                // HIKING abre el MISMO destino/estado que tocar Hiking en el mapa
                                // (MapScreen con la categoría activa). El resto de categorías (incl.
                                // SUP) mantienen la lista de itinerarios de siempre.
                                onCategoryClick = { cat ->
                                    if (cat == Category.HIKING.name) {
                                        navController.navigate(MapRoute(category = cat))
                                    } else {
                                        navController.navigate(ItineraryListRoute(cat))
                                    }
                                }
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
                    composable<MyMallorcaRoute> {
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            MyMallorcaScreen(
                                onPlaceClick = { id -> navController.navigate(PlaceDetailRoute(id)) },
                                onTripClick = { id -> navController.navigate(TripBuilderRoute(id)) },
                                onNewTrip = { navController.navigate(TripBuilderRoute()) },
                            )
                        }
                    }
                    composable<EventsRoute>(
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "mallorca://events?filter={filter}" },
                            navDeepLink { uriPattern = "mallorca://events" },
                        )
                    ) { backStack ->
                        val route = backStack.toRoute<EventsRoute>()
                        CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                            EventsScreen(initialFilter = route.filter)
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
                        val route = backStack.toRoute<PlaceDetailRoute>()
                        val supRoute = SUP_PLACE_TO_ROUTE[route.placeId]
                        if (supRoute != null) {
                            // Lugar SUP antiguo → abrir su ruta y quitar esta entrada del back stack.
                            LaunchedEffect(supRoute) {
                                navController.navigate(ItineraryDetailRoute(supRoute)) {
                                    popUpTo(PlaceDetailRoute(route.placeId)) { inclusive = true }
                                }
                            }
                        } else {
                            val viewModel: PlaceDetailViewModel = hiltViewModel()
                            // Lugar de senderismo con ruta: la ficha se conserva y muestra un
                            // botón "Ver ruta" que abre su itinerario (no redirige).
                            val hikingRoute = HIKING_PLACE_TO_ROUTE[route.placeId]
                            CompositionLocalProvider(LocalAnimatedContentScope provides this) {
                                PlaceDetailScreen(
                                    viewModel = viewModel,
                                    placeId = route.placeId,
                                    onBack = { navController.popBackStack() },
                                    onAddToTrip = { navController.navigate(TripSelectorRoute(route.placeId)) },
                                    onViewRoute = hikingRoute?.let { r -> { navController.navigate(ItineraryDetailRoute(r)) } },
                                )
                            }
                        }
                    }
                    composable<ItineraryDetailRoute>(
                        deepLinks = listOf(
                            navDeepLink { uriPattern = "mallorca://itinerary?itineraryId={itineraryId}" },
                            navDeepLink { uriPattern = "https://chemakens.github.io/mallorca-explorer-web/itinerary/?itineraryId={itineraryId}" },
                        )
                    ) { backStack ->
                        val route = backStack.toRoute<ItineraryDetailRoute>()
                        val aliasTarget = ITINERARY_ALIAS[route.itineraryId]
                        if (aliasTarget != null) {
                            // Itinerario fusionado → abrir la ruta que lo contiene y salir del back stack.
                            LaunchedEffect(aliasTarget) {
                                navController.navigate(ItineraryDetailRoute(aliasTarget)) {
                                    popUpTo(ItineraryDetailRoute(route.itineraryId)) { inclusive = true }
                                }
                            }
                        } else
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
                                onWaypointOnMap = { order ->
                                    navController.navigate(MapRoute(itineraryId = route.itineraryId, waypointOrder = order)) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
