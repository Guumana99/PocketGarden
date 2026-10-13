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

    private val slot = Wind.GUST_SLOT_MILLIS

    /** Every 40 ms over [slots] slots of time: enough to catch each gust as it passes. */
    private fun times(slots: Int, from: Long = 0L) = (from until from + slots * slot step 40L)

    @Test
    fun `a plant never leans further than the wind is strong`() {
        for (weather in WeatherType.entries) {
            val limit = Wind.strength(weather)
            for (time in times(6) step 3) {
                for (x in 0 until 9 step 2) for (y in 0 until 9 step 2) {
                    val sway = Wind.sway(time, x + 0.5f, y + 0.5f, weather)
                    assertTrue("$weather $time $x,$y: $sway", abs(sway) <= limit + 1e-4f)
                }
            }
        }
    }

    @Test
    fun `most of the time the air is almost still`() {
        val samples = times(30).map { abs(Wind.sway(it, 4.5f, 4.5f, sunny)) }
        val calm = samples.count { it <= Wind.CALM_SHARE + 1e-4f }

        // More than three quarters of the time a plant moves no more than the breeze moves it.
        assertTrue("calm ${calm * 100 / samples.size}%", calm > samples.size * 3 / 4)
        // And the breeze is slight: under a fifth of a full lean.
        assertTrue(Wind.CALM_SHARE < 0.2f)
        // But it is not dead still either.
        assertTrue(samples.sorted()[samples.size / 2] > 0.02f)
    }

    @Test
    fun `now and then a gust makes the plants bow much further`() {
        val perSlot = (0 until 30).map { k -> times(1, from = k * slot).maxOf { abs(Wind.sway(it, 4.5f, 4.5f, sunny)) } }

        // Every slot of time has its gust. At its height a plant leans at least twice as far as
        // the breeze ever moves it (even when the breeze happened to be leaning it the other
        // way), usually three times and more, and at best almost all the way.
        assertTrue(perSlot.all { it > Wind.CALM_SHARE * 2f })
        assertTrue(perSlot.count { it > Wind.CALM_SHARE * 3f } > perSlot.size * 2 / 3)
        assertTrue(perSlot.max() > 0.8f)
    }

    @Test
    fun `a gust passes and the calm comes back`() {
        // Find a moment at which the gust is at its height at the middle of the garden ...
        val peak = times(1).maxBy { Wind.gustiness(it, 4.5f, 4.5f) }
        assertTrue(Wind.gustiness(peak, 4.5f, 4.5f) > 0.9f)

        // ... a moment before there was nothing, and a few seconds later there is nothing again.
        assertEquals(0f, Wind.gustiness(peak - 400, 4.5f, 4.5f), 0f)
        assertTrue(Wind.gustiness(peak + 800, 4.5f, 4.5f) < 0.5f)
        assertTrue(Wind.gustiness(peak + 3_500, 4.5f, 4.5f) < 0.03f)
        // In between it only ever falls: one bow, not a second one.
        val after = (peak..peak + 3_500 step 50).map { Wind.gustiness(it, 4.5f, 4.5f) }
        assertEquals(after.sortedDescending(), after)
    }

    @Test
    fun `gusts come at irregular intervals`() {
        val peaks = (0 until 24).map { k -> times(1, from = k * slot).maxBy { Wind.gustiness(it, 4.5f, 4.5f) } }
        val gaps = peaks.zipWithNext { a, b -> b - a }

        assertTrue(gaps.all { it > 2_000L })            // never one on the heels of another
        assertTrue(gaps.max() - gaps.min() > 3_000L)    // and not like clockwork
        assertTrue(gaps.toSet().size > 12)
    }

    @Test
    fun `a gust sweeps across the garden - the plants bow one after another`() {
        // Find a gust and see when it is at its height along its path.
        val time = times(4).first { Wind.gustAt(it, sunny) != null }
        val front = Wind.gustAt(time, sunny)!!
        fun peakAt(along: Float): Long {
            val (x, y) = if (front.axis == GustAxis.ALONG_X) along to 4.5f else 4.5f to along
            return (time - 500..time + 3_000 step 10).maxBy { Wind.gustiness(it, x, y) }
        }
        val arrivals = listOf(0.5f, 2.5f, 4.5f, 6.5f, 8.5f).map(::peakAt)

        // Later and later along the way it is going, at an even pace, and across in a second or two.
        assertEquals(arrivals.sorted(), arrivals)
        val steps = arrivals.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it in 100L..600L })
        assertTrue(steps.max() - steps.min() <= 30L)
        assertTrue(arrivals.last() - arrivals.first() in 600L..1_800L)

        // Across the way it is going, everything is hit at the same moment: it is a front.
        val (xa, ya) = if (front.axis == GustAxis.ALONG_X) 4.5f to 0.5f else 0.5f to 4.5f
        val (xb, yb) = if (front.axis == GustAxis.ALONG_X) 4.5f to 8.5f else 8.5f to 4.5f
        val peakTime = peakAt(4.5f)
        assertEquals(Wind.gustiness(peakTime, xa, ya), Wind.gustiness(peakTime, xb, yb), 1e-5f)
    }

    @Test
    fun `the front that is drawn is where the plants are bowing`() {
        val time = times(4).first { t -> Wind.gustAt(t, sunny)?.let { it.position in 3f..6f } == true }
        val front = Wind.gustAt(time, sunny)!!
        fun gustiness(along: Float) =
            if (front.axis == GustAxis.ALONG_X) Wind.gustiness(time, along, 4.5f) else Wind.gustiness(time, 4.5f, along)

        // Just behind the front the plants are bowed; well ahead of it they have felt nothing yet.
        assertTrue(gustiness(front.position - 0.3f) > 0.6f)
        assertEquals(0f, gustiness(front.position + 2.5f), 0f)
        assertEquals(Wind.strength(sunny), front.strength, 0f)
    }

    @Test
    fun `gusts come from both corners, and push the plants the way they are going`() {
        val fronts = times(40).mapNotNull { Wind.gustAt(it, sunny) }
        assertTrue(fronts.any { it.axis == GustAxis.ALONG_X })
        assertTrue(fronts.any { it.axis == GustAxis.ALONG_Y })
        // The front runs from before the near edge of the garden to beyond the far one.
        assertTrue(fronts.minOf { it.position } < 0f)
        assertTrue(fronts.maxOf { it.position } > 9f)

        for (axis in GustAxis.entries) {
            val time = times(40).first { t -> Wind.gustAt(t, sunny)?.let { it.axis == axis && it.position in 5f..6f } == true }
            val lean = Wind.sway(time, 4.5f, 4.5f, sunny)
            // Along x is down-right on screen, along y is down-left.
            if (axis == GustAxis.ALONG_X) assertTrue(lean > Wind.CALM_SHARE) else assertTrue(lean < -Wind.CALM_SHARE)
        }
    }

    @Test
    fun `between gusts there is no front to draw`() {
        val withFront = times(30).count { Wind.gustAt(it, sunny) != null }
        val all = times(30).count()
        // A gust takes a second or two to cross, out of every ten.
        assertTrue(withFront > all / 12)
        assertTrue(withFront < all / 3)
    }

    @Test
    fun `in the calm neighbours are out of step with each other`() {
        // A moment with no gust anywhere near.
        val time = times(3).first { t -> (0 until 9).all { Wind.gustiness(t, it + 0.5f, it + 0.5f) == 0f } }
        val sways = (0 until 9).flatMap { x -> (0 until 9).map { y -> Wind.sway(time, x + 0.5f, y + 0.5f, sunny) } }

        assertTrue(sways.toSet().size > 70)
        assertTrue(sways.any { it > 0f } && sways.any { it < 0f })
        assertTrue(sways.all { abs(it) <= Wind.CALM_SHARE + 1e-4f })
    }

    @Test
    fun `nothing jerks from one frame to the next`() {
        val samples = times(6).map { Wind.sway(it, 4.5f, 4.5f, sunny) }
        // 40 ms apart: even as a gust hits, a plant does not jump.
        assertTrue(samples.zipWithNext { a, b -> abs(a - b) }.max() < 0.35f)
    }

    @Test
    fun `the same moment and place always give the same lean`() {
        assertEquals(Wind.sway(5_000L, 2.5f, 3.5f, sunny), Wind.sway(5_000L, 2.5f, 3.5f, sunny), 0f)
        assertEquals(Wind.gustAt(123_456L, sunny), Wind.gustAt(123_456L, sunny))
        assertFalse(Wind.sway(5_000L, 2.5f, 3.5f, sunny) == Wind.sway(5_400L, 2.5f, 3.5f, sunny))
    }

    @Test
    fun `it blows harder under clouds and hardest in the rain`() {
        assertTrue(Wind.strength(sunny) < Wind.strength(cloudy))
        assertTrue(Wind.strength(cloudy) < Wind.strength(rainy))
        // Same rhythm and the same gusts, bigger swing.
        for (time in listOf(7_777L, 23_456L, 61_000L)) {
            val fine = Wind.sway(time, 1.5f, 1.5f, sunny)
            val storm = Wind.sway(time, 1.5f, 1.5f, rainy)
            assertEquals(fine * Wind.strength(rainy) / Wind.strength(sunny), storm, 1e-4f)
        }
    }

    @Test
    fun `the movement stays small enough to read as wind in a garden`() {
        // A lean is this share of a plant's height at most; see PLANT_LEAN in the canvas.
        val plantLean = 0.09f
        assertTrue(WeatherType.entries.maxOf { Wind.strength(it) } * plantLean < 0.15f)
        // And in the calm of a fine day it is hardly anything.
        assertTrue(Wind.CALM_SHARE * plantLean < 0.02f)
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
