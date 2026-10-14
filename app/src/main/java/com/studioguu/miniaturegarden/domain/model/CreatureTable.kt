package com.studioguu.miniaturegarden.domain.model

/**
 * The visitor table: which creature comes to the garden for which combination of things in it.
 *
 * Each entry is one combination. To add or tune one, change this table and nothing else in the
 * rules; the screens ask the compiler for the new entry's words (its hint and its name in the
 * nature notes), so none can be forgotten. A creature can have several entries: it comes for
 * any of them.
 *
 *     entry               creature   needs                                          stays
 *     BUTTERFLY_BLOOMS    butterfly  2 crops in full bloom, not raining             over a bloom
 *     LADYBUG_TULIPS      ladybug    3 tulips (any size) and a tuft of grass        on a tulip
 *     BIRD_BENCH          bird       a bench and 3 sections of fence                on the bench
 *     BIRD_COTTAGE        bird       a finished cottage                             on its roof
 *     SQUIRREL_SUNFLOWER  squirrel   a sunflower in full bloom and a rock           beside the rock
 *     FROG_RAIN           frog       3 river tiles, on a rainy day                  beside the river
 *
 * Only finished things count: a work site is no place to sit.
 *
 * @property conditions all of them must hold.
 * @property anchor what the creature settles on or next to.
 * @property chance how likely it is to come, in a period when it could ([CreatureVisits.PERIOD_MILLIS]).
 */
enum class VisitRule(
    val creature: Creature,
    val conditions: List<VisitCondition>,
    val anchor: Anchor,
    val placement: VisitPlacement,
    val chance: Float,
) {
    BUTTERFLY_BLOOMS(
        Creature.BUTTERFLY,
        conditions = listOf(VisitCondition.Crops(atLeast = 2, minGrowth = 1f), VisitCondition.WeatherNot(WeatherType.RAINY)),
        anchor = Anchor(PlantSpecies.entries.map { it.objectId }.toSet(), minGrowth = 1f),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.6f,
    ),
    LADYBUG_TULIPS(
        Creature.LADYBUG,
        conditions = listOf(VisitCondition.Crops(atLeast = 3, species = PlantSpecies.TULIP), VisitCondition.Things(setOf("ground_grass"))),
        anchor = Anchor(setOf(PlantSpecies.TULIP.objectId)),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.5f,
    ),
    BIRD_BENCH(
        Creature.BIRD,
        conditions = listOf(
            VisitCondition.Things(setOf("deco_bench")),
            VisitCondition.Things(FencePart.entries.map { it.objectId }.toSet(), atLeast = 3),
        ),
        anchor = Anchor(setOf("deco_bench")),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.5f,
    ),
    BIRD_COTTAGE(
        Creature.BIRD,
        conditions = listOf(VisitCondition.Things(setOf(StructureData.COTTAGE.objectId))),
        anchor = Anchor(setOf(StructureData.COTTAGE.objectId)),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.5f,
    ),
    SQUIRREL_SUNFLOWER(
        Creature.SQUIRREL,
        conditions = listOf(
            VisitCondition.Crops(atLeast = 1, species = PlantSpecies.SUNFLOWER, minGrowth = 1f),
            VisitCondition.Things(setOf("deco_rock")),
        ),
        anchor = Anchor(setOf("deco_rock")),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.5f,
    ),
    FROG_RAIN(
        Creature.FROG,
        conditions = listOf(VisitCondition.Things(setOf(GridObject.RIVER_OBJECT_ID), atLeast = 3), VisitCondition.WeatherIs(WeatherType.RAINY)),
        anchor = Anchor(setOf(GridObject.RIVER_OBJECT_ID)),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.7f,
    );

    /** Whether the garden as it is would draw this creature. */
    fun isMetBy(garden: GardenSnapshot): Boolean = conditions.all { it.isMetBy(garden) }

    companion object {
        fun forCreature(creature: Creature): List<VisitRule> = entries.filter { it.creature == creature }
    }
}

/** The creatures that may visit, in the order the nature notes show them. */
enum class Creature(val id: String) {
    BUTTERFLY("butterfly"),
    LADYBUG("ladybug"),
    BIRD("bird"),
    SQUIRREL("squirrel"),
    FROG("frog"),
}

/** The garden as the visitors see it: what stands in it and today's weather. */
data class GardenSnapshot(val objects: List<GridObject>, val weather: WeatherType) {
    /** Everything finished: plants that are alive, and anything that is not a work site. */
    val settled: List<GridObject> = objects.filter { !it.isWorking && !(it.isPlant && it.status == PlantStatus.WITHERED) }
}

/** One thing a [VisitRule] needs to see. */
sealed interface VisitCondition {
    fun isMetBy(garden: GardenSnapshot): Boolean

    /** At least [atLeast] living crops (of [species], or of any kind) grown at least [minGrowth]. */
    data class Crops(val atLeast: Int, val species: PlantSpecies? = null, val minGrowth: Float = 0f) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.settled.count {
            it.isPlant && (species == null || it.objectId == species.objectId) && it.growthProgress >= minGrowth
        } >= atLeast
    }

    /** At least [atLeast] finished things with one of [objectIds]. */
    data class Things(val objectIds: Set<String>, val atLeast: Int = 1) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.settled.count { it.objectId in objectIds } >= atLeast
    }

    /** Today's weather is [weather]. */
    data class WeatherIs(val weather: WeatherType) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.weather == weather
    }

    /** Today's weather is anything but [weather]. */
    data class WeatherNot(val weather: WeatherType) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.weather != weather
    }
}

/** What a visitor settles by: finished things with one of [objectIds], grown at least [minGrowth] if they are crops. */
data class Anchor(val objectIds: Set<String>, val minGrowth: Float = 0f) {
    fun matches(obj: GridObject): Boolean = obj.objectId in objectIds && (!obj.isPlant || obj.growthProgress >= minGrowth)
}

/** Where a visitor is, relative to its [Anchor]. */
enum class VisitPlacement {
    /** On it, or hovering over it: a butterfly on a flower, a bird on a bench or a roof. */
    ON_ANCHOR,

    /** On an empty cell of lawn right next to it. */
    BESIDE_ANCHOR,
}
