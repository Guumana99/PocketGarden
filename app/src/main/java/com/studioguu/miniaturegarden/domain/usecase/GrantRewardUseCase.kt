package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import javax.inject.Inject

enum class RewardResult {
    GRANTED,

    /** Today's claims are used up; nothing was given. */
    LIMIT_REACHED,
}

/**
 * Hands out the rewards that are limited per day. Each call uses up one of the day's claims; if
 * none are left, nothing is given. The ad rewards are only asked for once their ad has been
 * watched to the end, so an abandoned ad costs the player nothing.
 */
class GrantRewardUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val rewardLimits: RewardLimitRepository,
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    /**
     * Blessed rain: every living plant is first brought up to [now], then loses half of the
     * growth it still has ahead of it. Fully grown and withered plants are left as they are.
     */
    suspend fun blessedRain(now: Long, weather: WeatherForecast, today: Long): RewardResult {
        if (!rewardLimits.tryConsume(RewardType.BLESSED_RAIN, today)) return RewardResult.LIMIT_REACHED
        gardenRepository.runInTransaction {
            val updated = gardenRepository.getGridObjects()
                .filter { it.isPlant }
                .map { calculateOfflineProgress(it, now, weather) }
                .map { plant ->
                    if (plant.status == PlantStatus.WITHERED) return@map plant
                    val remaining = 1f - plant.growthProgress
                    plant.copy(growthProgress = (1f - remaining * (1f - RewardType.BLESSED_RAIN_REDUCTION)).coerceIn(0f, 1f))
                }
            if (updated.isNotEmpty()) gardenRepository.upsertGridObjects(updated)
        }
        return RewardResult.GRANTED
    }

    /** Coin bonus: [RewardType.COIN_BONUS_AMOUNT] coins, through a signed profile save. */
    suspend fun coinBonus(today: Long): RewardResult =
        payCoins(RewardType.COIN_BONUS, RewardType.COIN_BONUS_AMOUNT, today)

    /**
     * Opening the encyclopedia: [RewardType.BOOK_OPEN_AMOUNT] coins, the first time each day.
     * Opening it again the same day gives nothing, so there is no point in flipping it open and
     * shut.
     */
    suspend fun bookOpen(today: Long): RewardResult = payCoins(RewardType.BOOK_OPEN, RewardType.BOOK_OPEN_AMOUNT, today)

    private suspend fun payCoins(type: RewardType, amount: Int, today: Long): RewardResult {
        if (!rewardLimits.tryConsume(type, today)) return RewardResult.LIMIT_REACHED
        gardenRepository.runInTransaction {
            gardenRepository.getUserProfile()?.let {
                gardenRepository.saveUserProfile(it.copy(coins = it.coins + amount))
            }
        }
        return RewardResult.GRANTED
    }
}
