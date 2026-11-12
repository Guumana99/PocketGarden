package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1111 (8): the windmill's sails turn on the plane of its front wall, not flat to the screen. */
class WindmillWallTest {

    private val pivot = Offset(100f, 200f)

    @Test
    fun `a point right of the hub goes across less and up, as along the wall`() {
        val m = wallMatrix(pivot, flipped = false)
        val p = m.map(Offset(110f, 200f))
        assertEquals(100f + 10f * WALL_SQUEEZE, p.x, 1e-3f)
        assertEquals(200f - 10f * WALL_SQUEEZE * WALL_SLOPE, p.y, 1e-3f)
        // Heights are kept, and the hub stays where it is.
        assertEquals(Offset(100f, 190f), m.map(Offset(100f, 190f)))
        assertEquals(pivot, m.map(pivot))
    }

    @Test
    fun `a turned mill's wall slants the other way`() {
        val p = wallMatrix(pivot, flipped = true).map(Offset(110f, 200f))
        assertEquals(100f - 10f * WALL_SQUEEZE, p.x, 1e-3f)
        assertEquals(200f - 10f * WALL_SQUEEZE * WALL_SLOPE, p.y, 1e-3f)
    }

    @Test
    fun `the squeeze is the garden's view, 30 degrees up and 45 round`() {
        assertEquals(Math.cos(Math.PI / 4) / Math.cos(Math.PI / 6), WALL_SQUEEZE.toDouble(), 1e-3)
        assertEquals(0.5f, WALL_SLOPE, 0f)
    }

    @Test
    fun `the garden draws the sails on the wall, the right way round for a turned mill`() {
        val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertTrue(canvas.contains("drawSpriteOnWall(atlasImage, sails, Offset(hub.first, hub.second), spriteScale, angle, flipped = obj.isFlipped)"))
        assertTrue(canvas.contains("wallMatrix(Offset(hub.first, hub.second), obj.isFlipped).map(flat)"))
    }
}
