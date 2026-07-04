package com.mallorca.explorer.core.domain.model

data class PlaceImage(
    val url: String,
    val source: ImageSource = ImageSource.OTHER,
    val author: String? = null,
    // Fraction (0f-1f) of the image height that should sit above the header
    // crop's visible top edge. Null keeps the default TopCenter behavior.
    val heroFocalY: Float? = null,
)

enum class ImageSource { AETIB, PIXABAY, PEXELS, UNSPLASH, MINE, OTHER }

fun PlaceImage.attributionText(): String? = when (source) {
    ImageSource.AETIB ->
        if (author != null) "$author / AETIB - Govern de les Illes Balears"
        else "AETIB - Govern de les Illes Balears"
    else -> null
}
