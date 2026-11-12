package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CreaturePerches
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Perch
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.ui.render.EdgeSnow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1111 (7): the hut turns like the other things, and its roof snow and perches turn with it. */
class HutFlipTest {

    private fun hut(flipped: Boolean) = GridObject(
        instanceId = "h", objectId = StructureData.HUT.objectId, gridX = 3, gridY = 4, isFlipped = flipped, lastUpdatedTimestamp = 0L,
    )

    @Test
    fun `the hut can face either way, on the same cells`() {
        assertTrue(GridObject.canFaceEitherWay(StructureData.HUT.objectId))
        assertTrue(hut(false).canFlip)
        assertEquals(StructureData.HUT.footprint.width, StructureData.HUT.footprint.height)
    }

    @Test
    fun `a turned hut's ridge perches are mirrored across the screen`() {
        val plain = CreaturePerches.pointsOf(hut(false))
        val turned = CreaturePerches.pointsOf(hut(true))
        assertEquals(plain.size, turned.size)
        // Its footprint is 2 by 2, so its middle is (4, 5); across the screen is x - y.
        val across = { p: Perch -> (p.x - 4f) - (p.y - 5f) }
        val want = plain.map { -across(it) }.sorted()
        val got = turned.map(across).sorted()
        want.zip(got).forEach { (w, g) -> assertEquals(w, g, 1e-4f) }
        assertEquals(plain.map { it.x + it.y }.sorted(), turned.map { it.x + it.y }.sorted())
        assertEquals(plain.map { it.height }, turned.map { it.height })
    }

    @Test
    fun `the snow on a turned hut's roof and edges is mirrored`() {
        val faces = EdgeSnow.facesOf(StructureData.HUT.objectId, flipped = false)!!
        assertEquals(faces.map { f -> f.points.map { (a, u) -> -a to u } }, EdgeSnow.facesOf(StructureData.HUT.objectId, flipped = true)!!.map { it.points })
        val lines = EdgeSnow.linesOf(StructureData.HUT.objectId, flipped = false)!!
        assertEquals(lines.map { l -> l.points.map { (a, u) -> -a to u } }, EdgeSnow.linesOf(StructureData.HUT.objectId, flipped = true)!!.map { it.points })
    }
}
