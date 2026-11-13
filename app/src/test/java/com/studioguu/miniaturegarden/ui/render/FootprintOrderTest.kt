package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.GridObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1113 1: what stands just in front of a building, beside it, is drawn after it. */
class FootprintOrderTest {

    private fun thing(objectId: String, x: Int, y: Int, flipped: Boolean = false) =
        GridObject(instanceId = "$objectId-$x-$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L, isFlipped = flipped)

    // The cottage on (2..4, 2..4): its front corner (4, 4) sorts it at 8.
    private val cottage = thing("struct_cottage", 2, 2)
    private val boxes = FootprintOrder.boxesOf(listOf(cottage, thing("struct_well", 9, 9)))

    private fun drawnAt(x: Int, y: Int) = FootprintOrder.depthOf(x, y, x, y, LayerPipeline.depthOf(x, y), boxes)

    @Test
    fun `only buildings of several cells count`() {
        assertEquals(listOf(FootprintOrder.Box(2, 2, 4, 4, 8f)), boxes)
    }

    @Test
    fun `a well at the cottage's right front, level with its middle, is drawn after it`() {
        // (5, 3) sorted at 8 and (5, 2) at 7: both behind the cottage until now.
        for ((x, y) in listOf(5 to 2, 5 to 3, 5 to 4, 6 to 3)) assertTrue("$x,$y", drawnAt(x, y) > 8f)
        // Down its left front side too.
        for ((x, y) in listOf(2 to 5, 3 to 5, 4 to 5, 3 to 6)) assertTrue("$x,$y", drawnAt(x, y) > 8f)
    }

    @Test
    fun `those in front keep their order among themselves, before the next row`() {
        assertTrue(drawnAt(5, 2) < drawnAt(5, 3))
        assertTrue(drawnAt(5, 3) < drawnAt(6, 3))
        // A thing already in front of the corner is not moved, and stays after the others.
        assertEquals(LayerPipeline.depthOf(5, 5), drawnAt(5, 5), 0f)
        assertTrue(drawnAt(6, 3) < drawnAt(5, 5))
    }

    @Test
    fun `behind it, off to the side, or far off, nothing changes`() {
        for ((x, y) in listOf(1 to 3, 3 to 1, 5 to 1, 1 to 5, 7 to 3, 3 to 7)) {
            assertEquals("$x,$y", LayerPipeline.depthOf(x, y), drawnAt(x, y), 0f)
        }
        assertFalse(FootprintOrder.isInFrontBeside(5, 1, 5, 1, boxes.single()))
    }

    @Test
    fun `a building beside another is ordered the same way`() {
        // A windmill (2 x 2) on (5..6, 1..2), past the cottage's right side and level with its back row.
        val depth = FootprintOrder.depthOf(5, 1, 6, 2, LayerPipeline.depthOf(6, 2), boxes)
        assertTrue(depth > 8f)
        // The cottage itself is not put after itself.
        assertEquals(8f, FootprintOrder.depthOf(2, 2, 4, 4, 8f, boxes), 0f)
    }
}
