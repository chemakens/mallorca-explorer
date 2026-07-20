package com.mallorca.explorer.core.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.mallorca.explorer.core.data.database.entity.EventEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val EVENTS_COLLECTION = "events"

class FirestoreEventDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : EventRemoteDataSource {

    override suspend fun fetchAll(): List<EventEntity> =
        fetchSnapshot().mapNotNull { doc -> doc.toEventEntityOrNull() }

    private suspend fun fetchSnapshot(): QuerySnapshot = suspendCancellableCoroutine { cont ->
        val task = firestore.collection(EVENTS_COLLECTION).get()
        task.addOnSuccessListener { snapshot -> cont.resume(snapshot) }
        task.addOnFailureListener { error -> cont.resumeWithException(error) }
    }

    private fun QueryDocumentSnapshot.toEventEntityOrNull(): EventEntity? = try {
        val title = getString("title") ?: error("missing title")
        val category = getString("category") ?: error("missing category")
        val startDate = getString("start_date") ?: error("missing start_date")

        EventEntity(
            id = id,
            title = title,
            titleEs = getString("title_es") ?: "",
            titleDe = getString("title_de") ?: "",
            titleRu = getString("title_ru") ?: "",
            titleZh = getString("title_zh") ?: "",
            description = getString("description") ?: "",
            descriptionEs = getString("description_es") ?: "",
            descriptionDe = getString("description_de") ?: "",
            descriptionRu = getString("description_ru") ?: "",
            descriptionZh = getString("description_zh") ?: "",
            category = category.uppercase(),
            startDateEpoch = parseDate(startDate),
            endDateEpoch = getString("end_date")?.let { parseDate(it) },
            municipality = getString("municipality") ?: "",
            address = getString("address"),
            isFree = getBoolean("is_free") ?: true,
            price = getString("price"),
            imageUrl = getString("image_url"),
            isRecurring = getBoolean("is_recurring") ?: false,
            recurringDayOfWeek = getLong("recurring_day_of_week")?.toInt(),
            websiteUrl = getString("website_url"),
        )
    } catch (e: Exception) {
        Timber.w(e, "Skipping malformed Firestore event document: $id")
        null
    }

    private fun parseDate(s: String): Long =
        LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
