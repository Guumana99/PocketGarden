package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Soil
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
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
 * A stretch of one weather is computed in O(1) per plant instead of looping over ticks. That
 * works because nothing waters a plant while offline: moisture only falls, so the ticks always
 * split into a healthy phase followed by a deficient phase, and a plant
 * that comes into full bloom stays there; each phase's contribution is a plain multiplication.
 * Time away that spans several kinds of weather is cut at the changes and computed stretch by
 * stretch.
 *
 * How a plant withers:
 * - thirst adds stress, as long as it lasts;
 * - a plant left standing in full bloom goes over: from the moment it is fully grown it gains
 *   stress too, and withers [OVERRIPE_HOURS] later if it is not harvested;
 * - at full stress it is withered. It is never removed: it stays where it is until the player
 *   revives it or clears it away.
 * Rain and snow water every plant and stop all of the above for as long as they fall.
 */
class CalculateOfflineProgressUseCase @Inject constructor() {

    operator fun invoke(
        gridObjects: List<GridObject>,
        currentTimeMillis: Long,
        weather: WeatherForecast = WeatherType.SUNNY,
    ): List<GridObject> = gridObjects.map { invoke(it, currentTimeMillis, weather, gridObjects) }

    /**
     * @param garden everything in the garden, the object itself included. Buildings in it can
     *   help the plants round them (see [PlantSurroundings]); with nothing passed, the plant is
     *   treated as standing in the open.
     */
    operator fun invoke(
        gridObject: GridObject,
        currentTimeMillis: Long,
        weather: WeatherForecast = WeatherType.SUNNY,
        garden: List<GridObject> = emptyList(),
    ): GridObject {
        val surroundings = PlantSurroundings.of(gridObject, garden)
        // One kind of weather throughout: a single stretch.
        if (weather is WeatherType) return advance(gridObject, currentTimeMillis, weather, surroundings)

        // Only the last MAX_OFFLINE_MILLIS count; the stretches are cut out of that window.
        val windowStart = currentTimeMillis - MAX_OFFLINE_MILLIS
        var current = if (gridObject.lastUpdatedTimestamp < windowStart) {
            gridObject.copy(lastUpdatedTimestamp = windowStart)
        } else {
            gridObject
        }
        var cursor = current.lastUpdatedTimestamp
        // Nothing to cut up (this also covers a timestamp in the future, which advance() repairs).
        if (cursor >= currentTimeMillis) {
            return advance(current, currentTimeMillis, weather.at(currentTimeMillis), surroundings)
        }
        while (cursor < currentTimeMillis) {
            // The weather of a stretch is the weather at its start; it holds until the next change.
            val until = min(weather.nextChangeAfter(cursor), currentTimeMillis)
            // Whatever part of a minute is left at the change stays in the object's timestamp and
            // is counted with the next stretch, so nothing is lost at the joins.
            current = advance(current, until, weather.at(cursor), surroundings)
            cursor = until
        }
        return current
    }

    /** Advances one object to [currentTimeMillis] under [weather] throughout. */
    private fun advance(
        gridObject: GridObject,
        currentTimeMillis: Long,
        weather: WeatherType,
        surroundings: PlantSurroundings,
    ): GridObject {
        val elapsed = cappedElapsedMillis(gridObject.lastUpdatedTimestamp, currentTimeMillis)
        val ticks = elapsed / TICK_MILLIS
        val remainder = elapsed % TICK_MILLIS
        // A timestamp in the future (clock rolled back) is pulled back to now, otherwise the
        // object would stay frozen until the wall clock caught up with it.
        val newTimestamp = currentTimeMillis - remainder

        if (ticks == 0L && gridObject.lastUpdatedTimestamp <= currentTimeMillis) return gridObject
        // A lawn comes on with time alone, whatever the weather (LawnGrowth).
        if (ticks > 0L && LawnGrowth.isLawn(gridObject) && LawnGrowth.GROWS_IN_STAGES) {
            val grown = LawnGrowth.grownBy(gridObject.growthProgress, ticks * TICK_MILLIS)
            return gridObject.copy(growthProgress = grown, lastUpdatedTimestamp = newTimestamp)
        }
        if (ticks == 0L || !gridObject.isPlant) return gridObject.copy(lastUpdatedTimestamp = newTimestamp)
        val species = PlantSpecies.forObjectId(gridObject.objectId)
        // A plant that cannot wither (#58) and was saved withered comes back to life.
        if (gridObject.status == PlantStatus.WITHERED && species.canWither) {
            return gridObject.copy(lastUpdatedTimestamp = newTimestamp)
        }

        val protected = weather.isProtective
        // Rain waters the plant before anything else happens.
        val startMoisture = if (protected) 1f else gridObject.moistureLevel
        // A well nearby slows the drying, a hut nearby quickens the growing.
        val moistureDrain = weather.moistureDrainMultiplier * surroundings.moistureDrainFactor * surroundings.soilDrainFactor /
            (species.dryOutHours * TICKS_PER_HOUR)
        val growthPerTick = weather.growthMultiplier * surroundings.growthFactor /
            (species.growthHours * TICKS_PER_HOUR)
        val hydratedTicks = ticksAbove(startMoisture, THIRSTY_THRESHOLD, moistureDrain, ticks)
        val healthyTicks = hydratedTicks
        val thirstyTicks = ticks - hydratedTicks

        // Growing happens in the healthy phase, which comes first. Once the plant is fully grown
        // it is in bloom for every tick that is left, healthy or not.
        val growthLeft = (1.0 - gridObject.growthProgress).coerceAtLeast(0.0)
        val ticksToBloom = if (growthLeft <= 0.0) 0L else ceil(growthLeft / growthPerTick - GROWTH_EPSILON).toLong()
        val bloomTicks = if (healthyTicks >= ticksToBloom) ticks - ticksToBloom else 0L
        val growingTicks = min(healthyTicks, ticksToBloom)

        val moisture = (startMoisture - moistureDrain * ticks).coerceIn(0.0, 1.0)
        val growth = (gridObject.growthProgress + growthPerTick * healthyTicks).coerceIn(0.0, 1.0)
        // A healthy plant that is still growing gets over earlier hardship; one in bloom does
        // not, it only ever goes over. Recovery is applied first, as the healthy phase is first.
        val recovered = max(0.0, gridObject.stressRate - STRESS_RECOVERY_PER_TICK * growingTicks)
        val gained = if (protected) {
            0.0
        } else {
            STRESS_THIRSTY_PER_TICK * thirstyTicks + STRESS_OVERRIPE_PER_TICK * bloomTicks
        }
        val stress = (recovered + gained).coerceIn(0.0, if (species.canWither) 1.0 else NEVER_WITHERED_STRESS)

        val status = statusOf(moisture, gridObject.nutritionLevel.toDouble(), stress)

        return gridObject.copy(
            growthProgress = growth.toFloat(),
            moistureLevel = moisture.toFloat(),
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
            Soil.isEmpty(nutrition.toFloat()) -> PlantStatus.UNNOURISHED
            else -> PlantStatus.HEALTHY
        }

        const val TICK_MILLIS = 60_000L
        const val MAX_OFFLINE_MILLIS = 7L * 24 * 60 * 60 * 1000 // 604,800,000 ms

        const val TICKS_PER_HOUR = 60.0

        /** At or below this level a plant counts as thirsty and gains stress. */
        const val THIRSTY_THRESHOLD = 0.2

        /**
         * A thirsty plant withers after 24 h. Lack of food never withers anything: it is the
         * soil's ([Soil]), runs out by harvests rather than by the hour, and only weakens a crop.
         */
        const val STRESS_THIRSTY_PER_TICK = 1.0 / (24 * TICKS_PER_HOUR)
        const val STRESS_RECOVERY_PER_TICK = 1.0 / (48 * TICKS_PER_HOUR)

        /** A plant left in full bloom withers this many hours after it finished growing. */
        const val OVERRIPE_HOURS = 72

        /** The most stress a plant that cannot wither ([PlantSpecies.canWither]) is ever under. */
        const val NEVER_WITHERED_STRESS = 0.99
        const val STRESS_OVERRIPE_PER_TICK = 1.0 / (OVERRIPE_HOURS * TICKS_PER_HOUR)

        /** Keeps rounding in the growth sums from adding a tick to the time left until bloom. */
        private const val GROWTH_EPSILON = 1e-6
    }
}
