package com.mallorca.explorer.feature.map

import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Difficulty
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.RouteType
import com.mallorca.explorer.core.domain.model.WaypointRole
import com.mallorca.explorer.core.domain.model.WaypointType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class RouteWaypointMarker(
    val name: String,
    val location: LatLng,
    val order: Int,
    // Papel del punto: salida/llegada (siempre visibles) o punto de interés (se atenúa junto a
    // su variante). WAYPOINT por defecto para rutas sin roles explícitos.
    val role: WaypointRole = WaypointRole.WAYPOINT,
    // Senderismo: tipo de punto (para el icono) y nombre/nota localizados (para la burbuja).
    val type: WaypointType = WaypointType.OTHER,
    val nameEs: String = "",
    val nameDe: String = "",
    val note: String = "",
    val noteEs: String = "",
    val noteDe: String = "",
) {
    fun localizedName(locale: String): String = when (locale) {
        "es" -> nameEs.ifBlank { name }
        "de" -> nameDe.ifBlank { name }
        else -> name
    }.ifBlank { name }

    fun localizedNote(locale: String): String = when (locale) {
        "es" -> noteEs.ifBlank { note }
        "de" -> noteDe.ifBlank { note }
        else -> note
    }
}

/**
 * Una variante de trazado lista para el mapa: etiqueta ya localizada, color asignado y su
 * polilínea. Solo se rellena cuando la ruta tiene >=2 variantes; una ruta de una sola línea
 * deja `variants` vacío y se dibuja como siempre con `coords`.
 */
data class RouteVariantUi(
    val id: String,
    val color: String,
    val coords: ImmutableList<LatLng>,
    val distanceKm: Float?,
    val difficulty: Difficulty?,
    val durationHoursText: String?,
)

data class ItineraryRoute(
    val id: String,
    val title: String,
    val color: String,
    val coords: ImmutableList<LatLng>,
    val waypoints: ImmutableList<RouteWaypointMarker>,
    val coverPhotoUrl: String,
    val category: Category,
    val durationDays: Int,
    // Senderismo: forma de la ruta (para elegir el icono de salida/llegada/giro en el mapa).
    val routeType: RouteType? = null,
    // Si es false, NO se pinta la línea de ruta (los marcadores sí se ven).
    // Las rutas SUP solo dibujan línea cuando tienen un track real en route_polyline_points
    // (ver MapViewModel); el resto de categorías dibujan siempre.
    val drawLine: Boolean = true,
    // Variantes de trazado (>=2). Vacío = ruta de una sola línea (comportamiento previo).
    val variants: ImmutableList<RouteVariantUi> = persistentListOf(),
)

data class MapUiState(
    val places: ImmutableList<Place> = persistentListOf(),
    val categorySheetPlaces: ImmutableList<Place> = persistentListOf(),
    val hiddenGems: ImmutableList<Place> = persistentListOf(),
    val selectedPlace: Place? = null,
    val selectedGem: Place? = null,
    val activeCategory: Category? = null,
    val isOffline: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeItinerary: Itinerary? = null,
    val allItineraryRoutes: ImmutableList<ItineraryRoute> = persistentListOf(),
    val previewItineraryId: String? = null,
    val locale: String = "en",
    // Senderismo: punto (waypoint) seleccionado en el mapa → muestra su burbuja. null = sin burbuja.
    val selectedWaypointOrder: Int? = null,
)
