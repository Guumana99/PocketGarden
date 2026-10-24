package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.LawnStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A lawn coming up stands in solid clumps over its share of the cell, not as a thin wash (#60). */
class LawnPatchesTest {

    @Test
    fun `each stage has as many clumps as it covers, and a full lawn every part`() {
        assertEquals(0, LawnPatches.clumps(2, 3, LawnStage.SOWN.cover).size)
        assertEquals(3, LawnPatches.clumps(2, 3, LawnStage.SPROUTING.cover).size)
        assertEquals(6, LawnPatches.clumps(2, 3, LawnStage.PATCHY.cover).size)
        assertEquals(9, LawnPatches.clumps(2, 3, LawnStage.FULL.cover).size)
        LawnStage.entries.zipWithNext { a, b ->
            assertTrue(LawnPatches.clumps(0, 0, a.cover).size <= LawnPatches.clumps(0, 0, b.cover).size)
        }
    }

    @Test
    fun `clumps lie on their cell, in a pattern of the cell's own that grows without moving`() {
        for (x in 0 until 6) for (y in 0 until 6) {
            for (clump in LawnPatches.clumps(x, y, 1f)) {
                assertTrue(clump.u in 0f..1f && clump.v in 0f..1f)
                assertTrue(clump.radius in LawnPatches.MIN_RADIUS..LawnPatches.MAX_RADIUS)
            }
            // The same cell always comes up the same way.
            assertEquals(LawnPatches.clumps(x, y, 0.7f), LawnPatches.clumps(x, y, 0.7f))
        }
        // Clumps join up: a little over half a part across, so neighbours overlap.
        assertTrue(LawnPatches.MIN_RADIUS > 0.5f / LawnPatches.GRID)
        // Two cells do not fill in the same order.
        assertNotEquals(LawnPatches.clumps(1, 1, 0.35f), LawnPatches.clumps(4, 2, 0.35f))
    }
}
