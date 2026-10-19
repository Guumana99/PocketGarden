package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The running sprinkler's animation is plain data: frames, a turning head and drops in the air. */
class SprinklerSprayTest {

    @Test
    fun `a stopped sprinkler stands still and dry`() {
        val still = SprinklerSpray.frameAt(1_234L, running = false)
        assertEquals(0, still.index)
        assertTrue(still.drops.isEmpty())
        assertEquals(still, SprinklerSpray.frameAt(99_999L, running = false))
    }

    @Test
    fun `a running one turns through every frame once a turn, throwing its jets`() {
        val step = SprinklerSpray.TURN_MILLIS / SprinklerSpray.FRAME_COUNT
        val frames = (0 until SprinklerSpray.FRAME_COUNT).map { SprinklerSpray.frameAt(it * step + 1, running = true).index }
        assertEquals((0 until SprinklerSpray.FRAME_COUNT).toList(), frames)
        // The same moment a turn later is the same picture.
        assertEquals(SprinklerSpray.frameAt(500L, true), SprinklerSpray.frameAt(500L + SprinklerSpray.TURN_MILLIS, true))
        val frame = SprinklerSpray.frameAt(700L, running = true)
        assertEquals(SprinklerSpray.JETS * SprinklerSpray.DROPS_PER_JET, frame.drops.size)
        assertTrue(frame.drops.all { it.reach in 0f..1f && it.alpha in 0f..1f })
    }

    @Test
    fun `artwork can take over frame by frame`() {
        val ids = (0 until SprinklerSpray.FRAME_COUNT).map(SprinklerSpray::spriteIdFor)
        assertEquals("deco_sprinkler_spray_0", ids.first())
        assertTrue(TileSetDefinition.expectedSpriteIds.containsAll(ids))
        assertTrue("deco_sprinkler" in TileSetDefinition.objectIds)
    }
}
