package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.GardenMapSource
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/**
 * Takes something off the garden the way the player asked for: into the bag, or thrown away.
 *
 * Buildings are not gone at a tap. One that stands finished is first taken apart, which takes its
 * [com.studioguu.miniaturegarden.domain.model.GardenItem.demolishSeconds]; it stays on the garden
 * as a work site until then, and [CompleteWorkUseCase] finishes the job. One that is still being
 * built is simply called off, at once. Everything else (rocks, paving, plants ...) goes at once,
 * as before, through [StoreObjectUseCase] and [RemoveObjectUseCase].
 */
class StartRemovalUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val storeObject: StoreObjectUseCase,
    private val removeObject: RemoveObjectUseCase,
) {
    /** @param toBag true to put the thing back into the inventory, false to discard it. */
    suspend operator fun invoke(instanceId: String, toBag: Boolean, now: Long): ActionResult =
        gardenRepository.runInTransaction {
            val target = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            val item = StoreObjectUseCase.itemFor(target)
            if (toBag && item == null) return@runInTransaction ActionResult.NOT_RETURNABLE
            // A greenhouse with something growing in it stays until that is harvested or cleared.
            if (target.isGreenhouse && gardenRepository.getGridObjects().any { it.isPlant && it.gridX == target.gridX && it.gridY == target.gridY }) {
                return@runInTransaction ActionResult.IN_USE
            }
            // Already coming down: one job at a time.
            if (target.workKind == WorkKind.DEMOLISHING || target.workKind == WorkKind.PACKING) {
                return@runInTransaction ActionResult.BUSY
            }
            val seconds = item?.demolishSeconds ?: 0
            // Not unveiled yet counts as not built yet: it is called off, not taken apart.
            if (seconds <= 0 || target.workKind == WorkKind.BUILDING || target.workKind == WorkKind.READY) {
                return@runInTransaction if (toBag) storeObject(instanceId) else removeObject(instanceId)
            }
            if (toBag && !hasRoomFor(item!!.itemId)) return@runInTransaction ActionResult.INVENTORY_FULL

            gardenRepository.upsertGridObject(
                target.copy(
                    workKind = if (toBag) WorkKind.PACKING else WorkKind.DEMOLISHING,
                    workStartedAt = now,
                    workEndsAt = now + seconds * 1000L,
                )
            )
            ActionResult.SUCCESS
        }

    private suspend fun hasRoomFor(itemId: String): Boolean {
        val profile = gardenRepository.getUserProfile() ?: return false
        return gardenRepository.getInventory()
            .filter { it.slotIndex < profile.inventoryCapacity }
            .any { it.isEmpty || (it.itemId == itemId && it.amount < com.studioguu.miniaturegarden.domain.model.InventorySlot.MAX_STACK) }
    }
}

/** A job on the garden that came to its end. */
data class FinishedWork(val objectId: String, val kind: WorkKind)

/**
 * Finishes every job whose time is up at [now]: one being demolished disappears, one being packed
 * up goes into the bag. A building under construction does not simply appear: it becomes
 * [WorkKind.READY] and stays under wraps until the player unveils it ([UnveilBuildingUseCase]).
 * Jobs that still have time to run are left alone.
 *
 * If the bag has filled up while something was being packed, it cannot go in; the job is called
 * off and the building stays where it is, whole.
 */
class CompleteWorkUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val storeObject: StoreObjectUseCase,
    private val removeObject: RemoveObjectUseCase,
) {
    /** @return what was finished, in no particular order; empty if nothing was due. */
    suspend operator fun invoke(now: Long): List<FinishedWork> = gardenRepository.runInTransaction {
        val due = gardenRepository.getGridObjects()
            .filter { it.isWorking && it.workKind != WorkKind.READY && it.workEndsAt <= now }
        val finished = mutableListOf<FinishedWork>()
        for (site in due) {
            val standing = site.withoutWork()
            when (site.workKind) {
                WorkKind.BUILDING -> {
                    gardenRepository.upsertGridObject(site.copy(workKind = WorkKind.READY))
                    finished += FinishedWork(site.objectId, WorkKind.READY)
                }
                WorkKind.DEMOLISHING -> {
                    gardenRepository.upsertGridObject(standing)
                    if (removeObject(site.instanceId) == ActionResult.SUCCESS) {
                        finished += FinishedWork(site.objectId, WorkKind.DEMOLISHING)
                    }
                }
                WorkKind.PACKING -> {
                    gardenRepository.upsertGridObject(standing)
                    if (storeObject(site.instanceId) == ActionResult.SUCCESS) {
                        finished += FinishedWork(site.objectId, WorkKind.PACKING)
                    }
                }
                WorkKind.READY, WorkKind.NONE -> Unit
            }
        }
        finished
    }
}

/**
 * Unveils a building that is finished and waiting: the wraps come off and it stands there as
 * what it is. This is the player's tap; nothing else finishes a building.
 */
class UnveilBuildingUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val recordCollection: RecordCollectionUseCase,
) {
    /**
     * Unveiling is when a building counts (#50): it earns its [GardenItem.placeXp] and, the first
     * time, readies its page of the encyclopedia, both of which used to come when its site went up.
     *
     * @return [ActionResult.BUSY] if it is still being built, [ActionResult.NOT_FOUND] if there is nothing to unveil.
     */
    suspend operator fun invoke(instanceId: String, now: Long): ActionResult = gardenRepository.runInTransaction {
        val target = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        when (target.workKind) {
            WorkKind.READY -> {
                gardenRepository.upsertGridObject(target.withoutWork())
                val item = StoreObjectUseCase.itemFor(target)
                recordCollection(StoreObjectUseCase.itemFor(target)?.objectId ?: target.objectId, CollectionTrigger.PLACEMENT, now)
                if (item != null && item.placeXp > 0) {
                    gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it.gainXp(item.placeXp)) }
                }
                ActionResult.SUCCESS
            }
            WorkKind.NONE -> ActionResult.NOT_FOUND
            else -> ActionResult.BUSY
        }
    }
}

/**
 * Moves something that stands on the garden to another place, in one step: it does not go
 * through the bag and nothing has to be built again. The new place must be free by the same rules
 * as for placing the thing there, except that the cells it stands on now do not count as taken.
 *
 * Plants and tilled soil stay where they are (they are in the ground), and so does anything that
 * is being built or taken apart.
 */
class MoveObjectUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMapSource,
) {
    suspend operator fun invoke(instanceId: String, gridX: Int, gridY: Int): ActionResult =
        gardenRepository.runInTransaction {
            val objects = gardenRepository.getGridObjects()
            val target = objects.firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!canMove(target)) return@runInTransaction ActionResult.NOT_MOVABLE
            if (target.isWorking) return@runInTransaction ActionResult.BUSY
            if (target.gridX == gridX && target.gridY == gridY) return@runInTransaction ActionResult.SUCCESS

            // Checked against the garden as it would be with the thing lifted off it.
            val others = GardenGrid(gardenMap.current(), objects.filter { it.instanceId != instanceId })
            when (others.checkPlacement(target.footprint, gridX, gridY, PlacementKind.forObjectId(target.objectId))) {
                PlacementCheck.OUT_OF_BOUNDS -> return@runInTransaction ActionResult.OUT_OF_BOUNDS
                PlacementCheck.NOT_BUILDABLE -> return@runInTransaction ActionResult.TILE_NOT_BUILDABLE
                PlacementCheck.OCCUPIED -> return@runInTransaction ActionResult.TILE_OCCUPIED
                PlacementCheck.OK -> Unit
            }
            gardenRepository.upsertGridObject(target.copy(gridX = gridX, gridY = gridY))
            ActionResult.SUCCESS
        }

    companion object {
        /**
         * Whether [gridObject] is the kind of thing that can be moved at all; see [GardenLayout]
         * (which also keeps moving to edit mode, on the screen's side).
         */
        fun canMove(gridObject: GridObject): Boolean = GardenLayout.isMovable(gridObject)
    }
}
