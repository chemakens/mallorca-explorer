package com.mallorca.explorer.core.domain.repository

import com.mallorca.explorer.core.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<AuthUser?>
    suspend fun signInWithGoogle(activityContext: Any): Result<AuthUser>
    suspend fun signOut()
}
