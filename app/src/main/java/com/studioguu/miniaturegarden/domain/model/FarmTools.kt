package com.studioguu.miniaturegarden.domain.model

import kotlin.math.roundToInt

/*
 * The farm tools grow better with use. Nobody walks the garden, so there is no stamina: a tool
 * costs nothing to use, and using it is what makes it better.
 */

/**
 * A tool that gets better with use. Its level comes from how often it has been used ([xp], one
 * per use that did something), through [thresholds]: the uses needed to reach each level from 1.
 */
enum class FarmTool(private val thresholds: IntArray) {
    /** Ground broken up into a bed. */
    HOE(intArrayOf(0, 5, 15, 30, 50)),

    /** Plants watered. */
    WATERING_CAN(intArrayOf(0, 10, 30, 60, 100));

    val maxLevel: Int get() = thresholds.size

    /** The level a tool used [xp] times has reached, 1 to [maxLevel]. */
    fun levelFor(xp: Int): Int = thresholds.count { xp >= it }.coerceAtLeast(1)

    /** Uses still needed from [xp] to the next level; null at the top. */
    fun usesToNextLevel(xp: Int): Int? = thresholds.getOrNull(levelFor(xp))?.let { it - xp }
}

/**
 * How hard the untouched ground of a cell is, 1 (soft) to [MAX]: the strikes of a new hoe it takes
 * to break it up. It is a property of the place, always the same for the same cell, and mixes
 * soft and stubborn patches across the garden.
 */
object SoilHardness {
    const val MAX = 3

    fun at(gridX: Int, gridY: Int): Int {
        // A small integer hash: neighbours differ, but nothing changes between visits.
        var h = gridX * 73_856_093 xor gridY * 19_349_663
        h = h xor (h ushr 13)
        h *= 0x5bd1e995
        h = h xor (h ushr 15)
        return 1 + (h and 0x7fffffff) % MAX
    }
}

/** What the hoe's level does: fewer strikes to break the ground, and richer beds. */
object HoeSkill {
    /**
     * Strikes needed to till ground of [hardness] with a hoe of [level]: one fewer at each level,
     * never fewer than one. A new hoe takes three strikes on the hardest ground; from level 3 on
     * any ground goes at one.
     */
    fun strikesNeeded(hardness: Int, level: Int): Int = (hardness - (level - 1)).coerceAtLeast(1)

    /** The richness of a bed tilled by a hoe of [level]: 0 from a new hoe, one more each level. */
    fun soilQualityFor(level: Int): Int = (level - 1).coerceIn(0, SoilQuality.MAX)
}

/** How rich a bed is, 0 to [MAX]: a richer bed's crops fetch more. */
object SoilQuality {
    const val MAX = 4

    /** Extra share of a harvest's coins for each point of richness. */
    const val BONUS_PER_POINT = 0.05f

    /** What a harvest of [coins] fetches from a bed of [quality]. */
    fun harvestCoins(coins: Int, quality: Int): Int =
        (coins * (1f + BONUS_PER_POINT * quality.coerceIn(0, MAX))).roundToInt()

    /** The bonus as a whole percentage, for the screen. */
    fun bonusPercent(quality: Int): Int = (BONUS_PER_POINT * 100 * quality.coerceIn(0, MAX)).roundToInt()
}

/** What the watering can's level does: more water at each pour, so fewer pours are needed. */
object WateringCanSkill {
    /** Moisture one pour adds at level 1. */
    const val BASE_AMOUNT = 0.35f

    /** And how much more each level adds. */
    const val AMOUNT_PER_LEVEL = 0.1f

    /** Moisture one pour from a can of [level] adds: 35 points at first, 75 at level 5. */
    fun waterAmount(level: Int): Float = BASE_AMOUNT + AMOUNT_PER_LEVEL * (level - 1).coerceAtLeast(0)
}

/** The levels the hoe and the watering can have reached. */
data class ToolLevels(val hoe: Int = 1, val wateringCan: Int = 1)
