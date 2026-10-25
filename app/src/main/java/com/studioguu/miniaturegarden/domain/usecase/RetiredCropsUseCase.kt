package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Takes crops that are no longer in the game out of a saved garden (#68: the pumpkin, whose
 * place the shiitake took). Its plants are taken up, the tilled soil under them left as it is,
 * and its seeds are taken out of the bag and paid back at what they cost.
 *
 * Run at every start, before anything else reads the garden. It does its work only once: what
 * it takes away cannot come back (nothing sells it any more), so on later starts it finds
 * nothing and changes nothing, and seeds are never paid back twice.
 */
class RetiredCropsUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** @return the coins paid back. */
    suspend operator fun invoke(): Long = gardenRepository.runInTransaction {
        for (plant in gardenRepository.getGridObjects().filter { it.objectId in RETIRED_PLANTS }) {
            gardenRepository.deleteGridObject(plant.instanceId)
        }
        var refund = 0L
        for (slot in gardenRepository.getInventory()) {
            val price = RETIRED_SEEDS[slot.itemId] ?: continue
            refund += price.toLong() * slot.amount
            gardenRepository.upsertInventorySlot(InventorySlot(slot.slotIndex))
        }
        if (refund > 0) {
            gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it.copy(coins = it.coins + refund)) }
        }
        refund
    }

    companion object {
        /** Plants no longer in the game (#68). */
        val RETIRED_PLANTS = setOf("plant_pumpkin")

        /** Seeds no longer sold, with what they cost (#68: the pumpkin's 70). */
        val RETIRED_SEEDS = mapOf("seed_pumpkin" to 70)
    }
}
