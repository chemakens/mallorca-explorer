package com.mallorca.explorer.core.data.auth

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acceso a Firestore para datos de usuario: favoritos, gems desbloqueadas, lugares visitados.
 * Paths: users/{uid}/favorites/{placeId}, users/{uid}/gems/{placeId}, users/{uid}/visited/{placeId}
 */
@Singleton
class UserCloudDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun favCol(uid: String) = firestore.collection("users").document(uid).collection("favorites")
    private fun gemsCol(uid: String) = firestore.collection("users").document(uid).collection("gems")
    private fun visitedCol(uid: String) = firestore.collection("users").document(uid).collection("visited")

    // Favoritos
    suspend fun addFavorite(uid: String, placeId: String) {
        try { favCol(uid).document(placeId).set(mapOf("ts" to System.currentTimeMillis())).await() }
        catch (e: Exception) { Timber.w(e, "addFavorite cloud failed") }
    }
    suspend fun removeFavorite(uid: String, placeId: String) {
        try { favCol(uid).document(placeId).delete().await() }
        catch (e: Exception) { Timber.w(e, "removeFavorite cloud failed") }
    }
    suspend fun getFavoriteIds(uid: String): List<String> = try {
        favCol(uid).get().await().documents.map { it.id }
    } catch (e: Exception) { Timber.w(e, "getFavoriteIds failed"); emptyList() }

    // Gems
    suspend fun addGem(uid: String, placeId: String) {
        try { gemsCol(uid).document(placeId).set(mapOf("ts" to System.currentTimeMillis())).await() }
        catch (e: Exception) { Timber.w(e, "addGem cloud failed") }
    }
    suspend fun getGemIds(uid: String): List<String> = try {
        gemsCol(uid).get().await().documents.map { it.id }
    } catch (e: Exception) { Timber.w(e, "getGemIds failed"); emptyList() }

    // Visited
    suspend fun addVisited(uid: String, placeId: String) {
        try { visitedCol(uid).document(placeId).set(mapOf("ts" to System.currentTimeMillis())).await() }
        catch (e: Exception) { Timber.w(e, "addVisited cloud failed") }
    }
    suspend fun removeVisited(uid: String, placeId: String) {
        try { visitedCol(uid).document(placeId).delete().await() }
        catch (e: Exception) { Timber.w(e, "removeVisited cloud failed") }
    }
    suspend fun getVisitedIds(uid: String): List<String> = try {
        visitedCol(uid).get().await().documents.map { it.id }
    } catch (e: Exception) { Timber.w(e, "getVisitedIds failed"); emptyList() }
}
