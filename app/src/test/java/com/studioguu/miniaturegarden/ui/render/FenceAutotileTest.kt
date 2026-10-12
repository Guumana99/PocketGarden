package com.studioguu.miniaturegarden.ui.render

import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.FakeGardenRepository
import com.studioguu.miniaturegarden.domain.usecase.PlaceItemUseCase
import com.studioguu.miniaturegarden.domain.usecase.RecordCollectionUseCase
import com.studioguu.miniaturegarden.domain.usecase.RemoveObjectUseCase
import com.studioguu.miniaturegarden.domain.usecase.StoreObjectUseCase
import com.studioguu.miniaturegarden.ui.render.FenceAutotile.EAST
import com.studioguu.miniaturegarden.ui.render.FenceAutotile.NORTH
import com.studioguu.miniaturegarden.ui.render.FenceAutotile.SOUTH
import com.studioguu.miniaturegarden.ui.render.FenceAutotile.WEST
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FenceAutotileTest {

    private val t0 = 1_700_000_000_000L

    private fun fence(x: Int, y: Int, part: FencePart = FencePart.SECTION) =
        GridObject(instanceId = "f_${x}_$y", gridX = x, gridY = y, objectId = part.objectId, lastUpdatedTimestamp = t0)

    private fun thing(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "o_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    /** The mask of the cell (x, y) in a garden holding [objects]. */
    private fun maskIn(objects: List<GridObject>, x: Int, y: Int): Int {
        val grid = GardenGrid(GardenMap.DEFAULT, objects)
        return FenceAutotile.maskAt(x, y) { ax, ay -> grid.occupantAt(ax, ay)?.isFence == true }
    }

    private fun maskOf(x: Int, y: Int, vararg fences: Pair<Int, Int>) =
        maskIn(fences.map { fence(it.first, it.second) }, x, y)

    // --- Reading the neighbours ---

    @Test
    fun `each neighbour sets its own bit`() {
        assertEquals(0, maskOf(4, 4))
        assertEquals(NORTH, maskOf(4, 4, 4 to 3))
        assertEquals(EAST, maskOf(4, 4, 5 to 4))
        assertEquals(SOUTH, maskOf(4, 4, 4 to 5))
        assertEquals(WEST, maskOf(4, 4, 3 to 4))
        assertEquals(listOf(1, 2, 4, 8), listOf(NORTH, EAST, SOUTH, WEST))
    }

    @Test
    fun `neighbours combine, and diagonals and the tile itself do not count`() {
        assertEquals(NORTH or SOUTH, maskOf(4, 4, 4 to 3, 4 to 5))
        assertEquals(EAST or SOUTH, maskOf(4, 4, 5 to 4, 4 to 5))
        assertEquals(15, maskOf(4, 4, 4 to 3, 5 to 4, 4 to 5, 3 to 4))
        assertEquals(0, maskOf(4, 4, 3 to 3, 5 to 3, 3 to 5, 5 to 5))
        assertEquals(EAST, maskOf(4, 4, 4 to 4, 5 to 4))
        // Two cells apart is not next to.
        assertEquals(0, maskOf(4, 4, 6 to 4, 4 to 2))
    }

    @Test
    fun `at the edge of the garden there is simply no neighbour`() {
        assertEquals(EAST, maskOf(0, 0, 1 to 0))
        assertEquals(WEST or NORTH, maskOf(8, 8, 7 to 8, 8 to 7))
        assertEquals(0, maskOf(0, 8))
    }

    @Test
    fun `only fences join, under any of the ids a fence has been stored as`() {
        val around = listOf(
            thing(4, 3, "deco_rock"),
            thing(5, 4, GridObject.COBBLESTONE_OBJECT_ID),
            thing(4, 5, "plant_tulip"),
            thing(3, 4, GridObject.RIVER_OBJECT_ID),
        )
        assertEquals(0, maskIn(around + fence(4, 4), 4, 4))

        val oldSave = listOf(fence(4, 3, FencePart.RAIL), fence(5, 4, FencePart.POST), fence(4, 5, FencePart.SECTION))
        assertEquals(NORTH or EAST or SOUTH, maskIn(oldSave + fence(4, 4, FencePart.POST), 4, 4))
    }

    @Test
    fun `a fence joins across paving, which lies under it`() {
        val objects = listOf(
            thing(4, 4, GridObject.COBBLESTONE_OBJECT_ID), fence(4, 4),
            thing(5, 4, GridObject.COBBLESTONE_OBJECT_ID).copy(instanceId = "stone2"), fence(5, 4),
        )
        assertEquals(EAST, maskIn(objects, 4, 4))
        assertEquals(WEST, maskIn(objects, 5, 4))
    }

    // --- Which piece a mask means ---

    @Test
    fun `every mask has a shape`() {
        val shapes = (0..15).associateWith(FenceAutotile::shapeOf)

        assertEquals(FenceShape.SINGLE, shapes[0])
        assertEquals(listOf(1, 2, 4, 8), shapes.filterValues { it == FenceShape.END }.keys.toList())
        assertEquals(listOf(5, 10), shapes.filterValues { it == FenceShape.STRAIGHT }.keys.toList())
        assertEquals(listOf(3, 6, 9, 12), shapes.filterValues { it == FenceShape.CORNER }.keys.toList())
        assertEquals(listOf(7, 11, 13, 14), shapes.filterValues { it == FenceShape.TEE }.keys.toList())
        assertEquals(FenceShape.CROSS, shapes[15])
        assertEquals(FenceShape.entries.toSet(), shapes.values.toSet())
    }

    @Test
    fun `a straight run goes north-south or east-west, a corner turns`() {
        assertEquals(FenceShape.STRAIGHT, FenceAutotile.shapeOf(NORTH or SOUTH))
        assertEquals(FenceShape.STRAIGHT, FenceAutotile.shapeOf(EAST or WEST))
        for (corner in listOf(NORTH or EAST, EAST or SOUTH, SOUTH or WEST, WEST or NORTH)) {
            assertEquals(FenceShape.CORNER, FenceAutotile.shapeOf(corner))
        }
    }

    @Test
    fun `a fence reaches out exactly towards its neighbours`() {
        assertEquals(emptyList<Int>(), FenceAutotile.arms(0))
        assertEquals(listOf(EAST), FenceAutotile.arms(EAST))
        assertEquals(listOf(NORTH, SOUTH), FenceAutotile.arms(NORTH or SOUTH))
        assertEquals(listOf(NORTH, EAST, SOUTH, WEST), FenceAutotile.arms(15))
        for (mask in 0..15) {
            assertEquals(Integer.bitCount(mask), FenceAutotile.arms(mask).size)
            assertEquals(mask, FenceAutotile.arms(mask).fold(0) { acc, direction -> acc or direction })
        }
    }

    // --- Meeting the neighbour ---

    @Test
    fun `two neighbours' arms end at the very same point`() {
        // East of (3,4) is (4,4), whose west arm comes back to meet it; likewise north and south.
        assertEquals(FenceAutotile.armEnd(3, 4, EAST), FenceAutotile.armEnd(4, 4, WEST))
        assertEquals(FenceAutotile.armEnd(4, 4, NORTH), FenceAutotile.armEnd(4, 3, SOUTH))
        for (x in 0 until 8) for (y in 0 until 8) {
            assertEquals(FenceAutotile.armEnd(x, y, EAST), FenceAutotile.armEnd(x + 1, y, WEST))
            assertEquals(FenceAutotile.armEnd(x, y, SOUTH), FenceAutotile.armEnd(x, y + 1, NORTH))
        }
    }

    @Test
    fun `an arm ends in the middle of its side of the tile`() {
        assertEquals(4.5f to 4f, FenceAutotile.armEnd(4, 4, NORTH))
        assertEquals(5f to 4.5f, FenceAutotile.armEnd(4, 4, EAST))
        assertEquals(4.5f to 5f, FenceAutotile.armEnd(4, 4, SOUTH))
        assertEquals(4f to 4.5f, FenceAutotile.armEnd(4, 4, WEST))
        assertThrows(IllegalArgumentException::class.java) { FenceAutotile.armEnd(4, 4, 3) }
    }

    @Test
    fun `arms running up the screen are drawn behind the post, the others in front`() {
        val transform = IsometricTransform(tileWidth = 128f, tileHeight = 64f, heightStep = 16f)
        val centre = transform.worldToScreen(4.5f, 4.5f)
        for (direction in FenceAutotile.DIRECTIONS) {
            val (x, y) = FenceAutotile.armEnd(4, 4, direction)
            val runsUpTheScreen = transform.worldToScreen(x, y).y < centre.y
            assertEquals("direction $direction", runsUpTheScreen, FenceAutotile.isBehindPost(direction))
        }
    }

    // --- Through the game: place, remove, put away ---

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 100)
        repeat(4) { putSlot(InventorySlot(it)) }
    }
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))

    private fun maskNow(x: Int, y: Int) = maskIn(repo.objects.values.toList(), x, y)
    private fun idAt(x: Int, y: Int) = repo.objects.values.first { it.gridX == x && it.gridY == y }.instanceId

    @Test
    fun `placing fences one after another builds a joined run with nothing else to do`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_FENCE.itemId, 4))

        place(0, 2, 2, t0)
        assertEquals(FenceShape.SINGLE, FenceAutotile.shapeOf(maskNow(2, 2)))

        place(0, 3, 2, t0)
        assertEquals(FenceShape.END, FenceAutotile.shapeOf(maskNow(2, 2)))
        assertEquals(FenceShape.END, FenceAutotile.shapeOf(maskNow(3, 2)))

        place(0, 4, 2, t0)
        assertEquals(FenceShape.STRAIGHT, FenceAutotile.shapeOf(maskNow(3, 2)))

        place(0, 4, 3, t0)
        assertEquals(FenceShape.CORNER, FenceAutotile.shapeOf(maskNow(4, 2)))
        assertEquals(WEST or SOUTH, maskNow(4, 2))

        // Every fence is stored the same way: where it is, and that it is a fence.
        assertTrue(repo.objects.values.all { it.objectId == GridObject.FENCE_OBJECT_ID })
    }

    @Test
    fun `taking a fence out of a run leaves two ends`() = runBlocking {
        listOf(2, 3, 4).forEach { repo.put(fence(it, 5)) }
        assertEquals(FenceShape.STRAIGHT, FenceAutotile.shapeOf(maskNow(3, 5)))

        assertEquals(ActionResult.SUCCESS, RemoveObjectUseCase(repo)(idAt(3, 5)))

        assertEquals(FenceShape.SINGLE, FenceAutotile.shapeOf(maskNow(2, 5)))
        assertEquals(FenceShape.SINGLE, FenceAutotile.shapeOf(maskNow(4, 5)))
        assertFalse(repo.objects.values.any { it.gridX == 3 })
    }

    @Test
    fun `a fence put back into the bag is a plain fence item, however it was joined`() = runBlocking {
        // A crossing, built partly from ids an older version stored.
        repo.put(fence(4, 4, FencePart.POST))
        repo.put(fence(4, 3, FencePart.RAIL))
        repo.put(fence(5, 4))
        repo.put(fence(4, 5))
        repo.put(fence(3, 4))
        assertEquals(FenceShape.CROSS, FenceAutotile.shapeOf(maskNow(4, 4)))
        val store = StoreObjectUseCase(repo)

        assertEquals(ActionResult.SUCCESS, store(idAt(4, 4)))
        assertEquals(ActionResult.SUCCESS, store(idAt(4, 3)))

        assertEquals(InventorySlot(0, GardenItem.STRUCT_FENCE.itemId, 2), repo.slots.getValue(0))
        // The three that are left no longer touch each other.
        for ((x, y) in listOf(5 to 4, 4 to 5, 3 to 4)) assertEquals(0, maskNow(x, y))

        // Placed again, it joins up wherever it now stands.
        assertEquals(ActionResult.SUCCESS, place(0, 4, 4, t0))
        assertEquals(EAST or SOUTH or WEST, maskNow(4, 4))
    }

    @Test
    fun `a garden read back from storage joins up exactly as it did before`() {
        val saved = listOf(fence(1, 1), fence(2, 1), fence(2, 2), fence(6, 6, FencePart.RAIL))
        val masksBefore = saved.associate { (it.gridX to it.gridY) to maskIn(saved, it.gridX, it.gridY) }

        // Stored and loaded in another order, as a database may return them.
        val loaded = saved.reversed().map { it.copy() }

        for ((cell, mask) in masksBefore) assertEquals(mask, maskIn(loaded, cell.first, cell.second))
        assertEquals(EAST, masksBefore[1 to 1])
        assertEquals(WEST or SOUTH, masksBefore[2 to 1])
        assertEquals(NORTH, masksBefore[2 to 2])
        assertEquals(0, masksBefore[6 to 6])
    }
}
