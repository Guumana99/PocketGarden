package com.studioguu.miniaturegarden.domain.model

/**
 * The visitor table: which creature comes to the garden for which combination of things in it.
 *
 * Each entry is one combination. To add or tune one, change this table and nothing else in the
 * rules; the screens ask the compiler for the new entry's words (its hint and its name in the
 * nature notes), so none can be forgotten. A creature can have several entries: it comes for
 * any of them, and rolls once, at the best chance among those the garden meets.
 *
 *     entry               creature   needs                                                chance    stays
 *     BUTTERFLY_BLOOMS    butterfly  2 flowers in bloom, fair weather, morning/day        20%       over a bloom
 *     BUTTERFLY_LAVENDER  butterfly  the same, and a lavender (any size)                  25%       over a lavender
 *     LADYBUG_CROPS       ladybug    3 crops (any size) and a tuft of grass               15%       on a crop
 *     LADYBUG_SPRING      ladybug    the same, in spring                                  20%       on a crop
 *     BIRD_BENCH          bird       a bench, not at night                                10% (20%) on the bench
 *     BIRD_FENCE          bird       3 sections of fence, not at night                    10% (20%) beside the fence
 *     BIRD_COTTAGE        bird       a finished cottage, not at night                     10% (20%) on its roof
 *     BIRD_HUT            bird       a finished hut, not at night                         10% (20%) on its roof
 *     BIRD_WELL           bird       a well, not at night                                 10% (20%) beside the well
 *     SQUIRREL_SHIITAKE   squirrel   a shiitake log ready to pick (stage 3)               20% (25%) beside the log
 *     SQUIRREL_SUNFLOWER  squirrel   a sunflower in full bloom and a rock                 20% (25%) beside the rock
 *     FROG_RAIN           frog       3 cells of river or pond, on a rainy day             25%       beside the water
 *     FROG_SUMMER         frog       3 cells of river or pond, in summer                  10%       beside the water
 *     DRAGONFLY_AUTUMN    dragonfly  2 cells of river or pond, late summer or autumn,     20%       over the water
 *                                    fair weather, day/evening
 *
 * In brackets is the chance with its [ChanceBoost]: for a bird a flower in bloom, for a squirrel
 * the autumn.
 *
 * "Fair weather" is sunny or cloudy: no butterfly or dragonfly is out in the rain or the snow.
 * The snow does not count as rain either, so the frogs that come with the rain stay away on a
 * snowy day. The time of day is the device's own ([TimeOfDay]).
 *
 * Only finished things count: a work site is no place to sit.
 *
 * @property conditions all of them must hold.
 * @property anchor what the creature settles on or next to.
 * @property chance how likely it is to come, in a period when it could ([CreatureVisits.PERIOD_MILLIS]).
 * @property boost a better chance when the garden has something more to offer.
 */
enum class VisitRule(
    val creature: Creature,
    val conditions: List<VisitCondition>,
    val anchor: Anchor,
    val placement: VisitPlacement,
    val chance: Float,
    val boost: ChanceBoost? = null,
) {
    BUTTERFLY_BLOOMS(
        Creature.BUTTERFLY,
        conditions = listOf(VisitCondition.Crops(atLeast = 2, species = FLOWERS, minGrowth = 1f), VisitCondition.FAIR_WEATHER, VisitCondition.TimeIs(DAYTIME)),
        anchor = Anchor(FLOWERS.map { it.objectId }.toSet(), minGrowth = 1f),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.2f,
    ),

    // Its scent draws them in flower or not, but only to a garden already in bloom.
    BUTTERFLY_LAVENDER(
        Creature.BUTTERFLY,
        conditions = listOf(
            VisitCondition.Crops(atLeast = 2, species = FLOWERS, minGrowth = 1f),
            VisitCondition.Crops(atLeast = 1, species = setOf(PlantSpecies.LAVENDER)),
            VisitCondition.FAIR_WEATHER,
            VisitCondition.TimeIs(DAYTIME),
        ),
        anchor = Anchor(setOf(PlantSpecies.LAVENDER.objectId)),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.25f,
    ),
    LADYBUG_CROPS(
        Creature.LADYBUG,
        conditions = listOf(VisitCondition.Crops(atLeast = 3), VisitCondition.Things(setOf(LawnGrowth.OBJECT_ID))),
        anchor = Anchor(PlantSpecies.entries.map { it.objectId }.toSet()),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.15f,
    ),
    LADYBUG_SPRING(
        Creature.LADYBUG,
        conditions = listOf(
            VisitCondition.Crops(atLeast = 3),
            VisitCondition.Things(setOf(LawnGrowth.OBJECT_ID)),
            VisitCondition.SeasonIs(setOf(Season.SPRING)),
        ),
        anchor = Anchor(PlantSpecies.entries.map { it.objectId }.toSet()),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.2f,
    ),
    BIRD_BENCH(
        Creature.BIRD,
        conditions = listOf(VisitCondition.Things(setOf("deco_bench")), VisitCondition.TimeIs(NOT_NIGHT)),
        anchor = Anchor(setOf("deco_bench")),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.1f,
        boost = BIRD_BOOST,
    ),
    BIRD_FENCE(
        Creature.BIRD,
        conditions = listOf(VisitCondition.Things(FENCES, atLeast = 3), VisitCondition.TimeIs(NOT_NIGHT)),
        anchor = Anchor(FENCES),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.1f,
        boost = BIRD_BOOST,
    ),
    BIRD_COTTAGE(
        Creature.BIRD,
        conditions = listOf(VisitCondition.Things(setOf(StructureData.COTTAGE.objectId)), VisitCondition.TimeIs(NOT_NIGHT)),
        anchor = Anchor(setOf(StructureData.COTTAGE.objectId)),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.1f,
        boost = BIRD_BOOST,
    ),
    BIRD_HUT(
        Creature.BIRD,
        conditions = listOf(VisitCondition.Things(setOf(StructureData.HUT.objectId)), VisitCondition.TimeIs(NOT_NIGHT)),
        anchor = Anchor(setOf(StructureData.HUT.objectId)),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.1f,
        boost = BIRD_BOOST,
    ),
    BIRD_WELL(
        Creature.BIRD,
        conditions = listOf(VisitCondition.Things(setOf(StructureData.WELL.objectId)), VisitCondition.TimeIs(NOT_NIGHT)),
        anchor = Anchor(setOf(StructureData.WELL.objectId)),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.1f,
        boost = BIRD_BOOST,
    ),
    SQUIRREL_SHIITAKE(
        Creature.SQUIRREL,
        conditions = listOf(VisitCondition.Crops(atLeast = 1, species = setOf(PlantSpecies.SHIITAKE), minGrowth = 1f)),
        anchor = Anchor(setOf(PlantSpecies.SHIITAKE.objectId), minGrowth = 1f),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.2f,
        boost = SQUIRREL_BOOST,
    ),
    SQUIRREL_SUNFLOWER(
        Creature.SQUIRREL,
        conditions = listOf(
            VisitCondition.Crops(atLeast = 1, species = setOf(PlantSpecies.SUNFLOWER), minGrowth = 1f),
            VisitCondition.Things(ROCKS),
        ),
        anchor = Anchor(ROCKS),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.2f,
        boost = SQUIRREL_BOOST,
    ),
    FROG_RAIN(
        Creature.FROG,
        conditions = listOf(VisitCondition.Things(WATER, atLeast = 3), VisitCondition.WeatherIs(WeatherType.RAINY)),
        anchor = Anchor(WATER),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.25f,
    ),
    FROG_SUMMER(
        Creature.FROG,
        conditions = listOf(VisitCondition.Things(WATER, atLeast = 3), VisitCondition.SeasonIs(setOf(Season.SUMMER))),
        anchor = Anchor(WATER),
        placement = VisitPlacement.BESIDE_ANCHOR,
        chance = 0.1f,
    ),
    DRAGONFLY_AUTUMN(
        Creature.DRAGONFLY,
        conditions = listOf(
            VisitCondition.SeasonIs(setOf(Season.AUTUMN), alsoLateIn = Season.SUMMER),
            VisitCondition.Things(WATER, atLeast = 2),
            VisitCondition.FAIR_WEATHER,
            VisitCondition.TimeIs(setOf(TimeOfDay.DAY, TimeOfDay.EVENING)),
        ),
        anchor = Anchor(WATER),
        placement = VisitPlacement.ON_ANCHOR,
        chance = 0.2f,
    );

    /** Whether the garden as it is would draw this creature. */
    fun isMetBy(garden: GardenSnapshot): Boolean = conditions.all { it.isMetBy(garden) }

    /** How likely this combination is to bring its creature to [garden], which meets it. */
    fun chanceIn(garden: GardenSnapshot): Float = if (boost?.condition?.isMetBy(garden) == true) boost.chance else chance

    companion object {
        fun forCreature(creature: Creature): List<VisitRule> = entries.filter { it.creature == creature }
    }
}

/** A better [chance] for a [VisitRule] in a garden that also meets [condition]. */
data class ChanceBoost(val condition: VisitCondition, val chance: Float)

/** The creatures that may visit, in the order the nature notes show them. */
enum class Creature(val id: String) {
    BUTTERFLY("butterfly"),
    LADYBUG("ladybug"),
    BIRD("bird"),
    SQUIRREL("squirrel"),
    FROG("frog"),

    /** Added after the first five; it only comes from the end of summer through autumn. */
    DRAGONFLY("dragonfly"),
}

/** The crops that flower, as the bed they grow in says. */
private val FLOWERS = PlantSpecies.entries.filter { it.isFlower }.toSet()

/** Every kind of rock: a new one goes in here, and all that count rocks count it. */
private val ROCKS = setOf("deco_rock")

/** Every kind of fence, old and new. */
private val FENCES = FencePart.entries.map { it.objectId }.toSet()

/** Water a frog or a dragonfly comes to. */
private val WATER = setOf(GridObject.RIVER_OBJECT_ID, GridObject.POND_OBJECT_ID)

/** A flower in bloom brings a bird out twice as often. */
private val BIRD_BOOST = ChanceBoost(VisitCondition.Crops(atLeast = 1, species = FLOWERS, minGrowth = 1f), 0.2f)

/** In autumn the squirrels are out looking for food. */
private val SQUIRREL_BOOST = ChanceBoost(VisitCondition.SeasonIs(setOf(Season.AUTUMN)), 0.25f)

/** The times of day the butterflies are out. */
private val DAYTIME = setOf(TimeOfDay.MORNING, TimeOfDay.DAY)

/** All but the night: the birds have gone to roost. */
private val NOT_NIGHT = setOf(TimeOfDay.MORNING, TimeOfDay.DAY, TimeOfDay.EVENING)

/** The garden as the visitors see it: what stands in it, today's weather, the season and its phase, and the time of day. */
data class GardenSnapshot(
    val objects: List<GridObject>,
    val weather: WeatherType,
    val season: Season = Season.SPRING,
    val timeOfDay: TimeOfDay = TimeOfDay.DAY,
    val phase: SeasonPhase = SeasonPhase.EARLY,
) {
    /** Everything finished: plants that are alive, and anything that is not a work site. */
    val settled: List<GridObject> = objects.filter { !it.isWorking && !(it.isPlant && it.status == PlantStatus.WITHERED) }
}

/** One thing a [VisitRule] needs to see. */
sealed interface VisitCondition {
    fun isMetBy(garden: GardenSnapshot): Boolean

    /** At least [atLeast] living crops (of [species], or of any kind) grown at least [minGrowth]. */
    data class Crops(val atLeast: Int, val species: Set<PlantSpecies>? = null, val minGrowth: Float = 0f) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.settled.count {
            it.isPlant && (species == null || PlantSpecies.fromObjectId(it.objectId) in species) && it.growthProgress >= minGrowth
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

    /** The garden's season is one of [seasons], or it is the last phase of [alsoLateIn]. */
    data class SeasonIs(val seasons: Set<Season>, val alsoLateIn: Season? = null) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean =
            garden.season in seasons || (garden.season == alsoLateIn && garden.phase == SeasonPhase.LATE)
    }

    /** Today's weather is one of [weathers]. */
    data class WeatherIn(val weathers: Set<WeatherType>) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.weather in weathers
    }

    /** It is one of [times] of day. */
    data class TimeIs(val times: Set<TimeOfDay>) : VisitCondition {
        override fun isMetBy(garden: GardenSnapshot): Boolean = garden.timeOfDay in times
    }

    companion object {
        /** Neither rain nor snow: sunny or cloudy. */
        val FAIR_WEATHER = WeatherIn(setOf(WeatherType.SUNNY, WeatherType.CLOUDY))
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
