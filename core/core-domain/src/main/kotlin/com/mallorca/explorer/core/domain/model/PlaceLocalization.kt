package com.mallorca.explorer.core.domain.model

/**
 * Localización centralizada de los TEXTOS DE DATOS de un [Place] (ÚNICA fuente de verdad).
 *
 * Regla del proyecto: nunca mostrar `place.name` / `place.description` / `place.tips`
 * directamente en la UI; usar SIEMPRE estas extensiones con el idioma activo
 * (normalmente `LocalAppLocale.current` o el `locale` del uiState, que sale de LocaleSource).
 *
 * `locale` es un código ISO de 2 letras (es/en/de; también ru/zh si existieran en datos).
 * Si el campo del idioma pedido está vacío, se usa el texto base en inglés (fallback).
 */
fun Place.localizedName(locale: String): String = when (locale) {
    "es" -> nameEs.ifEmpty { name }
    "de" -> nameDe.ifEmpty { name }
    "ru" -> nameRu.ifEmpty { name }
    "zh" -> nameZh.ifEmpty { name }
    else -> name
}

fun Place.localizedDescription(locale: String): String = when (locale) {
    "es" -> descriptionEs.ifEmpty { description }
    "de" -> descriptionDe.ifEmpty { description }
    "ru" -> descriptionRu.ifEmpty { description }
    "zh" -> descriptionZh.ifEmpty { description }
    else -> description
}

fun Place.localizedTips(locale: String): List<String> = when (locale) {
    "es" -> tipsEs.ifEmpty { tips }
    "de" -> tipsDe.ifEmpty { tips }
    "ru" -> tipsRu.ifEmpty { tips }
    "zh" -> tipsZh.ifEmpty { tips }
    else -> tips
}
