package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import javax.inject.Inject
import kotlin.random.Random

enum class RewardResult {
    GRANTED,

    /** Today's claims are used up; nothing was given. */
    LIMIT_REACHED,
}

/** What a coin bonus came to: the [coins] paid, none when [result] is [RewardResult.LIMIT_REACHED]. */
data class CoinBonus(val result: RewardResult, val coins: Int)

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
     * Blessed rain: every living plant is first brought up to [now], then watered to the full
     * and loses half of the growth it still has ahead of it (a fully grown one is only watered).
     * Its status is worked out again from its levels, so a thirsty plant is thirsty no more.
     * Withered plants are left as they are: the rain does not bring them back.
     *
     * Before, it only shortened the growing time, and the plants' water stayed where it was: a
     * thirsty crop was still thirsty under the rain.
     */
    suspend fun blessedRain(now: Long, weather: WeatherForecast, today: Long): RewardResult {
        if (!rewardLimits.tryConsume(RewardType.BLESSED_RAIN, today)) return RewardResult.LIMIT_REACHED
        gardenRepository.runInTransaction {
            val garden = gardenRepository.getGridObjects()
            val updated = garden
                .filter { it.isPlant }
                .map { calculateOfflineProgress(it, now, weather, garden) }
                .map { plant ->
                    if (plant.status == PlantStatus.WITHERED) return@map plant
                    val remaining = 1f - plant.growthProgress
                    val rained = plant.copy(
                        moistureLevel = 1f,
                        growthProgress = (1f - remaining * (1f - RewardType.BLESSED_RAIN_REDUCTION)).coerceIn(0f, 1f),
                    )
                    rained.copy(
                        status = CalculateOfflineProgressUseCase.statusOf(
                            rained.moistureLevel.toDouble(),
                            rained.nutritionLevel.toDouble(),
                            rained.stressRate.toDouble(),
                        ),
                    )
                }
            if (updated.isNotEmpty()) gardenRepository.upsertGridObjects(updated)
        }
        return RewardResult.GRANTED
    }

    /**
     * Coin bonus: an amount for the player's level, with some luck in it
     * ([RewardType.coinBonusAmount]), through a signed profile save.
     */
    suspend fun coinBonus(today: Long, random: Random = Random.Default): CoinBonus {
        if (!rewardLimits.tryConsume(RewardType.COIN_BONUS, today)) return CoinBonus(RewardResult.LIMIT_REACHED, 0)
        val paid = gardenRepository.runInTransaction {
            gardenRepository.getUserProfile()?.let {
                val amount = RewardType.coinBonusAmount(it.playerLevel, random)
                gardenRepository.saveUserProfile(it.copy(coins = it.coins + amount))
                amount
            } ?: 0
        }
        return CoinBonus(RewardResult.GRANTED, paid)
    }

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
