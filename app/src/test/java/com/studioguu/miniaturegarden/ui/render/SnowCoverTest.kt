package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.data.asset.AssetManifest
import com.studioguu.miniaturegarden.domain.model.StructureData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1113 2-5: snow read off the pictures themselves. */
class SnowCoverTest {

    private val w = 80
    private val h = 80

    /** A roof of tile courses: a light face with a dark line every 10 rows, opaque everywhere. */
    private fun courses(): IntArray = IntArray(w * h) { i ->
        val y = i / w
        val grey = if (y % 10 < 2) 0x30 else 0xB0
        (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
    }

    @Test
    fun `a roof's snow lies only on its face, and gathers on the courses' steps`() {
        val face = listOf(0.1f to 0.1f, 0.9f to 0.1f, 0.9f to 0.9f, 0.1f to 0.9f)
        val spec = SnowCover.Spec(SnowCover.Kind.ROOF, amount = 0.92f, faces = listOf(face), floor = 0.45f)
        val a = SnowCover.alphaOf(courses(), w, h, spec, seed = 3)
        // Nothing off the face.
        assertEquals(0f, a[2 * w + 2], 0f)
        assertEquals(0f, a[(h - 2) * w + 40], 0f)
        // On it, never thinner than its floor, never more than its amount.
        val on = (10 until 70).flatMap { y -> (10 until 70).map { x -> a[y * w + x] } }
        assertTrue(on.all { it > 0f && it <= 0.92f + 1e-4f })
        // Just under a dark line it lies thicker than on that line, on average.
        fun row(y: Int) = (15 until 65).map { a[y * w + it] }.average()
        assertTrue(row(43) > row(41))
        // And not evenly: some of it is thinner than the rest.
        assertTrue(on.max() - on.min() > 0.1f)
    }

    @Test
    fun `a dusting lies on what faces up, thinning downwards`() {
        // A stone: transparent above row 20, opaque below.
        val stone = IntArray(w * h) { i -> if (i / w >= 20) (0xFF shl 24) or 0x909090 else 0 }
        val a = SnowCover.alphaOf(stone, w, h, SnowCover.Spec(SnowCover.Kind.TOP, amount = 0.6f), seed = 1)
        fun row(y: Int) = (5 until 75).map { a[y * w + it] }.average()
        assertEquals(0.0, row(10), 0.0)
        assertTrue(row(21) > row(45))
        assertTrue(a.max() <= 0.6f + 1e-4f)
    }

    @Test
    fun `the small things come in amounts, by where they stand, and the roofs are the four buildings`() {
        val rock = AssetManifest(assetId = "deco_rock", width = 200, height = 150)
        val amounts = (0 until PictureSnow.VARIANTS).map { PictureSnow.specOf("deco_rock", rock, 512, it)!!.amount }
        assertEquals(PictureSnow.VARIANTS, amounts.toSet().size)
        assertTrue(amounts.all { it < 0.92f })
        for (structure in listOf(StructureData.COTTAGE, StructureData.HUT, StructureData.WELL, StructureData.WINDMILL)) {
            val asset = AssetManifest(assetId = structure.objectId, width = 400, height = 400)
            val spec = PictureSnow.specOf(structure.objectId, asset, 512)
            assertNotNull(structure.name, spec)
            assertEquals(SnowCover.Kind.ROOF, spec!!.kind)
            assertEquals(structure == StructureData.COTTAGE, spec.solid)
        }
        assertNull(PictureSnow.specOf("deco_lantern", AssetManifest(assetId = "deco_lantern", width = 10, height = 10), 512))
        assertNotEquals(PictureSnow.specOf("deco_bench", AssetManifest(assetId = "deco_bench", width = 10, height = 10), 512), null)
    }
}
