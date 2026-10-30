package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMapSource
import com.studioguu.miniaturegarden.domain.model.Gift
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PracticeGarden
import com.studioguu.miniaturegarden.domain.model.Soil
import com.studioguu.miniaturegarden.domain.model.TutorialGuide
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.nextAcquisition
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.PracticeMode
import javax.inject.Inject

/**
 * Sets [gift] down in the garden, in its box, on the free cell nearest the middle. Whatever hands
 * out gifts calls this and nothing else. A garden gets one box of each gift at a time, and the
 * box of a save from before gifts counts as the first one.
 */
class PlaceGiftUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMapSource,
) {
    suspend operator fun invoke(gift: Gift, now: Long): ActionResult = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val boxes = objects.filter { it.isGiftBox }
        if (boxes.any { Gift.inside(it).id == gift.id }) return@runInTransaction ActionResult.SUCCESS
        val map = gardenMap.current()
        val grid = GardenGrid(map, objects)
        val cell = (0 until map.size).flatMap { y -> (0 until map.size).map { x -> GridCell(x, y) } }
            .filter { grid.checkPlacement(Footprint.SINGLE, it.x, it.y, PlacementKind.OBJECT) == PlacementCheck.OK }
            .minWithOrNull(compareBy({ distanceToMiddle(it, map.size) }, { it.y }, { it.x }))
            ?: return@runInTransaction ActionResult.TILE_OCCUPIED
        gardenRepository.upsertGridObject(
            GridObject(
                instanceId = "gift_${gift.id}",
                gridX = cell.x,
                gridY = cell.y,
                objectId = GridObject.GIFT_BOX_OBJECT_ID,
                lastUpdatedTimestamp = now,
                gift = gift.encoded,
            ),
        )
        ActionResult.SUCCESS
    }

    // Doubled, so that the middle of an even-sized garden stays on whole numbers.
    private fun distanceToMiddle(cell: GridCell, size: Int): Int {
        val dx = 2 * cell.x - (size - 1)
        val dy = 2 * cell.y - (size - 1)
        return dx * dx + dy * dy
    }
}

/**
 * Opens a gift box: it goes from the garden and what it held goes to the player, each kind of
 * item stacked onto its own or else into the first free slot. Without room for all of it nothing
 * happens and the box stays where it is, to be opened later.
 */
class OpenGiftUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val box = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId && it.isGiftBox }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
        val gift = Gift.inside(box)

        val slots = gardenRepository.getInventory().filter { it.slotIndex < profile.inventoryCapacity }.toMutableList()
        for (item in gift.items) {
            var left = item.amount
            while (left > 0) {
                val index = slots.indexOfFirst { !it.isEmpty && it.itemId == item.itemId && it.amount < InventorySlot.MAX_STACK }
                    .takeIf { it >= 0 } ?: slots.indexOfFirst { it.isEmpty }
                if (index < 0) return@runInTransaction ActionResult.INVENTORY_FULL
                val slot = slots[index]
                val taken = minOf(left, InventorySlot.MAX_STACK - slot.amount)
                slots[index] = InventorySlot(slot.slotIndex, item.itemId, slot.amount + taken, slots.nextAcquisition())
                left -= taken
            }
        }
        gardenRepository.deleteGridObject(box.instanceId)
        gardenRepository.upsertInventorySlots(slots)
        if (gift.coins > 0L) gardenRepository.saveUserProfile(profile.copy(coins = profile.coins + gift.coins))
        ActionResult.SUCCESS
    }
}

/**
 * Leaves the practice garden, by coming to the end of it or by skipping. The saved garden is
 * there again as it was. If it had not been through the first steps yet, it has now: the closing
 * board comes up, and the first gift is waiting in the garden.
 */
class LeavePracticeUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val practice: PracticeMode,
    private val placeGift: PlaceGiftUseCase,
) {
    /** @return the step the saved garden is on. */
    suspend operator fun invoke(now: Long): TutorialStep {
        practice.leave()
        val saved = gardenRepository.getProgress() ?: return TutorialStep.DONE
        if (!saved.tutorialStep.isGuiding) return saved.tutorialStep
        placeGift(Gift.FIRST, now)
        gardenRepository.saveProgress(saved.copy(tutorialStep = TutorialStep.FINISHED))
        return TutorialStep.FINISHED
    }
}

/**
 * Moves the first steps along. Most of them go by what the garden shows, so that this can be
 * called after anything the player does:
 *
 * - Tilling: done once there are ridges.
 * - The shop: done once the seed is in the bag.
 * - Sowing: done once a plant stands in the garden. A seed comes out of the shop with the soil
 *   moist and would have to dry out before it could be watered, so here it is left a little dry.
 * - Harvesting: done once the plant is gone.
 *
 * The others are the player's own taps: [welcomed], [wateringDone], [fed] and [grow].
 *
 * After that the closing board and the handing over of features wait for the player's own taps
 * too ([acknowledge]); the garden does not move them on.
 */
class FollowTutorialUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    /** @return the step the player is on now. */
    suspend operator fun invoke(): TutorialStep = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: return@runInTransaction TutorialStep.DONE
        val objects = gardenRepository.getGridObjects()
        val plant = TutorialGuide.tutorialPlant(objects)
        val seed = gardenRepository.getInventory().any { it.itemId == PracticeGarden.SEED.itemId && !it.isEmpty }
        // Several steps may have been done since the last look: take them all at once.
        var settled = progress.tutorialStep
        while (true) {
            val next = when (settled) {
                TutorialStep.TILL_SOIL -> if (objects.any { it.isTilledSoil }) TutorialStep.BUY_SEED else settled
                TutorialStep.BUY_SEED -> if (seed || plant != null) TutorialStep.PLANT_SEED else settled
                TutorialStep.PLANT_SEED -> if (plant != null) TutorialStep.WATER_SEEDLING else settled
                TutorialStep.HARVEST -> if (plant == null) TutorialStep.PRACTICE_DONE else settled
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

    /** The player has read the welcome. */
    suspend fun welcomed() = moveOn(TutorialStep.WELCOME, TutorialStep.TILL_SOIL)

    /**
     * The player has watered while the watering step was the current one. Next the soil turns out
     * to have nothing left in it, and the seedling shows it, to be put right with fertilizer.
     *
     * @return true if this is what moved them on.
     */
    suspend fun wateringDone(): Boolean = gardenRepository.runInTransaction {
        if (!moveOn(TutorialStep.WATER_SEEDLING, TutorialStep.FEED_SEEDLING)) return@runInTransaction false
        val objects = gardenRepository.getGridObjects()
        TutorialGuide.tutorialPlant(objects)?.let { plant ->
            gardenRepository.upsertGridObject(plant.copy(nutritionLevel = 0f, status = PlantStatus.UNNOURISHED))
            Soil.under(plant, objects)?.let { gardenRepository.upsertGridObject(it.copy(nutritionLevel = 0f)) }
        }
        true
    }

    /** The player has fertilized while that step was the current one. */
    suspend fun fed() = moveOn(TutorialStep.FEED_SEEDLING, TutorialStep.GROW)

    /** Time is brought on: the plant has grown to [growth], and at 1 it is ready. */
    suspend fun grow(growth: Float) = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        TutorialGuide.tutorialPlant(objects)?.let { gardenRepository.upsertGridObject(it.copy(growthProgress = growth)) }
        if (growth >= 1f) moveOn(TutorialStep.GROW, TutorialStep.HARVEST)
    }

    private suspend fun moveOn(from: TutorialStep, to: TutorialStep): Boolean {
        val progress = gardenRepository.getProgress()
        if (progress?.tutorialStep != from) return false
        gardenRepository.saveProgress(progress.copy(tutorialStep = to))
        return true
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
