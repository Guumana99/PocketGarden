package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.StarterGarden
import com.studioguu.miniaturegarden.domain.model.TutorialGuide
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Opens the starter crate: it goes from the garden and its seed goes into the bag, stacked onto
 * seeds of the same kind or else into the first free slot. With the bag full nothing happens and
 * the crate stays where it is, to be opened later.
 */
class OpenStarterCrateUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val crate = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId && it.isStarterCrate }
            ?: return@runInTransaction ActionResult.NOT_FOUND

        val itemId = StarterGarden.CRATE_REWARD.itemId
        val slots = gardenRepository.getInventory()
        val slot = slots.firstOrNull { it.itemId == itemId && !it.isEmpty }
            ?.let { it.copy(amount = it.amount + StarterGarden.CRATE_REWARD_AMOUNT) }
            ?: slots.firstOrNull { it.isEmpty }
                ?.let { InventorySlot(it.slotIndex, itemId, StarterGarden.CRATE_REWARD_AMOUNT) }
            ?: return@runInTransaction ActionResult.INVENTORY_FULL

        gardenRepository.deleteGridObject(crate.instanceId)
        gardenRepository.upsertInventorySlot(slot)
        gardenRepository.getProgress()
            ?.takeIf { it.tutorialStep == TutorialStep.OPEN_CRATE }
            ?.let { gardenRepository.saveProgress(it.copy(tutorialStep = TutorialStep.TILL_SOIL)) }
        ActionResult.SUCCESS
    }
}

/**
 * Moves the first steps along as the garden shows each one done. It reads the garden rather
 * than being told what the player did, so it also puts things right after a step was done
 * some other way (or the app was closed half-way through one).
 *
 * - Opening the crate: done once the crate is gone.
 * - Tilling: done once there is tilled soil anywhere (a plant always stands on some).
 * - Sowing: done once a living plant stands in the garden. A seed comes out of the shop with the
 *   soil round it moist, which would leave nothing to water; the tutorial's seed is set a little
 *   dry instead, below [CareForPlantUseCase.WATERING_THRESHOLD], so the next step can be done.
 * - Watering: done by [wateringDone], at the player's tap; or passed over if the plant is gone.
 *
 * After that the closing board and the handing over of features wait for the player's own taps
 * ([acknowledge]); the garden does not move them on.
 */
class FollowTutorialUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** @return the step the player is on now. */
    suspend operator fun invoke(): TutorialStep = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: return@runInTransaction TutorialStep.DONE
        val objects = gardenRepository.getGridObjects()
        val plant = TutorialGuide.tutorialPlant(objects)
        val tilled = objects.any { it.isTilledSoil }
        // Several steps may have been done since the last look: take them all at once.
        var settled = progress.tutorialStep
        while (true) {
            val next = when (settled) {
                TutorialStep.OPEN_CRATE -> if (objects.none { it.isStarterCrate }) TutorialStep.TILL_SOIL else settled
                TutorialStep.TILL_SOIL -> if (tilled || plant != null) TutorialStep.PLANT_SEED else settled
                TutorialStep.PLANT_SEED -> if (plant != null) TutorialStep.WATER_SEEDLING else settled
                TutorialStep.WATER_SEEDLING -> if (plant != null) settled else TutorialStep.FINISHED
                else -> settled
            }
            if (next == settled) break
            settled = next
        }
        if (settled == TutorialStep.WATER_SEEDLING && progress.tutorialStep != TutorialStep.WATER_SEEDLING && plant != null &&
            plant.moistureLevel >= CareForPlantUseCase.WATERING_THRESHOLD
        ) {
            gardenRepository.upsertGridObject(plant.copy(moistureLevel = SOWN_SEED_MOISTURE))
        }
        if (settled != progress.tutorialStep) gardenRepository.saveProgress(progress.copy(tutorialStep = settled))
        settled
    }

    /**
     * The player has watered (or tried to water, in the rain) while the watering step was the
     * current one: the guided steps are over and the closing board is due.
     *
     * @return true if this is what finished them.
     */
    suspend fun wateringDone(): Boolean = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress()
        if (progress?.tutorialStep != TutorialStep.WATER_SEEDLING) return@runInTransaction false
        gardenRepository.saveProgress(progress.copy(tutorialStep = TutorialStep.FINISHED))
        true
    }

    /**
     * The player has read the closing board, or the introduction of a feature just handed over
     * ([seen] is the step that was showing): the next feature is handed over, or after the last
     * one the garden is theirs. A tap on a board that is no longer current changes nothing.
     *
     * @return the step the player is on now.
     */
    suspend fun acknowledge(seen: TutorialStep): TutorialStep = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: return@runInTransaction TutorialStep.DONE
        val current = progress.tutorialStep
        if (current != seen || current.isGuiding || current == TutorialStep.DONE) return@runInTransaction current
        val next = TutorialStep.entries[current.ordinal + 1]
        gardenRepository.saveProgress(progress.copy(tutorialStep = next))
        next
    }

    companion object {
        /** How moist the tutorial's freshly sown seed is: enough to be safe, dry enough to water. */
        const val SOWN_SEED_MOISTURE = 0.5f
    }
}
