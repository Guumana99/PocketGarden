package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import kotlin.math.abs
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
    fun `a drop keeps the direction it was thrown in, while the head turns on`() {
        // Follow the newest drop of the first jet from just after its launch to near its landing.
        val launchedAt = 30 * SprinklerSpray.LAUNCH_MILLIS
        val early = SprinklerSpray.frameAt(launchedAt + 10, running = true)
        val late = SprinklerSpray.frameAt(launchedAt + SprinklerSpray.FLIGHT_MILLIS - 10, running = true)
        val thrown = early.drops.minBy { it.flight }
        val same = late.drops.filter { abs(it.angle - thrown.angle) < 1e-4f }
        assertEquals(1, same.size)
        assertTrue(same.single().reach > thrown.reach)
        // The head has turned on meanwhile.
        assertTrue(abs(late.headAngle - early.headAngle) > 1f)
    }

    @Test
    fun `drops fly an arc, from the nozzle to the ground, and splash where they land`() {
        assertEquals(0f, SprinklerSpray.arc(0f), 1e-6f)
        assertEquals(1f, SprinklerSpray.arc(0.5f), 1e-6f)
        assertEquals(0f, SprinklerSpray.arc(1f), 1e-6f)
        val frame = SprinklerSpray.frameAt(5_000L, running = true)
        assertTrue(frame.drops.all { abs(it.lift - SprinklerSpray.arc(it.flight)) < 1e-5f })
        assertTrue(frame.splashes.isNotEmpty())
        assertTrue(frame.splashes.all { it.progress in 0f..1f && it.reach in (1f - SprinklerSpray.REACH_SPREAD)..1f })
        // A drop is barely there as it leaves the nozzle, and thins out as it comes down.
        val newest = frame.drops.minBy { it.flight }
        val middle = frame.drops.minBy { abs(it.flight - 0.5f) }
        assertTrue(newest.alpha < middle.alpha)
    }

    @Test
    fun `drops move smoothly - a moment later each one is a little further out`() {
        val a = SprinklerSpray.frameAt(1_000L, running = true)
        val b = SprinklerSpray.frameAt(1_016L, running = true)
        for (drop in a.drops.filter { it.flight < 0.95f }) {
            val next = b.drops.single { abs(it.angle - drop.angle) < 1e-4f }
            assertTrue(next.reach - drop.reach in 0f..0.05f)
        }
    }

    @Test
    fun `artwork can take over frame by frame`() {
        val ids = (0 until SprinklerSpray.FRAME_COUNT).map(SprinklerSpray::spriteIdFor)
        assertEquals("deco_sprinkler_spray_0", ids.first())
        assertTrue(TileSetDefinition.expectedSpriteIds.containsAll(ids))
        assertTrue("deco_sprinkler" in TileSetDefinition.objectIds)
    }
}
