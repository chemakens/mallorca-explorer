package com.mallorca.explorer.feature.explore

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.ui.component.BannerSkeleton
import com.mallorca.explorer.core.ui.component.CardRowSkeleton
import com.mallorca.explorer.core.ui.component.EmptyState
import com.mallorca.explorer.core.ui.component.PlaceCardSkeleton
import com.mallorca.explorer.core.domain.model.Event
import com.mallorca.explorer.core.domain.model.EventCategory
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.SUPTrafficLight
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.model.windDegToCardinal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onPlaceClick: (String) -> Unit,
    onItineraryClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var localSearch by remember { mutableStateOf("") }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    @SuppressLint("MissingPermission")
    fun fetchAndSetLocation(lm: LocationManager) {
        val cached = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        if (cached != null) {
            viewModel.setLocation(cached.latitude, cached.longitude)
            return
        }
        // No cached fix — request a fresh one-shot location
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        lm.requestSingleUpdate(provider, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                viewModel.setLocation(location.latitude, location.longitude)
            }
            override fun onProviderDisabled(provider: String) {}
        }, Looper.getMainLooper())
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchAndSetLocation(context.getSystemService(LocationManager::class.java))
    }

    fun onNearMeTapped() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            if (uiState.nearMeEnabled) {
                viewModel.toggleNearMe()
            } else {
                fetchAndSetLocation(context.getSystemService(LocationManager::class.java))
            }
        } else {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp)
                .padding(top = 8.dp),
        ) {
            Text(
                stringResource(R.string.explore_header_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.explore_header_subtitle, uiState.totalPlaceCount, uiState.featuredItineraries.size),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = localSearch,
                onValueChange = { v ->
                    localSearch = v
                    viewModel.onSearchQueryChanged(v)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.explore_search_placeholder),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                },
                trailingIcon = if (localSearch.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            localSearch = ""
                            viewModel.onSearchCleared()
                        }) {
                            Icon(Icons.Outlined.Clear, contentDescription = null, tint = Color.White)
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedLeadingIconColor = Color.White,
                    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.8f),
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(10.dp))
            // Filter chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val todosSelected = uiState.minRating == null && !uiState.nearMeEnabled
                FilterChip(
                    selected = todosSelected,
                    onClick = { viewModel.setMinRating(null); if (uiState.nearMeEnabled) viewModel.toggleNearMe() },
                    label = { Text(stringResource(R.string.explore_filter_all)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = todosSelected,
                        borderColor = Color.White.copy(alpha = 0.65f),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp,
                    ),
                )
                val rating3Selected = uiState.minRating == 3f
                FilterChip(
                    selected = rating3Selected,
                    onClick = { viewModel.setMinRating(if (rating3Selected) null else 3f) },
                    label = { Text("3+ ★") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = rating3Selected,
                        borderColor = Color.White.copy(alpha = 0.65f),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp,
                    ),
                )
                val rating4Selected = uiState.minRating == 4f
                FilterChip(
                    selected = rating4Selected,
                    onClick = { viewModel.setMinRating(if (rating4Selected) null else 4f) },
                    label = { Text("4+ ★") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = rating4Selected,
                        borderColor = Color.White.copy(alpha = 0.65f),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp,
                    ),
                )
                val nearMeSelected = uiState.nearMeEnabled
                FilterChip(
                    selected = nearMeSelected,
                    onClick = { onNearMeTapped() },
                    label = { Text(stringResource(R.string.explore_filter_near_me)) },
                    leadingIcon = if (nearMeSelected) {
                        { Icon(Icons.Outlined.MyLocation, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = nearMeSelected,
                        borderColor = Color.White.copy(alpha = 0.65f),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp,
                    ),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> ExploreLoadingSkeleton()
                    uiState.isSearching -> {
                        if (uiState.searchResults.isEmpty()) {
                            EmptyState(
                                emoji = "🔍",
                                title = stringResource(R.string.explore_no_results),
                                subtitle = stringResource(R.string.explore_no_results_query, uiState.searchQuery),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(uiState.searchResults, key = { it.id }) { place ->
                                    val alpha = remember { Animatable(0f) }
                                    val offsetY = remember { Animatable(16f) }
                                    LaunchedEffect(place.id) {
                                        launch { alpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
                                        launch { offsetY.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                                    }
                                    com.mallorca.explorer.core.ui.component.PlaceCard(
                                        place = place,
                                        onClick = { onPlaceClick(place.id) },
                                        locale = uiState.locale,
                                        modifier = Modifier.graphicsLayer {
                                            this.alpha = alpha.value
                                            translationY = offsetY.value
                                        },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            // Weather + SUP
                            uiState.weather?.let { weather ->
                                item { WeatherBanner(weather = weather, modifier = Modifier.padding(horizontal = 16.dp)) }
                                item {
                                    SUPSection(
                                        weather = weather,
                                        supPlaces = uiState.supPlaces,
                                        locale = uiState.locale,
                                        onPlaceClick = onPlaceClick,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                            // Category grid
                            item {
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(stringResource(R.string.explore_categories_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    CategoryGrid(onCategoryClick = onCategoryClick)
                                }
                            }
                            // Featured Itineraries
                            if (uiState.featuredItineraries.isNotEmpty()) {
                                item {
                                    Column {
                                        Text(
                                            stringResource(R.string.explore_featured_routes_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(uiState.featuredItineraries, key = { it.id }) { itin ->
                                                ItineraryCard(
                                                    itinerary = itin,
                                                    onClick = { onItineraryClick(itin.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Popular / filtered places
                            item {
                                val popularTitle = when {
                                    uiState.nearMeEnabled && uiState.minRating != null ->
                                        stringResource(R.string.explore_popular_near_me_rated, uiState.minRating!!.toInt())
                                    uiState.nearMeEnabled ->
                                        stringResource(R.string.explore_popular_nearest)
                                    uiState.minRating != null ->
                                        stringResource(R.string.explore_popular_top_rated, uiState.minRating!!.toInt())
                                    else ->
                                        stringResource(R.string.explore_popular_now)
                                }
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        popularTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (uiState.popularPlaces.isEmpty() && (uiState.minRating != null || uiState.nearMeEnabled)) {
                                        Text(
                                            stringResource(R.string.explore_no_results),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            itemsIndexed(
                                uiState.popularPlaces.take(10),
                                key = { _, place -> "popular-${place.id}" },
                            ) { index, place ->
                                val alpha = remember { Animatable(0f) }
                                val offsetY = remember { Animatable(24f) }
                                LaunchedEffect(place.id) {
                                    delay(index * 60L)
                                    launch { alpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
                                    launch { offsetY.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                                }
                                com.mallorca.explorer.core.ui.component.PlaceCard(
                                    place = place,
                                    onClick = { onPlaceClick(place.id) },
                                    locale = uiState.locale,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 4.dp)
                                        .graphicsLayer {
                                            this.alpha = alpha.value
                                            translationY = offsetY.value
                                        },
                                )
                            }
                            // Recently viewed
                            if (uiState.recentPlaces.isNotEmpty()) {
                                item {
                                    Column {
                                        Text(
                                            stringResource(R.string.explore_recently_viewed_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            items(uiState.recentPlaces, key = { "recent-${it.id}" }) { place ->
                                                RecentPlaceCard(place = place, locale = uiState.locale, onClick = { onPlaceClick(place.id) })
                                            }
                                        }
                                    }
                                }
                            }
                            // Events
                            item {
                                EventsSection(
                                    events = uiState.upcomingEvents,
                                    timeFilter = uiState.eventTimeFilter,
                                    categoryFilter = uiState.eventCategoryFilter,
                                    locale = uiState.locale,
                                    onTimeFilterChange = viewModel::setEventTimeFilter,
                                    onCategoryFilterChange = viewModel::setEventCategoryFilter,
                                )
                            }
                        }
                    }
                }
            }

            // Scroll-to-top FAB
            val fabAlpha = remember { Animatable(0f) }
            val fabScale = remember { Animatable(0.5f) }
            LaunchedEffect(showScrollToTop) {
                if (showScrollToTop) {
                    launch { fabAlpha.animateTo(1f, tween(200)) }
                    launch { fabScale.animateTo(1f, tween(200)) }
                } else {
                    launch { fabAlpha.animateTo(0f, tween(200)) }
                    launch { fabScale.animateTo(0.5f, tween(200)) }
                }
            }
            if (fabAlpha.value > 0f) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(48.dp)
                        .graphicsLayer {
                            alpha = fabAlpha.value
                            scaleX = fabScale.value
                            scaleY = fabScale.value
                        },
                    shape = CircleShape,
                ) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.explore_scroll_to_top_cd))
                }
            }
        }
    }
}

@Composable
private fun RecentPlaceCard(place: Place, locale: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val displayName = when (locale) {
        "es" -> place.nameEs.ifEmpty { place.name }
        "de" -> place.nameDe.ifEmpty { place.name }
        "ru" -> place.nameRu.ifEmpty { place.name }
        "zh" -> place.nameZh.ifEmpty { place.name }
        else -> place.name
    }
    Card(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            AsyncImage(
                model = place.thumbnailUrl.ifEmpty { null },
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    place.category.emoji,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ExploreLoadingSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        item { BannerSkeleton() }
        item { CardRowSkeleton() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { PlaceCardSkeleton() }
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = Category.entries.chunked(4)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cat ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCategoryClick(cat.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cat.emoji, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(cat.displayName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItineraryCard(itinerary: Itinerary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .size(width = 180.dp, height = 260.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        val isPremium = itinerary.tags.any { it == "luxury" || it == "premium" }
        Box {
            if (itinerary.coverPhoto.url.isNotEmpty()) {
                AsyncImage(model = itinerary.coverPhoto.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(itinerary.category.emoji, style = MaterialTheme.typography.displayMedium)
                }
            }
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(0f to Color.Transparent, 0.5f to Color.Black.copy(alpha = 0.10f), 1f to Color.Black.copy(alpha = 0.78f)))))
            if (isPremium) {
                Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFD700)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.WorkspacePremium, null, tint = Color(0xFF5C3D00), modifier = Modifier.size(11.dp))
                    Text("PREMIUM", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF5C3D00))
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(itinerary.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                Text("⏱ ${itinerary.durationDays}d · 📍 ${itinerary.places.size} stops", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun SUPSection(weather: WeatherCondition, supPlaces: ImmutableList<Place>, locale: String, onPlaceClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val windKnots = weather.windKnots
    val light: SUPTrafficLight = when { windKnots > 10f -> SUPTrafficLight.RED; windKnots > 5f -> SUPTrafficLight.YELLOW; else -> SUPTrafficLight.GREEN }
    val cardBg = when (light) { SUPTrafficLight.GREEN -> Color(0xFFE8F5E9); SUPTrafficLight.YELLOW -> Color(0xFFFFFDE7); SUPTrafficLight.RED -> Color(0xFFFFEBEE) }
    val accentColor = when (light) { SUPTrafficLight.GREEN -> Color(0xFF2E7D32); SUPTrafficLight.YELLOW -> Color(0xFFF57F17); SUPTrafficLight.RED -> Color(0xFFC62828) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("🏄", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.explore_sup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(light.emoji, style = MaterialTheme.typography.headlineSmall)
                        Column { Text(light.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor); Text(light.detail, style = MaterialTheme.typography.bodySmall, color = accentColor) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${"%.1f".format(windKnots)} kn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
                        Text("💨 ${windDegToCardinal(weather.windDirectionDeg)}", style = MaterialTheme.typography.bodySmall, color = accentColor)
                    }
                }
                Text(stringResource(R.string.explore_sup_full_analysis), style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.75f))
            }
        }
        if (supPlaces.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(supPlaces, key = { it.id }) { place ->
                    Card(modifier = Modifier.width(150.dp).clickable { onPlaceClick(place.id) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(0.dp)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🏄", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (when (locale) { "es" -> place.nameEs.ifEmpty { place.name }; "de" -> place.nameDe.ifEmpty { place.name }; "ru" -> place.nameRu.ifEmpty { place.name }; "zh" -> place.nameZh.ifEmpty { place.name }; else -> place.name }).removePrefix("SUP "),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            )
                            Text(place.municipality, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherBanner(weather: WeatherCondition, modifier: Modifier = Modifier) {
    val recommended = weather.recommendedCategories
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(weather.bannerEmoji, style = MaterialTheme.typography.headlineSmall)
                Text(weather.bannerText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recommended.take(3).forEach { cat ->
                    Row(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(cat.emoji, style = MaterialTheme.typography.labelSmall)
                        Text(cat.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsSection(
    events: ImmutableList<Event>,
    timeFilter: EventTimeFilter,
    categoryFilter: EventCategory?,
    locale: String,
    onTimeFilterChange: (EventTimeFilter) -> Unit,
    onCategoryFilterChange: (EventCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.explore_events_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (events.isEmpty()) {
                Text(
                    stringResource(R.string.explore_no_results),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Time filter chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventTimeFilter.entries.forEach { filter ->
                val filterLabel = when (filter) {
                    EventTimeFilter.ALL        -> stringResource(R.string.explore_time_filter_all)
                    EventTimeFilter.THIS_WEEK  -> stringResource(R.string.explore_time_filter_this_week)
                    EventTimeFilter.THIS_MONTH -> stringResource(R.string.explore_time_filter_this_month)
                    EventTimeFilter.NEXT_MONTH -> stringResource(R.string.explore_time_filter_next_month)
                }
                FilterChip(
                    selected = timeFilter == filter,
                    onClick = { onTimeFilterChange(filter) },
                    label = { Text(filterLabel, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        // Category filter chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = categoryFilter == null,
                onClick = { onCategoryFilterChange(null) },
                label = { Text(stringResource(R.string.explore_filter_all), style = MaterialTheme.typography.labelMedium) },
            )
            EventCategory.entries.forEach { cat ->
                val catLabel = when (cat) {
                    EventCategory.MARKET   -> stringResource(R.string.explore_event_category_market)
                    EventCategory.FESTIVAL -> stringResource(R.string.explore_event_category_festival)
                    EventCategory.CONCERT  -> stringResource(R.string.explore_event_category_concert)
                    EventCategory.CULTURE  -> stringResource(R.string.explore_event_category_culture)
                    EventCategory.SPORT    -> stringResource(R.string.explore_event_category_sport)
                }
                FilterChip(
                    selected = categoryFilter == cat,
                    onClick = { onCategoryFilterChange(if (categoryFilter == cat) null else cat) },
                    label = { Text("${cat.emoji} $catLabel", style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        if (events.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    EventCard(event = event, locale = locale)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.explore_events_no_filter_results),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: Event, locale: String, modifier: Modifier = Modifier) {
    val displayTitle = when (locale) {
        "es" -> event.titleEs.ifEmpty { event.title }
        "de" -> event.titleDe.ifEmpty { event.title }
        "ru" -> event.titleRu.ifEmpty { event.title }
        "zh" -> event.titleZh.ifEmpty { event.title }
        else -> event.title
    }
    val categoryLabel = when (event.category) {
        EventCategory.MARKET   -> stringResource(R.string.explore_event_category_market)
        EventCategory.FESTIVAL -> stringResource(R.string.explore_event_category_festival)
        EventCategory.CONCERT  -> stringResource(R.string.explore_event_category_concert)
        EventCategory.CULTURE  -> stringResource(R.string.explore_event_category_culture)
        EventCategory.SPORT    -> stringResource(R.string.explore_event_category_sport)
    }
    val recurringLabel = stringResource(R.string.explore_events_recurring)
    val dateStr = remember(event.startDateEpoch, event.isRecurring, recurringLabel) {
        if (event.isRecurring) recurringLabel
        else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(event.startDateEpoch))
    }
    Card(modifier = modifier.width(160.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(event.category.emoji, style = MaterialTheme.typography.titleSmall) }
                Column { Text(categoryLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold); Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(displayTitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(event.municipality, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (event.isFree) {
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF1B5E20).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(stringResource(R.string.explore_events_free), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
            } else {
                event.price?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
