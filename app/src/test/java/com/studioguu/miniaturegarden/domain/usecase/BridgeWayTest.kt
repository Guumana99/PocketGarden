package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.BridgeFit
import com.studioguu.miniaturegarden.domain.model.BridgeProblem
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PaintPart
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A bridge finds its own way over the river; the well's roof takes the cottage's colours. */
class BridgeWayTest {
    private val map = GardenMap.DEFAULT
    private val bridge = StructureData.BRIDGE.objectId

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private fun river(vararg cells: Pair<Int, Int>) = cells.map { (x, y) -> at(x, y, GridObject.RIVER_OBJECT_ID) }

    private val alongX = river(3 to 5, 4 to 5, 5 to 5, 6 to 5)
    private val alongY = river(5 to 3, 5 to 4, 5 to 5, 5 to 6)

    @Test
    fun `it goes across a river running either way, from a tap on the water or beside it`() {
        val over = GardenGrid(map, alongX).bridgeFor(4, 5)
        assertFalse(over.flipped)
        assertEquals(setOf(GridCell(4, 4), GridCell(4, 5), GridCell(4, 6)), over.cells.toSet())
        val turned = GardenGrid(map, alongY).bridgeFor(5, 4)
        assertTrue(turned.flipped)
        assertEquals(setOf(GridCell(4, 4), GridCell(5, 4), GridCell(6, 4)), turned.cells.toSet())
        assertEquals(setOf(GridCell(4, 4), GridCell(5, 4), GridCell(6, 4)), GardenGrid(map, alongY).bridgeFor(4, 4).cells.toSet())
    }

    @Test
    fun `it takes three cells over one, four over two`() {
        val wide = alongX + river(3 to 6, 4 to 6, 5 to 6, 6 to 6)
        assertEquals(4, GardenGrid(map, wide).bridgeFor(4, 5).cells.size)
        assertEquals(3, GardenGrid(map, alongX).bridgeFor(4, 5).cells.size)
    }

    @Test
    fun `with two ways, it takes the shorter, across the flow`() {
        val pool = river(4 to 4, 5 to 4)
        val choice = GardenGrid(map, pool).bridgeFor(4, 4)
        assertFalse(choice.flipped)
        assertEquals(3, choice.cells.size)
        assertTrue(GardenGrid(map, river(4 to 4, 4 to 5)).bridgeFor(4, 4).flipped)
    }

    @Test
    fun `where both are as long, either can be had, and the button turns it`() = runBlocking {
        val spot = river(5 to 5)
        assertFalse(GardenGrid(map, spot).bridgeFor(5, 5).flipped)
        assertTrue(GardenGrid(map, spot).bridgeFor(5, 5, preferFlipped = true).flipped)
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 100, playerLevel = 20)
            spot.forEach { put(it) }
            putSlot(InventorySlot(0, GardenItem.STRUCT_BRIDGE.itemId, 1))
        }
        assertEquals(ActionResult.SUCCESS, PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))(0, 5, 5, 0L))
        val placed = repo.objects.values.first { it.isBridge }
        repo.put(placed.withoutWork())
        assertFalse(placed.isFlipped)
        assertEquals(ActionResult.SUCCESS, FlipObjectUseCase(repo, map)(placed.instanceId))
        assertTrue(repo.objects.values.first { it.isBridge }.isFlipped)
    }

    @Test
    fun `put down over a river running the other way, it needs no turning`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 100, playerLevel = 20)
            alongY.forEach { put(it) }
            putSlot(InventorySlot(0, GardenItem.STRUCT_BRIDGE.itemId, 1))
        }
        assertEquals(ActionResult.SUCCESS, PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))(0, 5, 4, 0L))
        assertTrue(repo.objects.values.first { it.isBridge }.isFlipped)
    }

    @Test
    fun `a bridge from an old garden keeps its way when it is moved, where it can`() = runBlocking {
        val spots = river(3 to 3, 7 to 7)
        val old = at(3, 2, bridge)
        val repo = FakeGardenRepository().apply { spots.forEach { put(it) }; put(old) }
        assertEquals(ActionResult.SUCCESS, MoveObjectUseCase(repo, map, CalculateOfflineProgressUseCase())(old.instanceId, 7, 7))
        assertFalse(repo.objects.getValue(old.instanceId).isFlipped)
        assertEquals(GridCell(7, 6), repo.objects.getValue(old.instanceId).let { GridCell(it.gridX, it.gridY) })
    }

    @Test
    fun `when neither way will do, it says what stands in the way`() {
        fun why(objects: List<GridObject>, x: Int, y: Int) = (GardenGrid(map, objects).bridgeFor(x, y).fit as BridgeFit.Refused).why
        assertEquals(BridgeProblem.NO_RIVER, why(alongX, 8, 8))
        val wide = (2..8).flatMap { x -> (3..5).map { y -> at(x, y, GridObject.RIVER_OBJECT_ID) } }
        assertEquals(BridgeProblem.TOO_WIDE, why(wide, 4, 4))
        val atTheEdge = river(3 to 0, 4 to 0, 5 to 0, 6 to 0)
        assertEquals(BridgeProblem.OUTSIDE, why(atTheEdge, 4, 0))
        assertEquals(BridgeProblem.ENDS_TAKEN, why(alongX + at(4, 4, "deco_bench"), 4, 5))
        assertEquals(ActionResult.BRIDGE_OFF_THE_EDGE, bridgeRefusal(BridgeProblem.OUTSIDE))
        assertEquals(ActionResult.BRIDGE_ENDS_TAKEN, bridgeRefusal(BridgeProblem.ENDS_TAKEN))
    }

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test
    fun `the words for it are short and look forward`() {
        val strings = File(main, "res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"msg_bridge_off_the_edge\">岸から岸まで届きません</string>"))
        assertTrue(strings.contains("<string name=\"msg_river_too_wide\">川の幅は\u00A02\u00A0マスまでです</string>"))
    }

    @Test
    fun `the banner has no turning for a bridge, which finds its own way`() {
        val screen = File(main, "java/com/studioguu/miniaturegarden/ui/GardenScreen.kt").readText()
        assertTrue(screen.contains("item != GardenItem.STRUCT_BRIDGE && GridObject.canFaceEitherWay"))
    }

    // --- The well's roof ---

    @Test
    fun `the well's roof takes the cottage's roof colours, and it has no walls to paint`() {
        val well = StructureData.WELL.objectId
        assertEquals(BuildingPaint.colours(StructureData.COTTAGE.objectId, PaintPart.ROOF), BuildingPaint.colours(well, PaintPart.ROOF))
        assertEquals(listOf(PaintPart.ROOF), BuildingPaint.parts(well))
        assertTrue(BuildingPaint.canPaint(well))
        assertTrue(well in BuildingPaint.PAINTABLE_OBJECT_IDS)
        val manifest = File(main, "assets/atlas/assets_manifest.json").readText()
        assertTrue(manifest.contains("\"mask_struct_well\""))
    }

    @Test
    fun `a painted well is painted through its mask, and a plain one drawn as it was`() = runBlocking {
        val well = at(3, 3, StructureData.WELL.objectId)
        val repo = FakeGardenRepository().apply { put(well) }
        val blue = BuildingPaint.colours(well.objectId, PaintPart.ROOF).first { it.id == "blue" }
        assertFalse(BuildingPaint.isPainted(well))
        assertEquals(ActionResult.SUCCESS, PaintBuildingUseCase(repo)(well.instanceId, PaintPart.ROOF, blue))
        assertTrue(BuildingPaint.isPainted(repo.objects.getValue(well.instanceId)))
    }
}
