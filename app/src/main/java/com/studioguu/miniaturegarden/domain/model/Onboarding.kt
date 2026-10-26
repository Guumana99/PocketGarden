package com.studioguu.miniaturegarden.domain.model

/**
 * The season the garden is in. A new garden starts in spring; [SeasonClock] moves it on. It decides
 * which seeds the shop sells and which can be sown outside a greenhouse, and how the garden looks.
 */
enum class Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    companion object {
        /** Unknown values (e.g. written by a newer app version) count as spring. */
        fun fromName(name: String): Season = entries.firstOrNull { it.name == name } ?: SPRING
    }
}

/**
 * How far a new player has got with the first steps. They follow one another in this order,
 * and none of them stops the player from doing anything else in the meantime.
 *
 * The guided steps ([OPEN_CRATE] to [WATER_SEEDLING]) are followed by a closing board
 * ([FINISHED]) and then by the garden's other features being handed over one at a time
 * ([REVEAL_EDIT], [REVEAL_RAIN], [REVEAL_COLLECTION], [REVEAL_CAMERA], [REVEAL_POST]), each at
 * the player's own tap. Stored by name, so new steps can be put anywhere in the order.
 *
 * During the guided steps only the tool the step needs can be picked up ([allows]): nothing
 * while the crate waits, the hoe to till, the bag to sow, the watering can to water. So the
 * player does one thing at a time, and tills one cell, not a field.
 */
enum class TutorialStep {
    /** The starter crate stands in the middle of the garden, waiting to be opened. */
    OPEN_CRATE,

    /** The crate has given its seed; first a patch of the lawn has to be tilled with the hoe. */
    TILL_SOIL,

    /** There is tilled soil; the seed is to be sown in it. */
    PLANT_SEED,

    /** The seed is in the ground and wants its first watering. */
    WATER_SEEDLING,

    /** The first steps are over: the closing board is showing. */
    FINISHED,

    /** Edit mode (and the grid) has just been handed over and is being introduced (#46). */
    REVEAL_EDIT,

    /** The blessed rain has just been handed over and is being introduced. */
    REVEAL_RAIN,

    /** The encyclopedia has just been handed over and is being introduced. */
    REVEAL_COLLECTION,

    /** The camera has just been handed over and is being introduced. */
    REVEAL_CAMERA,

    /** The post has just been handed over and is being introduced. */
    REVEAL_POST,

    /** Done, or never started (a garden from before there were first steps): free play. */
    DONE;

    /** Whether one of the guided steps is current: a hint sign shows and a cell sparkles. */
    val isGuiding: Boolean get() = this < FINISHED

    /**
     * Whether the garden is still in its first play: the guided steps and their closing board.
     * The weather is fixed to sunny meanwhile ([WeatherForecast.forGarden]); the season is spring
     * anyway, as every new garden starts in it ([GardenProgress.NEW_GARDEN]).
     */
    val isFirstPlay: Boolean get() = this <= FINISHED

    /** Whether [feature] is in the player's hands at this step. */
    fun reveals(feature: GardenFeature): Boolean = this >= feature.revealedAt

    /**
     * Whether [tool] can be picked up at this step. During the guided steps only the one the
     * step asks for; from the closing board on, all of them.
     */
    fun allows(tool: TutorialTool): Boolean = when (this) {
        OPEN_CRATE -> false
        TILL_SOIL -> tool == TutorialTool.HOE
        PLANT_SEED -> tool == TutorialTool.BAG
        WATER_SEEDLING -> tool == TutorialTool.WATER
        else -> true
    }

    /** The feature being introduced at this step, if any. */
    val introducing: GardenFeature? get() = GardenFeature.entries.firstOrNull { it.revealedAt == this }

    companion object {
        /** Unknown values count as done: a step that cannot be read is not shown. */
        fun fromName(name: String): TutorialStep = entries.firstOrNull { it.name == name } ?: DONE
    }
}

/**
 * The parts of the game that are put away during the first steps, so that a new player meets
 * one thing at a time, and handed over one by one once they are done.
 */
enum class GardenFeature(val revealedAt: TutorialStep) {
    /** Edit mode, where the garden is laid out (and the grid shows). Handed over first (#46). */
    EDIT_MODE(TutorialStep.REVEAL_EDIT),
    BLESSED_RAIN(TutorialStep.REVEAL_RAIN),
    COLLECTION(TutorialStep.REVEAL_COLLECTION),
    CAMERA(TutorialStep.REVEAL_CAMERA),
    POST(TutorialStep.REVEAL_POST),
}

/** The tools along the bottom of the garden, which the guided steps hand out one at a time. */
enum class TutorialTool { HOE, WATER, FERTILIZE, BAG, SHOP }

/** Where the garden as a whole stands: its season and the player's first steps. */
data class GardenProgress(
    val season: Season = Season.SPRING,
    val tutorialStep: TutorialStep = TutorialStep.DONE,
    /** How many cells the garden is across; see [GardenExpansion]. */
    val gardenSize: Int = GridObject.GRID_SIZE,
    /** When the current season began (epoch millis); 0 until the season clock has started. */
    val seasonStartedAt: Long = 0L,
    /** How often the hoe has tilled a bed: its level ([FarmTool.HOE]). */
    val hoeXp: Int = 0,
    /** How often the watering can has watered: its level ([FarmTool.WATERING_CAN]). */
    val wateringCanXp: Int = 0,
    /** Where the garden's own weather comes from ([WeatherPlanner]); 0 until it has been chosen. */
    val weatherSeed: Long = 0L,
) {
    val hoeLevel: Int get() = FarmTool.HOE.levelFor(hoeXp)
    val wateringCanLevel: Int get() = FarmTool.WATERING_CAN.levelFor(wateringCanXp)

    companion object {
        /** A garden made just now: spring, with the first steps ahead. */
        val NEW_GARDEN = GardenProgress(Season.SPRING, TutorialStep.OPEN_CRATE)

        /** A garden saved before there were first steps: its player knows the way already. */
        val EXISTING_GARDEN = GardenProgress(Season.SPRING, TutorialStep.DONE, gardenSize = GridObject.LEGACY_GRID_SIZE)
    }
}

/**
 * The garden a new player finds: bare lawn, with nothing on it but the starter crate in the
 * middle. Fences, beds and everything else are theirs to make.
 */
object StarterGarden {
    /** The middle of a new garden. */
    val CRATE = GridCell(GridObject.GRID_SIZE / 2, GridObject.GRID_SIZE / 2)

    /** What the crate holds: one seed, the first crop of the game. */
    val CRATE_REWARD: GardenItem = GardenItem.SEED_TULIP
    const val CRATE_REWARD_AMOUNT = 1

    /** Everything the new garden starts with: the crate alone. */
    fun objects(now: Long): List<GridObject> =
        listOf(GridObject(STARTER_CRATE_INSTANCE_ID, CRATE.x, CRATE.y, objectId = GridObject.STARTER_CRATE_OBJECT_ID, lastUpdatedTimestamp = now))

    const val STARTER_CRATE_INSTANCE_ID = "starter_crate"
}

/** Which cells the first steps point at, with a quiet sparkle, while each step is the current one. */
object TutorialGuide {

    /**
     * The crate while it waits to be opened; nothing while tilling (any cell of lawn will do, the
     * player chooses); then the free tilled cell nearest the middle; then the young plant.
     * Nothing at all once the guided steps are over.
     */
    fun hintCells(step: TutorialStep, objects: List<GridObject>, gardenMap: GardenMap = GardenMap.DEFAULT): Set<GridCell> {
        val grid = GardenGrid(gardenMap, objects)
        fun nearestMiddle(cells: List<GridCell>): Set<GridCell> =
            cells.minWithOrNull(compareBy({ distanceToMiddle(it, gardenMap.size) }, { it.y }, { it.x }))?.let { setOf(it) } ?: emptySet()
        return when (step) {
            TutorialStep.OPEN_CRATE -> objects.filter { it.isStarterCrate }.map { GridCell(it.gridX, it.gridY) }.toSet()
            TutorialStep.TILL_SOIL -> emptySet()
            TutorialStep.PLANT_SEED -> nearestMiddle(
                objects.filter { it.isTilledSoil && grid.occupantAt(it.gridX, it.gridY) == null }.map { GridCell(it.gridX, it.gridY) },
            )
            TutorialStep.WATER_SEEDLING -> tutorialPlant(objects)?.let { setOf(GridCell(it.gridX, it.gridY)) } ?: emptySet()
            else -> emptySet()
        }
    }

    /** The plant the watering step is about: the first one that is alive. */
    fun tutorialPlant(objects: List<GridObject>): GridObject? =
        objects.filter { it.isPlant && it.status != PlantStatus.WITHERED }.minWithOrNull(compareBy({ it.gridY }, { it.gridX }))

    // Squared distance to the middle of a garden [size] across, doubled so that the middle of
    // an even-sized garden stays on whole numbers.
    private fun distanceToMiddle(cell: GridCell, size: Int): Int {
        val dx = 2 * cell.x - (size - 1)
        val dy = 2 * cell.y - (size - 1)
        return dx * dx + dy * dy
    }
}
