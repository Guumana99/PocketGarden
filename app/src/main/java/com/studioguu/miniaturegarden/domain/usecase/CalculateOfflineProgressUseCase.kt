package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherType
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Advances plants by the time that passed while the app was not running.
 *
 * Time is consumed in whole ticks of [TICK_MILLIS] (Δt / tick); the remainder (Δt % tick) is
 * carried over by leaving it in `lastUpdatedTimestamp`, so frequent short sessions lose nothing.
 *
 * The result is computed in O(1) per plant instead of looping over ticks. That works because
 * nothing waters or feeds a plant while offline: moisture and nutrition only fall, so the elapsed
 * ticks always split into a healthy phase followed by a deficient phase, and each phase's
 * contribution is a plain multiplication.
 */
class CalculateOfflineProgressUseCase @Inject constructor() {

    operator fun invoke(
        gridObjects: List<GridObject>,
        currentTimeMillis: Long,
        weather: WeatherType = WeatherType.SUNNY,
    ): List<GridObject> = gridObjects.map { invoke(it, currentTimeMillis, weather) }

    operator fun invoke(
        gridObject: GridObject,
        currentTimeMillis: Long,
        weather: WeatherType = WeatherType.SUNNY,
    ): GridObject {
        val elapsed = cappedElapsedMillis(gridObject.lastUpdatedTimestamp, currentTimeMillis)
        val ticks = elapsed / TICK_MILLIS
        val remainder = elapsed % TICK_MILLIS
        // A timestamp in the future (clock rolled back) is pulled back to now, otherwise the
        // object would stay frozen until the wall clock caught up with it.
        val newTimestamp = currentTimeMillis - remainder

        if (ticks == 0L && gridObject.lastUpdatedTimestamp <= currentTimeMillis) return gridObject
        if (ticks == 0L || !gridObject.isPlant || gridObject.status == PlantStatus.WITHERED) {
            return gridObject.copy(lastUpdatedTimestamp = newTimestamp)
        }

        val moistureDrain = MOISTURE_DRAIN_PER_TICK * weather.moistureDrainMultiplier
        val hydratedTicks = ticksAbove(gridObject.moistureLevel, THIRSTY_THRESHOLD, moistureDrain, ticks)
        val nourishedTicks = ticksAbove(gridObject.nutritionLevel, UNNOURISHED_THRESHOLD, NUTRITION_DRAIN_PER_TICK, ticks)
        val healthyTicks = min(hydratedTicks, nourishedTicks)
        val thirstyTicks = ticks - hydratedTicks
        val starvedTicks = ticks - nourishedTicks

        val moisture = (gridObject.moistureLevel - moistureDrain * ticks).coerceIn(0.0, 1.0)
        val nutrition = (gridObject.nutritionLevel - NUTRITION_DRAIN_PER_TICK * ticks).coerceIn(0.0, 1.0)
        val growth = (gridObject.growthProgress + GROWTH_PER_TICK * healthyTicks).coerceIn(0.0, 1.0)
        // The healthy phase comes first, so recovery is applied before stress accumulates.
        val recovered = max(0.0, gridObject.stressRate - STRESS_RECOVERY_PER_TICK * healthyTicks)
        val stress = (recovered + STRESS_THIRSTY_PER_TICK * thirstyTicks + STRESS_UNNOURISHED_PER_TICK * starvedTicks)
            .coerceIn(0.0, 1.0)

        val status = statusOf(moisture, nutrition, stress)

        return gridObject.copy(
            growthProgress = growth.toFloat(),
            moistureLevel = moisture.toFloat(),
            nutritionLevel = nutrition.toFloat(),
            stressRate = stress.toFloat(),
            status = status,
            lastUpdatedTimestamp = newTimestamp,
        )
    }

    /** Δt clamped to 0 .. [MAX_OFFLINE_MILLIS]; also safe when the subtraction itself overflows. */
    private fun cappedElapsedMillis(lastUpdatedTimestamp: Long, currentTimeMillis: Long): Long {
        if (lastUpdatedTimestamp >= currentTimeMillis) return 0L
        val delta = currentTimeMillis - lastUpdatedTimestamp
        return if (delta < 0L) MAX_OFFLINE_MILLIS else min(delta, MAX_OFFLINE_MILLIS)
    }

    /** Number of ticks (out of [ticks]) that begin with the level still above [threshold]. */
    private fun ticksAbove(level: Float, threshold: Double, drainPerTick: Double, ticks: Long): Long {
        val margin = level - threshold
        return when {
            margin <= 0.0 -> 0L
            drainPerTick <= 0.0 -> ticks
            else -> min(ticks, ceil(margin / drainPerTick).toLong())
        }
    }

    companion object {
        /** Status implied by the three levels; withering wins over everything else. */
        fun statusOf(moisture: Double, nutrition: Double, stress: Double): PlantStatus = when {
            stress >= 1.0 -> PlantStatus.WITHERED
            moisture <= THIRSTY_THRESHOLD -> PlantStatus.THIRSTY
            nutrition <= UNNOURISHED_THRESHOLD -> PlantStatus.UNNOURISHED
            else -> PlantStatus.HEALTHY
        }

        const val TICK_MILLIS = 60_000L
        const val MAX_OFFLINE_MILLIS = 7L * 24 * 60 * 60 * 1000 // 604,800,000 ms

        private const val TICKS_PER_HOUR = 60.0

        /** At or below these levels a plant counts as thirsty / unnourished and gains stress. */
        const val THIRSTY_THRESHOLD = 0.2
        const val UNNOURISHED_THRESHOLD = 0.2

        /** Full soil dries out in 24 h (sunny); nutrition lasts 72 h. */
        const val MOISTURE_DRAIN_PER_TICK = 1.0 / (24 * TICKS_PER_HOUR)
        const val NUTRITION_DRAIN_PER_TICK = 1.0 / (72 * TICKS_PER_HOUR)

        /** A seed needs 48 healthy hours to mature. */
        const val GROWTH_PER_TICK = 1.0 / (48 * TICKS_PER_HOUR)

        /** A thirsty plant withers after 24 h, an unnourished one after 48 h; both stack. */
        const val STRESS_THIRSTY_PER_TICK = 1.0 / (24 * TICKS_PER_HOUR)
        const val STRESS_UNNOURISHED_PER_TICK = 1.0 / (48 * TICKS_PER_HOUR)
        const val STRESS_RECOVERY_PER_TICK = 1.0 / (48 * TICKS_PER_HOUR)
    }
}
