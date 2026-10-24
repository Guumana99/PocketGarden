package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Notes a harvest or a placement in the garden encyclopedia. The first one makes the page ready
 * to be filled in; it does not fill it in and pays nothing. That is the player's to do, in the
 * book, through [RevealCollectionEntryUseCase].
 */
class RecordCollectionUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /**
     * @param colour the colour the harvested plant bloomed in, for a crop that blooms in several
     *   ([PlantSpecies.bloomColourOf]): noted with the page, so that the book shows the colours
     *   the player really grew (#59).
     * @return the page that just became ready, or null if this was not a first time.
     */
    suspend operator fun invoke(objectId: String, trigger: CollectionTrigger, now: Long, colour: String? = null): CollectionEntry? {
        val entry = CollectionEntry.fromObjectId(objectId)?.takeIf { it.trigger == trigger } ?: return null

        val records = gardenRepository.getCollection()
        val existing = count(records, entry.objectId, now)
        if (colour != null && colour in PlantSpecies.forObjectId(objectId).bloomColours) {
            count(records, CollectionEntry.colourRecordId(entry.objectId, colour), now)
        }
        return entry.takeIf { existing == null }
    }

    /** Counts one more under [entryId]; @return the record as it was before, null for a first time. */
    private suspend fun count(records: List<CollectionRecord>, entryId: String, now: Long): CollectionRecord? {
        val existing = records.firstOrNull { it.entryId == entryId }
        gardenRepository.upsertCollectionRecord(
            existing?.copy(count = existing.count + 1)
                ?: CollectionRecord(entryId = entryId, count = 1, firstObtainedAt = now)
        )
        return existing
    }
}

/**
 * Fills in a page of the encyclopedia at the player's request and pays its discovery coins.
 *
 * The coins hang on the page going from "ready" to "revealed" inside one transaction, which can
 * only happen once, so a page can never pay twice.
 */
class RevealCollectionEntryUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** @return the coins paid, or null when the page is not ready (locked, or filled in already). */
    suspend operator fun invoke(entry: CollectionEntry): Int? = gardenRepository.runInTransaction {
        val record = gardenRepository.getCollection().firstOrNull { it.entryId == entry.objectId }
        if (record == null || record.revealed) return@runInTransaction null
        val profile = gardenRepository.getUserProfile() ?: return@runInTransaction null

        gardenRepository.upsertCollectionRecord(record.copy(revealed = true))
        gardenRepository.saveUserProfile(profile.copy(coins = profile.coins + entry.discoveryCoins))
        entry.discoveryCoins
    }
}
