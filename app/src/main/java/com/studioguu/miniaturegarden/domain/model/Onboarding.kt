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
 * How far a new player has got with the first steps. They follow one another in this order.
 *
 * The guided steps ([WELCOME] to [PRACTICE_DONE]) are played in a practice garden
 * ([com.studioguu.miniaturegarden.domain.repository.PracticeMode]), and it is there that these
 * steps are followed. Leaving it, by coming to the end or by skipping, the saved garden goes
 * straight to the closing board ([FINISHED]) and then to the garden's other features being handed
 * over one at a time ([REVEAL_EDIT], [REVEAL_RAIN], [REVEAL_COLLECTION], [REVEAL_CAMERA],
 * [REVEAL_POST]), each at the player's own tap. A saved garden that is on a guided step has simply
 * not been through them yet. Stored by name, so new steps can be put anywhere in the order.
 *
 * During the guided steps only the tool the step needs can be picked up ([allows]), so the player
 * does one thing at a time.
 */
enum class TutorialStep {
    /** A few words of welcome. */
    WELCOME,

    /** The cell that shines is to be hoed twice over: tilled earth, then ridges. */
    TILL_SOIL,

    /** A seed is to be had from the shop, for nothing ([givesAway]). */
    BUY_SEED,

    /** There is a seed in the bag, and it is to be sown in the ridges. */
    PLANT_SEED,

    /** The seed is in the ground and wants its first watering. */
    WATER_SEEDLING,

    /** The soil has run out of food and the seedling looks it: fertilizer, for nothing. */
    FEED_SEEDLING,

    /** Time is brought on, this once, at the player's tap. */
    GROW,

    /** It is ready to be harvested. */
    HARVEST,

    /** The harvest is in: a word of thanks, and the practice garden is left. */
    PRACTICE_DONE,

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
        WELCOME, GROW, HARVEST, PRACTICE_DONE -> false
        TILL_SOIL -> tool == TutorialTool.HOE
        BUY_SEED -> tool == TutorialTool.SHOP
        PLANT_SEED -> tool == TutorialTool.BAG
        WATER_SEEDLING -> tool == TutorialTool.WATER
        FEED_SEEDLING -> tool == TutorialTool.FERTILIZE
        else -> true
    }

    /** Whether the shop gives [item] away at this step. */
    fun givesAway(item: GardenItem): Boolean = this == BUY_SEED && item == PracticeGarden.SEED

    /** The feature being introduced at this step, if any. */
    val introducing: GardenFeature? get() = GardenFeature.entries.firstOrNull { it.revealedAt == this }

    companion object {
        /**
         * Unknown values count as done: a step that cannot be read is not shown. A garden still
         * waiting at the old starter crate has the first steps ahead of it.
         */
        fun fromName(name: String): TutorialStep =
            if (name == "OPEN_CRATE") WELCOME else entries.firstOrNull { it.name == name } ?: DONE
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
    /** Where the garden's own weather comes from ([WeatherPlanner]); 0 until it has been chosen. */
    val weatherSeed: Long = 0L,
) {

    companion object {
        /** A garden made just now: spring, with the first steps ahead. */
        val NEW_GARDEN = GardenProgress(Season.SPRING, TutorialStep.WELCOME)

        /** A garden saved before there were first steps: its player knows the way already. */
        val EXISTING_GARDEN = GardenProgress(Season.SPRING, TutorialStep.DONE, gardenSize = GridObject.LEGACY_GRID_SIZE)
    }
}

/** What the first steps are played with, in the practice garden. */
object PracticeGarden {
    /** The cell that shines: the middle of a new garden. */
    val PLOT = GridCell(GridObject.GRID_SIZE / 2, GridObject.GRID_SIZE / 2)

    /** The seed the shop gives away: a crop of one short harvest, for every season. */
    val SEED: GardenItem = GardenItem.SEED_MINT
}

/** Which cells the first steps point at, with a quiet sparkle, while each step is the current one. */
object TutorialGuide {

    /** The cell to hoe, then the ridges to sow, then the plant that grows there. */
    fun hintCells(step: TutorialStep, objects: List<GridObject>): Set<GridCell> = when (step) {
        TutorialStep.TILL_SOIL, TutorialStep.PLANT_SEED -> setOf(PracticeGarden.PLOT)
        TutorialStep.WATER_SEEDLING, TutorialStep.FEED_SEEDLING, TutorialStep.HARVEST ->
            tutorialPlant(objects)?.let { setOf(GridCell(it.gridX, it.gridY)) } ?: emptySet()
        else -> emptySet()
    }

    /** The plant the steps are about: the first one that is alive. */
    fun tutorialPlant(objects: List<GridObject>): GridObject? =
        objects.filter { it.isPlant && it.status != PlantStatus.WITHERED }.minWithOrNull(compareBy({ it.gridY }, { it.gridX }))
}
