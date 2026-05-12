package com.mallorca.explorer.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Azure,
    onPrimary = NeutralWhite,
    primaryContainer = AzureContainer,
    onPrimaryContainer = AzureDark,
    secondary = Terracotta,
    onSecondary = NeutralWhite,
    secondaryContainer = TerracottaContainer,
    background = NeutralSurface,
    surface = NeutralWhite,
    onSurface = NeutralOnSurface,
    outline = NeutralOutline,
    onSurfaceVariant = NeutralVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = AzureLight,
    onPrimary = AzureDark,
    primaryContainer = AzureDark,
    secondary = Terracotta,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF2B2930),
)

@Composable
fun MallorcaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private fun Color(value: Long) = androidx.compose.ui.graphics.Color(value.toULong())
