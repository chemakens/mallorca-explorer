package com.mallorca.explorer.feature.place

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.mallorca.explorer.core.domain.model.Discount
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.model.SUPTrafficLight
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WeatherCondition
import com.mallorca.explorer.core.domain.model.WindCategory
import com.mallorca.explorer.core.domain.model.windDegToCardinal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PlaceDetailScreen(
    placeId: String,
    onBack: () -> Unit,
    onAddToTrip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val dismissThreshold = with(density) { 100.dp.toPx() }
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount ->
                        if (amount > 0f || offsetX.value > 0f) {
                            scope.launch {
                                offsetX.snapTo((offsetX.value + amount).coerceAtLeast(0f))
                            }
                        }
                    },
                    onDragEnd = {
                        if (offsetX.value >= dismissThreshold) {
                            onBack()
                        } else {
                            scope.launch { offsetX.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                    },
                )
            },
    ) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.isHiddenGem && !uiState.isUnlocked -> HiddenGemLockScreen(
                onBack = onBack,
                onUnlock = viewModel::onUnlockGem,
            )
            uiState.place != null -> PlaceDetailContent(
                place = uiState.place!!,
                locale = uiState.locale,
                weather = uiState.weather,
                supStatus = uiState.supStatus,
                isSUPPlace = uiState.isSUPPlace,
                discounts = uiState.discounts,
                isHiddenGem = uiState.isHiddenGem,
                isVisited = uiState.isVisited,
                onBack = onBack,
                onFavoriteToggle = viewModel::onFavoriteToggled,
                onVisitedToggle = viewModel::onVisitedToggled,
                onAddToTrip = onAddToTrip,
                modifier = Modifier.graphicsLayer { translationX = offsetX.value },
            )
        }
    }
}

@Composable
private fun PlaceDetailContent(
    place: Place,
    locale: String,
    weather: WeatherCondition?,
    supStatus: SUPWeatherStatus? = null,
    isSUPPlace: Boolean = false,
    discounts: ImmutableList<Discount> = persistentListOf(),
    isHiddenGem: Boolean = false,
    isVisited: Boolean = false,
    onBack: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onVisitedToggle: () -> Unit = {},
    onAddToTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val displayName = place.localizedName(locale)
    val displayDescription = place.localizedDescription(locale)
    val displayTips = place.localizedTips(locale)
    val scrollState = rememberScrollState()
    var fullscreenPage by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Photo carousel header
        val pagerState = rememberPagerState { place.photoUrls.size.coerceAtLeast(1) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (place.photoUrls.isNotEmpty()) {
                    SubcomposeAsyncImage(
                        model = place.photoUrls[page],
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clickable { fullscreenPage = page },
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(place.category.emoji, style = MaterialTheme.typography.displayLarge)
                                }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(place.category.emoji, style = MaterialTheme.typography.displayLarge)
                    }
                }
            }
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(0.3f), Color.Transparent, Color.Transparent)
                        )
                    )
            )
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.4f)),
            ) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
            }
            // Share + Favorite buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {
                        val text = buildString {
                            append("¡Mira este lugar en Mallorca! 🌴\n")
                            append("📍 $displayName — ${place.municipality}\n")
                            place.rating?.let { append("⭐ ${"%.1f".format(it)}\n") }
                            append("\nhttps://maps.google.com/?q=${place.location.latitude},${place.location.longitude}")
                            append("\n\nAbrirlo en la app: mallorca://place?placeId=${place.id}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, displayName)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.4f)),
                ) {
                    Icon(Icons.Outlined.Share, "Share", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFavoriteToggle()
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.4f)),
                ) {
                    Icon(
                        if (place.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVisitedToggle()
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isVisited) Color(0xFF2E7D32).copy(alpha = 0.85f)
                            else Color.Black.copy(0.4f)
                        ),
                ) {
                    Text(
                        if (isVisited) "✓" else "○",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Dot indicators (only if multiple photos)
            if (place.photoUrls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(place.photoUrls.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == i) Color.White
                                    else Color.White.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            // Title row
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("📍 ${place.municipality}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                place.priceLevel?.let {
                    androidx.compose.material3.Badge(
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(it.name) }
                }
            }

            // Rating
            place.rating?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★★★★★".take((it + 0.5f).toInt().coerceIn(0, 5)), color = Color(0xFFF9A825))
                    Text(
                        " ${"%.1f".format(it)} (${place.reviewCount} reviews)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Description
            if (displayDescription.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(displayDescription, style = MaterialTheme.typography.bodyMedium)
            }

            // Hidden gem badge
            if (isHiddenGem) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(Color(0xFF4A148C).copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("💎", style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.place_hidden_gem_unlocked),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6A1B9A),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Ticket purchase button
            place.ticketUrl?.let { url ->
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE65100),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.ConfirmationNumber, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.place_buy_ticket), fontWeight = FontWeight.Bold)
                }
            }

            // Beach conditions card (only for beach places with marine data)
            if (weather != null && (weather.waveHeightM != null || weather.seaTempC != null)) {
                Spacer(Modifier.height(16.dp))
                BeachConditionsCard(weather = weather)
            }

            // SUP traffic light card
            if (isSUPPlace) {
                Spacer(Modifier.height(16.dp))
                if (supStatus != null) {
                    SUPTrafficLightCard(status = supStatus)
                } else {
                    SUPLoadingCard()
                }
            }

            // Info card
            if (place.address != null || displayTips.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        place.address?.let {
                            Row(verticalAlignment = Alignment.Top) {
                                Text("📍", modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(stringResource(R.string.place_address_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (displayTips.isNotEmpty()) Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        displayTips.forEachIndexed { i, tip ->
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Row {
                                Text("💡", modifier = Modifier.size(20.dp))
                                Text(tip, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            // Discounts card
            if (discounts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                discounts.forEach { discount ->
                    DiscountCard(discount = discount, locale = locale)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Directions button
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val uri = Uri.parse("geo:${place.location.latitude},${place.location.longitude}?q=${Uri.encode(place.name)}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Directions, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.place_get_directions))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAddToTrip,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) {
                Text(stringResource(R.string.place_add_to_trip))
            }
        }
    }

    // Fullscreen photo viewer
    val page = fullscreenPage
    if (page != null && place.photoUrls.isNotEmpty()) {
        PhotoFullscreenViewer(
            photos = place.photoUrls.toImmutableList(),
            initialPage = page,
            onDismiss = { fullscreenPage = null },
        )
    }
}

private fun Color(value: Long) = Color(value.toULong())

@Composable
private fun BeachConditionsCard(
    weather: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    val flagLabel = when {
        (weather.waveHeightM ?: 0f) > 1.5f || weather.windKmh > 35f -> stringResource(R.string.place_flag_red)
        (weather.waveHeightM ?: 0f) > 0.8f || weather.windKmh > 20f -> stringResource(R.string.place_flag_yellow)
        else -> stringResource(R.string.place_flag_green)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🌊", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.place_beach_conditions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    flagLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weather.waveHeightM?.let {
                    BeachMetric(emoji = "🌊", label = stringResource(R.string.place_metric_waves), value = "${"%.1f".format(it)}m")
                }
                weather.seaTempC?.let {
                    BeachMetric(emoji = "🌡️", label = stringResource(R.string.place_metric_sea), value = "${it.toInt()}°C")
                }
                BeachMetric(emoji = "💨", label = stringResource(R.string.place_metric_wind), value = "${weather.windKmh.toInt()} km/h")
                BeachMetric(emoji = "🌡️", label = stringResource(R.string.place_metric_air), value = "${weather.tempC.toInt()}°C")
            }
        }
    }
}

@Composable
private fun BeachMetric(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF546E7A))
    }
}

@Composable
private fun DiscountCard(
    discount: Discount,
    locale: String,
    modifier: Modifier = Modifier,
) {
    val displayHeadline = when (locale) {
        "es" -> discount.headlineEs.ifEmpty { discount.headline }
        "de" -> discount.headlineDe.ifEmpty { discount.headline }
        "ru" -> discount.headlineRu.ifEmpty { discount.headline }
        "zh" -> discount.headlineZh.ifEmpty { discount.headline }
        else -> discount.headline
    }
    val displayTerms = when (locale) {
        "es" -> discount.termsEs.ifEmpty { discount.terms }
        "de" -> discount.termsDe.ifEmpty { discount.terms }
        "ru" -> discount.termsRu.ifEmpty { discount.terms }
        "zh" -> discount.termsZh.ifEmpty { discount.terms }
        else -> discount.terms
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🎟️", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayHeadline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5C3D00),
                )
                if (displayTerms.isNotEmpty()) {
                    Text(
                        displayTerms,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF795548),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFD600))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        discount.code,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A00),
                    )
                }
                Text(stringResource(R.string.place_discount_code), style = MaterialTheme.typography.labelSmall, color = Color(0xFF795548))
            }
        }
    }
}

@Composable
private fun SUPLoadingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.place_sup_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF0D47A1),
            )
        }
    }
}

@Composable
private fun SUPTrafficLightCard(
    status: SUPWeatherStatus,
    modifier: Modifier = Modifier,
) {
    val light = status.trafficLight
    val cardBg = when (light) {
        SUPTrafficLight.GREEN  -> Color(0xFFE8F5E9)
        SUPTrafficLight.YELLOW -> Color(0xFFFFFDE7)
        SUPTrafficLight.RED    -> Color(0xFFFFEBEE)
    }
    val accentColor = when (light) {
        SUPTrafficLight.GREEN  -> Color(0xFF2E7D32)
        SUPTrafficLight.YELLOW -> Color(0xFFF57F17)
        SUPTrafficLight.RED    -> Color(0xFFC62828)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🏄", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.place_sup_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${light.emoji} ${light.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }

            // Detail text
            Text(
                light.detail,
                style = MaterialTheme.typography.bodySmall,
                color = accentColor,
            )

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val windDir = windDegToCardinal(status.windDirectionDeg)
                val windCategoryLabel = when (status.windCategory) {
                    WindCategory.ONSHORE     -> stringResource(R.string.place_wind_onshore)
                    WindCategory.OFFSHORE    -> stringResource(R.string.place_wind_offshore)
                    WindCategory.CROSS_SHORE -> stringResource(R.string.place_wind_cross)
                }
                BeachMetric(
                    emoji = "💨",
                    label = stringResource(R.string.place_metric_wind),
                    value = "${"%.1f".format(status.windKnots)} kn",
                )
                BeachMetric(
                    emoji = "🧭",
                    label = windCategoryLabel,
                    value = windDir,
                )
                status.waveHeightM?.let {
                    BeachMetric(emoji = "🌊", label = stringResource(R.string.place_metric_waves), value = "${"%.1f".format(it)}m")
                }
                status.seaTempC?.let {
                    BeachMetric(emoji = "🌡️", label = stringResource(R.string.place_metric_sea), value = "${it.toInt()}°C")
                }
            }

            // Gust warning
            if (status.hasGustWarning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6F00).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⚠️", style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.place_sup_gust_warning, status.windGustKnots),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Offshore danger warning
            if (status.windCategory == WindCategory.OFFSHORE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFC62828).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🚨", style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.place_sup_offshore_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenGemLockScreen(
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gemPurple = Color(0xFF6A1B9A)
    val gemGold = Color(0xFFF9A825)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A0030), Color(0xFF2D1B4E), Color(0xFF0D0D1A)),
                )
            )
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("💎", style = MaterialTheme.typography.displayLarge)

            Text(
                stringResource(R.string.place_hidden_gem_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = gemGold,
            )

            Text(
                stringResource(R.string.place_hidden_gem_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            // Perks list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    stringResource(R.string.place_hidden_gem_perk_1),
                    stringResource(R.string.place_hidden_gem_perk_2),
                    stringResource(R.string.place_hidden_gem_perk_3),
                    stringResource(R.string.place_hidden_gem_perk_4),
                ).forEach { perk ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(perk, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }

            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = gemGold, contentColor = Color(0xFF1A0030)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.place_hidden_gem_unlock), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }

            Text(
                stringResource(R.string.place_hidden_gem_no_subscription),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
    }
}

private fun Place.localizedName(locale: String) = when (locale) {
    "es" -> nameEs.ifEmpty { name }
    "de" -> nameDe.ifEmpty { name }
    "ru" -> nameRu.ifEmpty { name }
    "zh" -> nameZh.ifEmpty { name }
    else -> name
}

private fun Place.localizedDescription(locale: String) = when (locale) {
    "es" -> descriptionEs.ifEmpty { description }
    "de" -> descriptionDe.ifEmpty { description }
    "ru" -> descriptionRu.ifEmpty { description }
    "zh" -> descriptionZh.ifEmpty { description }
    else -> description
}

private fun Place.localizedTips(locale: String) = when (locale) {
    "es" -> tipsEs.ifEmpty { tips }
    "de" -> tipsDe.ifEmpty { tips }
    "ru" -> tipsRu.ifEmpty { tips }
    "zh" -> tipsZh.ifEmpty { tips }
    else -> tips
}
