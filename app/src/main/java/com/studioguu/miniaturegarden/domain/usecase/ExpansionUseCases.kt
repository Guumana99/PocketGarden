package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenMapSource
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Grows the garden to its next size (see [GardenExpansion]): a ring of lawn is added all round
 * and everything already in the garden moves one cell along each axis, in one transaction.
 */
class ExpandGardenUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(): ActionResult = gardenRepository.runInTransaction {
        val progress = gardenRepository.getProgress() ?: GardenProgress()
        val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
        val objects = gardenRepository.getGridObjects()
        when (GardenExpansion.check(progress.gardenSize, objects, profile.playerLevel)) {
            ExpansionCheck.AT_MAX_SIZE -> return@runInTransaction ActionResult.GARDEN_AT_MAX_SIZE
            ExpansionCheck.NEEDS_SYMBOL, ExpansionCheck.NEEDS_LEVEL -> return@runInTransaction ActionResult.EXPANSION_LOCKED
            ExpansionCheck.READY -> Unit
        }
        val stage = GardenExpansion.nextStage(progress.gardenSize) ?: return@runInTransaction ActionResult.GARDEN_AT_MAX_SIZE
        gardenRepository.upsertGridObjects(objects.map(GardenExpansion::shifted))
        gardenRepository.saveProgress(progress.copy(gardenSize = stage.size))
        ActionResult.SUCCESS
    }
}

/** The map of the garden at its current size, read from the stored progress. */
class StoredGardenMap @Inject constructor(
    private val gardenRepository: GardenRepository,
) : GardenMapSource {
    override suspend fun current(): GardenMap =
        GardenMap.allGrass(gardenRepository.getProgress()?.gardenSize ?: GridObject.GRID_SIZE)
}

/** The symbol building, wherever it is: standing in the garden (finished or not) or in the bag. */
internal suspend fun GardenRepository.ownsSymbolBuilding(): Boolean =
    getGridObjects().any { it.isSymbol } ||
        getInventory().any { slot -> !slot.isEmpty && GardenItem.fromItemId(slot.itemId)?.isSymbol == true }
