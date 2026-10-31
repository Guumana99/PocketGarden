package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.BridgeFit
import com.studioguu.miniaturegarden.domain.model.BridgeProblem
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenSnapshot
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PaintPart
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.render.BankPiece
import com.studioguu.miniaturegarden.ui.render.FlowDirection
import com.studioguu.miniaturegarden.ui.render.PaintMix
import com.studioguu.miniaturegarden.ui.render.RiverBanks
import com.studioguu.miniaturegarden.ui.render.RiverFlow
import com.studioguu.miniaturegarden.ui.shop.ShelfTag
import com.studioguu.miniaturegarden.ui.shop.ShopShelves
import com.studioguu.miniaturegarden.ui.shop.ShopStage
import com.studioguu.miniaturegarden.domain.model.Season
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pictures taken in together: painting, the bench, the rocks, the bridge and the river's course. */
class ArtImportTest {

    private val repo = FakeGardenRepository()
    private val map = GardenMap.DEFAULT
    private val flip = FlipObjectUseCase(repo, map)

    private fun at(x: Int, y: Int, objectId: String, id: String = "${objectId}_${x}_$y") =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private fun river(vararg cells: Pair<Int, Int>) = cells.map { (x, y) -> at(x, y, GridObject.RIVER_OBJECT_ID) }

    // --- Painting ---

    @Test
    fun `a building takes its colours for nothing, and its own colour is stored as none`() = runBlocking {
        val hut = at(1, 1, StructureData.HUT.objectId, "hut")
        repo.objects[hut.instanceId] = hut
        repo.profile = UserProfile(coins = 5)
        val paint = PaintBuildingUseCase(repo)
        val green = BuildingPaint.colours(hut.objectId, PaintPart.ROOF).first { it.id == "green" }
        assertEquals(ActionResult.SUCCESS, paint("hut", PaintPart.ROOF, green))
        assertEquals("green", repo.objects.getValue("hut").roofColour)
        assertTrue(BuildingPaint.isPainted(repo.objects.getValue("hut")))
        assertEquals(5L, repo.profile!!.coins)

        // Back to the picture's own grey.
        assertEquals(ActionResult.SUCCESS, paint("hut", PaintPart.ROOF, BuildingPaint.colours(hut.objectId, PaintPart.ROOF).first()))
        assertNull(repo.objects.getValue("hut").roofColour)
        assertFalse(BuildingPaint.isPainted(repo.objects.getValue("hut")))

        // A wall colour is not a roof colour, and a bench has neither.
        val cream = BuildingPaint.colours(StructureData.COTTAGE.objectId, PaintPart.WALL).first { it.id == "cream" }
        assertEquals(ActionResult.NOT_PAINTABLE, paint("hut", PaintPart.WALL, cream))
        assertFalse(BuildingPaint.canPaint("deco_bench"))
    }

    @Test
    fun `the buildings start in the colours of their pictures, and each part has four`() {
        val cottage = StructureData.COTTAGE.objectId
        val hut = StructureData.HUT.objectId
        assertEquals(listOf("red", "blue", "green", "dark_brown"), BuildingPaint.colours(cottage, PaintPart.ROOF).map { it.id })
        assertEquals(listOf("white", "cream", "pale_blue", "pale_pink"), BuildingPaint.colours(cottage, PaintPart.WALL).map { it.id })
        assertEquals("grey", BuildingPaint.colourOf(at(0, 0, hut), PaintPart.ROOF).id)
        assertEquals("wood", BuildingPaint.colourOf(at(0, 0, hut), PaintPart.WALL).id)
        assertEquals(4, BuildingPaint.colours(hut, PaintPart.ROOF).size)
        assertEquals(4, BuildingPaint.colours(hut, PaintPart.WALL).size)
    }

    @Test
    fun `paint goes only where the mask says, and keeps the light`() {
        val dark = 0xFF404040.toInt()
        val light = 0xFF808080.toInt()
        val roofOnly = 0xFFFF0000.toInt()
        val nothing = 0xFF000000.toInt()
        val painted = PaintMix.paint(intArrayOf(dark, light, dark), intArrayOf(roofOnly, roofOnly, nothing), 0xFF204060.toInt(), null)
        // Left alone where it is not masked; on the roof, the lighter pixel stays the lighter.
        assertEquals(dark, painted[2])
        assertTrue((painted[1] and 255) > (painted[0] and 255))
        assertEquals(0xFF, painted[0] ushr 24)
    }

    // --- The bench ---

    @Test
    fun `the bench goes a quarter turn at a time through its four pictures`() = runBlocking {
        val bench = at(3, 3, "deco_bench", "bench")
        repo.objects["bench"] = bench
        val seen = (0 until 4).map {
            val now = repo.objects.getValue("bench")
            assertEquals(ActionResult.SUCCESS, flip("bench"))
            TileSetDefinition.assetIdFor(now) to now.isFlipped
        }
        assertEquals(
            listOf("deco_bench" to false, "deco_bench_back" to true, "deco_bench_back" to false, "deco_bench" to true),
            seen,
        )
        assertEquals(bench, repo.objects.getValue("bench"))
    }

    // --- The rocks ---

    @Test
    fun `the shop keeps its rocks under one, and the kinds open with the levels`() {
        val shelf = ShopShelves.stock(ShopStage.SUNDRIES, Season.SPRING, PlayerStats(level = 20, totalXp = 0L), 9_999L, ownsSymbol = false).map { it.item }
        assertEquals(1, shelf.count { it in GardenItem.ROCK_KINDS })
        val kinds = ShopShelves.rockKinds(PlayerStats(level = 7, totalXp = 0L), 9_999L)
        assertEquals(listOf(2, 4, 7, 10, 13), kinds.map { it.item.unlockLevel })
        assertEquals(listOf(60, 80, 150, 250, 300), kinds.map { it.item.price })
        assertEquals(listOf(false, false, false, true, true), kinds.map { it.tag == ShelfTag.LOCKED })
        // Each is one cell, and has two sides.
        for (kind in kinds) {
            assertEquals(Footprint.SINGLE, Footprint.forObjectId(kind.item.objectId))
            assertTrue(kind.item.objectId, GridObject.canFaceEitherWay(kind.item.objectId))
        }
    }

    @Test
    fun `any kind of rock will do for the squirrel`() {
        for (kind in GardenItem.ROCK_KINDS) {
            val garden = GardenSnapshot(listOf(at(5, 5, PlantSpecies.SUNFLOWER.objectId).copy(growthProgress = 1f), at(1, 1, kind.objectId)), WeatherType.SUNNY)
            assertTrue(kind.objectId, VisitRule.SQUIRREL_SUNFLOWER.isMetBy(garden))
        }
    }

    // --- The bridge ---

    private val bridge = StructureData.BRIDGE.objectId

    @Test
    fun `a bridge goes bank to bank over a river one or two cells wide, and no wider`() {
        val one = river(1 to 3, 2 to 3, 3 to 3)
        val two = one + river(1 to 4, 2 to 4, 3 to 4)
        val three = two + river(1 to 5, 2 to 5, 3 to 5)
        assertEquals(BridgeFit.Across(GridCell(2, 2), longer = false), GardenGrid(map, one).bridgeAcross(2, 3, flipped = false))
        assertEquals(BridgeFit.Across(GridCell(2, 2), longer = true), GardenGrid(map, two).bridgeAcross(2, 4, flipped = false))
        assertEquals(BridgeFit.Refused(BridgeProblem.TOO_WIDE), GardenGrid(map, three).bridgeAcross(2, 4, flipped = false))
        // A tap on the bank will do.
        assertEquals(BridgeFit.Across(GridCell(2, 2), longer = false), GardenGrid(map, one).bridgeAcross(2, 2, flipped = false))
        assertEquals(BridgeFit.Refused(BridgeProblem.NO_RIVER), GardenGrid(map, one).bridgeAcross(7, 7, flipped = false))
        // Flipped it runs along x, so along this river it would be over three cells of water.
        assertEquals(BridgeFit.Refused(BridgeProblem.TOO_WIDE), GardenGrid(map, one).bridgeAcross(2, 3, flipped = true))
        assertEquals(
            BridgeFit.Across(GridCell(4, 6), longer = false),
            GardenGrid(map, river(5 to 5, 5 to 6, 5 to 7)).bridgeAcross(5, 6, flipped = true),
        )
        // The river at the garden's edge leaves no land for an end.
        assertEquals(BridgeFit.Refused(BridgeProblem.OUTSIDE), GardenGrid(map, river(0 to 3, 1 to 3, 2 to 3).map { it.copy(gridY = 0) }).bridgeAcross(1, 0, flipped = false))
        assertEquals(Footprint(1, 3), Footprint.forObjectId(bridge))
        assertEquals(Footprint(1, 4), Footprint.forObjectId(bridge, longer = true))
        assertEquals(Footprint(4, 1), Footprint.forObjectId(bridge, flipped = true, longer = true))
    }

    @Test
    fun `each end of a bridge rests on free land`() {
        val water = river(1 to 3, 2 to 3, 3 to 3)
        fun endOn(objectId: String) = GardenGrid(map, water + at(2, 2, objectId)).bridgeAcross(2, 3, flipped = false)
        assertEquals(BridgeFit.Refused(BridgeProblem.ENDS_TAKEN), endOn("deco_bench"))
        assertEquals(BridgeFit.Refused(BridgeProblem.ENDS_TAKEN), endOn(GridObject.TILLED_SOIL_OBJECT_ID))
        // What only lies on the ground stays under it.
        assertTrue(endOn("ground_grass") is BridgeFit.Across)
        assertTrue(endOn(GridObject.COBBLESTONE_OBJECT_ID) is BridgeFit.Across)
    }

    @Test
    fun `a bridge costs the same either way, and what is under it stays while it stands`() = runBlocking {
        assertEquals(3_500, GardenItem.STRUCT_BRIDGE.price)
        val cells = river(1 to 3, 2 to 3, 3 to 3, 1 to 4, 2 to 4, 3 to 4)
        val lawn = at(2, 2, "ground_grass")
        (cells + lawn).forEach { repo.objects[it.instanceId] = it }
        repo.profile = UserProfile()
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_BRIDGE.itemId, 3))
        val place = PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))
        assertEquals(ActionResult.NEEDS_RIVER_ACROSS, place(0, 7, 7, 0L))
        assertEquals(ActionResult.SUCCESS, place(0, 2, 3, 0L))
        val placed = repo.objects.values.first { it.isBridge }
        assertEquals(GridCell(2, 2), GridCell(placed.gridX, placed.gridY))
        assertEquals("struct_bridge_taiko_long", TileSetDefinition.assetIdFor(placed))
        assertEquals(4, placed.occupiedCells().size)
        val grid = GardenGrid(map, repo.objects.values.toList())
        assertTrue(grid.occupantAt(2, 3)!!.isTerrain)
        for (y in 2..5) assertTrue(grid.objectAt(2, y)!!.isBridge)
        // Nothing goes on its ends, or on the river, and neither comes out from under it.
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 2, 5))
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 2, 2, PlacementKind.FLOOR))
        val store = StoreObjectUseCase(repo)
        val remove = StartRemovalUseCase(repo, store, RemoveObjectUseCase(repo))
        assertEquals(ActionResult.BRIDGE_ABOVE, remove(cells.first { it.gridX == 2 && it.gridY == 3 }.instanceId, toBag = true, 0L))
        assertEquals(ActionResult.BRIDGE_ABOVE, remove(lawn.instanceId, toBag = true, 0L))
        // Turned round it would have to cross three cells of water.
        assertEquals(ActionResult.RIVER_TOO_WIDE, flip(placed.instanceId))
    }

    @Test
    fun `a bridge moved takes the length of the river it goes to`() = runBlocking {
        val over = at(4, 6, bridge, "bridge").copy(turned = true)
        val move = MoveObjectUseCase(repo, map, CalculateOfflineProgressUseCase())
        val more = river(1 to 1, 2 to 1, 3 to 1)
        more.forEach { repo.objects[it.instanceId] = it }
        repo.objects["bridge"] = over
        assertEquals(ActionResult.SUCCESS, move("bridge", 2, 1))
        val moved = repo.objects.getValue("bridge")
        assertEquals(GridCell(2, 0), GridCell(moved.gridX, moved.gridY))
        assertFalse(moved.turned)
    }

    // --- The river ---

    @Test
    fun `water runs along the river and turns with it`() {
        val cells = setOf(GridCell(1, 1), GridCell(2, 1), GridCell(3, 1), GridCell(3, 2), GridCell(3, 3))
        val flows = RiverFlow.of(cells)
        assertEquals(cells, flows.keys)
        assertEquals(FlowDirection.EAST, flows.getValue(GridCell(2, 1)).to)
        val bend = flows.getValue(GridCell(3, 1))
        assertEquals(FlowDirection.EAST, bend.from)
        assertEquals(FlowDirection.SOUTH, bend.to)
        assertTrue(bend.isBend)
        assertEquals(FlowDirection.SOUTH, flows.getValue(GridCell(3, 3)).to)
        // A pool goes nowhere, and has a flow all the same.
        assertFalse(RiverFlow.of(setOf(GridCell(5, 5))).getValue(GridCell(5, 5)).isBend)
    }

    @Test
    fun `four pieces of bank make every shape of river`() {
        // A straight stretch along x, and a corner of land between two arms.
        val straight = setOf(GridCell(0, 1), GridCell(1, 1), GridCell(2, 1))
        fun piece(cells: Set<GridCell>, x: Int, y: Int, quarter: Int) = RiverBanks.pieceAt(x, y, quarter) { px, py -> GridCell(px, py) in cells }
        assertEquals(BankPiece.ALONG_FIRST, piece(straight, 1, 1, 0))
        assertEquals(BankPiece.ALONG_SECOND, piece(straight, 1, 1, 1))
        // Its end: land round three sides, so two bends.
        assertEquals(BankPiece.BEND, piece(straight, 2, 1, 1))
        assertEquals(BankPiece.BEND, piece(straight, 2, 1, 2))
        val square = setOf(GridCell(1, 1), GridCell(2, 1), GridCell(1, 2), GridCell(2, 2))
        assertEquals(BankPiece.NONE, piece(square, 1, 1, 2))
        val notch = square - GridCell(1, 1)
        assertEquals(BankPiece.POINT, piece(notch, 2, 2, 0))
    }
}
