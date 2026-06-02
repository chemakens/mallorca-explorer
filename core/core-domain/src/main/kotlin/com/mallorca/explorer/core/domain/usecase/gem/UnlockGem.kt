package com.mallorca.explorer.core.domain.usecase.gem

import com.mallorca.explorer.core.domain.repository.HiddenGemRepository
import javax.inject.Inject

class UnlockGem @Inject constructor(private val repo: HiddenGemRepository) {
    suspend operator fun invoke(placeId: String) = repo.unlockGem(placeId)
}
