package com.mallorca.explorer.feature.trips.builder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
import com.mallorca.explorer.core.ui.transition.LocalAnimatedContentScope
import com.mallorca.explorer.core.ui.transition.LocalSharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.UserTripStop
import com.mallorca.explorer.core.ui.component.PlaceCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TripBuilderScreen(
    tripId: String?,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit,
    onViewMap: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TripBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(TextFieldValue("")) }

    // Intercept back when the overlay is open — reliable because this is in the
    // main composition window, not inside a Dialog/Popup.
    BackHandler(enabled = uiState.showAddSheet) {
        viewModel.onDismissAddSheet()
    }

    if (showRenameDialog) {
        val renameFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { delay(300); renameFocus.requestFocus(); keyboard?.show() }
        ModalBottomSheet(onDismissRequest = { showRenameDialog = false }) {
            Column(modifier = Modifier.padding(16.dp).imePadding()) {
                Text("Rename Trip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Trip name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(renameFocus),
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            if (renameText.text.isNotBlank()) {
                                viewModel.renameTrip(renameText.text)
                                showRenameDialog = false
                            }
                        },
                    ) { Text("Rename") }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.trip?.name ?: "New Trip") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                    actions = {
                        if ((uiState.trip?.stops?.size ?: 0) >= 2) {
                            IconButton(onClick = onViewMap) {
                                Icon(Icons.Outlined.Map, contentDescription = "Ver en el mapa")
                            }
                        }
                        IconButton(onClick = {
                            val name = uiState.trip?.name ?: ""
                            renameText = TextFieldValue(name, selection = TextRange(0, name.length))
                            showRenameDialog = true
                        }) {
                            Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename trip")
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onShowAddSheet,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add Stop") },
                )
            },
        ) { padding ->
            val stops = uiState.trip?.stops ?: emptyList()

            val localStops = remember { mutableStateListOf<UserTripStop>() }
            var draggingIndex by remember { mutableStateOf<Int?>(null) }
            var dragAccumulator by remember { mutableStateOf(0f) }
            var itemHeightPx by remember { mutableStateOf(0f) }
            val density = LocalDensity.current
            val spacingPx = remember(density) { with(density) { 8.dp.toPx() } }

            LaunchedEffect(stops) {
                if (draggingIndex == null) {
                    localStops.clear()
                    localStops.addAll(stops)
                }
            }

            if (localStops.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🗺️", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("No stops yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap Add Stop to build your trip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(localStops, key = { _, s -> s.place.id }) { idx, stop ->
                        StopItem(
                            stop = stop,
                            number = idx + 1,
                            isDragging = idx == draggingIndex,
                            onClick = { if (idx != draggingIndex) onPlaceClick(stop.place.id) },
                            onRemove = { viewModel.onRemovePlace(stop.place.id) },
                            onDragStart = {
                                draggingIndex = idx
                                dragAccumulator = 0f
                            },
                            onDrag = { delta ->
                                dragAccumulator += delta
                                val stepPx = itemHeightPx + spacingPx
                                if (stepPx > 0f && draggingIndex != null) {
                                    while (dragAccumulator >= stepPx && (draggingIndex ?: 0) < localStops.size - 1) {
                                        val c = draggingIndex ?: break
                                        localStops.add(c + 1, localStops.removeAt(c))
                                        draggingIndex = c + 1
                                        dragAccumulator -= stepPx
                                    }
                                    while (dragAccumulator <= -stepPx && (draggingIndex ?: 0) > 0) {
                                        val c = draggingIndex ?: break
                                        localStops.add(c - 1, localStops.removeAt(c))
                                        draggingIndex = c - 1
                                        dragAccumulator += stepPx
                                    }
                                }
                            },
                            onDragEnd = {
                                viewModel.onReorderPlaces(localStops.map { it.place.id })
                                draggingIndex = null
                                dragAccumulator = 0f
                            },
                            onHeightMeasured = { h -> if (h > 0f) itemHeightPx = h },
                        )
                    }
                    item {
                        TripSummaryCard(stops = localStops.toList())
                    }
                }
            }
        }

        // Add-place overlay — AnimatedVisibility in the main window avoids the
        // Dialog/back-press conflict that ModalBottomSheet causes with Navigation 2.8.
        AnimatedVisibility(
            visible = uiState.showAddSheet,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(250)) { it / 2 },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Scrim — tap outside sheet to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { viewModel.onDismissAddSheet() },
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides null,
                        LocalAnimatedContentScope provides null,
                    ) {
                        AddPlaceSheet(
                            query = uiState.searchQuery,
                            results = uiState.searchResults,
                            onQueryChanged = viewModel::onSearchQueryChanged,
                            onPlaceSelected = viewModel::onAddPlace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StopItem(
    stop: UserTripStop,
    number: Int,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onHeightMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updatedOnDrag by rememberUpdatedState(onDrag)
    val updatedOnDragEnd by rememberUpdatedState(onDragEnd)
    val draggableState = rememberDraggableState { delta -> updatedOnDrag(delta) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeightMeasured(it.size.height.toFloat()) },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(if (isDragging) 8.dp else 1.dp),
        colors = if (isDragging)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        else
            CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDragging) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$number",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stop.place.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${stop.place.category.emoji} ${stop.place.category.displayName} · ~${stop.suggestedDurationMinutes / 60}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Clear, "Remove", tint = MaterialTheme.colorScheme.error)
            }
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { onDragStart() },
                    onDragStopped = { updatedOnDragEnd() },
                ),
            )
        }
    }
}

@Composable
private fun TripSummaryCard(stops: List<UserTripStop>) {
    val totalMin = stops.sumOf { it.suggestedDurationMinutes }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryItem("${stops.size}", "Stops")
            SummaryItem("~${totalMin / 60}h", "Duration")
        }
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
    }
}

@Composable
private fun AddPlaceSheet(
    query: String,
    results: kotlinx.collections.immutable.ImmutableList<Place>,
    onQueryChanged: (String) -> Unit,
    onPlaceSelected: (Place) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).imePadding()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Añadir parada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = {
                Text(
                    "Buscar lugar…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results.size) { idx ->
                PlaceCard(
                    place = results[idx],
                    onClick = { onPlaceSelected(results[idx]) },
                )
            }
        }
    }
}
