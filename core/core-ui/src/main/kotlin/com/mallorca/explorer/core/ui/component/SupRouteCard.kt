package com.mallorca.explorer.core.ui.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.Difficulty
import com.mallorca.explorer.core.domain.model.Itinerary
import com.mallorca.explorer.core.ui.R

/**
 * Tarjeta pequeña con foto para una RUTA SUP (itinerario), con el mismo aspecto compacto que
 * [PlaceCard]. Reemplaza en las listas a las antiguas fichas de lugar sup-*. Al pulsar abre el
 * detalle del itinerario (diseño Malgrats). Icono de dificultad por categoría: SUP → 🏄.
 */
@Composable
fun SupRouteCard(
    itinerary: Itinerary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))) {
                if (itinerary.coverPhoto.url.isNotEmpty()) {
                    AsyncImage(
                        model = itinerary.coverPhoto.url,
                        contentDescription = itinerary.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(itinerary.category.emoji, style = MaterialTheme.typography.titleLarge) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = itinerary.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Un solo icono (el de categoría). La dificultad va como palabra: "Dificultad: …".
                val diffText = itinerary.difficulty?.let { " · " + stringResource(it.labelRes()) } ?: ""
                Text(
                    text = "${itinerary.category.emoji} ${itinerary.category.displayName}$diffText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Recurso de texto de la dificultad como palabra ("Dificultad: Fácil/Moderada/Difícil"). */
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.route_difficulty_easy
    Difficulty.MODERATE -> R.string.route_difficulty_moderate
    Difficulty.HARD -> R.string.route_difficulty_hard
}
