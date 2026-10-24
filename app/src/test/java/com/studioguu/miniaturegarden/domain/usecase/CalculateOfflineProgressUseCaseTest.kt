package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.MAX_OFFLINE_MILLIS
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.NUTRITION_DRAIN_PER_TICK
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.STRESS_RECOVERY_PER_TICK
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.STRESS_THIRSTY_PER_TICK
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.STRESS_UNNOURISHED_PER_TICK
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.THIRSTY_THRESHOLD
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.TICK_MILLIS
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase.Companion.UNNOURISHED_THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CalculateOfflineProgressUseCaseTest {

    private val useCase = CalculateOfflineProgressUseCase()
    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L

    // plant() below is a sunflower (#58: the tulip no longer withers), so its rates are the
    // reference for the expected values.
    private val GROWTH_PER_TICK = 1.0 / (PlantSpecies.SUNFLOWER.growthHours * 60.0)
    private val MOISTURE_DRAIN_PER_TICK = 1.0 / (PlantSpecies.SUNFLOWER.dryOutHours * 60.0)

    private fun plant(
        growth: Float = 0f,
        moisture: Float = 1f,
        nutrition: Float = 1f,
        stress: Float = 0f,
        status: PlantStatus = PlantStatus.HEALTHY,
        objectId: String = "plant_sunflower",
    ) = GridObject(
        instanceId = "id",
        gridX = 0,
        gridY = 0,
        objectId = objectId,
        growthProgress = growth,
        moistureLevel = moisture,
        nutritionLevel = nutrition,
        stressRate = stress,
        status = status,
        lastUpdatedTimestamp = t0,
    )

    @Test
    fun `negative delta changes nothing but re-anchors the timestamp`() {
        val now = t0 - 5 * hour
        assertEquals(plant().copy(lastUpdatedTimestamp = now), useCase(plant(), now))
    }

    @Test
    fun `delta is capped at seven days`() {
        val week = useCase(plant(), t0 + MAX_OFFLINE_MILLIS)
        val month = useCase(plant(), t0 + 30 * 24 * hour)
        assertEquals(week.copy(lastUpdatedTimestamp = month.lastUpdatedTimestamp), month)
        assertEquals(t0 + 30 * 24 * hour, month.lastUpdatedTimestamp)
    }

    @Test
    fun `sub-tick time is carried over instead of dropped`() {
        val p = plant()
        assertSame(p, useCase(p, t0 + TICK_MILLIS - 1))

        val result = useCase(p, t0 + TICK_MILLIS + 30_000)
        assertEquals(t0 + TICK_MILLIS, result.lastUpdatedTimestamp)
        assertEquals(1f - MOISTURE_DRAIN_PER_TICK.toFloat(), result.moistureLevel, 1e-6f)
    }

    @Test
    fun `healthy plant grows and dries`() {
        val result = useCase(plant(), t0 + 12 * hour)
        assertEquals(12f / PlantSpecies.SUNFLOWER.growthHours, result.growthProgress, 1e-4f)
        assertEquals(1f - 12f / PlantSpecies.SUNFLOWER.dryOutHours, result.moistureLevel, 1e-4f)
        assertEquals(0f, result.stressRate, 0f)
        assertEquals(PlantStatus.HEALTHY, result.status)
    }

    @Test
    fun `plant withers when stress reaches one`() {
        val result = useCase(plant(), t0 + MAX_OFFLINE_MILLIS)
        assertEquals(1f, result.stressRate, 0f)
        assertEquals(PlantStatus.WITHERED, result.status)
        assertEquals(0f, result.moistureLevel, 0f)
    }

    @Test
    fun `thirsty plant stops growing and gains stress`() {
        val result = useCase(plant(moisture = 0.1f), t0 + 6 * hour)
        assertEquals(0f, result.growthProgress, 0f)
        assertEquals(0.25f, result.stressRate, 1e-4f)
        assertEquals(PlantStatus.THIRSTY, result.status)
    }

    @Test
    fun `rain keeps soil moist so only nutrition matters`() {
        val result = useCase(plant(), t0 + 60 * hour, WeatherType.RAINY)
        assertEquals(1f, result.moistureLevel, 0f)
        assertEquals(PlantStatus.UNNOURISHED, result.status)
    }

    @Test
    fun `withered plants and non-plants only get their timestamp moved`() {
        val now = t0 + 10 * hour
        val withered = plant(stress = 1f, status = PlantStatus.WITHERED)
        assertEquals(withered.copy(lastUpdatedTimestamp = now), useCase(withered, now))
        val rock = plant(objectId = "deco_rock")
        assertEquals(rock.copy(lastUpdatedTimestamp = now), useCase(rock, now))
    }

    @Test
    fun `closed form matches a tick-by-tick simulation`() {
        val starts = listOf(
            plant(),
            plant(growth = 0.3f, moisture = 0.55f, nutrition = 0.9f, stress = 0.4f),
            plant(moisture = 0.9f, nutrition = 0.25f, stress = 0.1f),
            plant(moisture = 0.15f, nutrition = 0.1f),
            plant(growth = 0.8f),                 // comes into bloom on the way and then goes over
            plant(growth = 1f, stress = 0.2f),    // in bloom from the start
        )
        val durations = listOf(1L, 7L, 19L, 31L, 50L, 100L, 168L).map { it * hour + 17 * TICK_MILLIS }
        for (start in starts) for (weather in WeatherType.entries) for (duration in durations) {
            val expected = simulate(start, duration / TICK_MILLIS, weather)
            val actual = useCase(start, t0 + duration, weather)
            val label = "$start $weather ${duration / hour}h"
            assertEquals(label, expected.growthProgress, actual.growthProgress, 2e-3f)
            assertEquals(label, expected.moistureLevel, actual.moistureLevel, 2e-3f)
            assertEquals(label, expected.nutritionLevel, actual.nutritionLevel, 2e-3f)
            assertEquals(label, expected.stressRate, actual.stressRate, 2e-3f)
        }
    }

    /** Reference implementation: the same rules applied one tick at a time. */
    private fun simulate(start: GridObject, ticks: Long, weather: WeatherType): GridObject {
        var growth = start.growthProgress.toDouble()
        var moisture = start.moistureLevel.toDouble()
        var nutrition = start.nutritionLevel.toDouble()
        var stress = start.stressRate.toDouble()
        // Rain waters the plant, and while it falls nothing brings the plant closer to withering.
        val protected = weather.isProtective
        if (protected && ticks > 0) moisture = 1.0
        repeat(ticks.toInt()) {
            val thirsty = moisture <= THIRSTY_THRESHOLD
            val starved = nutrition <= UNNOURISHED_THRESHOLD
            // A plant in full bloom no longer grows or recovers: it only goes over.
            val inBloom = growth >= 1.0 - 1e-9
            if (!thirsty && !starved && !inBloom) {
                growth = (growth + GROWTH_PER_TICK * weather.growthMultiplier).coerceAtMost(1.0)
                stress = (stress - STRESS_RECOVERY_PER_TICK).coerceAtLeast(0.0)
            }
            if (!protected) {
                if (thirsty) stress += STRESS_THIRSTY_PER_TICK
                if (starved) stress += STRESS_UNNOURISHED_PER_TICK
                if (inBloom) stress += CalculateOfflineProgressUseCase.STRESS_OVERRIPE_PER_TICK
            }
            stress = stress.coerceAtMost(1.0)
            moisture = (moisture - MOISTURE_DRAIN_PER_TICK * weather.moistureDrainMultiplier).coerceAtLeast(0.0)
            nutrition = (nutrition - NUTRITION_DRAIN_PER_TICK).coerceAtLeast(0.0)
        }
        return start.copy(
            growthProgress = growth.toFloat(),
            moistureLevel = moisture.toFloat(),
            nutritionLevel = nutrition.toFloat(),
            stressRate = stress.toFloat(),
        )
    }
}
