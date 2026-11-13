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
    fun `only the cottage has heaps along its edges, its ridge and its eave, mirrored when turned`() {
        val lines = EdgeSnow.linesOf(StructureData.COTTAGE.objectId, flipped = false)!!
        // #fb-1113 2: the ridge as it was and the near eave; none down the gable's sloping verges.
        assertEquals(listOf(-0.65f to 1.965f, 0.71f to 1.49f), lines[0].points)
        assertEquals(listOf(-1.099f to 1.012f, 0.149f to 0.656f), lines[1].points)
        assertEquals(2, lines.size)
        val turned = EdgeSnow.linesOf(StructureData.COTTAGE.objectId, flipped = true)!!
        assertEquals(lines.map { line -> line.points.map { (a, u) -> -a to u } }, turned.map { it.points })
        // #fb-1113 3-4: the hut, the well and the windmill have only the snow read off their pictures.
        for (structure in listOf(StructureData.HUT, StructureData.WELL, StructureData.WINDMILL)) {
            assertNull(structure.name, EdgeSnow.linesOf(structure.objectId, false))
        }
        assertFalse(EdgeSnow.hasLines("deco_bench"))
    }

    @Test
    fun `snow covers the whole of each roof face, mirrored when turned, under the heaped edges`() {
        for (structure in listOf(StructureData.COTTAGE, StructureData.HUT, StructureData.WELL)) {
            val faces = EdgeSnow.facesOf(structure.objectId, flipped = false)!!
            assertTrue(structure.name, faces.isNotEmpty() && faces.all { it.points.size >= 3 })
            assertTrue(structure.name, faces.flatMap { it.points }.all { (_, up) -> up > 0.3f && up < 2.2f })
            val turned = EdgeSnow.facesOf(structure.objectId, flipped = true)!!
            assertEquals(faces.map { f -> f.points.map { (a, u) -> -a to u } }, turned.map { it.points })
        }
        assertNull(EdgeSnow.facesOf(StructureData.WINDMILL.objectId, false))
        val canvas = File(listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        // Drawn from the pictures now (#fb-1113), as the picture is drawn, turned with it.
        assertTrue(canvas.contains("drawPictureSnow("))
        assertTrue(canvas.contains("flipped = obj.isFlipped, variant = PictureSnow.variantOf(obj), depth = ambience.edgeSnow"))
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
        // Read with LF line ends: a Windows checkout has CRLF.
        fun read(path: String) = File(main, path).readText().replace("\r\n", "\n")
        val canvas = read("java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt")
        assertTrue(canvas.contains("drawEdgeSnow(base, unit, lines, ambience.edgeSnow)"))
        assertTrue(canvas.contains("EdgeSnow.railTop(run)"))
        val model = read("java/com/studioguu/miniaturegarden/ui/GardenViewModel.kt")
        assertTrue(model.contains("fun debugSnowLying() {\n        if (!BuildConfig.DEBUG) return"))
        assertTrue(File(main, "res/values/strings.xml").readText().contains("debug_snow_lying"))
    }

    @Test
    fun `only the cottage's roof wears its snow as a solid cover`() {
        assertEquals(setOf(com.studioguu.miniaturegarden.domain.model.StructureData.COTTAGE.objectId), EdgeSnow.SOLID_ROOFS)
        assertTrue(EdgeSnow.SOLID_ALPHA >= 0.93f && EdgeSnow.SOLID_ALPHA < 1f)
        val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
        val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText()
        val snow = File(main, "java/com/studioguu/miniaturegarden/ui/render/PictureSnow.kt").readText()
        assertTrue(snow.contains("val solid = objectId in EdgeSnow.SOLID_ROOFS"))
    }
}
