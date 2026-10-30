package com.studioguu.miniaturegarden.domain.model

import kotlin.random.Random

/** How rare a colour of a flower is. */
enum class BloomRarity { COMMON, RARE, VERY_RARE }

/**
 * Which colour a flower opens in. It is decided once, as the flower reaches its last stage, from
 * the soil it stands in then, and stays the plant's own ([GridObject.bloomColour]) whatever
 * happens to the soil afterwards.
 *
 *     rarity     tulip              when
 *     COMMON     red, pink, yellow  always
 *     RARE       white, purple      on good soil or better: 15 % between them
 *     VERY_RARE  blue               on fluffy soil, in spring, from Lv.10: 3 %
 *
 * The very rare one is rolled first and the rare ones only if it did not come up.
 */
object BloomColours {
    /** Stored for a plant in bloom before colours were decided like this: it goes by its id. */
    const val BY_ID = "by_id"

    const val RARE_CHANCE = 0.15f
    const val VERY_RARE_CHANCE = 0.03f
    const val VERY_RARE_LEVEL = 10

    private val RARITY: Map<PlantSpecies, Map<String, BloomRarity>> = mapOf(
        PlantSpecies.TULIP to mapOf(
            "red" to BloomRarity.COMMON,
            "pink" to BloomRarity.COMMON,
            "yellow" to BloomRarity.COMMON,
            "white" to BloomRarity.RARE,
            "purple" to BloomRarity.RARE,
            "blue" to BloomRarity.VERY_RARE,
        ),
    )

    fun rarityOf(species: PlantSpecies, colour: String): BloomRarity = RARITY[species]?.get(colour) ?: BloomRarity.COMMON

    /** The colour [plant] blooms in; null for a crop of one colour, or one that has not opened. */
    fun colourOf(plant: GridObject): String? {
        val species = PlantSpecies.fromObjectId(plant.objectId) ?: return null
        return if (plant.bloomColour == BY_ID) species.bloomColourOf(plant.instanceId) else plant.bloomColour
    }

    /** The colour a flower of [species] opens in, on soil of [grade]. Null for a crop of one colour. */
    fun pick(species: PlantSpecies, grade: SoilGrade, level: Int, season: Season, random: Random): String? {
        val colours = RARITY[species] ?: return null
        fun of(rarity: BloomRarity) = colours.filterValues { it == rarity }.keys.toList().random(random)
        val veryRare = grade == SoilGrade.FLUFFY && level >= VERY_RARE_LEVEL && season == Season.SPRING
        return when {
            veryRare && random.nextFloat() < VERY_RARE_CHANCE -> of(BloomRarity.VERY_RARE)
            grade >= SoilGrade.GOOD && random.nextFloat() < RARE_CHANCE -> of(BloomRarity.RARE)
            else -> of(BloomRarity.COMMON)
        }
    }

    /** [plant] with its colour decided, if it has just opened; otherwise as it is. */
    fun settled(plant: GridObject, garden: List<GridObject>, level: Int, season: Season, random: Random): GridObject {
        if (!plant.isPlant || plant.bloomColour != null || plant.growthProgress < 1f) return plant
        val species = PlantSpecies.fromObjectId(plant.objectId) ?: return plant
        val grade = SoilGrade.of(Soil.under(plant, garden)?.soilQuality ?: 0)
        return plant.copy(bloomColour = pick(species, grade, level, season, random) ?: return plant)
    }
}
