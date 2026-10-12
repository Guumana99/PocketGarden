package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

/** Outcome of a player action. Anything but [SUCCESS] means nothing was changed. */
enum class ActionResult {
    SUCCESS,
    NOT_FOUND,
    NOT_A_PLANT,
    WITHERED,
    NOT_READY,
    ALREADY_MOIST,
    TILE_OCCUPIED,
    TILE_NOT_BUILDABLE,
    OUT_OF_BOUNDS,
    SLOT_EMPTY,
    NOT_ENOUGH_COINS,
    INVENTORY_FULL,
    ITEM_LOCKED,

    /** A seed was put on ground that has not been tilled with the hoe. */
    NEEDS_TILLED_SOIL,

    /** Reviving was asked for a plant that has not withered. */
    NOT_WITHERED,

    /** Something that is not an item (a growing plant, tilled soil) was to go back into the bag. */
    NOT_RETURNABLE,

    /** Something that is in the ground (a plant, tilled soil) was to be moved. */
    NOT_MOVABLE,

    /** The thing is being built or taken apart; it has to be finished first. */
    BUSY,
}

/**
 * Watering and fertilizing. Both first bring the plant up to [now] with the same rules as
 * offline progress, so the refill applies to its real current state and the time already
 * accounted for is never counted twice.
 */
class CareForPlantUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    /** Refills moisture to 100% for [WATER_XP]. Only allowed below [WATERING_THRESHOLD]. */
    suspend fun water(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        care(
            instanceId, now, weather,
            coinCost = 0,
            xp = WATER_XP,
            refuse = { if (it.moistureLevel >= WATERING_THRESHOLD) ActionResult.ALREADY_MOIST else null },
        ) { it.copy(moistureLevel = 1f) }

    /**
     * Costs [FERTILIZER_COST] coins: refills nutrition to 100%, advances growth by what the plant
     * would grow in [FERTILIZER_GROWTH_HOURS] healthy hours and earns [FERTILIZE_XP]. Being a fixed
     * amount of time, it is worth proportionally less on slow, valuable plants.
     */
    suspend fun fertilize(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        care(instanceId, now, weather, coinCost = FERTILIZER_COST, xp = FERTILIZE_XP, refuse = { null }) {
            it.copy(
                nutritionLevel = 1f,
                growthProgress = min(
                    1f,
                    it.growthProgress + FERTILIZER_GROWTH_HOURS * PlantSpecies.forObjectId(it.objectId).growthPerHour,
                ),
            )
        }

    /**
     * Brings a withered plant back for [REVIVE_COST] coins: watered, fed and free of stress, at
     * the growth it had reached. One that withered in full bloom is in bloom again and can be
     * harvested, though it will go over once more if it is left standing.
     */
    suspend fun revive(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val stored = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT
            // Brought up to now first: a plant can have withered since it was last looked at.
            val current = calculateOfflineProgress(stored, now, weather)
            if (current.status != PlantStatus.WITHERED) {
                gardenRepository.upsertGridObject(current)
                return@runInTransaction ActionResult.NOT_WITHERED
            }
            val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
            if (profile.coins < REVIVE_COST) return@runInTransaction ActionResult.NOT_ENOUGH_COINS

            gardenRepository.upsertGridObject(
                current.copy(
                    moistureLevel = 1f,
                    nutritionLevel = 1f,
                    stressRate = 0f,
                    status = PlantStatus.HEALTHY,
                    lastUpdatedTimestamp = now,
                )
            )
            gardenRepository.saveUserProfile(profile.copy(coins = profile.coins - REVIVE_COST))
            ActionResult.SUCCESS
        }

    private suspend fun care(
        instanceId: String,
        now: Long,
        weather: WeatherForecast,
        coinCost: Int,
        xp: Int,
        refuse: (GridObject) -> ActionResult?,
        treat: (GridObject) -> GridObject,
    ): ActionResult = gardenRepository.runInTransaction {
        val stored = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT

        val current = calculateOfflineProgress(stored, now, weather)
        if (current.status == PlantStatus.WITHERED) {
            gardenRepository.upsertGridObject(current)
            return@runInTransaction ActionResult.WITHERED
        }
        refuse(current)?.let { return@runInTransaction it }
        val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
        if (profile.coins < coinCost) return@runInTransaction ActionResult.NOT_ENOUGH_COINS

        val treated = treat(current)
        gardenRepository.upsertGridObject(
            treated.copy(
                status = CalculateOfflineProgressUseCase.statusOf(
                    treated.moistureLevel.toDouble(),
                    treated.nutritionLevel.toDouble(),
                    treated.stressRate.toDouble(),
                )
            )
        )
        gardenRepository.saveUserProfile(profile.copy(coins = profile.coins - coinCost).gainXp(xp))
        ActionResult.SUCCESS
    }

    companion object {
        const val WATER_XP = 5
        const val FERTILIZE_XP = 10
        const val FERTILIZER_COST = 15
        const val FERTILIZER_GROWTH_HOURS = 2
        const val REVIVE_COST = 30

        /** Watering is only possible while moisture is below this. */
        const val WATERING_THRESHOLD = 0.8f
    }
}

/**
 * Takes one item out of an inventory slot and puts its object on the garden with its first cell
 * at (gridX, gridY). Every cell of the object's footprint must be inside the grid, on buildable
 * ground (not the river) and free in its own layer: paving goes under things, and things can
 * be stood on paving. A building ([GardenItem.buildSeconds]) starts as a work site and is
 * finished by [CompleteWorkUseCase] when its time is up. Seeds and seedlings are the exception: they only go into soil that has
 * been tilled with the hoe (see [HoeUseCase]). Building a structure earns its [GardenItem.placeXp], and a
 * first placement makes its page of the encyclopedia ready to be filled in.
 */
class PlaceItemUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMap,
    private val recordCollection: RecordCollectionUseCase,
) {
    suspend operator fun invoke(slotIndex: Int, gridX: Int, gridY: Int, now: Long): ActionResult =
        gardenRepository.runInTransaction {
            val slot = gardenRepository.getInventory().firstOrNull { it.slotIndex == slotIndex }
            val item = slot?.takeUnless { it.isEmpty }?.let { GardenItem.fromItemId(it.itemId) }
                ?: return@runInTransaction ActionResult.SLOT_EMPTY

            val grid = GardenGrid(gardenMap, gardenRepository.getGridObjects())
            val kind = PlacementKind.forObjectId(item.objectId)
            when (grid.checkPlacement(Footprint.forObjectId(item.objectId), gridX, gridY, kind)) {
                PlacementCheck.OUT_OF_BOUNDS -> return@runInTransaction ActionResult.OUT_OF_BOUNDS
                PlacementCheck.NOT_BUILDABLE -> return@runInTransaction ActionResult.TILE_NOT_BUILDABLE
                PlacementCheck.OCCUPIED -> return@runInTransaction ActionResult.TILE_OCCUPIED
                PlacementCheck.OK -> Unit
            }
            if (item.objectId.startsWith(GridObject.PLANT_ID_PREFIX) && !grid.isTilled(gridX, gridY)) {
                return@runInTransaction ActionResult.NEEDS_TILLED_SOIL
            }

            // A seed planted in composted soil starts off ahead, and that uses the compost up.
            val compost = grid.floorAt(gridX, gridY)
                ?.takeIf { it.isComposted && item.objectId.startsWith(GridObject.PLANT_ID_PREFIX) }
            gardenRepository.upsertGridObject(
                GridObject(
                    instanceId = UUID.randomUUID().toString(),
                    gridX = gridX,
                    gridY = gridY,
                    objectId = item.objectId,
                    growthProgress = if (compost != null) GridObject.COMPOST_GROWTH_BONUS else 0f,
                    lastUpdatedTimestamp = now,
                    // A building is not there at a tap: it goes up over its building time.
                    workKind = if (item.buildSeconds > 0) WorkKind.BUILDING else WorkKind.NONE,
                    workStartedAt = if (item.buildSeconds > 0) now else 0L,
                    workEndsAt = if (item.buildSeconds > 0) now + item.buildSeconds * 1000L else 0L,
                )
            )
            if (compost != null) gardenRepository.upsertGridObject(compost.copy(growthProgress = 0f))
            val remaining = slot.amount - 1
            gardenRepository.upsertInventorySlot(
                if (remaining > 0) slot.copy(amount = remaining) else InventorySlot(slotIndex)
            )
            recordCollection(item.objectId, CollectionTrigger.PLACEMENT, now)
            if (item.placeXp > 0) {
                gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it.gainXp(item.placeXp)) }
            }
            ActionResult.SUCCESS
        }
}

/**
 * The hoe: turns a cell of open ground into tilled soil, which is where seeds can be planted.
 * It is a tool, not an item, so it costs nothing and is never used up. The cell must be empty
 * ground: no river, no paving, nothing standing on it, and not tilled already.
 */
class HoeUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMap,
) {
    suspend operator fun invoke(gridX: Int, gridY: Int, now: Long): ActionResult =
        gardenRepository.runInTransaction {
            val grid = GardenGrid(gardenMap, gardenRepository.getGridObjects())
            // Checked like terrain, which needs the whole cell to itself.
            when (grid.checkPlacement(Footprint.SINGLE, gridX, gridY, PlacementKind.TERRAIN)) {
                PlacementCheck.OUT_OF_BOUNDS -> return@runInTransaction ActionResult.OUT_OF_BOUNDS
                PlacementCheck.NOT_BUILDABLE -> return@runInTransaction ActionResult.TILE_NOT_BUILDABLE
                PlacementCheck.OCCUPIED -> return@runInTransaction ActionResult.TILE_OCCUPIED
                PlacementCheck.OK -> Unit
            }
            gardenRepository.upsertGridObject(tilledSoil(gridX, gridY, now))
            ActionResult.SUCCESS
        }

    /**
     * Gardens saved before the hoe existed have plants standing on bare ground. This puts tilled
     * soil under each of them, so they look right and the spot can be replanted after the
     * harvest. Plants on paving are left as they are. Does nothing once every plant has soil.
     */
    suspend fun tillUnderExistingPlants(now: Long) = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val grid = GardenGrid(gardenMap, objects)
        val missing = objects.filter { it.isPlant && grid.floorAt(it.gridX, it.gridY) == null }
            .map { tilledSoil(it.gridX, it.gridY, now) }
        if (missing.isNotEmpty()) gardenRepository.upsertGridObjects(missing)
    }

    private fun tilledSoil(gridX: Int, gridY: Int, now: Long) = GridObject(
        instanceId = UUID.randomUUID().toString(),
        gridX = gridX,
        gridY = gridY,
        objectId = GridObject.TILLED_SOIL_OBJECT_ID,
        lastUpdatedTimestamp = now,
    )
}

/**
 * Sells a fully grown plant: removes it, notes it in the encyclopedia and credits its coins and
 * XP to the (re-signed) profile.
 */
class HarvestPlantUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
    private val recordCollection: RecordCollectionUseCase,
) {
    suspend operator fun invoke(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val stored = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT

            val current = calculateOfflineProgress(stored, now, weather)
            if (current.status == PlantStatus.WITHERED) return@runInTransaction ActionResult.WITHERED
            if (current.growthProgress < 1f) return@runInTransaction ActionResult.NOT_READY
            val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND

            gardenRepository.deleteGridObject(instanceId)
            val species = PlantSpecies.forObjectId(current.objectId)
            recordCollection(current.objectId, CollectionTrigger.HARVEST, now)
            gardenRepository.saveUserProfile(
                profile.copy(coins = profile.coins + species.harvestCoins).gainXp(species.harvestXp)
            )
            ActionResult.SUCCESS
        }
}

/**
 * Takes something off the garden and puts it back into the inventory as the item it was placed
 * from, to be placed again later. It joins a stack of the same item if one has room, otherwise
 * it takes the first empty slot; with no room for it, nothing happens.
 *
 * Plants cannot go back (a seed that has grown is not a seed any more) and neither can tilled
 * soil, which was never an item. A fence goes back as a fence however it was joined up.
 * Experience that was earned for placing the thing is taken back with it, so that picking a
 * building up and putting it down again earns nothing.
 */
class StoreObjectUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val target = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        val item = itemFor(target) ?: return@runInTransaction ActionResult.NOT_RETURNABLE
        val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND

        val slots = gardenRepository.getInventory().filter { it.slotIndex < profile.inventoryCapacity }
        val slot = slots.firstOrNull { !it.isEmpty && it.itemId == item.itemId && it.amount < InventorySlot.MAX_STACK }
            ?: slots.firstOrNull { it.isEmpty }
            ?: return@runInTransaction ActionResult.INVENTORY_FULL

        gardenRepository.deleteGridObject(instanceId)
        val amount = if (slot.isEmpty) 1 else slot.amount + 1
        gardenRepository.upsertInventorySlot(InventorySlot(slot.slotIndex, item.itemId, amount))
        if (item.placeXp > 0) gardenRepository.saveUserProfile(profile.loseXp(item.placeXp))
        ActionResult.SUCCESS
    }

    companion object {
        /** The item [gridObject] goes back into the bag as, or null if it cannot go back. */
        fun itemFor(gridObject: GridObject): GardenItem? {
            if (gridObject.isPlant || gridObject.isTilledSoil) return null
            val objectId = if (gridObject.isFence) GridObject.FENCE_OBJECT_ID else gridObject.objectId
            return GardenItem.entries.firstOrNull { it.objectId == objectId }
        }
    }
}

/**
 * Clears a tile. Nothing is refunded, with one consolation: a withered plant is dug into the
 * tilled soil it stood on as compost, which gives the next seed planted there a head start.
 */
class RemoveObjectUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val target = objects.firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        gardenRepository.deleteGridObject(instanceId)
        if (leavesCompost(target)) {
            objects.firstOrNull { it.isTilledSoil && it.gridX == target.gridX && it.gridY == target.gridY }
                ?.let { gardenRepository.upsertGridObject(it.copy(growthProgress = 1f)) }
        }
        ActionResult.SUCCESS
    }

    companion object {
        /** Whether clearing [gridObject] away turns it into compost, given soil to dig it into. */
        fun leavesCompost(gridObject: GridObject): Boolean =
            gridObject.isPlant && gridObject.status == PlantStatus.WITHERED
    }
}

/**
 * Buys one [GardenItem]: tops up an existing stack of it if one has room, otherwise takes the
 * first empty slot. Coins are only spent when the item actually fits. Items the player has not
 * unlocked yet cannot be bought, whatever they can afford.
 */
class PurchaseItemUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(item: GardenItem): ActionResult = gardenRepository.runInTransaction {
        val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
        if (!item.isUnlockedFor(profile.stats)) return@runInTransaction ActionResult.ITEM_LOCKED
        if (profile.coins < item.price) return@runInTransaction ActionResult.NOT_ENOUGH_COINS

        val slots = gardenRepository.getInventory().filter { it.slotIndex < profile.inventoryCapacity }
        val target = slots.firstOrNull { !it.isEmpty && it.itemId == item.itemId && it.amount < InventorySlot.MAX_STACK }
            ?: slots.firstOrNull { it.isEmpty }
            ?: return@runInTransaction ActionResult.INVENTORY_FULL

        val amount = if (target.isEmpty) 1 else target.amount + 1
        gardenRepository.upsertInventorySlot(InventorySlot(target.slotIndex, item.itemId, amount))
        gardenRepository.saveUserProfile(profile.copy(coins = profile.coins - item.price))
        ActionResult.SUCCESS
    }
}

/**
 * Brings every plant up to [now] and re-stamps the profile. Run on launch / resume and
 * periodically while the garden is open.
 *
 * Integrity is checked first, against the clocks recorded by the previous run. If the device
 * clock was manipulated or the profile's signature is broken, the elapsed time is not credited:
 * objects are simply re-anchored to [now], and building jobs start over.
 */
class SyncGardenUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val verifyIntegrity: VerifyIntegrityUseCase,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    suspend operator fun invoke(now: Long, weather: WeatherForecast): IntegrityResult {
        val integrity = verifyIntegrity()
        val trusted = integrity.signatureValid && integrity.timeConsistent
        gardenRepository.runInTransaction {
            val objects = gardenRepository.getGridObjects()
            val updated = if (trusted) {
                calculateOfflineProgress(objects, now, weather)
            } else {
                // Time that cannot be trusted does not build anything either: jobs in progress
                // start over from now with their full length still to go.
                objects.map {
                    val restarted = if (it.isWorking) {
                        it.copy(workStartedAt = now, workEndsAt = now + (it.workEndsAt - it.workStartedAt))
                    } else {
                        it
                    }
                    restarted.copy(lastUpdatedTimestamp = now)
                }
            }
            val changed = updated.filterIndexed { index, obj -> obj != objects[index] }
            if (changed.isNotEmpty()) gardenRepository.upsertGridObjects(changed)
            gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it) }
        }
        return integrity
    }
}

/**
 * Development aid: makes every object [millis] older and applies the resulting offline progress,
 * as if that much time had passed with the app closed. Only object timestamps move; the
 * profile's clocks are left alone, so this does not look like clock tampering.
 */
class SkipTimeUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    suspend operator fun invoke(millis: Long, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val aged = gardenRepository.getGridObjects()
                .map { it.copy(lastUpdatedTimestamp = it.lastUpdatedTimestamp - millis) }
            gardenRepository.upsertGridObjects(calculateOfflineProgress(aged, now, weather))
            ActionResult.SUCCESS
        }
}
