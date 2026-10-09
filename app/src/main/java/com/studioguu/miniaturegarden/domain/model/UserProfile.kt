package com.studioguu.miniaturegarden.domain.model

data class UserProfile(
    val coins: Long = 0L,
    val playerLevel: Int = 1,
    /** Total experience earned; [playerLevel] is derived from it via [LevelTable]. */
    val currentXp: Long = 0L,
    val inventoryCapacity: Int = DEFAULT_INVENTORY_CAPACITY,
    /** Wall clock (epoch millis) at the last save. */
    val lastUpdatedTimestamp: Long = 0L,
    /** Monotonic clock (elapsedRealtime millis) at the last save. */
    val lastMonoClockTime: Long = 0L,
    val signature: String = "",
) {
    /** Adds experience and raises [playerLevel] to match. */
    fun gainXp(amount: Int): UserProfile {
        val xp = currentXp + amount
        return copy(currentXp = xp, playerLevel = maxOf(playerLevel, LevelTable.levelForXp(xp)))
    }

    companion object {
        const val DEFAULT_INVENTORY_CAPACITY = 16

        /** What a new player starts with: enough to buy and plant a first few seeds. */
        const val STARTING_COINS = 100L
    }
}
