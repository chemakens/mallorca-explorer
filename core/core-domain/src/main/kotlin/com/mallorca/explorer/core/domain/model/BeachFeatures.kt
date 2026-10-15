package com.mallorca.explorer.core.domain.model

/**
 * Características opcionales de una PLAYA, para mostrar en su ficha (sin filtros).
 * Todas anulables: un campo sin dato NO se muestra (nunca se pinta «no» por falta de datos).
 *
 * Reglas de visualización (las aplica la ficha de playa):
 *  - Siempre que tengan dato (positivo o negativo): [pets], [lifeguard], [accessible], [parking].
 *  - Solo si son positivos: [shade], [showers], [food].
 *  - [surface] siempre que haya dato (es descriptivo).
 *  - Si [noServicesConfirmed] == true: una sola línea «Playa sin servicios: lleva agua y sombra».
 *  - [pets] lleva SIEMPRE el aviso «consulta la normativa municipal vigente».
 *
 * La FUENTE y la FECHA de comprobación de cada dato NO viven aquí (no se muestran en la app):
 * se guardan en tools/beach_features/<id>.json.
 */
data class BeachFeatures(
    val surface: BeachSurface? = null,
    val shade: Boolean? = null,
    val showers: Boolean? = null,
    val food: Boolean? = null,
    val lifeguard: Boolean? = null,
    val accessible: Boolean? = null,
    val parking: Boolean? = null,
    val pets: PetPolicy? = null,
    // Nota libre para mascotas (p. ej. temporada de restricción), es/en/de.
    val petsNote: String? = null,
    val petsNoteEs: String? = null,
    val petsNoteDe: String? = null,
    val noServicesConfirmed: Boolean? = null,
) {
    /** true si hay AL MENOS un dato que mostrar. */
    val hasAny: Boolean
        get() = surface != null || shade != null || showers != null || food != null ||
            lifeguard != null || accessible != null || parking != null || pets != null ||
            noServicesConfirmed == true
}

/** Tipo de suelo de la playa (vocabulario fijo → traducible por recurso de texto). */
enum class BeachSurface { FINE_SAND, SAND, GRAVEL, PEBBLES, ROCK, MIXED }

/** Política de mascotas. SEASONAL = permitido solo parte del año (detalle en la nota). */
enum class PetPolicy { ALLOWED, PROHIBITED, SEASONAL }

/** Nota de mascotas en el idioma pedido (es/de con respaldo al inglés). */
fun BeachFeatures.localizedPetsNote(locale: String): String? = when (locale) {
    "es" -> petsNoteEs ?: petsNote
    "de" -> petsNoteDe ?: petsNote
    else -> petsNote
}?.takeIf { it.isNotBlank() }
