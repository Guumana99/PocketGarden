package com.studioguu.miniaturegarden.ui.render

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The river's flow from tile to tile, and the texture of tilled soil. */
class FlowAndSoilTest {

    // --- River: every tile one frame behind the one upstream ---

    // --- Tilled soil ---

    @Test
    fun `a bed looks the same every time and no two beds look alike`() {
        assertEquals(SoilTexture.outline(3, 4), SoilTexture.outline(3, 4))
        assertEquals(SoilTexture.grains(3, 4), SoilTexture.grains(3, 4))
        assertEquals(SoilTexture.drift(3, 4), SoilTexture.drift(3, 4))

        val beds = (0 until 9).flatMap { x -> (0 until 9).map { y -> x to y } }
        // Compared by shape, with each bed's own position taken out.
        fun shapeOf(x: Int, y: Int) = SoilTexture.grains(x, y).map { ((it.x - x) * 1000).toInt() to ((it.y - y) * 1000).toInt() }
        assertEquals(beds.size, beds.map { (x, y) -> shapeOf(x, y) }.toSet().size)
        assertEquals(beds.size, beds.map { (x, y) -> SoilTexture.outline(x, y).map { ((it.first - x) * 1000).toInt() } }.toSet().size)
        assertTrue(beds.map { (x, y) -> SoilTexture.dampness(x, y) }.toSet().size > 60)
    }

    @Test
    fun `the edge of a bed wanders, but its corners stay put`() {
        val inset = SoilTexture.INSET
        for ((x, y) in listOf(0 to 0, 4 to 4, 8 to 2)) {
            val ring = SoilTexture.outline(x, y)
            assertEquals(20, ring.size)
            // The four corners, every fifth point, are exactly where a straight-edged bed has them.
            assertEquals((x + inset) to (y + inset), ring[0])
            assertEquals((x + 1 - inset) to (y + inset), ring[5])
            assertEquals((x + 1 - inset) to (y + 1 - inset), ring[10])
            assertEquals((x + inset) to (y + 1 - inset), ring[15])
            // The points between them stray from the straight line, by no more than the wobble.
            val northSide = ring.subList(1, 5).map { it.second - (y + inset) }
            assertTrue(northSide.any { abs(it) > 0.003f })
            assertTrue(northSide.all { abs(it) <= SoilTexture.EDGE_WOBBLE + 1e-5f })
            // Nowhere does the bed reach the edge of its tile: grass shows between two beds.
            assertTrue(ring.all { (px, py) -> px > x + 0.015f && px < x + 0.985f && py > y + 0.015f && py < y + 0.985f })
        }
    }

    @Test
    fun `neighbouring beds leave the same strip of grass at their corners`() {
        val left = SoilTexture.outline(3, 4)
        val right = SoilTexture.outline(4, 4)
        // East corners of the one, west corners of the other: 2 x INSET apart, top and bottom.
        assertEquals(2 * SoilTexture.INSET, right[0].first - left[5].first, 1e-5f)
        assertEquals(2 * SoilTexture.INSET, right[15].first - left[10].first, 1e-5f)
    }

    @Test
    fun `grains lie on the bed, well inside its edge, in a mix of shades`() {
        for ((x, y) in listOf(0 to 0, 5 to 3, 8 to 8)) {
            val grains = SoilTexture.grains(x, y)
            assertEquals(SoilTexture.GRAIN_COUNT, grains.size)
            val margin = SoilTexture.INSET + SoilTexture.EDGE_WOBBLE
            assertTrue(grains.all { it.x > x + margin && it.x < x + 1 - margin && it.y > y + margin && it.y < y + 1 - margin })
            assertTrue(grains.all { it.size > 0f && it.size < 0.03f && it.shade in 0f..1f })
            // Dry crumbs, mid-brown ones and dark hollows are all there.
            assertTrue(grains.any { it.shade < 0.4f } && grains.any { it.shade in 0.4f..0.75f } && grains.any { it.shade > 0.75f })
            // Spread over the bed, not bunched in one corner.
            assertTrue(grains.maxOf { it.x } - grains.minOf { it.x } > 0.5f)
            assertTrue(grains.maxOf { it.y } - grains.minOf { it.y } > 0.5f)
        }
    }

    @Test
    fun `earth spills over the edge and thins out away from the bed`() {
        val all = (0 until 9).flatMap { x -> (0 until 9).flatMap { y -> SoilTexture.drift(x, y).map { Triple(x, y, it) } } }
        assertEquals(81 * SoilTexture.DRIFT_COUNT, all.size)

        /** How far outside the bed's straight edge a speck lies; negative is inside it. */
        fun outside(x: Int, y: Int, speck: SoilSpeck): Float {
            val inset = SoilTexture.INSET
            val dx = maxOf(x + inset - speck.x, speck.x - (x + 1 - inset))
            val dy = maxOf(y + inset - speck.y, speck.y - (y + 1 - inset))
            return maxOf(dx, dy)
        }
        val distances = all.map { (x, y, speck) -> outside(x, y, speck) }
        // All of it lies on the grass side of the edge, none further out than the reach ...
        assertTrue(distances.all { it >= -1e-4f && it <= SoilTexture.DRIFT_REACH + 1e-4f })
        // ... most of it close by: more than half within the nearest third of the reach.
        assertTrue(distances.count { it < SoilTexture.DRIFT_REACH / 3f } > distances.size / 2)
        assertTrue(distances.count { it > SoilTexture.DRIFT_REACH * 2f / 3f } < distances.size / 4)

        // The further out, the fainter and the smaller.
        val near = all.filter { (x, y, s) -> outside(x, y, s) < SoilTexture.DRIFT_REACH / 3f }.map { it.third }
        val far = all.filter { (x, y, s) -> outside(x, y, s) > SoilTexture.DRIFT_REACH * 2f / 3f }.map { it.third }
        assertTrue(near.map { it.shade }.average() > far.map { it.shade }.average() + 0.3)
        assertTrue(near.map { it.size }.average() > far.map { it.size }.average())
        assertTrue(all.all { it.third.shade in 0f..1f && it.third.size > 0f })
    }

    @Test
    fun `earth spills on all four sides of a bed`() {
        val drift = SoilTexture.drift(4, 4)
        val inset = SoilTexture.INSET
        assertTrue(drift.any { it.y <= 4 + inset })      // north
        assertTrue(drift.any { it.x >= 5 - inset })      // east
        assertTrue(drift.any { it.y >= 5 - inset })      // south
        assertTrue(drift.any { it.x <= 4 + inset })      // west
    }
}
