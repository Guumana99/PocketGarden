package com.studioguu.miniaturegarden.domain.model

import kotlin.random.Random

/**
 * Bonuses that can only be claimed so many times a day. The first two are earned by watching a
 * rewarded ad to the end; the last is simply for opening the encyclopedia.
 *
 * @property dailyLimit how many times a day it can be claimed.
 */
enum class RewardType(val dailyLimit: Int) {
    /** Halves the remaining growth time of every plant in the garden. */
    BLESSED_RAIN(dailyLimit = 1),

    /** Pays some coins, more as the player's level grows ([coinBonusAmount]). */
    COIN_BONUS(dailyLimit = 5),

    /** Pays [BOOK_OPEN_AMOUNT] coins the first time the encyclopedia is opened each day. */
    BOOK_OPEN(dailyLimit = 1);

    companion object {
        /** The coin bonus at [level] before its luck: 40 + 10 a level. */
        fun coinBonusBase(level: Int): Int = 40 + 10 * level

        /** The luck of a coin bonus: the base is multiplied by somewhere from this ... */
        const val COIN_BONUS_LUCK_LOW = 0.8

        /** ... to this. */
        const val COIN_BONUS_LUCK_HIGH = 1.3

        /**
         * What one coin bonus pays a player at [level]: [coinBonusBase] times a luck drawn from
         * [COIN_BONUS_LUCK_LOW] to [COIN_BONUS_LUCK_HIGH] by [random], rounded to whole coins.
         */
        fun coinBonusAmount(level: Int, random: Random): Int =
            Math.round(coinBonusBase(level) * (COIN_BONUS_LUCK_LOW + random.nextDouble() * (COIN_BONUS_LUCK_HIGH - COIN_BONUS_LUCK_LOW))).toInt()

        const val BOOK_OPEN_AMOUNT = 10

        /** The share of a plant's remaining growth that the blessed rain takes away. */
        const val BLESSED_RAIN_REDUCTION = 0.5f
    }
}

/**
 * How often each reward has been claimed on [epochDay] (days since 1970-01-01, local time).
 * Counts from any other day no longer apply.
 */
data class RewardUsage(
    val epochDay: Long = 0L,
    val used: Map<RewardType, Int> = emptyMap(),
) {
    fun usedOn(today: Long, type: RewardType): Int = if (epochDay == today) used[type] ?: 0 else 0

    fun remaining(type: RewardType, today: Long): Int = (type.dailyLimit - usedOn(today, type)).coerceAtLeast(0)
}
