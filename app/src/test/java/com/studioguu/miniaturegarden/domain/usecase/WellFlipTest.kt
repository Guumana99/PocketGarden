package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CreaturePerches
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.ui.render.EdgeSnow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1110 (4): the well turns like the other things, and what sits on it turns with it. */
class WellFlipTest {

    private fun well(flipped: Boolean) = GridObject(
        instanceId = "w", objectId = StructureData.WELL.objectId, gridX = 3, gridY = 4, isFlipped = flipped, lastUpdatedTimestamp = 0L,
    )

    @Test
    fun `the well can face either way`() {
        assertTrue(GridObject.canFaceEitherWay(StructureData.WELL.objectId))
        assertTrue(well(false).canFlip)
    }

    @Test
    fun `a turned well's rim points are mirrored across the screen`() {
        val plain = CreaturePerches.pointsOf(well(false))
        val turned = CreaturePerches.pointsOf(well(true))
        assertEquals(plain.size, turned.size)
        // Across the screen is x - y: mirrored, it changes sign; the depth (x + y) and height stay.
        val across = { p: com.studioguu.miniaturegarden.domain.model.Perch -> (p.x - 3.5f) - (p.y - 4.5f) }
        val want = plain.map { -across(it) }.sorted()
        val got = turned.map(across).sorted()
        want.zip(got).forEach { (w, g) -> assertEquals(w, g, 1e-4f) }
        val depthWant = plain.map { it.x + it.y }.sorted()
        val depthGot = turned.map { it.x + it.y }.sorted()
        depthWant.zip(depthGot).forEach { (w, g) -> assertEquals(w, g, 1e-4f) }
        assertEquals(plain.map { it.height }, turned.map { it.height })
    }

    @Test
    fun `the snow on a turned well's roof is mirrored`() {
        // #fb-1113 3: no heaps along its edges; the snow read off its picture is drawn mirrored with it.
        assertEquals(null, EdgeSnow.linesOf(StructureData.WELL.objectId, flipped = false))
        assertTrue(com.studioguu.miniaturegarden.ui.render.PictureSnow.has(StructureData.WELL.objectId, StructureData.WELL.objectId))
    }
}
