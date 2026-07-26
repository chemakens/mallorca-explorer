package com.mallorca.explorer.core.data.repository

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.mallorca.explorer.core.domain.repository.AnalyticsRepository
import javax.inject.Inject

class AnalyticsRepositoryImpl @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsRepository {

    override fun logGemUnlocked(gemId: String, gemMunicipality: String, unlockMethod: String) {
        firebaseAnalytics.logEvent(
            "gem_unlocked",
            Bundle().apply {
                putString("gem_id", gemId)
                putString("gem_municipality", gemMunicipality)
                putString("unlock_method", unlockMethod)
            },
        )
    }

    override fun logGemCodeAccepted(gemId: String) {
        firebaseAnalytics.logEvent(
            "gem_code_accepted",
            Bundle().apply { putString("gem_id", gemId) },
        )
    }

    override fun logGemViewed(gemId: String, gemMunicipality: String) {
        firebaseAnalytics.logEvent(
            "gem_viewed",
            Bundle().apply {
                putString("gem_id", gemId)
                putString("gem_municipality", gemMunicipality)
            },
        )
    }

    override fun logItineraryViewed(itineraryId: String, itineraryName: String, durationDays: Int) {
        firebaseAnalytics.logEvent(
            "itinerary_viewed",
            Bundle().apply {
                putString("itinerary_id", itineraryId)
                putString("itinerary_name", itineraryName)
                putInt("duration_days", durationDays)
            },
        )
    }

    override fun logItinerarySaveClicked(itineraryId: String, itineraryName: String, durationDays: Int) {
        firebaseAnalytics.logEvent(
            "itinerary_save_clicked",
            Bundle().apply {
                putString("itinerary_id", itineraryId)
                putString("itinerary_name", itineraryName)
                putInt("duration_days", durationDays)
            },
        )
    }

    override fun logEventMoreInfoClicked(eventId: String, eventTitle: String) {
        firebaseAnalytics.logEvent(
            "event_more_info_clicked",
            Bundle().apply {
                putString("event_id", eventId)
                putString("event_title", eventTitle)
            },
        )
    }

    override fun logPlaceViewed(placeId: String, placeCategory: String) {
        firebaseAnalytics.logEvent(
            "place_viewed",
            Bundle().apply {
                putString("place_id", placeId)
                putString("place_category", placeCategory)
            },
        )
    }

    override fun logSearchPerformed(query: String) {
        firebaseAnalytics.logEvent(
            "search_performed",
            Bundle().apply { putString("query", query) },
        )
    }
}
