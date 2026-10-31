package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Takes crops that are no longer in the game out of a saved garden (#68: the pumpkin, whose
 * place the shiitake took). Its plants are taken up, the tilled soil under them left as it is,
 * and its seeds are taken out of the bag and paid back at what they cost. Things withdrawn from
 * the game go the same way (#86: the brick edging), and those standing in the garden are paid back too.
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
        var refund = 0L
        for (thing in gardenRepository.getGridObjects()) {
            val price = RETIRED_THINGS[thing.objectId]
            if (price == null && thing.objectId !in RETIRED_PLANTS) continue
            refund += price?.toLong() ?: 0L
            gardenRepository.deleteGridObject(thing.instanceId)
        }
        for (slot in gardenRepository.getInventory()) {
            val price = RETIRED_SEEDS[slot.itemId] ?: RETIRED_THINGS[slot.itemId] ?: continue
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

        /**
         * What is withdrawn from the game, by the id it has both in the garden and in the bag, with
         * what it cost (#86: the brick edging). Taken off this list when it comes back.
         */
        val RETIRED_THINGS = mapOf(RunKind.BRICK_EDGING.objectId to 60)
    }
}
