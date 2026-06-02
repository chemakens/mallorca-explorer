package com.mallorca.explorer.feature.itinerary.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mallorca.explorer.core.domain.model.CommercialBlock
import com.mallorca.explorer.core.domain.model.RouteWaypoint
import com.mallorca.explorer.core.domain.model.SUPTrafficLight
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WaypointRole
import com.mallorca.explorer.core.domain.model.windDegToCardinal

// ─── BLOQUE 1: ALERTA CLIMÁTICA ──────────────────────────────────────────────

@Composable
fun OfflineWeatherWarning(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFFFF9800), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚠️", style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Sin datos del mar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                )
                Text(
                    "No se puede verificar el estado del mar. Comprueba las condiciones antes de entrar al agua.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBF360C),
                )
            }
        }
    }
}

@Composable
fun SupWeatherAlertCard(
    supStatus: SUPWeatherStatus,
    tramuntanaNoteEs: String = "",
    caveEntryMaxWaveM: Float? = null,
    isStale: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (bgColor, borderColor, textColor) = when (supStatus.trafficLight) {
        SUPTrafficLight.GREEN  -> Triple(Color(0xFFE8F5E9), Color(0xFF4CAF50), Color(0xFF1B5E20))
        SUPTrafficLight.YELLOW -> Triple(Color(0xFFFFF8E1), Color(0xFFFFB300), Color(0xFFE65100))
        SUPTrafficLight.RED    -> Triple(Color(0xFFFFEBEE), Color(0xFFF44336), Color(0xFFB71C1C))
    }

    AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ── Traffic light banner ──
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(supStatus.trafficLight.emoji, style = MaterialTheme.typography.headlineSmall)
                    Column {
                        Text(
                            supStatus.trafficLight.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                        Text(
                            supStatus.trafficLight.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f),
                        )
                    }
                }

                // ── Stale data warning ──
                if (isStale) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF616161).copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⏱", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Datos sin conexión — pueden tener más de 30 min",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // ── Wind metrics row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WindChip("💨 ${String.format("%.1f", supStatus.windKnots)} kt", textColor, bgColor, borderColor)
                    WindChip("↗ ${windDegToCardinal(supStatus.windDirectionDeg)}", textColor, bgColor, borderColor)
                    supStatus.waveHeightM?.let { WindChip("🌊 ${String.format("%.1f", it)} m", textColor, bgColor, borderColor) }
                    supStatus.seaTempC?.let { WindChip("🌡 ${it.toInt()}°C", textColor, bgColor, borderColor) }
                }

                // ── Gust warning ──
                if (supStatus.hasGustWarning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚡", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Ráfagas ${String.format("%.1f", supStatus.windGustKnots)} kt (+${((supStatus.windGustKnots!! / supStatus.windKnots - 1) * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // ── Cave entry status (if route has a conditional cave) ──
                caveEntryMaxWaveM?.let { maxWave ->
                    val caveOk = (supStatus.waveHeightM ?: 0f) <= maxWave &&
                        supStatus.windKnots <= (supStatus.windKnots.coerceAtMost(8f))
                    val caveColor = if (caveOk) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    val caveBg = if (caveOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(caveBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (caveOk) "🟢" else "🔴", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (caveOk) "Cueva Azul: condiciones de entrada OK" else "Cueva Azul: NO entrar hoy — oleaje > ${maxWave}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = caveColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // ── Tramuntana warning ──
                if (tramuntanaNoteEs.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("⚠", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Tramontana: $tramuntanaNoteEs",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WindChip(label: String, textColor: Color, bgColor: Color, borderColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.Medium)
    }
}

// ─── BLOQUE 2: TIMELINE DE RUTA (5 pasos) ────────────────────────────────────

@Composable
fun SupRouteTimeline(
    waypoints: List<RouteWaypoint>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        waypoints.sortedBy { it.order }.forEachIndexed { idx, wp ->
            SupWaypointRow(
                waypoint = wp,
                stepNumber = idx + 1,
                isLast = idx == waypoints.lastIndex,
            )
        }
    }
}

@Composable
private fun SupWaypointRow(
    waypoint: RouteWaypoint,
    stepNumber: Int,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val (dotColor, dotEmoji) = when (waypoint.role) {
        WaypointRole.LAUNCH             -> Color(0xFF2196F3) to "🏄"
        WaypointRole.WAYPOINT           -> Color(0xFF1976D2) to "📍"
        WaypointRole.WAYPOINT_CONDITIONAL -> Color(0xFFFF9800) to "🔵"
        WaypointRole.FINISH             -> Color(0xFF4CAF50) to "🏁"
    }

    Row(modifier = modifier.fillMaxWidth()) {
        // ── Timeline spine ──
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(dotColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(dotEmoji, style = MaterialTheme.typography.bodySmall)
            }
            if (!isLast) {
                Spacer(modifier = Modifier.width(2.dp).height(16.dp).background(Color(0xFFBBDEFB)))
            }
        }

        Spacer(Modifier.width(10.dp))

        // ── Step card ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (waypoint.conditional) Color(0xFFFFF8E1)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "$stepNumber. ${waypoint.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (waypoint.conditional) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Si el mar lo permite", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFFE0B2),
                            ),
                        )
                    }
                    if (waypoint.distanceFromPrevKm > 0f) {
                        Text(
                            "+${waypoint.distanceFromPrevKm} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                if (waypoint.noteEs.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        waypoint.noteEs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (waypoint.conditional && waypoint.conditionNoteEs.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠ ${waypoint.conditionNoteEs}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ─── BLOQUE 3: CTA COMERCIAL ──────────────────────────────────────────────────

@Composable
fun SupCommercialBlock(
    block: CommercialBlock,
    modifier: Modifier = Modifier,
) {
    if (!block.enabled) return
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1)),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Partner header ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🏄", style = MaterialTheme.typography.headlineSmall)
                Column {
                    Text(
                        block.partnerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (block.discountPct > 0) {
                        Text(
                            "🏷 ${block.discountPct}% dto · código: ${block.discountCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // ── Primary CTA ──
            Button(
                onClick = {
                    val uri = Uri.parse("https://wa.me/${block.whatsapp.replace("[^0-9]".toRegex(), "")}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF29B6F6),
                    contentColor = Color(0xFF0D47A1),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    block.ctaLabelEs,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            // ── Secondary actions ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("https://wa.me/${block.whatsapp.replace("[^0-9]".toRegex(), "")}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.5f)),
                ) {
                    Text("💬 WhatsApp", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("tel:${block.phone.replace("[^0-9+]".toRegex(), "")}")
                        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.5f)),
                ) {
                    Text("📞 Llamar", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (block.discountNoteEs.isNotBlank()) {
                Text(
                    block.discountNoteEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.7f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
