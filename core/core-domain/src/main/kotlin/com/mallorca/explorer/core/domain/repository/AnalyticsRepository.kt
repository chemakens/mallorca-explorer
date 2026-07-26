package com.mallorca.explorer.core.domain.repository

interface AnalyticsRepository {
    fun logGemUnlocked(gemId: String, gemMunicipality: String, unlockMethod: String)
    fun logGemCodeAccepted(gemId: String)
    fun logGemViewed(gemId: String, gemMunicipality: String)
    fun logItineraryViewed(itineraryId: String, itineraryName: String, durationDays: Int)
    fun logItinerarySaveClicked(itineraryId: String, itineraryName: String, durationDays: Int)
    fun logEventMoreInfoClicked(eventId: String, eventTitle: String)
    fun logPlaceViewed(placeId: String, placeCategory: String)
    fun logSearchPerformed(query: String)
}
