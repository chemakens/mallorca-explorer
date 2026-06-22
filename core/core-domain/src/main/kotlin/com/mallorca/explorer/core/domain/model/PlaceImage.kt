package com.mallorca.explorer.core.domain.model

data class PlaceImage(
    val url: String,
    val source: ImageSource = ImageSource.OTHER,
    val author: String? = null,
)

enum class ImageSource { AETIB, PIXABAY, PEXELS, UNSPLASH, MINE, OTHER }

fun PlaceImage.attributionText(): String? = when (source) {
    ImageSource.AETIB ->
        if (author != null) "$author / AETIB - Govern de les Illes Balears"
        else "AETIB - Govern de les Illes Balears"
    else -> null
}
