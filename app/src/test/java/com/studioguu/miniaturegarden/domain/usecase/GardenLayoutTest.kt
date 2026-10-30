package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.StructureData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edit mode and what may be moved, put back or thrown away (see [GardenLayout]). */
class GardenLayoutTest {

    private fun obj(objectId: String) = GridObject(instanceId = objectId, gridX = 2, gridY = 2, objectId = objectId, lastUpdatedTimestamp = 0L)

    private val crop = obj(PlantSpecies.TULIP.objectId)
    private val soil = obj(GridObject.TILLED_SOIL_OBJECT_ID)
    private val crate = obj(GridObject.GIFT_BOX_OBJECT_ID)
    private val greenhouse = obj(GridObject.GREENHOUSE_OBJECT_ID)

    /** The garden's exterior and its buildings: lawn, paving, fences, the river, decorations, a hut. */
    private val layout = listOf(
        GardenItem.GROUND_GRASS, GardenItem.GROUND_PEBBLE, GardenItem.GROUND_FLOWERS, GardenItem.FLOOR_COBBLESTONE,
        GardenItem.FLOOR_WOOD, GardenItem.STRUCT_FENCE, GardenItem.TILE_RIVER, GardenItem.DECO_ROCK, GardenItem.DECO_BENCH,
        GardenItem.DECO_LANTERN, GardenItem.STRUCT_HUT, GardenItem.STRUCT_WELL, GardenItem.STRUCT_COTTAGE,
    ).map { obj(it.objectId) }

    @Test
    fun `the exterior and the buildings are moved and put back in edit mode only`() {
        for (piece in layout) {
            assertTrue(piece.objectId, GardenLayout.isLayoutPiece(piece))
            for (action in LayoutAction.entries) {
                assertEquals("${piece.objectId} $action", LayoutCheck.NEEDS_EDIT_MODE, GardenLayout.check(action, piece, editing = false))
                assertEquals("${piece.objectId} $action", LayoutCheck.OK, GardenLayout.check(action, piece, editing = true))
            }
        }
    }

    @Test
    fun `a fence goes back as a fence whatever part of it is drawn`() {
        for (part in listOf("struct_fence_post", "struct_fence_rail")) {
            val fence = obj(part)
            if (!fence.isFence) continue
            assertEquals(LayoutCheck.OK, GardenLayout.check(LayoutAction.STORE, fence, editing = true))
        }
    }

    @Test
    fun `crops and tilled soil stay where they are, and are cleared away in edit mode only`() {
        for (inGround in listOf(crop, soil, crop.copy(status = com.studioguu.miniaturegarden.domain.model.PlantStatus.WITHERED))) {
            assertFalse(GardenLayout.isLayoutPiece(inGround))
            for (editing in listOf(false, true)) {
                assertEquals(LayoutCheck.IN_THE_GROUND, GardenLayout.check(LayoutAction.MOVE, inGround, editing))
                assertEquals(LayoutCheck.IN_THE_GROUND, GardenLayout.check(LayoutAction.STORE, inGround, editing))
            }
            // Since #49 nothing in the garden is cleared away outside edit mode.
            assertEquals(LayoutCheck.NEEDS_EDIT_MODE, GardenLayout.check(LayoutAction.DISCARD, inGround, editing = false))
            assertEquals(LayoutCheck.OK, GardenLayout.check(LayoutAction.DISCARD, inGround, editing = true))
        }
        assertFalse(MoveObjectUseCase.canMove(crop))
    }

    @Test
    fun `a gift box is not touched, and the greenhouse bed does not move`() {
        for (action in LayoutAction.entries) assertEquals(LayoutCheck.FIXED, GardenLayout.check(action, crate, editing = true))
        assertEquals(LayoutCheck.FIXED, GardenLayout.check(LayoutAction.MOVE, greenhouse, editing = true))
        assertEquals(LayoutCheck.OK, GardenLayout.check(LayoutAction.STORE, greenhouse, editing = true))
        assertEquals(LayoutCheck.NEEDS_EDIT_MODE, GardenLayout.check(LayoutAction.STORE, greenhouse, editing = false))
    }

    @Test
    fun `edit mode keeps to rearranging - only the shop is in hand there`() {
        // #50: no hoe, watering, feeding or putting new things down from the bag in edit mode.
        assertEquals(setOf(com.studioguu.miniaturegarden.domain.model.TutorialTool.SHOP),
            com.studioguu.miniaturegarden.domain.model.TutorialTool.entries.filter { GardenLayout.toolAllowedInEditMode(it) }.toSet())
    }

    @Test
    fun `a building can be moved in edit mode, the same as before`() {
        val hut = obj(StructureData.HUT.objectId)
        assertTrue(MoveObjectUseCase.canMove(hut))
        assertEquals(LayoutCheck.OK, GardenLayout.check(LayoutAction.MOVE, hut, editing = true))
    }

    @Test
    fun `the grid shows in edit mode only, and the screen asks the rules before arranging`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        fun source(path: String) = java.io.File(main, "java/com/studioguu/miniaturegarden/$path").readText()
        assertTrue(source("ui/GardenScreen.kt").contains("showGrid = editMode && !cameraMode"))
        val model = source("ui/GardenViewModel.kt")
        for (action in listOf("LayoutAction.MOVE", "LayoutAction.STORE", "LayoutAction.DISCARD")) {
            assertTrue(action, model.contains("GardenLayout.check($action, obj, _editMode.value)"))
        }
        // Nothing is put down, tended or tilled in edit mode; the bag and the hoe are refused there.
        for (action in listOf("fun waterSelected()", "fun fertilizeSelected()", "fun harvestSelected()", "fun reviveSelected()", "fun startPlacing(")) {
            val body = model.substringAfter(action).substringBefore("\n    fun ")
            assertTrue(action, body.contains("lockedByEditMode()"))
        }
        assertTrue(model.substringAfter("fun toggleHoe()").substringBefore("\n    fun ").contains("lockedByEditMode()"))
        // Going into edit mode puts the hoe and what was in hand down.
        val toggle = model.substringAfter("fun toggleEditMode()").substringBefore("\n    fun ")
        assertTrue(toggle.contains("_hoeActive.value = false") && toggle.contains("_placingSlotIndex.value = null"))
        assertFalse(model.contains("placingNeedsEditMode"))
    }
}
