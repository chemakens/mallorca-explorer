package com.mallorca.explorer.core.ui.component

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mallorca.explorer.core.ui.R
import com.mallorca.explorer.core.domain.model.SUPTrafficLight
import com.mallorca.explorer.core.domain.model.SUPWeatherStatus
import com.mallorca.explorer.core.domain.model.WindCategory

/**
 * Tarjeta ÚNICA de estado del mar para SUP, compartida por la ficha de lugar SUP y por el
 * detalle de itinerario SUP. Sustituye a las antiguas "Condiciones Ahora" + "Semáforo SUP".
 *
 * Cabecera = estado del semáforo (color + título + frase). Una sola fila de datos (sin repetir):
 * viento (nudos; con racha → viento/racha), dirección respecto a la costa, oleaje, mar y aire.
 * Debajo, los avisos que apliquen (rachas, viento de tierra, cueva, tramontana) y el botón de
 * mapa de viento (Windy) centrado en windMapLat/windMapLng.
 *
 * NO cambia la lógica del semáforo: usa [SUPWeatherStatus.trafficLight]/windCategory/hasGustWarning tal cual.
 */
@Composable
fun SupConditionsCard(
    status: SUPWeatherStatus,
    airTempC: Float?,
    modifier: Modifier = Modifier,
    isStale: Boolean = false,
    tramuntanaText: String? = null,
    caveEntryMaxWaveM: Float? = null,
    windMapLat: Double? = null,
    windMapLng: Double? = null,
) {
    val locale = LocalConfiguration.current.locales[0].language
    val context = LocalContext.current

    val (bgColor, accentColor) = when (status.trafficLight) {
        SUPTrafficLight.GREEN -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        SUPTrafficLight.YELLOW -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
        SUPTrafficLight.RED -> Color(0xFFFFEBEE) to Color(0xFFC62828)
    }
    val label = when (status.trafficLight) {
        SUPTrafficLight.GREEN -> stringResource(R.string.supc_label_green)
        SUPTrafficLight.YELLOW -> stringResource(R.string.supc_label_yellow)
        SUPTrafficLight.RED -> stringResource(R.string.supc_label_red)
    }
    val detail = when (status.trafficLight) {
        SUPTrafficLight.GREEN -> stringResource(R.string.supc_detail_green)
        SUPTrafficLight.YELLOW -> stringResource(R.string.supc_detail_yellow)
        SUPTrafficLight.RED -> stringResource(R.string.supc_detail_red)
    }

    Card(
        modifier = modifier.fillMaxWidth().border(1.5.dp, accentColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Cabecera: estado del semáforo ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status.trafficLight.emoji, style = MaterialTheme.typography.headlineSmall)
                Column {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = accentColor.copy(alpha = 0.85f))
                }
            }

            // ── Fila ÚNICA de datos (sin repetir) ──
            val windValue = status.windGustKnots?.let { gust ->
                "${"%.1f".format(status.windKnots)}/${"%.1f".format(gust)}"
            } ?: "%.1f".format(status.windKnots)
            val coastLabel = when (status.windCategory) {
                WindCategory.ONSHORE -> stringResource(R.string.supc_wind_onshore)
                WindCategory.OFFSHORE -> stringResource(R.string.supc_wind_offshore)
                WindCategory.CROSS_SHORE -> stringResource(R.string.supc_wind_cross)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SupMetric("💨", stringResource(R.string.supc_metric_wind), "$windValue kt", accentColor)
                SupMetric("🧭", coastLabel, windCardinal(status.windDirectionDeg, locale), accentColor)
                status.waveHeightM?.let { SupMetric("🌊", stringResource(R.string.supc_metric_wave), "${"%.1f".format(it)}m", accentColor) }
                status.seaTempC?.let { SupMetric("🌡", stringResource(R.string.supc_metric_sea), "${it.toInt()}°", accentColor) }
                airTempC?.let { SupMetric("☀️", stringResource(R.string.supc_metric_air), "${it.toInt()}°", accentColor) }
            }

            // ── Avisos ──
            if (isStale) WarningLine("⏱", stringResource(R.string.supc_stale), Color(0xFF616161), Color(0xFF616161).copy(alpha = 0.12f))
            if (status.windCategory == WindCategory.OFFSHORE) {
                WarningLine("🚨", stringResource(R.string.supc_offshore_warning), Color(0xFFC62828), Color(0xFFC62828).copy(alpha = 0.12f))
            }
            if (status.hasGustWarning && status.windGustKnots != null) {
                val pct = ((status.windGustKnots!! / status.windKnots - 1) * 100).toInt()
                WarningLine("⚡", stringResource(R.string.supc_gust_warning, status.windGustKnots!!, pct), Color(0xFFE65100), Color(0xFFFFF3E0))
            }
            caveEntryMaxWaveM?.let { maxWave ->
                val caveOk = (status.waveHeightM ?: 0f) <= maxWave
                WarningLine(
                    if (caveOk) "🟢" else "🔴",
                    if (caveOk) stringResource(R.string.supc_cave_ok) else stringResource(R.string.supc_cave_warning, maxWave),
                    if (caveOk) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                    if (caveOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                )
            }
            if (!tramuntanaText.isNullOrBlank()) {
                WarningLine("⚠", "Tramontana: $tramuntanaText", Color(0xFFE65100), Color(0xFFFFF3E0))
            }

            // ── Botón mapa de viento (Windy) ──
            if (windMapLat != null && windMapLng != null) {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buildWindyUrl(windMapLat, windMapLng))))
                        } catch (e: ActivityNotFoundException) {
                            // sin navegador: no hacer nada
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("🌊 ${stringResource(R.string.supc_wind_map)}")
                }
            }
        }
    }
}

@Composable
private fun SupMetric(emoji: String, label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.7f))
    }
}

@Composable
private fun WarningLine(icon: String, text: String, textColor: Color, bg: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(icon, style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall, color = textColor, fontWeight = FontWeight.Medium)
    }
}

private fun windCardinal(deg: Int, locale: String): String {
    val i = ((deg + 22.5) / 45).toInt() % 8
    return when (locale) {
        "es" -> listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[i]
        "de" -> listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")[i]
        else -> listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[i]
    }
}

/** URL del mapa de viento de Windy centrado en lat/lng (overlay de viento). Sin clave. */
fun buildWindyUrl(latitude: Double, longitude: Double): String =
    "https://embed.windy.com/embed2.html?" +
        "lat=$latitude&lon=$longitude&zoom=11&level=surface&overlay=wind&" +
        "menu=&message=&marker=&calendar=&pressure=&type=map&location=coordinates&" +
        "detail=&metricWind=kt&metricTemp=%C2%B0C&radarRange=-1"
