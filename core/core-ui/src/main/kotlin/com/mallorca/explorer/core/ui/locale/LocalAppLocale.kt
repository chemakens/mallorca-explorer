package com.mallorca.explorer.core.ui.locale

import androidx.compose.runtime.compositionLocalOf

/**
 * Idioma activo de la app (es/en/de), provisto en la raíz de la UI desde LocaleSource
 * (ver MainActivity). Cualquier composable puede leerlo con `LocalAppLocale.current` para
 * localizar textos de datos (place.localizedName/Description/Tips) sin depender de que la
 * pantalla reciba un `locale` por parámetro: así ninguna llamada puede "olvidar" el idioma.
 *
 * Por defecto "en"; se re-provee con el valor real en el arranque y cambia al instante cuando
 * el usuario elige otro idioma en Ajustes.
 */
val LocalAppLocale = compositionLocalOf { "en" }
