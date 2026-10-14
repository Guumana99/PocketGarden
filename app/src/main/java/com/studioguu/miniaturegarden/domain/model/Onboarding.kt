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
 */
enum class TutorialStep {
    /** The starter crate stands in the middle of the garden, waiting to be opened. */
    OPEN_CRATE,

    /** The crate has given its seed; it is to be sown in the tilled bed. */
    PLANT_SEED,

    /** The seed is in the ground and wants its first watering. */
    WATER_SEEDLING,

    /** Done, or never started (a garden from before there were first steps): free play. */
    DONE;

    companion object {
        /** Unknown values count as done: a step that cannot be read is not shown. */
        fun fromName(name: String): TutorialStep = entries.firstOrNull { it.name == name } ?: DONE
    }
}

/** Where the garden as a whole stands: its season and the player's first steps. */
data class GardenProgress(
    val season: Season = Season.SPRING,
    val tutorialStep: TutorialStep = TutorialStep.DONE,
) {
    companion object {
        /** A garden made just now: spring, with the first steps ahead. */
        val NEW_GARDEN = GardenProgress(Season.SPRING, TutorialStep.OPEN_CRATE)

        /** A garden saved before there were first steps: its player knows the way already. */
        val EXISTING_GARDEN = GardenProgress(Season.SPRING, TutorialStep.DONE)
    }
}

/**
 * The garden a new player finds: a small plot in the middle, fenced on three sides and open
 * towards the viewer, with a bed of tilled soil at the front and the starter crate behind it.
 *
 *     y\x 0 1 2 3 4 5 6 7 8
 *      2  . . . . . . . . .
 *      3  . . F F F F F . .      F fence, C crate, T tilled soil
 *      4  . . F . C . F . .
 *      5  . . F T T T F . .
 *      6  . . F T T T F . .
 *      7  . . . . . . . . .
 */
object StarterGarden {
    val CRATE = GridCell(4, 4)

    val BED: List<GridCell> = (5..6).flatMap { y -> (3..5).map { x -> GridCell(x, y) } }

    val FENCE: List<GridCell> =
        (2..6).map { x -> GridCell(x, 3) } + (4..6).flatMap { y -> listOf(GridCell(2, y), GridCell(6, y)) }

    /** What the crate holds: one seed, the first crop of the game. */
    val CRATE_REWARD: GardenItem = GardenItem.SEED_TULIP
    const val CRATE_REWARD_AMOUNT = 1

    /** Everything the new garden starts with, finished and standing. */
    fun objects(now: Long): List<GridObject> =
        BED.map { cell -> GridObject("starter_soil_${cell.x}_${cell.y}", cell.x, cell.y, objectId = GridObject.TILLED_SOIL_OBJECT_ID, lastUpdatedTimestamp = now) } +
            FENCE.map { cell -> GridObject("starter_fence_${cell.x}_${cell.y}", cell.x, cell.y, objectId = GridObject.FENCE_OBJECT_ID, lastUpdatedTimestamp = now) } +
            GridObject(STARTER_CRATE_INSTANCE_ID, CRATE.x, CRATE.y, objectId = GridObject.STARTER_CRATE_OBJECT_ID, lastUpdatedTimestamp = now)

    const val STARTER_CRATE_INSTANCE_ID = "starter_crate"
}

/** Which cells the first steps point at, with a quiet sparkle, while each step is the current one. */
object TutorialGuide {

    /**
     * The crate while it waits to be opened; then the free bed cell nearest the middle of the
     * bed; then the young plant. Nothing at all once the steps are done.
     */
    fun hintCells(step: TutorialStep, objects: List<GridObject>, gardenMap: GardenMap = GardenMap.DEFAULT): Set<GridCell> {
        val grid = GardenGrid(gardenMap, objects)
        return when (step) {
            TutorialStep.OPEN_CRATE -> objects.filter { it.isStarterCrate }.map { GridCell(it.gridX, it.gridY) }.toSet()
            TutorialStep.PLANT_SEED -> objects
                .filter { it.isTilledSoil && grid.occupantAt(it.gridX, it.gridY) == null }
                .minWithOrNull(compareBy({ distanceToBed(it) }, { it.gridY }, { it.gridX }))
                ?.let { setOf(GridCell(it.gridX, it.gridY)) }
                ?: emptySet()
            TutorialStep.WATER_SEEDLING -> tutorialPlant(objects)?.let { setOf(GridCell(it.gridX, it.gridY)) } ?: emptySet()
            TutorialStep.DONE -> emptySet()
        }
    }

    /** The plant the watering step is about: the first one that is alive. */
    fun tutorialPlant(objects: List<GridObject>): GridObject? =
        objects.filter { it.isPlant && it.status != PlantStatus.WITHERED }.minWithOrNull(compareBy({ it.gridY }, { it.gridX }))

    // Squared distance, doubled so that the bed's centre (4, 5.5) stays on whole numbers.
    private fun distanceToBed(obj: GridObject): Int {
        val dx = 2 * obj.gridX - 8
        val dy = 2 * obj.gridY - 11
        return dx * dx + dy * dy
    }
}
