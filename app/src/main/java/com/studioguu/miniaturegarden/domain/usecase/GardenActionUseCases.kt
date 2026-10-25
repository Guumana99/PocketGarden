package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.FarmTool
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.HoeSkill
import com.studioguu.miniaturegarden.domain.model.SoilHardness
import com.studioguu.miniaturegarden.domain.model.SoilQuality
import com.studioguu.miniaturegarden.domain.model.WateringCanSkill
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.Sprinkler
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMapSource
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.TutorialTool
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.HarvestType
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.nextAcquisition
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import java.util.UUID
import javax.inject.Inject

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

    /** A log (the shiitake's, [PlantGround.LOG]) was put on paving, a lawn, ground cover, water or glass. */
    NEEDS_OPEN_GROUND,

    /** Reviving was asked for a plant that has not withered. */
    NOT_WITHERED,

    /** Something that is not an item (a growing plant, tilled soil) was to go back into the bag. */
    NOT_RETURNABLE,

    /** Something that is in the ground (a plant, tilled soil) was to be moved. */
    NOT_MOVABLE,

    /** Something that has only one way to face was to be turned round. */
    NOT_FLIPPABLE,

    /** Fertilizer was given to a plant that is still well fed (#50: it cannot rush a crop). */
    ALREADY_FED,

    /** The thing is being built or taken apart; it has to be finished first. */
    BUSY,

    /** A second symbol building was to be bought or placed; a garden has one at most. */
    ONLY_ONE_SYMBOL,

    /** The garden cannot grow yet: see [com.studioguu.miniaturegarden.domain.model.ExpansionCheck]. */
    EXPANSION_LOCKED,

    /** A seasonal crop was to be sown out of its season, outside a greenhouse, or bought out of season. */
    OUT_OF_SEASON,

    /** Something else is in the way: a greenhouse with a plant growing in it cannot be taken away. */
    IN_USE,

    /** The garden is as large as it gets. */
    GARDEN_AT_MAX_SIZE,

    /** The first steps ask for something else just now (one cell is tilled, then the seed is sown). */
    NOT_THIS_STEP,

    /** The hoe struck hard ground and broke it a little: it needs more strikes to be tilled. */
    HOE_STRUCK,

    /** Only the sprinkler can be switched on and off. */
    NOT_SWITCHABLE,
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
    /**
     * Pours what the watering can holds at its level ([WateringCanSkill.waterAmount], 35 points
     * from a new can) up to 100% ([wateredMoisture]), for [WATER_XP]. Only allowed below
     * [WATERING_THRESHOLD]. A new can does not bring parched soil all the way back: a plant left
     * to dry right out needs a few pours, and fewer as the can gets better. Each pour is a use
     * of the can ([FarmTool.WATERING_CAN]).
     */
    suspend fun water(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        care(
            instanceId, now, weather,
            coinCost = 0,
            xp = WATER_XP,
            refuse = { if (it.moistureLevel >= WATERING_THRESHOLD) ActionResult.ALREADY_MOIST else null },
            afterwards = {
                gardenRepository.getProgress()?.let { gardenRepository.saveProgress(it.copy(wateringCanXp = it.wateringCanXp + 1)) }
            },
        ) {
            val level = gardenRepository.getProgress()?.wateringCanLevel ?: 1
            it.copy(moistureLevel = wateredMoisture(it.moistureLevel, level))
        }

    /**
     * Costs [FERTILIZER_COST] coins: refills nutrition to 100% and earns [FERTILIZE_XP]. Nutrition
     * is all it touches; growth is left exactly where it was (#59: feeding used to bring the crop
     * on by a share of its growing time as well, so the two parameters were tangled together).
     *
     * Only allowed once the plant has used up half its food ([FERTILIZING_THRESHOLD]); nutrition
     * runs down over three days, so a plant can be fed about once a day and a half. Before #50 it
     * could be fed again and again on the spot, and a few taps grew a crop to the full at once.
     */
    suspend fun fertilize(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        care(
            instanceId, now, weather,
            coinCost = FERTILIZER_COST,
            xp = FERTILIZE_XP,
            refuse = { if (it.nutritionLevel >= FERTILIZING_THRESHOLD) ActionResult.ALREADY_FED else null },
        ) { it.copy(nutritionLevel = 1f) }

    /**
     * Brings a withered plant back for [REVIVE_COST] coins: watered, fed and free of stress, at
     * the growth it had reached. One that withered in full bloom is in bloom again and can be
     * harvested, though it will go over once more if it is left standing.
     */
    suspend fun revive(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val garden = gardenRepository.getGridObjects()
            val stored = garden.firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT
            // Brought up to now first: a plant can have withered since it was last looked at.
            val current = calculateOfflineProgress(stored, now, weather, garden)
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
        afterwards: suspend () -> Unit = {},
        treat: suspend (GridObject) -> GridObject,
    ): ActionResult = gardenRepository.runInTransaction {
        val garden = gardenRepository.getGridObjects()
        val stored = garden.firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT

        val current = calculateOfflineProgress(stored, now, weather, garden)
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
        afterwards()
        ActionResult.SUCCESS
    }

    companion object {
        const val WATER_XP = 5
        /** Less than before (10) now that it is cheaper, so that it is not a way to buy levels. */
        const val FERTILIZE_XP = 6
        const val FERTILIZER_COST = 10

        const val REVIVE_COST = 30

        /** Watering is only possible while moisture is below this. */
        const val WATERING_THRESHOLD = 0.8f

        /** Fertilizing is only possible while nutrition is below this. */
        const val FERTILIZING_THRESHOLD = 0.5f

        /** How much one watering from a new can adds to the soil's moisture (35 points). */
        const val WATER_AMOUNT = WateringCanSkill.BASE_AMOUNT

        /**
         * The soil's moisture after one watering from [moisture] with a can of [canLevel]: what
         * that can pours ([WateringCanSkill.waterAmount]) more, never above 100%.
         */
        fun wateredMoisture(moisture: Float, canLevel: Int = 1): Float =
            (moisture.coerceAtLeast(0f) + WateringCanSkill.waterAmount(canLevel)).coerceAtMost(1f)
    }
}

/**
 * Takes one item out of an inventory slot and puts its object on the garden with its first cell
 * at (gridX, gridY). Every cell of the object's footprint must be inside the grid, on buildable
 * ground (not the river) and free in its own layer: paving goes under things, and things can
 * be stood on paving. A building ([GardenItem.buildSeconds]) starts as a work site and is
 * finished by [CompleteWorkUseCase] when its time is up. Seeds and seedlings are the exception: they only go into soil that has
 * been tilled with the hoe (see [HoeUseCase]). Something put down at once earns its
 * [GardenItem.placeXp] and, the first time, readies its page of the encyclopedia; a building does
 * both when it is finished and unveiled ([UnveilBuildingUseCase], #50), not when its site goes up.
 */
class PlaceItemUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMapSource,
    private val recordCollection: RecordCollectionUseCase,
) {
    /** @param flipped which way a building faces (see [GridObject.canFlip]); ignored for anything else. */
    suspend operator fun invoke(slotIndex: Int, gridX: Int, gridY: Int, now: Long, flipped: Boolean = false): ActionResult =
        gardenRepository.runInTransaction {
            val slot = gardenRepository.getInventory().firstOrNull { it.slotIndex == slotIndex }
            val item = slot?.takeUnless { it.isEmpty }?.let { GardenItem.fromItemId(it.itemId) }
                ?: return@runInTransaction ActionResult.SLOT_EMPTY

            val grid = GardenGrid(gardenMap.current(), gardenRepository.getGridObjects())
            // The symbol building is one of a kind: a second one never goes up.
            if (item.isSymbol && grid.hasSymbol) return@runInTransaction ActionResult.ONLY_ONE_SYMBOL
            val kind = PlacementKind.forObjectId(item.objectId)
            val check = grid.checkPlacement(Footprint.forObjectId(item.objectId), gridX, gridY, kind, item.objectId)
            when (check) {
                PlacementCheck.OUT_OF_BOUNDS -> return@runInTransaction ActionResult.OUT_OF_BOUNDS
                PlacementCheck.NOT_BUILDABLE -> return@runInTransaction ActionResult.TILE_NOT_BUILDABLE
                else -> Unit
            }
            // Something standing in the way is in the way. What only lies on the ground (a lawn,
            // pebbles, flowers, a pond) is the ground, and a crop is told where it can go instead.
            val inTheWay = check == PlacementCheck.OCCUPIED &&
                grid.occupantAt(gridX, gridY)?.let { it.isGroundCover || it.isPond } != true
            if (inTheWay) return@runInTransaction ActionResult.TILE_OCCUPIED
            // A crop goes only on the ground it grows on ([PlantGround]).
            val species = if (item.objectId.startsWith(GridObject.PLANT_ID_PREFIX)) PlantSpecies.forObjectId(item.objectId) else null
            if (species != null && !species.ground.allows(grid.groundAt(gridX, gridY))) {
                return@runInTransaction wrongGround(species.ground)
            }
            if (check == PlacementCheck.OCCUPIED) return@runInTransaction ActionResult.TILE_OCCUPIED
            // Out of its season a crop only grows under glass. Seeds bought earlier stay in the
            // bag for when their season comes round again.
            val season = gardenRepository.getProgress()?.season
            if (item.category == ItemCategory.SEED && season != null && !item.isInSeason(season) && !grid.isUnderGlass(gridX, gridY)) {
                return@runInTransaction ActionResult.OUT_OF_SEASON
            }

            // A seed planted in composted soil starts off ahead, and that uses the compost up. A
            // log only lies on the bed, and leaves its compost be.
            val compost = grid.floorAt(gridX, gridY)
                ?.takeIf { it.isComposted && species?.ground?.usesSoil == true }
            gardenRepository.upsertGridObject(
                GridObject(
                    instanceId = UUID.randomUUID().toString(),
                    gridX = gridX,
                    gridY = gridY,
                    isFlipped = flipped && GridObject.canFaceEitherWay(item.objectId),
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
            // A building earns and is recorded when it is unveiled; anything else now.
            if (item.buildSeconds <= 0) {
                recordCollection(item.objectId, CollectionTrigger.PLACEMENT, now)
                if (item.placeXp > 0) {
                    gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it.gainXp(item.placeXp)) }
                }
            }
            ActionResult.SUCCESS
        }

    /** What to tell the player who put a crop that grows on [ground] somewhere else. */
    private fun wrongGround(ground: PlantGround): ActionResult = when (ground) {
        PlantGround.BED -> ActionResult.NEEDS_TILLED_SOIL
        PlantGround.LOG -> ActionResult.NEEDS_OPEN_GROUND
    }
}

/**
 * The hoe: turns a cell of open ground into tilled soil, which is where seeds can be planted.
 * It is a tool, not an item, so it costs nothing and is never used up (nor tires anyone: there is
 * no stamina). The cell must be empty ground: no river, no paving, nothing standing on it, and
 * not tilled already.
 *
 * Ground is not all alike ([SoilHardness]): hard ground takes a few strikes. [strikes] is how many
 * the cell has had already; until there are enough ([HoeSkill.strikesNeeded]) a strike only
 * answers [ActionResult.HOE_STRUCK] and nothing is stored. The hoe gets better with each bed
 * ([FarmTool.HOE]): fewer strikes, and richer beds ([HoeSkill.soilQualityFor]) whose crops fetch
 * more at harvest ([SoilQuality]).
 *
 * During the first steps it tills one cell, wherever the player likes, and that moves them on
 * to sowing at once; until the guided steps are over it tills nothing more
 * ([TutorialStep.allows]).
 */
class HoeUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMapSource,
) {
    suspend operator fun invoke(gridX: Int, gridY: Int, now: Long, strikes: Int = 0): ActionResult =
        gardenRepository.runInTransaction {
            val progress = gardenRepository.getProgress()
            if (progress != null && !progress.tutorialStep.allows(TutorialTool.HOE)) return@runInTransaction ActionResult.NOT_THIS_STEP
            val grid = GardenGrid(gardenMap.current(), gardenRepository.getGridObjects())
            // Checked like terrain, which needs the whole cell to itself.
            when (grid.checkPlacement(Footprint.SINGLE, gridX, gridY, PlacementKind.TERRAIN)) {
                PlacementCheck.OUT_OF_BOUNDS -> return@runInTransaction ActionResult.OUT_OF_BOUNDS
                PlacementCheck.NOT_BUILDABLE -> return@runInTransaction ActionResult.TILE_NOT_BUILDABLE
                PlacementCheck.OCCUPIED -> return@runInTransaction ActionResult.TILE_OCCUPIED
                PlacementCheck.OK -> Unit
            }
            val level = progress?.hoeLevel ?: 1
            // The first steps ask for one cell, and one strike is enough for it.
            val guided = progress?.tutorialStep == TutorialStep.TILL_SOIL
            val needed = if (guided) 1 else HoeSkill.strikesNeeded(SoilHardness.at(gridX, gridY), level)
            if (strikes + 1 < needed) return@runInTransaction ActionResult.HOE_STRUCK
            gardenRepository.upsertGridObject(tilledSoil(gridX, gridY, now, HoeSkill.soilQualityFor(level)))
            if (progress != null) {
                // With it tilled, on to sowing.
                val step = if (guided) TutorialStep.PLANT_SEED else progress.tutorialStep
                gardenRepository.saveProgress(progress.copy(tutorialStep = step, hoeXp = progress.hoeXp + 1))
            }
            ActionResult.SUCCESS
        }

    /**
     * Gardens saved before the hoe existed have plants standing on bare ground. This puts tilled
     * soil under each of them, so they look right and the spot can be replanted after the
     * harvest. Plants on paving are left as they are. Does nothing once every plant has soil.
     * A crop that is not grown in a bed (#68: the shiitake's log, laid on the open earth) is left
     * on the ground it was put on: it runs at every start, and would otherwise till under it.
     */
    suspend fun tillUnderExistingPlants(now: Long) = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val grid = GardenGrid(gardenMap.current(), objects)
        val missing = objects.filter {
            it.isPlant && grid.floorAt(it.gridX, it.gridY) == null && PlantSpecies.forObjectId(it.objectId).ground.usesSoil
        }
            .map { tilledSoil(it.gridX, it.gridY, now) }
        if (missing.isNotEmpty()) gardenRepository.upsertGridObjects(missing)
    }

    private fun tilledSoil(gridX: Int, gridY: Int, now: Long, quality: Int = 0) = GridObject(
        instanceId = UUID.randomUUID().toString(),
        gridX = gridX,
        gridY = gridY,
        objectId = GridObject.TILLED_SOIL_OBJECT_ID,
        lastUpdatedTimestamp = now,
        soilQuality = quality,
    )
}

/**
 * Sells a fully grown plant: removes it, notes it in the encyclopedia and credits its coins and
 * XP to the (re-signed) profile.
 */
/**
 * Harvests a fully grown plant. What becomes of it depends on its [HarvestType]: a
 * single-harvest crop is taken up and its tilled soil is free again; a repeatable one stays
 * where it is, back at the start of its second stage, to ripen again. Either way the harvest
 * pays [PlantSpecies.harvestReward] and records the plant in the encyclopedia.
 *
 * A withered plant cannot be harvested: it is cleared away ([RemoveObjectUseCase]) or revived.
 */
class HarvestPlantUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
    private val recordCollection: RecordCollectionUseCase,
) {
    suspend operator fun invoke(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val garden = gardenRepository.getGridObjects()
            val stored = garden.firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT

            val current = calculateOfflineProgress(stored, now, weather, garden)
            if (current.status == PlantStatus.WITHERED) return@runInTransaction ActionResult.WITHERED
            if (current.growthProgress < 1f) return@runInTransaction ActionResult.NOT_READY
            val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND

            val species = PlantSpecies.forObjectId(current.objectId)
            val reward = species.harvestReward(current.harvestCount)
            // A richer bed, tilled by a better hoe, fetches more for what grew in it.
            val coins = SoilQuality.harvestCoins(reward.coins, bedUnder(current, garden)?.soilQuality ?: 0)
            afterHarvest(current, species, now)?.let { gardenRepository.upsertGridObject(it) }
                ?: gardenRepository.deleteGridObject(instanceId)
            // With the colour it bloomed in, for the book to show the one that was grown (#59).
            recordCollection(current.objectId, CollectionTrigger.HARVEST, now, colour = species.bloomColourOf(current.instanceId))
            gardenRepository.saveUserProfile(profile.copy(coins = profile.coins + coins).gainXp(reward.xp))
            ActionResult.SUCCESS
        }

    companion object {
        /** The tilled bed [plant] grows in, if it grows in one. */
        fun bedUnder(plant: GridObject, garden: List<GridObject>): GridObject? =
            garden.firstOrNull { it.isTilledSoil && it.gridX == plant.gridX && it.gridY == plant.gridY }

        /**
         * What is left of [plant] (fully grown, brought up to [now]) once it has been harvested:
         * null for a [HarvestType.SINGLE_HARVEST] crop, which goes; for a repeatable one, the same
         * plant back at [PlantSpecies.regrowFrom], one harvest more, its care levels and the
         * stress on its record unchanged.
         */
        fun afterHarvest(plant: GridObject, species: PlantSpecies, now: Long): GridObject? =
            if (!species.isRepeatable) {
                null
            } else {
                plant.copy(
                    growthProgress = species.regrowFrom,
                    harvestCount = plant.harvestCount + 1,
                    lastUpdatedTimestamp = now,
                )
            }
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
        gardenRepository.upsertInventorySlot(InventorySlot(slot.slotIndex, item.itemId, amount, slots.nextAcquisition()))
        // A building called off before it was unveiled never earned its experience (#50).
        val earned = target.workKind != WorkKind.BUILDING && target.workKind != WorkKind.READY
        if (item.placeXp > 0 && earned) gardenRepository.saveUserProfile(profile.loseXp(item.placeXp))
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
        /**
         * Whether clearing [gridObject] away turns it into compost, given soil to dig it into: a
         * withered plant that grew in the soil ([PlantGround.usesSoil]; a log only lay on it).
         */
        fun leavesCompost(gridObject: GridObject): Boolean =
            gridObject.isPlant && gridObject.status == PlantStatus.WITHERED &&
                PlantSpecies.forObjectId(gridObject.objectId).ground.usesSoil
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
        val pages = CollectionProgress(gardenRepository.getCollection()).revealed
        if (!item.isUnlockedFor(profile.stats.copy(collectionPages = pages))) return@runInTransaction ActionResult.ITEM_LOCKED
        // Seasonal crops are only on sale in their season; the shop does not show them otherwise.
        val season = gardenRepository.getProgress()?.season
        if (season != null && !item.isInSeason(season)) return@runInTransaction ActionResult.OUT_OF_SEASON
        // One symbol building per garden, and one in the bag counts as well.
        if (item.isSymbol && gardenRepository.ownsSymbolBuilding()) return@runInTransaction ActionResult.ONLY_ONE_SYMBOL
        if (profile.coins < item.price) return@runInTransaction ActionResult.NOT_ENOUGH_COINS

        val slots = gardenRepository.getInventory().filter { it.slotIndex < profile.inventoryCapacity }
        val target = slots.firstOrNull { !it.isEmpty && it.itemId == item.itemId && it.amount < InventorySlot.MAX_STACK }
            ?: slots.firstOrNull { it.isEmpty }
            ?: return@runInTransaction ActionResult.INVENTORY_FULL

        val amount = if (target.isEmpty) 1 else target.amount + 1
        gardenRepository.upsertInventorySlot(InventorySlot(target.slotIndex, item.itemId, amount, slots.nextAcquisition()))
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
            // Jobs on the clock are brought forward too, so that a building can be finished by
            // skipping; one waiting for its unveiling is left as it is.
            val aged = gardenRepository.getGridObjects().map {
                val onTheClock = it.isWorking && it.workKind != WorkKind.READY
                it.copy(
                    lastUpdatedTimestamp = it.lastUpdatedTimestamp - millis,
                    workStartedAt = if (onTheClock) it.workStartedAt - millis else it.workStartedAt,
                    workEndsAt = if (onTheClock) it.workEndsAt - millis else it.workEndsAt,
                )
            }
            gardenRepository.upsertGridObjects(calculateOfflineProgress(aged, now, weather))
            // The year turns with the skipped time as well.
            gardenRepository.getProgress()?.takeIf { it.seasonStartedAt > 0L }
                ?.let { gardenRepository.saveProgress(it.copy(seasonStartedAt = it.seasonStartedAt - millis)) }
            ActionResult.SUCCESS
        }
}

/**
 * Turns a building round to face the other way (see [GridObject.canFlip]). It stays on the same
 * cells; only the way it is drawn changes, and that is kept with it.
 */
class FlipObjectUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val target = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        if (!target.canFlip) return@runInTransaction ActionResult.NOT_FLIPPABLE
        // Turned round once it stands (#50): a work site is sheeting, with no front to face.
        if (target.isWorking) return@runInTransaction ActionResult.BUSY
        gardenRepository.upsertGridObject(target.copy(isFlipped = !target.isFlipped))
        ActionResult.SUCCESS
    }
}

/**
 * Switches a sprinkler on or off. The plants in its reach are brought up to [now] first, under
 * the sprinkler as it was, so that the time before the switch is counted the way it was spent
 * and only what comes after it changes.
 */
class ToggleSprinklerUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    suspend operator fun invoke(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val garden = gardenRepository.getGridObjects()
            val sprinkler = garden.firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!sprinkler.isSprinkler) return@runInTransaction ActionResult.NOT_SWITCHABLE
            val reach = Sprinkler.cellsInReach(sprinkler.gridX, sprinkler.gridY)
            val caughtUp = garden.filter { it.isPlant && GridCell(it.gridX, it.gridY) in reach }
                .map { calculateOfflineProgress(it, now, weather, garden) }
            if (caughtUp.isNotEmpty()) gardenRepository.upsertGridObjects(caughtUp)
            gardenRepository.upsertGridObject(sprinkler.copy(isOn = !sprinkler.isOn))
            ActionResult.SUCCESS
        }
}
