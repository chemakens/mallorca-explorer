package com.mallorca.explorer.feature.explore

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mallorca.explorer.core.domain.model.Event
import com.mallorca.explorer.core.domain.model.EventCategory
import com.mallorca.explorer.feature.explore.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.explore_events_title)) })
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventTimeFilter.entries.forEach { filter ->
                        val label = when (filter) {
                            EventTimeFilter.ALL          -> stringResource(R.string.explore_time_filter_all)
                            EventTimeFilter.TODAY        -> stringResource(R.string.explore_time_filter_today)
                            EventTimeFilter.TOMORROW     -> stringResource(R.string.explore_time_filter_tomorrow)
                            EventTimeFilter.THIS_WEEKEND -> stringResource(R.string.explore_time_filter_this_weekend)
                            EventTimeFilter.THIS_WEEK    -> stringResource(R.string.explore_time_filter_this_week)
                            EventTimeFilter.THIS_MONTH   -> stringResource(R.string.explore_time_filter_this_month)
                            EventTimeFilter.NEXT_MONTH   -> stringResource(R.string.explore_time_filter_next_month)
                        }
                        FilterChip(
                            selected = uiState.eventTimeFilter == filter,
                            onClick = { viewModel.setEventTimeFilter(filter) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.eventCategoryFilter == null,
                        onClick = { viewModel.setEventCategoryFilter(null) },
                        label = { Text(stringResource(R.string.explore_filter_all), style = MaterialTheme.typography.labelMedium) },
                    )
                    EventCategory.entries.forEach { cat ->
                        val catLabel = when (cat) {
                            EventCategory.MARKET    -> stringResource(R.string.explore_event_category_market)
                            EventCategory.FESTIVAL  -> stringResource(R.string.explore_event_category_festival)
                            EventCategory.CONCERT   -> stringResource(R.string.explore_event_category_concert)
                            EventCategory.CULTURE   -> stringResource(R.string.explore_event_category_culture)
                            EventCategory.SPORT     -> stringResource(R.string.explore_event_category_sport)
                            EventCategory.NIGHTLIFE -> stringResource(R.string.explore_event_category_nightlife)
                            EventCategory.GASTRONOMY -> stringResource(R.string.explore_event_category_gastronomy)
                            EventCategory.FAMILY    -> stringResource(R.string.explore_event_category_family)
                        }
                        FilterChip(
                            selected = uiState.eventCategoryFilter == cat,
                            onClick = { viewModel.setEventCategoryFilter(if (uiState.eventCategoryFilter == cat) null else cat) },
                            label = { Text("${cat.emoji} $catLabel", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
            if (uiState.upcomingEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.explore_events_no_filter_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(uiState.upcomingEvents, key = { it.id }) { event ->
                    EventsListCard(
                        event = event,
                        locale = uiState.locale,
                        onMoreInfoClicked = viewModel::onEventMoreInfoClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventsListCard(
    event: Event,
    locale: String,
    onMoreInfoClicked: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recurringLabel = stringResource(R.string.explore_events_recurring)
    val dateStr = remember(event.startDateEpoch, event.endDateEpoch, event.isRecurring) {
        if (event.isRecurring) recurringLabel
        else {
            val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
            val start = fmt.format(Date(event.startDateEpoch))
            val end = event.endDateEpoch?.let { fmt.format(Date(it)) }
            if (end != null && end != start) "$start – $end" else start
        }
    }
    val displayTitle = when (locale) {
        "es" -> event.titleEs.ifEmpty { event.title }
        "de" -> event.titleDe.ifEmpty { event.title }
        "ru" -> event.titleRu.ifEmpty { event.title }
        "zh" -> event.titleZh.ifEmpty { event.title }
        else -> event.title
    }
    val categoryLabel = when (event.category) {
        EventCategory.MARKET    -> stringResource(R.string.explore_event_category_market)
        EventCategory.FESTIVAL  -> stringResource(R.string.explore_event_category_festival)
        EventCategory.CONCERT   -> stringResource(R.string.explore_event_category_concert)
        EventCategory.CULTURE   -> stringResource(R.string.explore_event_category_culture)
        EventCategory.SPORT     -> stringResource(R.string.explore_event_category_sport)
        EventCategory.NIGHTLIFE -> stringResource(R.string.explore_event_category_nightlife)
        EventCategory.GASTRONOMY -> stringResource(R.string.explore_event_category_gastronomy)
        EventCategory.FAMILY    -> stringResource(R.string.explore_event_category_family)
    }
    val moreInfoLabel = when (locale) { "es" -> "Más info"; "de" -> "Mehr Info"; else -> "More info" }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(event.category.emoji, style = MaterialTheme.typography.titleMedium) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(categoryLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📅 $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("📍 ${event.municipality}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (event.isFree) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF1B5E20).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    ) { Text(stringResource(R.string.explore_events_free), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
                } else {
                    event.price?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                event.websiteUrl?.let { url ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable(role = Role.Button) {
                                onMoreInfoClicked(event)
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                catch (e: ActivityNotFoundException) { }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(moreInfoLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(0.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
