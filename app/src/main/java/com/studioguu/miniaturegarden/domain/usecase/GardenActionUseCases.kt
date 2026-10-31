package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.BloomColours
import com.studioguu.miniaturegarden.domain.model.BridgeFit
import com.studioguu.miniaturegarden.domain.model.BridgeProblem
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.CollectionTrigger
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.PracticeGarden
import com.studioguu.miniaturegarden.domain.model.Sprinkler
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMapSource
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.TutorialTool
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.HarvestType
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.nextAcquisition
import com.studioguu.miniaturegarden.domain.model.PaintColour
import com.studioguu.miniaturegarden.domain.model.PaintPart
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Soil
import com.studioguu.miniaturegarden.domain.model.SoilGrade
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.HarvestReward
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import java.util.UUID
import kotlin.random.Random
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

    /** A vegetable was put anywhere but on ridged soil. */
    NEEDS_RIDGE,

    /** Fertilizer was given where there is no soil to take it. */
    NO_SOIL,

    /** A flower was put on ground that has not been tilled with the hoe. */
    NEEDS_TILLED_SOIL,

    /** A log (the shiitake's, [PlantGround.LOG]) was put on paving, a lawn, ground cover, water or glass. */
    NEEDS_OPEN_GROUND,

    /** Reviving was asked for a plant that has not withered. */
    NOT_WITHERED,

    /** Something that is not an item (a growing plant, tilled soil) was to go back into the bag. */
    NOT_RETURNABLE,

    /** Something that stays where it is (tilled soil, a withered crop) was to be moved. */
    NOT_MOVABLE,

    /** Something that has only one way to face was to be turned round. */
    NOT_FLIPPABLE,

    /** Fertilizer was given to a cell whose food is full. */
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

    /** The hoe has struck, and the ground needs one more strike to change ([Soil.HOE_STRIKES]). */
    HOE_STRUCK,

    /** Only the sprinkler can be switched on and off. */
    NOT_SWITCHABLE,

    /** A cell of river was to go from under a bridge. */
    BRIDGE_ABOVE,

    /** A bridge was to go where there is no river to cross. */
    NEEDS_RIVER_ACROSS,

    /** A bridge was to go over a river wider than it reaches ([GardenGrid.bridgeAcross]). */
    RIVER_TOO_WIDE,

    /** Only a building with a mask can be painted ([BuildingPaint]), and only in its own colours. */
    NOT_PAINTABLE,
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
     * Pours [WATER_AMOUNT] up to 100% ([wateredMoisture]), for [WATER_XP]. Only allowed below
     * [WATERING_THRESHOLD]. It does not bring parched soil all the way back: a plant left to dry
     * right out needs a few pours.
     */
    suspend fun water(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val garden = gardenRepository.getGridObjects()
            val stored = garden.firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            if (!stored.isPlant) return@runInTransaction ActionResult.NOT_A_PLANT

            val current = calculateOfflineProgress(stored, now, weather, garden)
            if (current.status == PlantStatus.WITHERED) {
                gardenRepository.upsertGridObject(current)
                return@runInTransaction ActionResult.WITHERED
            }
            if (current.moistureLevel >= WATERING_THRESHOLD) return@runInTransaction ActionResult.ALREADY_MOIST
            val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
            gardenRepository.upsertGridObject(restated(current.copy(moistureLevel = wateredMoisture(current.moistureLevel))))
            gardenRepository.saveUserProfile(profile.gainXp(WATER_XP))
            ActionResult.SUCCESS
        }

    /**
     * Fills the food of the cell of soil that [instanceId] is, or that it grows in ([Soil]), for
     * [Soil.fertilizerCost] coins and [FERTILIZE_XP]. Food is all it touches: growth is left
     * exactly where it was. Refused while the cell is full, and where there is no soil to feed
     * (a log only lies on the ground).
     */
    suspend fun fertilize(instanceId: String, now: Long, weather: WeatherForecast): ActionResult =
        gardenRepository.runInTransaction {
            val garden = gardenRepository.getGridObjects()
            val target = garden.firstOrNull { it.instanceId == instanceId }
                ?: return@runInTransaction ActionResult.NOT_FOUND
            val soil = when {
                target.isSoil -> target
                target.isPlant && PlantSpecies.forObjectId(target.objectId).ground.usesSoil -> Soil.under(target, garden)
                else -> null
            } ?: return@runInTransaction ActionResult.NO_SOIL
            if (soil.nutritionLevel >= 1f) return@runInTransaction ActionResult.ALREADY_FED
            val profile = gardenRepository.getUserProfile() ?: return@runInTransaction ActionResult.NOT_FOUND
            // The first steps' fertilizer is on the house.
            val free = gardenRepository.getProgress()?.tutorialStep == TutorialStep.FEED_SEEDLING
            val cost = if (free) 0 else Soil.fertilizerCost(profile.playerLevel)
            if (profile.coins < cost) return@runInTransaction ActionResult.NOT_ENOUGH_COINS

            gardenRepository.upsertGridObject(soil.copy(nutritionLevel = 1f))
            garden.firstOrNull { it.isPlant && it.gridX == soil.gridX && it.gridY == soil.gridY }?.let { plant ->
                gardenRepository.upsertGridObject(restated(calculateOfflineProgress(plant, now, weather, garden).copy(nutritionLevel = 1f)))
            }
            gardenRepository.saveUserProfile(profile.copy(coins = profile.coins - cost).gainXp(FERTILIZE_XP))
            ActionResult.SUCCESS
        }

    private fun restated(plant: GridObject): GridObject = plant.copy(
        status = CalculateOfflineProgressUseCase.statusOf(
            plant.moistureLevel.toDouble(),
            plant.nutritionLevel.toDouble(),
            plant.stressRate.toDouble(),
        ),
    )

    /**
     * Brings a withered plant back for [REVIVE_COST] coins: watered and free of stress, at the
     * growth it had reached. The food in its soil is the soil's own affair. One that withered in full bloom is in bloom again and can be
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
                    stressRate = 0f,
                    status = CalculateOfflineProgressUseCase.statusOf(1.0, current.nutritionLevel.toDouble(), 0.0),
                    lastUpdatedTimestamp = now,
                )
            )
            gardenRepository.saveUserProfile(profile.copy(coins = profile.coins - REVIVE_COST))
            ActionResult.SUCCESS
        }

    companion object {
        const val WATER_XP = 5
        /** Less than before (10) now that it is cheaper, so that it is not a way to buy levels. */
        const val FERTILIZE_XP = 6

        const val REVIVE_COST = 30

        /** Watering is only possible while moisture is below this. */
        const val WATERING_THRESHOLD = 0.8f

        /** How much one watering adds to the soil's moisture (35 points). */
        const val WATER_AMOUNT = 0.35f

        /** The soil's moisture after one watering from [moisture], never above 100%. */
        fun wateredMoisture(moisture: Float): Float = (moisture.coerceAtLeast(0f) + WATER_AMOUNT).coerceAtMost(1f)
    }
}

/**
 * Whether [objectId] can go with its first cell at ([x], [y]) in the garden as it stands:
 * [ActionResult.SUCCESS], or what to tell the player. Every cell of it must be inside the grid, on
 * buildable ground and free in its own layer, and a crop only goes on the ground it grows on
 * ([PlantGround]). The same whether it comes out of the bag or is replanted from elsewhere.
 */
internal fun GardenGrid.fits(objectId: String, x: Int, y: Int, flipped: Boolean = false): ActionResult {
    val footprint = Footprint.forObjectId(objectId, flipped)
    val check = checkPlacement(footprint, x, y, PlacementKind.forObjectId(objectId), objectId)
    when (check) {
        PlacementCheck.OUT_OF_BOUNDS -> return ActionResult.OUT_OF_BOUNDS
        PlacementCheck.NOT_BUILDABLE -> return ActionResult.TILE_NOT_BUILDABLE
        else -> Unit
    }
    // Something standing in the way is in the way. What only lies on the ground (a lawn,
    // pebbles, flowers, a pond) is the ground, and a crop is told where it can go instead.
    val inTheWay = check == PlacementCheck.OCCUPIED && occupantAt(x, y)?.let { it.isGroundCover || it.isPond } != true
    if (inTheWay) return ActionResult.TILE_OCCUPIED
    // A crop goes only on the ground it grows on ([PlantGround]).
    val species = if (objectId.startsWith(GridObject.PLANT_ID_PREFIX)) PlantSpecies.forObjectId(objectId) else null
    if (species != null && !species.ground.allows(groundAt(x, y))) return wrongGround(species.ground)
    return if (check == PlacementCheck.OCCUPIED) ActionResult.TILE_OCCUPIED else ActionResult.SUCCESS
}

/** What to tell the player whose bridge was refused for [problem]. */
internal fun bridgeRefusal(problem: BridgeProblem): ActionResult = when (problem) {
    BridgeProblem.NO_RIVER -> ActionResult.NEEDS_RIVER_ACROSS
    BridgeProblem.TOO_WIDE -> ActionResult.RIVER_TOO_WIDE
    BridgeProblem.OUTSIDE -> ActionResult.OUT_OF_BOUNDS
    BridgeProblem.ENDS_TAKEN -> ActionResult.TILE_OCCUPIED
}

/** What to tell the player who put a crop that grows on [ground] somewhere else. */
private fun wrongGround(ground: PlantGround): ActionResult = when (ground) {
    PlantGround.BED -> ActionResult.NEEDS_RIDGE
    PlantGround.FLOWER_BED -> ActionResult.NEEDS_TILLED_SOIL
    PlantGround.LOG -> ActionResult.NEEDS_OPEN_GROUND
}

/** How much food a crop of [species] (or anything else, for null) finds at ([x], [y]): it keeps a copy of its soil's. */
internal fun GardenGrid.foodAt(species: PlantSpecies?, x: Int, y: Int): Float =
    floorAt(x, y)?.takeIf { it.isSoil && species?.ground?.usesSoil == true }?.nutritionLevel ?: 1f

/**
 * Takes one item out of an inventory slot and puts its object on the garden with its first cell
 * at (gridX, gridY). Every cell of the object's footprint must be inside the grid, on buildable
 * ground (not the river) and free in its own layer: paving goes under things, and things can
 * be stood on paving. A building ([GardenItem.buildSeconds]) starts as a work site and is
 * finished by [CompleteWorkUseCase] when its time is up. Seeds and seedlings are the exception: they only go on the ground
 * their crop grows on ([PlantGround], [HoeUseCase]). Something put down at once earns its
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
            val facing = flipped && GridObject.canFaceEitherWay(item.objectId)
            // A bridge finds its own place and length from the river at the tap.
            val across = if (item.objectId == StructureData.BRIDGE.objectId) {
                when (val fit = grid.bridgeAcross(gridX, gridY, facing)) {
                    is BridgeFit.Across -> fit
                    is BridgeFit.Refused -> return@runInTransaction bridgeRefusal(fit.why)
                }
            } else {
                val fit = grid.fits(item.objectId, gridX, gridY, facing)
                if (fit != ActionResult.SUCCESS) return@runInTransaction fit
                null
            }
            // Out of its season a crop only grows under glass. Seeds bought earlier stay in the
            // bag for when their season comes round again.
            val season = gardenRepository.getProgress()?.season
            if (item.category == ItemCategory.SEED && season != null && !item.isInSeason(season) && !grid.isUnderGlass(gridX, gridY)) {
                return@runInTransaction ActionResult.OUT_OF_SEASON
            }

            // A crop takes its food from the soil of its cell: a plant keeps a copy of how much there is.
            val species = if (item.objectId.startsWith(GridObject.PLANT_ID_PREFIX)) PlantSpecies.forObjectId(item.objectId) else null
            val food = grid.foodAt(species, gridX, gridY)
            gardenRepository.upsertGridObject(
                GridObject(
                    instanceId = UUID.randomUUID().toString(),
                    gridX = across?.origin?.x ?: gridX,
                    gridY = across?.origin?.y ?: gridY,
                    isFlipped = facing,
                    turned = across?.longer == true,
                    objectId = item.objectId,
                    nutritionLevel = food,
                    status = if (Soil.isEmpty(food)) PlantStatus.UNNOURISHED else PlantStatus.HEALTHY,
                    lastUpdatedTimestamp = now,
                    // A building is not there at a tap: it goes up over its building time.
                    workKind = if (item.buildSeconds > 0) WorkKind.BUILDING else WorkKind.NONE,
                    workStartedAt = if (item.buildSeconds > 0) now else 0L,
                    workEndsAt = if (item.buildSeconds > 0) now + item.buildSeconds * 1000L else 0L,
                )
            )
            val remaining = slot.amount - 1
            gardenRepository.upsertInventorySlot(
                if (remaining > 0) slot.copy(amount = remaining) else InventorySlot(slotIndex)
            )
            // A building earns and is recorded when it is unveiled; anything else now, but for a
            // lawn, which goes into the book once it has grown in (SyncGardenUseCase).
            if (item.buildSeconds <= 0) {
                if (item.objectId != LawnGrowth.OBJECT_ID) recordCollection(item.objectId, CollectionTrigger.PLACEMENT, now)
                if (item.placeXp > 0) {
                    gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it.gainXp(item.placeXp)) }
                }
            }
            ActionResult.SUCCESS
        }
}

/**
 * The hoe: turns open ground into tilled earth, and tilled earth into ridged soil
 * ([Soil.HOE_STRIKES] strikes each; flowers go on either, vegetables on ridges). It is a tool, not
 * an item, so it costs nothing and is never used up. The cell must be free: no river, no paving,
 * nothing standing on it. It only ever goes forward: the way back is by clearing away
 * ([RemoveObjectUseCase]).
 *
 * [strikes] is how many the cell has had already; until there are enough a strike only answers
 * [ActionResult.HOE_STRUCK] and nothing is stored. A cell left half done stays so while the app is
 * open ([GardenViewModel]'s strikes), and starts over after that.
 *
 * During the first steps the hoe only goes on the cell that shines ([PracticeGarden.PLOT]), and
 * until the guided steps are over it tills nothing more ([TutorialStep.allows]).
 */
class HoeUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val gardenMap: GardenMapSource,
) {
    suspend operator fun invoke(gridX: Int, gridY: Int, now: Long, strikes: Int = 0): ActionResult =
        gardenRepository.runInTransaction {
            val progress = gardenRepository.getProgress()
            if (progress != null && !progress.tutorialStep.allows(TutorialTool.HOE)) return@runInTransaction ActionResult.NOT_THIS_STEP
            if (progress?.tutorialStep == TutorialStep.TILL_SOIL && GridCell(gridX, gridY) != PracticeGarden.PLOT) {
                return@runInTransaction ActionResult.NOT_THIS_STEP
            }
            val grid = GardenGrid(gardenMap.current(), gardenRepository.getGridObjects())
            val loose = grid.floorAt(gridX, gridY)?.takeIf { it.isLooseSoil && grid.occupantAt(gridX, gridY) == null }
            if (loose == null) {
                // Checked like terrain, which needs the whole cell to itself.
                when (grid.checkPlacement(Footprint.SINGLE, gridX, gridY, PlacementKind.TERRAIN)) {
                    PlacementCheck.OUT_OF_BOUNDS -> return@runInTransaction ActionResult.OUT_OF_BOUNDS
                    PlacementCheck.NOT_BUILDABLE -> return@runInTransaction ActionResult.TILE_NOT_BUILDABLE
                    PlacementCheck.OCCUPIED -> return@runInTransaction ActionResult.TILE_OCCUPIED
                    PlacementCheck.OK -> Unit
                }
            }
            if (strikes + 1 < Soil.HOE_STRIKES) return@runInTransaction ActionResult.HOE_STRUCK
            gardenRepository.upsertGridObject(
                // Ridged, the cell keeps its food and grade: it is the same soil.
                loose?.copy(objectId = GridObject.TILLED_SOIL_OBJECT_ID)
                    ?: soil(gridX, gridY, now, GridObject.LOOSE_SOIL_OBJECT_ID),
            )
            ActionResult.SUCCESS
        }

    /**
     * Gardens saved before the hoe existed have plants standing on bare ground. This puts ridged
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
            .map { soil(it.gridX, it.gridY, now, GridObject.TILLED_SOIL_OBJECT_ID) }
        if (missing.isNotEmpty()) gardenRepository.upsertGridObjects(missing)
    }

    private fun soil(gridX: Int, gridY: Int, now: Long, objectId: String) = GridObject(
        instanceId = UUID.randomUUID().toString(),
        gridX = gridX,
        gridY = gridY,
        objectId = objectId,
        lastUpdatedTimestamp = now,
    )
}

/**
 * Harvests a fully grown plant. What becomes of it depends on its [HarvestType]: a
 * single-harvest crop is taken up and its soil is free again; a repeatable one stays where it is,
 * back at the start of its second stage, to ripen again. Either way the harvest pays
 * [PlantSpecies.harvestReward] and records the plant in the encyclopedia.
 *
 * A crop that lives in the soil ([PlantGround.usesSoil]) takes its food and raises its grade
 * ([Soil.afterHarvest]). With food in the soil the harvest may be a good one
 * ([SoilGrade.bountyChance], [Soil.bounty]); with none it is an ordinary one.
 *
 * Past its season a repeatable crop has no more in it ([endsAtHarvest]): this harvest is its last,
 * and it goes into the soil as compost, as if it had been pulled up. Under glass there are no seasons.
 *
 * A withered plant cannot be harvested: it is cleared away ([RemoveObjectUseCase]) or revived.
 */
class HarvestPlantUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
    private val recordCollection: RecordCollectionUseCase,
) {
    /**
     * @param random for whether it is a good harvest.
     * @param onPaid told what the harvest paid, and whether it was a good one.
     */
    suspend operator fun invoke(
        instanceId: String,
        now: Long,
        weather: WeatherForecast,
        random: Random = Random.Default,
        onPaid: (HarvestReward, Boolean) -> Unit = { _, _ -> },
    ): ActionResult =
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
            val soil = Soil.under(current, garden)?.takeIf { species.ground.usesSoil }
            val plain = species.harvestReward(current.harvestCount)
            val fed = soil != null && !Soil.isEmpty(soil.nutritionLevel)
            val bounty = fed && random.nextFloat() < SoilGrade.of(soil!!.soilQuality).bountyChance
            val reward = if (bounty) Soil.bounty(plain) else plain

            val season = gardenRepository.getProgress()?.season
            val ended = endsAtHarvest(current, season, soil?.isGreenhouse == true)
            val tended = soil?.let { Soil.afterHarvest(it, current.objectId, now, later = current.harvestCount > 0) }
            val left = if (ended) null else afterHarvest(current, species, now)
            if (tended != null) gardenRepository.upsertGridObject(if (ended) Soil.withCompost(tended) else tended)
            left?.let { plant ->
                val food = tended?.nutritionLevel ?: plant.nutritionLevel
                gardenRepository.upsertGridObject(
                    plant.copy(
                        nutritionLevel = food,
                        status = CalculateOfflineProgressUseCase.statusOf(plant.moistureLevel.toDouble(), food.toDouble(), plant.stressRate.toDouble()),
                    ),
                )
            } ?: gardenRepository.deleteGridObject(instanceId)
            // With the colour it bloomed in, for the book to show the one that was grown (#59).
            val opened = BloomColours.settled(current, garden, profile.playerLevel, season ?: Season.SPRING, random)
            recordCollection(current.objectId, CollectionTrigger.HARVEST, now, colour = BloomColours.colourOf(opened))
            gardenRepository.saveUserProfile(profile.copy(coins = profile.coins + reward.coins).gainXp(reward.xp))
            onPaid(reward, bounty)
            ActionResult.SUCCESS
        }

    companion object {
        /**
         * Whether the next harvest of [plant] is its last because its season has gone by: a crop
         * that bears again, out of its season ([GardenItem.isInSeason]) and not under glass.
         * A crop that is harvested once ends with its harvest anyway.
         */
        fun endsAtHarvest(plant: GridObject, season: Season?, underGlass: Boolean): Boolean {
            val species = PlantSpecies.fromObjectId(plant.objectId) ?: return false
            if (!species.isRepeatable || underGlass || season == null) return false
            return GardenItem.entries.firstOrNull { it.objectId == plant.objectId }?.isInSeason(season) == false
        }

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
 * Plants cannot go back (a seed that has grown is not a seed any more) and neither can what the
 * hoe made, which was never an item. A fence goes back as its kind of fence however it was joined up.
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
            if (gridObject.isPlant || gridObject.isHoedSoil) return null
            val objectId = gridObject.runKind?.objectId ?: gridObject.objectId
            return GardenItem.entries.firstOrNull { it.objectId == objectId }
        }
    }
}

/**
 * Clears a tile. Nothing is refunded, with one consolation: a withered crop pulled up goes into its
 * soil as compost, a serving of food ([Soil.withCompost]). A crop pulled while it grows leaves none.
 *
 * What the hoe made is not cleared but turned back, one step at a time: ridges to tilled earth,
 * tilled earth to the ground it was ([Soil.groundBelow]). For free and at once. A crop that does
 * not grow on the ground left goes with it (asked about first, [Soil.takesPlantBack]); any other
 * stays where it is.
 */
class RemoveObjectUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val target = objects.firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        if (target.isHoedSoil) return@runInTransaction turnBack(target, objects)
        gardenRepository.deleteGridObject(instanceId)
        if (leavesCompost(target)) {
            Soil.under(target, objects)?.let { gardenRepository.upsertGridObject(Soil.withCompost(it)) }
        }
        ActionResult.SUCCESS
    }

    /** Turns the ground under the plant [instanceId] back a step. */
    suspend fun turnBackUnder(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val plant = objects.firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        val soil = objects.firstOrNull { it.isHoedSoil && it.gridX == plant.gridX && it.gridY == plant.gridY }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        turnBack(soil, objects)
    }

    private suspend fun turnBack(soil: GridObject, objects: List<GridObject>): ActionResult {
        val plant = objects.firstOrNull { it.isPlant && it.gridX == soil.gridX && it.gridY == soil.gridY }
        if (plant != null && Soil.takesPlantBack(soil, plant)) gardenRepository.deleteGridObject(plant.instanceId)
        if (soil.isTilledSoil) {
            gardenRepository.upsertGridObject(soil.copy(objectId = GridObject.LOOSE_SOIL_OBJECT_ID))
        } else {
            gardenRepository.deleteGridObject(soil.instanceId)
        }
        return ActionResult.SUCCESS
    }

    companion object {
        /**
         * Whether clearing [gridObject] away turns it into compost, given soil to dig it into: a
         * withered crop that grew in the soil ([PlantGround.usesSoil]; a log only lay on it).
         */
        fun leavesCompost(gridObject: GridObject): Boolean =
            gridObject.isPlant && gridObject.status == PlantStatus.WITHERED &&
                PlantSpecies.forObjectId(gridObject.objectId).ground.usesSoil
    }
}

/**
 * Buys one [GardenItem]: tops up an existing stack of it if one has room, otherwise takes the
 * first empty slot. Coins are only spent when the item actually fits. Items the player has not
 * unlocked yet cannot be bought, whatever they can afford. The first steps' seed is given once:
 * asking for it again is answered with [ActionResult.NOT_THIS_STEP].
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
        // The first steps hand one seed over the counter for nothing, however often it is tapped.
        val step = gardenRepository.getProgress()?.tutorialStep
        val inBag = gardenRepository.getInventory().any { it.itemId == item.itemId && !it.isEmpty }
        if (step?.hasGivenAway(item) == true || (step?.givesAway(item) == true && inBag)) {
            return@runInTransaction ActionResult.NOT_THIS_STEP
        }
        val price = if (step?.givesAway(item) == true) 0 else item.price
        if (profile.coins < price) return@runInTransaction ActionResult.NOT_ENOUGH_COINS

        val slots = gardenRepository.getInventory().filter { it.slotIndex < profile.inventoryCapacity }
        val target = slots.firstOrNull { !it.isEmpty && it.itemId == item.itemId && it.amount < InventorySlot.MAX_STACK }
            ?: slots.firstOrNull { it.isEmpty }
            ?: return@runInTransaction ActionResult.INVENTORY_FULL

        val amount = if (target.isEmpty) 1 else target.amount + 1
        gardenRepository.upsertInventorySlot(InventorySlot(target.slotIndex, item.itemId, amount, slots.nextAcquisition()))
        gardenRepository.saveUserProfile(profile.copy(coins = profile.coins - price))
        ActionResult.SUCCESS
    }
}

/**
 * Brings every plant up to [now] and re-stamps the profile. Run on launch / resume and
 * periodically while the garden is open.
 *
 * [now] is the game's time ([com.studioguu.miniaturegarden.domain.repository.GameClock]), which
 * only moves as far as time has really passed, so a device clock set forward brings nothing on
 * and needs no checking here. The profile's signature is checked first: if it is broken, the
 * elapsed time is not credited: objects are simply re-anchored to [now], and building jobs start over.
 */
class SyncGardenUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val verifyIntegrity: VerifyIntegrityUseCase,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
    private val recordCollection: RecordCollectionUseCase,
) {
    suspend operator fun invoke(now: Long, weather: WeatherForecast, random: Random = Random.Default): IntegrityResult {
        val integrity = verifyIntegrity()
        val trusted = integrity.signatureValid
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
            val aged = if (trusted) updated.mapIndexed { index, obj -> if (obj.isSoil) agedSoil(objects[index], objects, now) else obj } else updated
            // A flower that has opened since the last look takes its colour from the soil as it is now.
            val level = gardenRepository.getUserProfile()?.playerLevel ?: 1
            val season = gardenRepository.getProgress()?.season ?: Season.SPRING
            val opened = aged.map { BloomColours.settled(it, aged, level, season, random) }
            val changed = opened.filterIndexed { index, obj -> obj != objects[index] }
            if (changed.isNotEmpty()) gardenRepository.upsertGridObjects(changed)
            // The first lawn to have grown in goes into the book.
            if (opened.any { LawnGrowth.isLawn(it) && it.growthProgress >= 1f } &&
                gardenRepository.getCollection().none { it.entryId == LawnGrowth.OBJECT_ID }
            ) {
                recordCollection(LawnGrowth.OBJECT_ID, CollectionTrigger.PLACEMENT, now)
            }
            gardenRepository.getUserProfile()?.let { gardenRepository.saveUserProfile(it) }
        }
        return integrity
    }

    /** [soil] as time has left it: what counts as growing in it is a plant that has not withered. */
    private fun agedSoil(soil: GridObject, objects: List<GridObject>, now: Long): GridObject {
        val planted = objects.any { it.isPlant && it.gridX == soil.gridX && it.gridY == soil.gridY && it.status != PlantStatus.WITHERED }
        return Soil.afterTime(soil, planted, now)
    }
}

/**
 * Development aid: makes every object [millis] older and applies the resulting offline progress,
 * as if that much time had passed with the app closed. Only object timestamps (and the season's
 * start) move; the game's clock itself is left alone.
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
    private val gardenMap: GardenMapSource,
) {
    suspend operator fun invoke(instanceId: String): ActionResult = gardenRepository.runInTransaction {
        val objects = gardenRepository.getGridObjects()
        val target = objects.firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        if (!target.canFlip) return@runInTransaction ActionResult.NOT_FLIPPABLE
        // Turned round once it stands (#50): a work site is sheeting, with no front to face.
        if (target.isWorking) return@runInTransaction ActionResult.BUSY
        // A bridge turned round crosses the river the other way, from the cell next to its end.
        if (target.isBridge) {
            val over = target.occupiedCells()[1]
            val others = GardenGrid(gardenMap.current(), objects.filter { it.instanceId != instanceId })
            when (val fit = others.bridgeAcross(over.x, over.y, !target.isFlipped)) {
                is BridgeFit.Refused -> return@runInTransaction bridgeRefusal(fit.why)
                is BridgeFit.Across -> gardenRepository.upsertGridObject(
                    target.copy(gridX = fit.origin.x, gridY = fit.origin.y, isFlipped = !target.isFlipped, turned = fit.longer),
                )
            }
            return@runInTransaction ActionResult.SUCCESS
        }
        gardenRepository.upsertGridObject(target.quarterTurned())
        ActionResult.SUCCESS
    }
}

/**
 * Paints the building [instanceId]: free, and at any time ([BuildingPaint]). The picture's own
 * colour is stored as none.
 */
class PaintBuildingUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(instanceId: String, part: PaintPart, colour: PaintColour): ActionResult = gardenRepository.runInTransaction {
        val target = gardenRepository.getGridObjects().firstOrNull { it.instanceId == instanceId }
            ?: return@runInTransaction ActionResult.NOT_FOUND
        val colours = BuildingPaint.colours(target.objectId, part)
        if (colour !in colours) return@runInTransaction ActionResult.NOT_PAINTABLE
        val stored = colour.id.takeIf { colour != colours.first() }
        gardenRepository.upsertGridObject(if (part == PaintPart.ROOF) target.copy(roofColour = stored) else target.copy(wallColour = stored))
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
