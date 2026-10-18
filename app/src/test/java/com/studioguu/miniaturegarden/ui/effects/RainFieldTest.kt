package com.studioguu.miniaturegarden.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rain falls unevenly: each drop at its own speed, place, moment and strength (#47). */
class RainFieldTest {

    private val width = 1080f
    private val height = 2400f

    @Test
    fun `the rain at a moment is the same however often it is drawn`() {
        assertEquals(RainField.drops(12_345, width, height), RainField.drops(12_345, width, height))
    }

    @Test
    fun `drops fall at many speeds, are of many strengths and lengths`() {
        val drops = (0 until 40).flatMap { RainField.drops(it * 97L, width, height) }
        assertTrue(drops.map { (it.alpha * 100).toInt() }.toSet().size > 20)
        assertTrue(drops.map { (it.length).toInt() }.toSet().size > 15)
        assertTrue(drops.map { (it.slant * 1000).toInt() }.toSet().size > 20)
        assertTrue(drops.all { it.alpha in 0.05f..0.85f })
    }

    @Test
    fun `not every drop is in the air at once, and how many are changes from moment to moment`() {
        val counts = (0 until 60).map { RainField.drops(it * 211L, width, height).size }
        assertTrue(counts.all { it in 20 until RainField.slots(RainStyle.RAIN) })
        assertTrue(counts.toSet().size > 5)
    }

    @Test
    fun `a drop that comes round again falls somewhere else`() {
        // Follow the drops near the top of the screen over a few seconds: their starting places differ.
        val starts = (0 until 200).flatMap { step ->
            RainField.drops(step * 50L, width, height).filter { it.y < height * 0.05f }.map { (it.x / 10).toInt() }
        }.toSet()
        assertTrue("only ${starts.size} places", starts.size > 40)
    }

    @Test
    fun `drops stay over the screen, give or take where they come in and go out`() {
        for (step in 0 until 50) {
            for (drop in RainField.drops(step * 173L, width, height)) {
                assertTrue("x ${drop.x}", drop.x in -width * 0.6f..width * 1.6f)
                assertTrue("y ${drop.y}", drop.y in -height * 0.2f..height * 1.15f)
            }
        }
    }

    @Test
    fun `the blessed rain is slower and softer, and some of it glints`() {
        assertTrue(RainField.fallRange(RainStyle.BLESSED).start > RainField.fallRange(RainStyle.RAIN).endInclusive)
        val blessed = (0 until 40).flatMap { RainField.drops(it * 131L, width, height, RainStyle.BLESSED) }
        val rain = (0 until 40).flatMap { RainField.drops(it * 131L, width, height, RainStyle.RAIN) }
        assertTrue(blessed.any { it.glint })
        assertTrue(rain.none { it.glint })
        assertTrue(blessed.map { it.length }.average() < rain.map { it.length }.average())
    }
}
