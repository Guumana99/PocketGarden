package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.WorkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapTargetsTest {

    private fun at(id: String, x: Int, y: Int, objectId: String, work: WorkKind = WorkKind.NONE, status: PlantStatus = PlantStatus.HEALTHY) =
        GridObject(
            instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L,
            workKind = work, status = status, growthProgress = 1f,
        )

    private val transform = IsometricTransform(tileWidth = 128f, tileHeight = 64f, heightStep = 16f, offsetX = 600f, offsetY = 300f)
    private val hut = at("hut", 4, 4, StructureData.HUT.objectId) // covers (4..5, 4..5)

    private fun tap(screen: Offset, objects: List<GridObject>, editing: Boolean = false, t: IsometricTransform = transform, minReach: Float = 0f) =
        TapTargets.resolve(screen, t, GardenGrid(GardenMap.allGrass(9), objects), objects, editing, minReach)

    @Test
    fun `the bubble over a finished work site picks the site, not the lawn behind it`() {
        val site = at("cottage", 3, 3, StructureData.COTTAGE.objectId, work = WorkKind.READY)
        val bubble = TapTargets.readyBubbleCentre(site, transform)
        // Read on the ground alone, the bubble lies over cells far behind the site.
        val ground = TilePicker.pick(bubble, transform)
        assertFalse(ground != null && GridCell(ground.x, ground.y) in site.occupiedCells())

        assertEquals(TileCoord(3, 3), tap(bubble, listOf(site)))
        // A little off its middle still counts.
        assertEquals(TileCoord(3, 3), tap(bubble + Offset(transform.tileWidth * 0.25f, 0f), listOf(site)))
    }

    @Test
    fun `the water bubble over a thirsty crop picks the crop`() {
        val tulip = at("tulip", 4, 4, "plant_tulip", status = PlantStatus.THIRSTY)
        val bubble = TapTargets.bubbleCentre(tulip, transform)!!
        assertNotEquals(TileCoord(4, 4), TilePicker.pick(bubble, transform))
        assertEquals(TileCoord(4, 4), tap(bubble, listOf(tulip)))
        // A healthy crop has no bubble.
        assertNull(TapTargets.bubbleCentre(at("tulip", 4, 4, "plant_tulip"), transform))
    }

    @Test
    fun `zoomed far out, a bubble is still a finger wide`() {
        val small = transform.copy(tileWidth = 24f, tileHeight = 12f, heightStep = 3f)
        val site = at("hut", 4, 4, StructureData.HUT.objectId, work = WorkKind.READY)
        val nearBy = TapTargets.readyBubbleCentre(site, small) + Offset(14f, 0f)
        assertNotEquals(TileCoord(4, 4), tap(nearBy, listOf(site), t = small))
        assertEquals(TileCoord(4, 4), tap(nearBy, listOf(site), t = small, minReach = 20f))
    }

    @Test
    fun `a tap on the roof over bare lawn picks the building`() {
        val roof = transform.worldToScreen(5f, 5f, StructureData.HUT.heightSteps - 1f)
        assertEquals(TileCoord(4, 4), tap(roof, listOf(hut)))
        // Just beside its wall as well.
        val wall = transform.worldToScreen(4f, 6f, 2f) + Offset(-transform.tileWidth * 0.08f, 0f)
        assertEquals(TileCoord(4, 4), tap(wall, listOf(hut)))
    }

    @Test
    fun `a crop behind the building is still picked through its roof`() {
        val roof = transform.worldToScreen(5f, 5f, StructureData.HUT.heightSteps - 1f)
        val behind = TilePicker.pick(roof, transform)!!
        val tulip = at("tulip", behind.x, behind.y, "plant_tulip")
        assertEquals(behind, tap(roof, listOf(hut, tulip)))
    }

    @Test
    fun `a tap on open lawn away from everything is the lawn, and off the grid is nothing`() {
        val objects = listOf(hut, at("cottage", 0, 6, StructureData.COTTAGE.objectId, work = WorkKind.READY))
        assertEquals(TileCoord(8, 0), tap(transform.tileCenter(8, 0), objects))
        assertNull(tap(transform.worldToScreen(12f, 12f), objects))
    }

    @Test
    fun `while a cell is being looked for, taps read the ground only`() {
        val site = at("cottage", 3, 3, StructureData.COTTAGE.objectId, work = WorkKind.READY)
        val bubble = TapTargets.readyBubbleCentre(site, transform)
        assertEquals(TilePicker.pick(bubble, transform), tap(bubble, listOf(site), editing = true))
        val roof = transform.worldToScreen(5f, 5f, StructureData.HUT.heightSteps - 1f)
        assertEquals(TilePicker.pick(roof, transform), tap(roof, listOf(hut), editing = true))
    }

    @Test
    fun `where buildings overlap on screen, the one in front is picked`() {
        val back = at("back", 2, 2, StructureData.HUT.objectId)
        // The front hut's roof rises over the back hut's footprint on screen.
        val roof = transform.worldToScreen(5f, 5f, StructureData.HUT.heightSteps - 1f)
        assertEquals(TileCoord(4, 4), TapTargets.buildingAt(roof, transform, listOf(back, hut))?.let { TileCoord(it.gridX, it.gridY) })
    }

    @Test
    fun `the outline used for taps is the one used for hiding`() {
        for (x in 0 until 9) for (y in 0 until 9) {
            val cell = GridCell(x, y)
            if (cell in hut.occupiedCells()) continue
            val u = (x - y).toFloat()
            val v = (x + y + 1).toFloat()
            assertEquals(cell.toString(), Occlusion.hides(hut, cell), Occlusion.covers(hut, u, v, StructureData.HUT.heightSteps))
        }
        // The margin only widens it.
        assertTrue(Occlusion.covers(hut, -0.1f + (4 - 6).toFloat(), 9f, StructureData.HUT.heightSteps, margin = 0.35f))
    }
}
