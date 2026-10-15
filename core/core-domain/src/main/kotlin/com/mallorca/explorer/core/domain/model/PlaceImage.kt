package com.mallorca.explorer.core.domain.model

data class PlaceImage(
    val url: String,
    val source: ImageSource = ImageSource.OTHER,
    val author: String? = null,
    // Nombre libre de la fuente cuando no es una de las del enum (p. ej. "Jordi").
    // Opcional y retrocompatible: si es null, no cambia nada.
    val sourceName: String? = null,
    // Fraction (0f-1f) of the image height that should sit above the header
    // crop's visible top edge. Null keeps the default TopCenter behavior.
    val heroFocalY: Float? = null,
    // Pie de foto corto (es/en/de), p. ej. «Escaleras de las casas». Opcional y
    // retrocompatible. `caption` es la base (inglés); captionEs/captionDe traducen.
    // PORTADA/PRINCIPAL son el PAPEL de la foto, no un pie: no se guardan aquí.
    val caption: String? = null,
    val captionEs: String? = null,
    val captionDe: String? = null,
)

enum class ImageSource { AETIB, PIXABAY, PEXELS, UNSPLASH, MINE, OTHER }

/** Pie de foto en el idioma pedido (es/de con respaldo al inglés). null si no hay pie. */
fun PlaceImage.localizedCaption(locale: String): String? = when (locale) {
    "es" -> captionEs ?: caption
    "de" -> captionDe ?: caption
    else -> caption
}?.takeIf { it.isNotBlank() }

fun PlaceImage.attributionText(): String? = when (source) {
    ImageSource.AETIB ->
        if (author != null) "$author / AETIB - Govern de les Illes Balears"
        else "AETIB - Govern de les Illes Balears"
    // Fotos de otras fuentes con autor en el nombre: "<autor> · Fuente: <fuente>".
    else -> when {
        author != null && sourceName != null -> "$author · Fuente: $sourceName"
        author != null -> author
        else -> null
    }
}
