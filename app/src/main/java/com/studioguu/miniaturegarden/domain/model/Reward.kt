package com.studioguu.miniaturegarden.domain.model

/**
 * Bonuses the player can claim by watching a rewarded ad to the end.
 *
 * @property dailyLimit how many times a day it can be claimed.
 */
enum class RewardType(val dailyLimit: Int) {
    /** Halves the remaining growth time of every plant in the garden. */
    BLESSED_RAIN(dailyLimit = 3),

    /** Pays [COIN_BONUS_AMOUNT] coins. */
    COIN_BONUS(dailyLimit = 5);

    companion object {
        const val COIN_BONUS_AMOUNT = 50

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
