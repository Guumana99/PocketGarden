package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.CollectionReward
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Notes a harvest or a placement in the garden encyclopedia and works out what it earned.
 *
 * Rewards hang on the moment a page first appears in storage, which can only happen once, so
 * nothing needs to remember whether a reward was already paid. The caller credits the returned
 * reward to the profile, inside the same transaction as the action that caused it.
 */
class RecordCollectionUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(objectId: String, trigger: CollectionTrigger, now: Long): CollectionReward {
        val entry = CollectionEntry.fromObjectId(objectId)?.takeIf { it.trigger == trigger }
            ?: return CollectionReward.NONE

        val records = gardenRepository.getCollection()
        val existing = records.firstOrNull { it.entryId == entry.objectId }
        gardenRepository.upsertCollectionRecord(
            existing?.copy(count = existing.count + 1)
                ?: CollectionRecord(entryId = entry.objectId, count = 1, firstObtainedAt = now)
        )
        if (existing != null) return CollectionReward.NONE

        val discoveredIds = records.mapTo(HashSet()) { it.entryId } + entry.objectId
        val completed = CollectionEntry.entries.all { it.objectId in discoveredIds }
        return CollectionReward(
            coins = entry.discoveryCoins + if (completed) CollectionEntry.COMPLETION_COINS else 0,
            xp = if (completed) CollectionEntry.COMPLETION_XP else 0,
            discovered = entry,
            completed = completed,
        )
    }
}
