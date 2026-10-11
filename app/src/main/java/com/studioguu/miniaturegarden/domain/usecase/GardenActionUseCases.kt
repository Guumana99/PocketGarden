package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.FencePart
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
import com.studioguu.miniaturegarden.domain.model.WeatherType
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
    suspend fun water(instanceId: String, now: Long, weather: WeatherType): ActionResult =
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
    suspend fun fertilize(instanceId: String, now: Long, weather: WeatherType): ActionResult =
        care(instanceId, now, weather, coinCost = FERTILIZER_COST, xp = FERTILIZE_XP, refuse = { null }) {
            it.copy(
                nutritionLevel = 1f,
                growthProgress = min(
                    1f,
                    it.growthProgress + FERTILIZER_GROWTH_HOURS * PlantSpecies.forObjectId(it.objectId).growthPerHour,
                ),
            )
        }

    private suspend fun care(
        instanceId: String,
        now: Long,
        weather: WeatherType,
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

        /** Watering is only possible while moisture is below this. */
        const val WATERING_THRESHOLD = 0.8f
    }
}

/**
 * Takes one item out of an inventory slot and puts its object on the garden with its first cell
 * at (gridX, gridY). Every cell of the object's footprint must be inside the grid, on buildable
 * ground (not the river) and free in its own layer: paving goes under things, and things can
 * be stood on paving. Building a structure earns its [GardenItem.placeXp], and a
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

            gardenRepository.upsertGridObject(
                GridObject(
                    instanceId = UUID.randomUUID().toString(),
                    gridX = gridX,
                    gridY = gridY,
                    objectId = item.objectId,
                    lastUpdatedTimestamp = now,
                )
            )
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
 * Sells a fully grown plant: removes it, notes it in the encyclopedia and credits its coins and
 * XP to the (re-signed) profile.
 */
class HarvestPlantUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
    private val recordCollection: RecordCollectionUseCase,
) {
    suspend operator fun invoke(instanceId: String, now: Long, weather: WeatherType): ActionResult =
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
 * Swaps a fence standing on the garden for the next of its parts (see [FencePart]), in place and
 * free of charge, so a run of fence can be put together from sections, rails and posts.
 */
class SwitchFencePartUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val stored = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        val part = FencePart.fromObjectId(stored.objectId) ?: return@runInTransaction ActionResult.NOT_FOUND
        gardenRepository.upsertGridObject(stored.copy(objectId = part.next().objectId))
        ActionResult.SUCCESS
    }
}

/** Clears a tile. Nothing is refunded. */
class RemoveObjectUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        if (gardenRepository.getGridObjects().none { it.instanceId == instanceId }) {
            return@runInTransaction ActionResult.NOT_FOUND
        }
        gardenRepository.deleteGridObject(instanceId)
        ActionResult.SUCCESS
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
 * objects are simply re-anchored to [now].
 */
class SyncGardenUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val verifyIntegrity: VerifyIntegrityUseCase,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    suspend operator fun invoke(now: Long, weather: WeatherType): IntegrityResult {
        val integrity = verifyIntegrity()
        val trusted = integrity.signatureValid && integrity.timeConsistent
        gardenRepository.runInTransaction {
            val objects = gardenRepository.getGridObjects()
            val updated = if (trusted) {
                calculateOfflineProgress(objects, now, weather)
            } else {
                objects.map { it.copy(lastUpdatedTimestamp = now) }
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
    suspend operator fun invoke(millis: Long, now: Long, weather: WeatherType): ActionResult =
        gardenRepository.runInTransaction {
            val aged = gardenRepository.getGridObjects()
                .map { it.copy(lastUpdatedTimestamp = it.lastUpdatedTimestamp - millis) }
            gardenRepository.upsertGridObjects(calculateOfflineProgress(aged, now, weather))
            ActionResult.SUCCESS
        }
}
