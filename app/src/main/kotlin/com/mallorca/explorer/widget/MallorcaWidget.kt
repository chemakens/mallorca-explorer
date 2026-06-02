package com.mallorca.explorer.widget

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mallorca.explorer.MainActivity

class MallorcaWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: android.content.Context, id: androidx.glance.GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val tempC    = prefs[PREF_TEMP_C] ?: -999
            val windKnots = prefs[PREF_WIND_KNOTS] ?: 0f
            val windDir  = prefs[PREF_WIND_DIR] ?: ""
            val supStatus = prefs[PREF_SUP_STATUS] ?: "UNKNOWN"
            val weatherEmoji = prefs[PREF_WEATHER_EMOJI] ?: "🌤️"
            val hasData  = tempC != -999

            val supEmoji = when (supStatus) {
                "GREEN"  -> "🟢"
                "YELLOW" -> "🟡"
                "RED"    -> "🔴"
                else     -> "⚪"
            }
            val supLabel = when (supStatus) {
                "GREEN"  -> "Condiciones ideales"
                "YELLOW" -> "Moderado"
                "RED"    -> "No recomendado"
                else     -> "Sin datos"
            }

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.surface)
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        // Header: app name
                        Text(
                            "🌴 Mallorca Explorer",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Spacer(GlanceModifier.height(10.dp))

                        if (hasData) {
                            // Temperature row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$weatherEmoji  ${tempC}°C",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                            Spacer(GlanceModifier.height(10.dp))

                            // SUP row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "🏄 ",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 13.sp,
                                    ),
                                )
                                Spacer(GlanceModifier.width(2.dp))
                                Column {
                                    Text(
                                        "$supEmoji $supLabel",
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    )
                                    if (windKnots > 0f) {
                                        Text(
                                            "💨 ${"%.1f".format(windKnots)} kn${if (windDir.isNotEmpty()) " · $windDir" else ""}",
                                            style = TextStyle(
                                                color = GlanceTheme.colors.onSurfaceVariant,
                                                fontSize = 11.sp,
                                            ),
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Cargando datos…",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        val PREF_TEMP_C       = intPreferencesKey("temp_c")
        val PREF_WIND_KNOTS   = floatPreferencesKey("wind_knots")
        val PREF_WIND_DIR     = stringPreferencesKey("wind_dir")
        val PREF_SUP_STATUS   = stringPreferencesKey("sup_status")
        val PREF_WEATHER_EMOJI = stringPreferencesKey("weather_emoji")
        val PREF_UPDATED_EPOCH = longPreferencesKey("updated_epoch")
    }
}
