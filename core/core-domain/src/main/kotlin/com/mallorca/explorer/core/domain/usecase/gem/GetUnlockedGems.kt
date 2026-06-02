package com.mallorca.explorer.core.domain.usecase.gem

import com.mallorca.explorer.core.domain.repository.HiddenGemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetUnlockedGems @Inject constructor(private val repo: HiddenGemRepository) {
    operator fun invoke(): Flow<Set<String>> = repo.getUnlockedGemIds()
}
