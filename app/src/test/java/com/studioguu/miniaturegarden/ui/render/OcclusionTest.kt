package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.StructureData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcclusionTest {

    private fun at(id: String, x: Int, y: Int, objectId: String) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private val hut = at("hut", 4, 4, StructureData.HUT.objectId) // covers (4..5, 4..5)
    private val well = at("well", 4, 4, StructureData.WELL.objectId) // covers (4, 4..5)
    private val cottage = at("cottage", 3, 3, StructureData.COTTAGE.objectId) // covers (3..5, 3..5)
    private val transform = IsometricTransform(tileWidth = 128f, tileHeight = 64f, heightStep = 16f)

    @Test
    fun `cells right behind a building are hidden by it`() {
        assertTrue(Occlusion.hides(hut, GridCell(3, 3)))
        assertTrue(Occlusion.hides(hut, GridCell(2, 2)))
        // Beside it at the back, behind its walls.
        assertTrue(Occlusion.hides(hut, GridCell(3, 4)))
        assertTrue(Occlusion.hides(hut, GridCell(4, 3)))
    }

    @Test
    fun `cells in front of, beside or far behind a building are not`() {
        assertFalse(Occlusion.hides(hut, GridCell(6, 6)))
        assertFalse(Occlusion.hides(hut, GridCell(5, 6)))
        assertFalse(Occlusion.hides(hut, GridCell(6, 5)))
        assertFalse(Occlusion.hides(hut, GridCell(1, 1)))
        assertFalse(Occlusion.hides(hut, GridCell(2, 6)))
        assertFalse(Occlusion.hides(hut, GridCell(6, 2)))
    }

    @Test
    fun `a building does not hide its own cells`() {
        for (cell in hut.occupiedCells()) assertFalse(cell.toString(), Occlusion.hides(hut, cell))
        for (cell in cottage.occupiedCells()) assertFalse(cell.toString(), Occlusion.hides(cottage, cell))
    }

    @Test
    fun `a taller building hides more of what is behind it`() {
        assertTrue(Occlusion.hides(well, GridCell(3, 3)))
        assertFalse(Occlusion.hides(well, GridCell(2, 2)))
        assertTrue(Occlusion.hides(hut, GridCell(2, 2)))
        val behindCottage = (0 until 9).flatMap { x -> (0 until 9).map { y -> GridCell(x, y) } }
            .count { Occlusion.hides(cottage, it) }
        val behindHut = (0 until 9).flatMap { x -> (0 until 9).map { y -> GridCell(x, y) } }
            .count { Occlusion.hides(hut, it) }
        assertTrue(behindCottage > behindHut)
    }

    @Test
    fun `only buildings hide anything`() {
        assertFalse(Occlusion.hides(at("rock", 4, 4, "deco_rock"), GridCell(3, 3)))
        assertFalse(Occlusion.hides(at("tulip", 4, 4, "plant_tulip"), GridCell(3, 3)))
    }

    @Test
    fun `a tap on the roof picks the ground behind the building, not the building`() {
        // The top of the hut's roof, straight above the middle of its footprint.
        val roof = transform.worldToScreen(5f, 5f, StructureData.HUT.heightSteps - 1f)
        val picked = TilePicker.pick(roof, transform)!!
        val cell = GridCell(picked.x, picked.y)
        assertFalse(cell in hut.occupiedCells())
        assertTrue(Occlusion.hides(hut, cell))
        // At its foot the building itself is picked.
        val foot = TilePicker.pick(transform.worldToScreen(5f, 5f), transform)!!
        assertTrue(GridCell(foot.x, foot.y) in hut.occupiedCells())
    }

    @Test
    fun `buildings fade while a cell is being looked for, or when they hide the selected one`() {
        val tulip = at("tulip", 3, 3, "plant_tulip")
        val objects = listOf(hut, tulip, at("rock", 0, 8, "deco_rock"))

        assertEquals(setOf("hut"), Occlusion.fadedBuildings(objects, selected = null, editing = true))
        assertEquals(setOf("hut"), Occlusion.fadedBuildings(objects, selected = GridCell(3, 3), editing = false))
        assertEquals(emptySet<String>(), Occlusion.fadedBuildings(objects, selected = GridCell(4, 4), editing = false))
        assertEquals(emptySet<String>(), Occlusion.fadedBuildings(objects, selected = GridCell(8, 8), editing = false))
        assertEquals(emptySet<String>(), Occlusion.fadedBuildings(objects, selected = null, editing = false))
    }
}
