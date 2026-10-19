package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsometricEngineTest {

    private val transform = IsometricTransform(
        tileWidth = 128f,
        tileHeight = 64f,
        heightStep = 16f,
        offsetX = 300f,
        offsetY = 40f,
    )
    private val metrics = IsometricMetrics(tileWidth = 128f, tileHeight = 64f, heightStep = 16f, gridSize = 9)
    private val viewport = Size(1000f, 2000f)

    private fun assertOffset(expected: Offset, actual: Offset, delta: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
    }

    // --- IsometricTransform ---

    @Test
    fun `projection follows the spec formulas`() {
        assertOffset(Offset(300f, 40f), transform.worldToScreen(0f, 0f))
        assertOffset(Offset(300f + 64f, 40f + 32f), transform.worldToScreen(1f, 0f))
        assertOffset(Offset(300f - 64f, 40f + 32f), transform.worldToScreen(0f, 1f))
        // (3 - 5) * 64 + 300, (3 + 5) * 32 - 2 * 16 + 40
        assertOffset(Offset(172f, 264f), transform.worldToScreen(3f, 5f, z = 2f))
    }

    @Test
    fun `screenToWorld inverts worldToScreen on the ground plane`() {
        for (x in listOf(0f, 0.25f, 4.5f, 8.99f)) for (y in listOf(0f, 3.7f, 9f)) {
            assertOffset(Offset(x, y), transform.screenToWorld(transform.worldToScreen(x, y)))
        }
    }

    // --- TilePicker ---

    @Test
    fun `every tile center picks its own tile`() {
        for (x in 0 until 9) for (y in 0 until 9) {
            assertEquals(TileCoord(x, y), TilePicker.pick(transform.tileCenter(x, y), transform))
        }
    }

    @Test
    fun `points just inside each diamond corner still pick that tile`() {
        val inset = 0.01f
        val corners = listOf(inset to inset, 1 - inset to inset, 1 - inset to 1 - inset, inset to 1 - inset)
        for ((dx, dy) in corners) {
            assertEquals(TileCoord(4, 7), TilePicker.pick(transform.worldToScreen(4 + dx, 7 + dy), transform))
        }
    }

    @Test
    fun `points outside the grid pick nothing`() {
        assertNull(TilePicker.pick(transform.worldToScreen(-0.1f, 4f), transform))
        assertNull(TilePicker.pick(transform.worldToScreen(4f, -0.1f), transform))
        assertNull(TilePicker.pick(transform.worldToScreen(9.1f, 4f), transform))
        assertNull(TilePicker.pick(transform.worldToScreen(4f, 9.1f), transform))
        // Inside the grid's bounding rectangle but outside the diamond.
        assertNull(TilePicker.pick(Offset(transform.offsetX - 400f, transform.offsetY + 10f), transform))
    }

    // --- LayerPipeline ---

    @Test
    fun `commands are ordered by layer, then depth, then submission`() {
        val pipeline = LayerPipeline()
        val order = mutableListOf<String>()
        fun submit(name: String, layer: RenderLayer, depth: Float = 0f) =
            pipeline.submit(layer, depth) { order += name }

        submit("hud", RenderLayer.HUD_OVERLAY)
        submit("plant-front", RenderLayer.GRID_OBJECTS, LayerPipeline.depthOf(8, 8))
        submit("effect", RenderLayer.TOP_EFFECTS)
        submit("plant-back", RenderLayer.GRID_OBJECTS, LayerPipeline.depthOf(0, 0))
        submit("shadow", RenderLayer.SHADOWS)
        submit("plant-mid-a", RenderLayer.GRID_OBJECTS, LayerPipeline.depthOf(3, 4))
        submit("plant-mid-b", RenderLayer.GRID_OBJECTS, LayerPipeline.depthOf(4, 3))
        submit("pebble", RenderLayer.BOTTOM_OBJECTS)
        submit("grid", RenderLayer.GRID_OVERLAY)
        submit("terrain", RenderLayer.BASE_TERRAIN)

        val sorted = pipeline.sortedCommands()
        assertEquals(RenderLayer.entries.map { it.zIndex }, (0..6).toList())
        assertEquals(
            listOf(0, 1, 2, 3, 4, 4, 4, 4, 5, 6),
            sorted.map { it.layer.zIndex },
        )
        assertEquals(listOf(0f, 7f, 7f, 16f), sorted.filter { it.layer == RenderLayer.GRID_OBJECTS }.map { it.depth })
    }

    // --- GardenCamera ---

    @Test
    fun `camera at rest centers the grid in the viewport`() {
        val t = GardenCamera().toTransform(metrics, viewport)
        assertOffset(Offset(500f, 1000f), t.worldToScreen(4.5f, 4.5f))
    }

    @Test
    fun `a kept view comes back as it was, within what the screen allows`() {
        val fit = GardenCamera.fitting(viewport, metrics)
        val kept = GardenCamera.restored(fit.scale * 1.5f, Offset(20f, -10f), viewport, metrics)
        assertEquals(fit.scale * 1.5f, kept.scale, 1e-4f)
        assertOffset(Offset(20f, -10f), kept.pan)
        assertEquals(fit.minScale, kept.minScale, 0f)
        // Too far in or out is brought back into range, and a pan off the garden is pulled back.
        assertEquals(GardenCamera.MAX_SCALE, GardenCamera.restored(99f, Offset.Zero, viewport, metrics).scale, 0f)
        assertEquals(fit.minScale, GardenCamera.restored(0.0001f, Offset.Zero, viewport, metrics).scale, 0f)
        assertEquals(fit.scale, GardenCamera.restored(Float.NaN, Offset.Zero, viewport, metrics).scale, 0f)
        val far = GardenCamera.restored(1f, Offset(1e6f, -1e6f), viewport, metrics)
        assertEquals(far, far.clamped(metrics))
    }

    @Test
    fun `zoom is limited to the allowed range`() {
        val center = Offset(500f, 1000f)
        val zoomedIn = GardenCamera().transformedBy(center, Offset.Zero, 100f, viewport, metrics)
        val zoomedOut = GardenCamera().transformedBy(center, Offset.Zero, 0.001f, viewport, metrics)
        assertEquals(GardenCamera.MAX_SCALE, zoomedIn.scale, 0f)
        assertEquals(GardenCamera.MIN_SCALE, zoomedOut.scale, 0f)
    }

    @Test
    fun `the world point under the pinch centroid stays under it`() {
        val start = GardenCamera(scale = 1f, pan = Offset(40f, -25f))
        val centroid = Offset(620f, 1130f)
        val worldBefore = start.toTransform(metrics, viewport).screenToWorld(centroid)

        val zoomed = start.transformedBy(centroid, Offset.Zero, 1.6f, viewport, metrics)

        assertEquals(1.6f, zoomed.scale, 1e-6f)
        assertOffset(worldBefore, zoomed.toTransform(metrics, viewport).screenToWorld(centroid))
    }

    @Test
    fun `pan is clamped so the viewport center stays over the grid's bounds`() {
        val farAway = Offset(1e6f, -1e6f)
        val camera = GardenCamera(scale = 2f).transformedBy(Offset.Zero, farAway, 1f, viewport, metrics)
        // Half of the 9-tile bounding box at 2x: 9 * 128 * 2 / 2 and 9 * 64 * 2 / 2.
        assertOffset(Offset(1152f, -576f), camera.pan)
    }


    @Test
    fun `initial camera shows the whole grid, however small the screen`() {
        // Plenty of room: no need to shrink, and the usual zoom limit applies.
        val roomy = GardenCamera.fitting(Size(4000f, 4000f), metrics)
        assertEquals(1f, roomy.scale, 0f)
        assertEquals(GardenCamera.MIN_SCALE, roomy.minScale, 0f)

        for (viewport in listOf(Size(800f, 2000f), Size(300f, 2000f), Size(2000f, 500f))) {
            val camera = GardenCamera.fitting(viewport, metrics)
            val t = camera.toTransform(metrics, viewport)
            // All four corners of the 9 x 9 grid are inside the screen.
            for ((x, y) in listOf(0f to 0f, 9f to 0f, 9f to 9f, 0f to 9f)) {
                val p = t.worldToScreen(x, y)
                assertTrue("$viewport corner ($x, $y) at $p", p.x in 0f..viewport.width && p.y in 0f..viewport.height)
            }
        }
        // A phone-sized screen needs to go below the usual limit to manage that.
        assertEquals(300f / 1152f * GardenCamera.FIT_MARGIN, GardenCamera.fitting(Size(300f, 2000f), metrics).scale, 1e-6f)
    }

    @Test
    fun `pinching out stops at the zoom that shows the whole grid`() {
        val small = Size(300f, 2000f)
        val fitted = GardenCamera.fitting(small, metrics)
        val center = Offset(150f, 1000f)

        val zoomedIn = fitted.transformedBy(center, Offset.Zero, 3f, small, metrics)
        val backOut = zoomedIn.transformedBy(center, Offset.Zero, 0.001f, small, metrics)

        assertEquals(fitted.scale, backOut.scale, 1e-6f)
        assertTrue(backOut.scale < GardenCamera.MIN_SCALE)
    }

    // --- The garden at its largest, 13 x 13 ---

    private val largest = IsometricMetrics(tileWidth = 128f, tileHeight = 64f, heightStep = 16f, gridSize = 13)

    /** The garden's canvas on a phone, in dp: the screen less the bars above and below. */
    private val phone = Size(411f, 640f)

    @Test
    fun `on a 13x13 garden every tile is picked where it is drawn, at the starting zoom of a phone`() {
        val camera = GardenCamera.fitting(phone, largest)
        val t = camera.toTransform(largest, phone)
        val inset = 0.02f
        for (x in 0 until 13) for (y in 0 until 13) {
            assertEquals(TileCoord(x, y), TilePicker.pick(t.tileCenter(x, y), t, gridSize = 13))
            for ((dx, dy) in listOf(inset to inset, 1 - inset to inset, 1 - inset to 1 - inset, inset to 1 - inset)) {
                assertEquals("($x, $y) + ($dx, $dy)", TileCoord(x, y), TilePicker.pick(t.worldToScreen(x + dx, y + dy), t, gridSize = 13))
            }
        }
        // Everything is on screen, and just past the edges nothing is picked.
        for ((x, y) in listOf(0f to 0f, 13f to 0f, 13f to 13f, 0f to 13f)) {
            val p = t.worldToScreen(x, y)
            assertTrue("corner ($x, $y) at $p", p.x in 0f..phone.width && p.y in 0f..phone.height)
        }
        assertNull(TilePicker.pick(t.worldToScreen(13.05f, 6f), t, gridSize = 13))
        assertNull(TilePicker.pick(t.worldToScreen(6f, 13.05f), t, gridSize = 13))
        // The cells that were outside the starting 9 x 9 are not cut off at 9.
        assertEquals(TileCoord(12, 12), TilePicker.pick(t.tileCenter(12, 12), t, gridSize = 13))
        assertNull(TilePicker.pick(t.tileCenter(12, 12), t, gridSize = 9))
    }

    @Test
    fun `zoomed in on a corner of the 13x13 garden, its tiles are big enough for a finger and still picked right`() {
        val fitted = GardenCamera.fitting(phone, largest)
        // Zoomed all the way in, then dragged as far as it goes towards the far right corner.
        val zoomed = fitted.transformedBy(phone.center, Offset.Zero, 100f, phone, largest)
            .transformedBy(phone.center, Offset(-100_000f, 100_000f), 1f, phone, largest)
        val t = zoomed.toTransform(largest, phone)
        assertTrue("a tile is ${t.tileWidth} dp across", t.tileWidth >= 48f)
        for (x in 9 until 13) for (y in 0 until 4) {
            val p = t.tileCenter(x, y)
            if (p.x !in 0f..phone.width || p.y !in 0f..phone.height) continue
            assertEquals(TileCoord(x, y), TilePicker.pick(p, t, gridSize = 13))
        }
        assertEquals(TileCoord(12, 0), TilePicker.pick(t.tileCenter(12, 0), t, gridSize = 13))
    }

    @Test
    fun `the 13x13 garden first shows with finger-sized tiles on a phone, centred`() {
        val start = GardenCamera.starting(phone, largest)
        val t = start.toTransform(largest, phone)
        assertEquals(GardenCamera.START_SCALE, start.scale, 0f)
        assertEquals(128f * 0.55f, t.tileWidth, 1e-3f)
        assertEquals(64f * 0.55f, t.tileHeight, 1e-3f)
        assertOffset(Offset.Zero, start.pan)
        // The middle of the garden is in the middle of the screen.
        assertOffset(phone.center, t.worldToScreen(6.5f, 6.5f))
        // Fitting the whole garden would have made a tile only about 27 dp across.
        assertTrue(GardenCamera.fitting(phone, largest).toTransform(largest, phone).tileWidth in 27f..28f)
    }

    @Test
    fun `from the starting view a pinch still goes out to the whole garden and in to the closest`() {
        val start = GardenCamera.starting(phone, largest)
        val fit = GardenCamera.fitting(phone, largest)
        assertEquals(fit.minScale, start.minScale, 0f)
        val out = start.transformedBy(phone.center, Offset.Zero, 0.001f, phone, largest)
        assertEquals(fit.scale, out.scale, 1e-6f)
        val all = out.toTransform(largest, phone)
        for ((x, y) in listOf(0f to 0f, 13f to 0f, 13f to 13f, 0f to 13f)) {
            val p = all.worldToScreen(x, y)
            assertTrue("corner ($x, $y) at $p", p.x in 0f..phone.width && p.y in 0f..phone.height)
        }
        assertEquals(GardenCamera.MAX_SCALE, start.transformedBy(phone.center, Offset.Zero, 100f, phone, largest).scale, 0f)
    }

    @Test
    fun `in the starting view every tile on screen is picked where it is drawn, and a drag reaches the corners`() {
        val start = GardenCamera.starting(phone, largest)
        val t = start.toTransform(largest, phone)
        var onScreen = 0
        for (x in 0 until 13) for (y in 0 until 13) {
            val p = t.tileCenter(x, y)
            if (p.x !in 0f..phone.width || p.y !in 0f..phone.height) continue
            onScreen++
            assertEquals(TileCoord(x, y), TilePicker.pick(p, t, gridSize = 13))
        }
        assertTrue("only $onScreen tiles on screen", onScreen >= 60)
        // Dragged as far as it goes, each corner of the garden comes on screen and is picked.
        for (drag in listOf(Offset(0f, 1e5f), Offset(-1e5f, 0f), Offset(0f, -1e5f), Offset(1e5f, 0f))) {
            val moved = start.transformedBy(phone.center, drag, 1f, phone, largest).toTransform(largest, phone)
            val corner = when {
                drag.y > 0f -> TileCoord(0, 0)
                drag.x < 0f -> TileCoord(12, 0)
                drag.y < 0f -> TileCoord(12, 12)
                else -> TileCoord(0, 12)
            }
            val p = moved.tileCenter(corner.x, corner.y)
            assertTrue("$corner at $p", p.x in 0f..phone.width && p.y in 0f..phone.height)
            assertEquals(corner, TilePicker.pick(p, moved, gridSize = 13))
        }
    }

    @Test
    fun `every screen starts at the same fixed zoom, whatever the garden's size`() {
        val tablet = Size(1280f, 800f)
        assertEquals(0.55f, GardenCamera.START_SCALE, 0f)
        for (screen in listOf(phone, tablet)) {
            assertEquals(GardenCamera.START_SCALE, GardenCamera.starting(screen, metrics).scale, 0f)
            assertEquals(GardenCamera.START_SCALE, GardenCamera.starting(screen, largest).scale, 0f)
        }
    }

    private val Size.center: Offset get() = Offset(width / 2f, height / 2f)
}
