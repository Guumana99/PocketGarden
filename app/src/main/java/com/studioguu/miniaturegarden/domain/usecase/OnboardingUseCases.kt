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
            ?.let { gardenRepository.saveProgress(it.copy(tutorialStep = TutorialStep.PLANT_SEED)) }
        ActionResult.SUCCESS
    }
}

/**
 * Moves the first steps along as the garden shows each one done. It reads the garden rather
 * than being told what the player did, so it also puts things right after a step was done
 * some other way (or the app was closed half-way through one).
 *
 * - Opening the crate: done once the crate is gone.
 * - Sowing: done once a living plant stands in the garden. A seed comes out of the shop with the
 *   soil round it moist, which would leave nothing to water; the tutorial's seed is set a little
 *   dry instead, below [CareForPlantUseCase.WATERING_THRESHOLD], so the next step can be done.
 * - Watering: done by [wateringDone], at the player's tap; or skipped if the plant is gone.
 */
class FollowTutorialUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** @return the step the player is on now. */
    suspend operator fun invoke(): TutorialStep = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: return@runInTransaction TutorialStep.DONE
        val objects = gardenRepository.getGridObjects()
        val plant = TutorialGuide.tutorialPlant(objects)
        val next = when (progress.tutorialStep) {
            TutorialStep.OPEN_CRATE -> if (objects.none { it.isStarterCrate }) TutorialStep.PLANT_SEED else TutorialStep.OPEN_CRATE
            TutorialStep.PLANT_SEED -> if (plant != null) TutorialStep.WATER_SEEDLING else TutorialStep.PLANT_SEED
            TutorialStep.WATER_SEEDLING -> if (plant != null) TutorialStep.WATER_SEEDLING else TutorialStep.DONE
            TutorialStep.DONE -> TutorialStep.DONE
        }
        // The crate opened and the seed sown since the last look: both steps are taken at once.
        val settled = if (next == TutorialStep.PLANT_SEED && plant != null) TutorialStep.WATER_SEEDLING else next
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
     * current one: the first steps are over.
     *
     * @return true if this is what finished them.
     */
    suspend fun wateringDone(): Boolean = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress()
        if (progress?.tutorialStep != TutorialStep.WATER_SEEDLING) return@runInTransaction false
        gardenRepository.saveProgress(progress.copy(tutorialStep = TutorialStep.DONE))
        true
    }

    companion object {
        /** How moist the tutorial's freshly sown seed is: enough to be safe, dry enough to water. */
        const val SOWN_SEED_MOISTURE = 0.5f
    }
}
