package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WeatherType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A little snow along the roofs and the fences (#fb-1107). */
class EdgeSnowTest {

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L

    /** Snow from [from] until [until], fine before and after. */
    private fun snowing(from: Long, until: Long = Long.MAX_VALUE) =
        WeatherForecast { t -> if (t in from until until) WeatherType.SNOWY else WeatherType.SUNNY }

    @Test
    fun `the snow builds up while it snows and goes slowly once it stops`() {
        val forecast = snowing(t0, t0 + 120 * minute)
        assertEquals(0f, EdgeSnow.depthAt(forecast, t0), 0f)
        // A little at first, more as it goes on, all of it after 40 minutes.
        val early = EdgeSnow.depthAt(forecast, t0 + 10 * minute)
        val later = EdgeSnow.depthAt(forecast, t0 + 30 * minute)
        assertEquals(0.25f, early, 0.01f)
        assertTrue(later > early)
        assertEquals(1f, EdgeSnow.depthAt(forecast, t0 + 41 * minute), 0f)
        assertEquals(1f, EdgeSnow.depthAt(forecast, t0 + 119 * minute), 0f)
        // Once it stops, it goes over an hour and a half.
        val melting = EdgeSnow.depthAt(forecast, t0 + 165 * minute)
        assertEquals(0.5f, melting, 0.02f)
        assertEquals(0f, EdgeSnow.depthAt(forecast, t0 + 211 * minute), 0f)
        // Snow all along (the debug menu's): as thick as it gets at once.
        assertEquals(1f, EdgeSnow.depthAt(WeatherType.SNOWY, t0), 0f)
        assertEquals(0f, EdgeSnow.depthAt(WeatherType.RAINY, t0), 0f)
    }

    @Test
    fun `the cottage, the hut and the well have lines set by hand, mirrored when turned`() {
        for (structure in listOf(StructureData.COTTAGE, StructureData.HUT, StructureData.WELL)) {
            val lines = EdgeSnow.linesOf(structure.objectId, flipped = false)!!
            assertTrue(structure.name, lines.isNotEmpty() && lines.all { it.points.size >= 2 })
            // Up off the ground, on the roof, and never as high as the next storey.
            assertTrue(structure.name, lines.flatMap { it.points }.all { (_, up) -> up > 0.3f && up < 2.2f })
            val turned = EdgeSnow.linesOf(structure.objectId, flipped = true)!!
            assertEquals(lines.map { line -> line.points.map { (a, u) -> -a to u } }, turned.map { it.points })
        }
        assertNull(EdgeSnow.linesOf(StructureData.WINDMILL.objectId, false))
        assertFalse(EdgeSnow.hasLines("deco_bench"))
    }

    @Test
    fun `every fence has the top of its rail, under its posts, and the kerb has none`() {
        for (kind in RunKind.entries.filter { it.isFence }) {
            val top = EdgeSnow.railTop(kind)!!
            assertTrue("$kind", top > 0.15f && top < EdgeSnow.POST_TOP)
            assertNotNull(EdgeSnow.aloneTop(kind))
        }
        assertNull(EdgeSnow.railTop(RunKind.BRICK_EDGING))
        assertNull(EdgeSnow.aloneTop(RunKind.BRICK_EDGING))
    }

    @Test
    fun `the debug menu has the button, and the screen draws the snow on roofs and rails`() {
        val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        assertTrue(canvas.contains("drawEdgeSnow(base, unit, lines, ambience.edgeSnow)"))
        assertTrue(canvas.contains("EdgeSnow.railTop(run)"))
        val model = File(main, "java/com/studioguu/miniaturegarden/ui/GardenViewModel.kt").readText()
        assertTrue(model.contains("fun debugSnowLying() {\n        if (!BuildConfig.DEBUG) return"))
        assertTrue(File(main, "res/values/strings.xml").readText().contains("debug_snow_lying"))
    }
}
