package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.components.convexHull
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereTest {

    private val sunny = WeatherType.SUNNY
    private val cloudy = WeatherType.CLOUDY
    private val rainy = WeatherType.RAINY

    // --- Light ---

    @Test
    fun `shadows point left in the morning and right in the evening`() {
        assertTrue(Daylight.at(7f, sunny).dx < 0f)
        assertTrue(Daylight.at(10f, sunny).dx < 0f)
        assertEquals(0f, Daylight.at(12f, sunny).dx, 1e-4f)
        assertTrue(Daylight.at(14f, sunny).dx > 0f)
        assertTrue(Daylight.at(17.5f, sunny).dx > 0f)
    }

    @Test
    fun `shadows are shortest at noon and grow towards either end of the day`() {
        val hours = listOf(5.5f, 7f, 9f, 11f, 12f)
        val morning = hours.map { Daylight.at(it, sunny).length }
        assertEquals(morning.sortedDescending(), morning)

        val afternoon = listOf(12f, 13f, 15f, 17f, 18.5f).map { Daylight.at(it, sunny).length }
        assertEquals(afternoon.sorted(), afternoon)

        // Noon: about a third of the thing's height. Near the horizon: longer than the thing.
        assertTrue(Daylight.at(12f, sunny).length < 0.45f)
        assertTrue(Daylight.at(5.5f, sunny).length > 1.2f)
        assertTrue(Daylight.at(18.5f, sunny).length > 1.2f)
    }

    @Test
    fun `morning and evening mirror each other`() {
        for (offset in listOf(1f, 2.5f, 4f, 6f)) {
            val before = Daylight.at(12f - offset, sunny)
            val after = Daylight.at(12f + offset, sunny)
            assertEquals(-before.dx, after.dx, 1e-4f)
            assertEquals(before.dy, after.dy, 1e-4f)
            assertEquals(before.alpha, after.alpha, 1e-4f)
        }
    }

    @Test
    fun `a shadow always falls a little towards the viewer`() {
        var hour = 0f
        while (hour < 24f) {
            for (weather in WeatherType.entries) assertTrue("$hour $weather", Daylight.at(hour, weather).dy > 0f)
            hour += 0.25f
        }
    }

    @Test
    fun `the light moves smoothly through the day`() {
        var previous = Daylight.at(Daylight.SUNRISE_HOUR, sunny)
        var hour = Daylight.SUNRISE_HOUR + 0.1f
        while (hour < Daylight.SUNSET_HOUR) {
            val now = Daylight.at(hour, sunny)
            assertTrue("jump at $hour", abs(now.dx - previous.dx) < 0.08f)
            assertTrue("jump at $hour", abs(now.dy - previous.dy) < 0.08f)
            assertTrue("jump at $hour", abs(now.alpha - previous.alpha) < 0.03f)
            previous = now
            hour += 0.1f
        }
    }

    @Test
    fun `clouds soften the shadows and rain all but removes them`() {
        for (hour in listOf(6f, 9f, 12f, 16f, 22f)) {
            val inSun = Daylight.at(hour, sunny)
            val underCloud = Daylight.at(hour, cloudy)
            val inRain = Daylight.at(hour, rainy)
            assertTrue(inSun.alpha > underCloud.alpha)
            assertTrue(underCloud.alpha > inRain.alpha)
            assertTrue(inRain.alpha > 0f)
            // The weather changes how dark a shadow is, not where it falls.
            assertEquals(inSun.dx, inRain.dx, 0f)
            assertEquals(inSun.dy, inRain.dy, 0f)
        }
        assertTrue(Daylight.at(12f, rainy).alpha < 0.1f)
    }

    @Test
    fun `the sun is strongest at noon and shadows stay soft at any hour`() {
        val noon = Daylight.at(12f, sunny).alpha
        assertEquals(Daylight.shadowStrength(sunny), noon, 1e-4f)
        var hour = 0f
        while (hour < 24f) {
            val alpha = Daylight.at(hour, sunny).alpha
            assertTrue(alpha <= noon + 1e-4f)
            assertTrue(alpha < 0.35f) // never a hard black shape
            hour += 0.5f
        }
    }

    @Test
    fun `at night a faint moon lights the garden from one side`() {
        val midnight = Daylight.at(0f, sunny)
        for (hour in listOf(19f, 21f, 23.9f, 2f, 4.9f)) assertEquals(midnight, Daylight.at(hour, sunny))
        assertTrue(midnight.alpha < Daylight.at(12f, sunny).alpha * 0.5f)
        assertTrue(midnight.alpha > 0f)
        // Hours outside 0..24 wrap round.
        assertEquals(Daylight.at(9f, sunny), Daylight.at(33f, sunny))
        assertEquals(Daylight.at(22f, sunny), Daylight.at(-2f, sunny))
    }

    // --- Wind ---

    @Test
    fun `a plant never leans further than the wind is strong`() {
        for (weather in WeatherType.entries) {
            val limit = Wind.strength(weather)
            var time = 0L
            while (time < 60_000L) {
                for (x in 0 until 9) for (y in 0 until 9) {
                    val sway = Wind.sway(time, x + 0.5f, y + 0.5f, weather)
                    assertTrue("$weather $time $x,$y: $sway", abs(sway) <= limit + 1e-4f)
                }
                time += 337L
            }
        }
    }

    @Test
    fun `plants swing both ways and come back`() {
        val samples = (0L until 20_000L step 50L).map { Wind.sway(it, 4.5f, 4.5f, sunny) }
        assertTrue(samples.max() > 0.3f)
        assertTrue(samples.min() < -0.3f)
        // No sudden jerks between two frames.
        assertTrue(samples.zipWithNext { a, b -> abs(a - b) }.max() < 0.2f)
        // Over time it leans neither way more than the other.
        assertEquals(0f, samples.average().toFloat(), 0.08f)
    }

    @Test
    fun `neighbours are out of step with each other`() {
        val time = 12_345L
        val sways = (0 until 9).flatMap { x -> (0 until 9).map { y -> Wind.sway(time, x + 0.5f, y + 0.5f, sunny) } }
        assertTrue(sways.toSet().size > 70)
        assertTrue(sways.any { it > 0f } && sways.any { it < 0f })
    }

    @Test
    fun `the same moment and place always give the same lean`() {
        assertEquals(Wind.sway(5_000L, 2.5f, 3.5f, sunny), Wind.sway(5_000L, 2.5f, 3.5f, sunny), 0f)
        assertFalse(Wind.sway(5_000L, 2.5f, 3.5f, sunny) == Wind.sway(5_400L, 2.5f, 3.5f, sunny))
    }

    @Test
    fun `it blows harder under clouds and hardest in the rain`() {
        assertTrue(Wind.strength(sunny) < Wind.strength(cloudy))
        assertTrue(Wind.strength(cloudy) < Wind.strength(rainy))
        // Same rhythm, bigger swing.
        val time = 7_777L
        val calm = Wind.sway(time, 1.5f, 1.5f, sunny)
        val storm = Wind.sway(time, 1.5f, 1.5f, rainy)
        assertEquals(calm * Wind.strength(rainy) / Wind.strength(sunny), storm, 1e-4f)
    }

    @Test
    fun `the movement stays small enough to read as a breeze`() {
        // A lean is this share of a plant's height at most; see PLANT_LEAN in the canvas.
        val worst = WeatherType.entries.maxOf { Wind.strength(it) } * 0.05f
        assertTrue(worst < 0.1f)
    }

    // --- The outline of a building's shadow ---

    @Test
    fun `the hull of a footprint and its shifted copy contains both`() {
        val foot = listOf(Offset(0f, 0f), Offset(10f, 5f), Offset(0f, 10f), Offset(-10f, 5f))
        val shifted = foot.map { it + Offset(14f, 4f) }

        val hull = convexHull(foot + shifted)

        assertEquals(6, hull.size)
        assertTrue(hull.all { it in foot || it in shifted })
        // The two inner corners are swallowed.
        assertFalse(Offset(10f, 5f) in hull && Offset(4f, 9f) in hull)
        // Left-most and right-most points are on it.
        assertTrue(Offset(-10f, 5f) in hull)
        assertTrue(Offset(24f, 9f) in hull)
    }

    @Test
    fun `with no shift the hull is the footprint itself`() {
        val foot = listOf(Offset(0f, 0f), Offset(10f, 5f), Offset(0f, 10f), Offset(-10f, 5f))

        val hull = convexHull(foot + foot)

        assertEquals(foot.toSet(), hull.toSet())
        assertEquals(4, hull.size)
    }

    @Test
    fun `a hull of very few points is those points`() {
        assertEquals(emptyList<Offset>(), convexHull(emptyList()))
        assertEquals(listOf(Offset(1f, 1f)), convexHull(listOf(Offset(1f, 1f), Offset(1f, 1f))))
        assertEquals(2, convexHull(listOf(Offset(0f, 0f), Offset(3f, 1f))).size)
    }
}
