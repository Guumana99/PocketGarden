package com.studioguu.miniaturegarden.ui.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A lawn coming up stands over its whole cell from the start, denser and longer as it grows (#61). */
class LawnPatchesTest {

    @Test
    fun `a lawn just sown already has blades, and more of them as it grows`() {
        assertEquals(LawnPatches.MIN_BLADES, LawnPatches.blades(2, 3, 0f).size)
        assertEquals(LawnPatches.MAX_BLADES, LawnPatches.blades(2, 3, 1f).size)
        assertTrue(LawnPatches.MIN_BLADES >= 50)
        val growths = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        growths.zipWithNext { a, b ->
            assertTrue(LawnPatches.blades(0, 0, a).size < LawnPatches.blades(0, 0, b).size)
            assertTrue(LawnPatches.blades(0, 0, a).maxOf { it.length } < LawnPatches.blades(0, 0, b).maxOf { it.length })
            assertTrue(LawnPatches.blades(0, 0, a).maxOf { it.width } < LawnPatches.blades(0, 0, b).maxOf { it.width })
        }
    }

    @Test
    fun `the blades cover the whole cell, not a part of it`() {
        for ((x, y) in listOf(0 to 0, 3 to 5, 8 to 1)) {
            val blades = LawnPatches.blades(x, y, 0f)
            blades.forEach {
                assertTrue(it.u in 0f..1f && it.v in 0f..1f)
                assertTrue(it.lean in -1f..1f)
            }
            // Even a lawn just sown has blades in every quarter of its cell.
            for (qu in 0..1) for (qv in 0..1) {
                assertTrue("$x,$y quarter $qu,$qv", blades.any { (it.u * 2).toInt() == qu && (it.v * 2).toInt() == qv })
            }
        }
    }

    @Test
    fun `a growing lawn keeps its blades where they were and only adds to them`() {
        val young = LawnPatches.blades(4, 2, 0.2f).map { it.u to it.v }
        val older = LawnPatches.blades(4, 2, 0.8f).map { it.u to it.v }
        assertEquals(young, older.take(young.size))
        assertEquals(LawnPatches.blades(4, 2, 0.7f), LawnPatches.blades(4, 2, 0.7f))
        assertNotEquals(LawnPatches.blades(1, 1, 0.35f), LawnPatches.blades(4, 2, 0.35f))
    }
}
