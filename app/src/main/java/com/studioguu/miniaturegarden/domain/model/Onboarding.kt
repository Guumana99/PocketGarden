package com.studioguu.miniaturegarden.domain.model

/**
 * The season the garden is in. Only the first one is used so far: a new garden starts in
 * spring, and nothing yet moves it on or changes with it.
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
 * ([REVEAL_RAIN], [REVEAL_COLLECTION], [REVEAL_CAMERA]), each at the player's own tap.
 * Stored by name, so new steps can be put anywhere in the order.
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

    /** The blessed rain has just been handed over and is being introduced. */
    REVEAL_RAIN,

    /** The encyclopedia has just been handed over and is being introduced. */
    REVEAL_COLLECTION,

    /** The camera has just been handed over and is being introduced. */
    REVEAL_CAMERA,

    /** Done, or never started (a garden from before there were first steps): free play. */
    DONE;

    /** Whether one of the guided steps is current: a hint sign shows and a cell sparkles. */
    val isGuiding: Boolean get() = this < FINISHED

    /** Whether [feature] is in the player's hands at this step. */
    fun reveals(feature: GardenFeature): Boolean = this >= feature.revealedAt

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
    BLESSED_RAIN(TutorialStep.REVEAL_RAIN),
    COLLECTION(TutorialStep.REVEAL_COLLECTION),
    CAMERA(TutorialStep.REVEAL_CAMERA),
}

/** Where the garden as a whole stands: its season and the player's first steps. */
data class GardenProgress(
    val season: Season = Season.SPRING,
    val tutorialStep: TutorialStep = TutorialStep.DONE,
    /** How many cells the garden is across; see [GardenExpansion]. */
    val gardenSize: Int = GridObject.GRID_SIZE,
) {
    companion object {
        /** A garden made just now: spring, with the first steps ahead. */
        val NEW_GARDEN = GardenProgress(Season.SPRING, TutorialStep.OPEN_CRATE)

        /** A garden saved before there were first steps: its player knows the way already. */
        val EXISTING_GARDEN = GardenProgress(Season.SPRING, TutorialStep.DONE)
    }
}

/**
 * The garden a new player finds: bare lawn, with nothing on it but the starter crate in the
 * middle. Fences, beds and everything else are theirs to make.
 */
object StarterGarden {
    val CRATE = GridCell(4, 4)

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
     * The crate while it waits to be opened; then the bare lawn cell nearest the middle, to be
     * tilled; then the free tilled cell nearest the middle; then the young plant. Nothing at all
     * once the guided steps are over.
     */
    fun hintCells(step: TutorialStep, objects: List<GridObject>, gardenMap: GardenMap = GardenMap.DEFAULT): Set<GridCell> {
        val grid = GardenGrid(gardenMap, objects)
        fun nearestMiddle(cells: List<GridCell>): Set<GridCell> =
            cells.minWithOrNull(compareBy({ distanceToMiddle(it, gardenMap.size) }, { it.y }, { it.x }))?.let { setOf(it) } ?: emptySet()
        return when (step) {
            TutorialStep.OPEN_CRATE -> objects.filter { it.isStarterCrate }.map { GridCell(it.gridX, it.gridY) }.toSet()
            TutorialStep.TILL_SOIL -> nearestMiddle(
                (0 until gardenMap.size).flatMap { y -> (0 until gardenMap.size).map { x -> GridCell(x, y) } }
                    .filter { grid.checkPlacement(Footprint.SINGLE, it.x, it.y, PlacementKind.TERRAIN) == PlacementCheck.OK },
            )
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
