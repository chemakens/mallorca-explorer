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
    onSecondaryContainer = AzureDark,
    tertiary = OliveGreen,
    onTertiary = NeutralWhite,
    tertiaryContainer = OliveContainer,
    background = NeutralSurface,
    surface = NeutralWhite,
    onSurface = NeutralOnSurface,
    outline = NeutralOutline,
    onSurfaceVariant = NeutralVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = AzureLight,
    onPrimary = DarkBackground,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = AzureContainer,
    secondary = Terracotta,
    onSecondary = NeutralWhite,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = OliveGreen,
    onTertiary = NeutralWhite,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
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
