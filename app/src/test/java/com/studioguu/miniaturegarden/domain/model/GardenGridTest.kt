package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenGridTest {

    /** 4x4 with a river down column 2. */
    private val map = GardenMap.parse(
        "..~.",
        "..~.",
        "..~.",
        "..~.",
    )

    private fun obj(id: String, x: Int, y: Int, objectId: String = "plant_tulip") =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    // --- TileType / GardenMap ---

    @Test
    fun `map rows are gridY and characters are gridX`() {
        val m = GardenMap.parse(
            "..~",
            "...",
            "~..",
        )
        assertEquals(3, m.size)
        assertEquals(TileType.RIVER, m.tileAt(2, 0))
        assertEquals(TileType.RIVER, m.tileAt(0, 2))
        assertEquals(TileType.GRASS, m.tileAt(0, 0))
        assertEquals(listOf(GridCell(2, 0), GridCell(0, 2)), m.cellsOf(TileType.RIVER))
    }

    @Test
    fun `cells outside the map have no tile and are not buildable`() {
        for ((x, y) in listOf(-1 to 0, 0 to -1, 4 to 0, 0 to 4)) {
            assertNull(map.tileAt(x, y))
            assertFalse(map.isInside(x, y))
            assertFalse(map.isBuildable(x, y))
        }
    }

    @Test
    fun `grass can be built on and river cannot`() {
        assertTrue(map.isBuildable(1, 1))
        assertFalse(map.isBuildable(2, 1))
        assertTrue(TileType.GRASS.isBuildable)
        assertFalse(TileType.RIVER.isBuildable)
    }

    @Test
    fun `isNextTo looks at the four edge neighbours only`() {
        assertTrue(map.isNextTo(1, 1, TileType.RIVER))  // river to the right
        assertTrue(map.isNextTo(3, 0, TileType.RIVER))  // river to the left
        assertFalse(map.isNextTo(0, 1, TileType.RIVER)) // two cells away

        val corner = GardenMap.parse(
            "~..",
            "...",
            "...",
        )
        assertFalse(corner.isNextTo(1, 1, TileType.RIVER)) // diagonal does not count
        assertTrue(corner.isNextTo(1, 0, TileType.RIVER))
    }

    @Test
    fun `malformed maps are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { GardenMap.parse() }
        assertThrows(IllegalArgumentException::class.java) { GardenMap.parse("..", "...") }
        assertThrows(IllegalArgumentException::class.java) { GardenMap.parse("..", ".x") }
    }


    @Test
    fun `the default map is plain lawn the size of the garden`() {
        val m = GardenMap.DEFAULT
        assertEquals(GridObject.GRID_SIZE, m.size)
        assertTrue(m.cellsOf(TileType.RIVER).isEmpty())
        assertTrue(m.isBuildable(0, 0) && m.isBuildable(8, 8))
    }

    // --- Footprint / StructureData ---

    @Test
    fun `a footprint lists its cells from the origin towards larger x and y`() {
        assertEquals(listOf(GridCell(3, 5)), Footprint.SINGLE.cells(3, 5))
        assertEquals(
            listOf(GridCell(1, 2), GridCell(2, 2), GridCell(1, 3), GridCell(2, 3)),
            Footprint(2, 2).cells(1, 2),
        )
        // 1 wide, 2 deep.
        assertEquals(listOf(GridCell(0, 0), GridCell(0, 1)), Footprint(width = 1, height = 2).cells(0, 0))
        assertThrows(IllegalArgumentException::class.java) { Footprint(0, 1) }
    }

    @Test
    fun `structures have their own footprint and everything else covers one cell`() {
        assertEquals(Footprint(2, 2), StructureData.HUT.footprint)
        assertEquals(Footprint(1, 2), StructureData.WELL.footprint)
        assertEquals(Footprint(2, 2), Footprint.forObjectId("struct_hut"))
        assertEquals(Footprint.SINGLE, Footprint.forObjectId("plant_tulip"))
        assertEquals(Footprint.SINGLE, Footprint.forObjectId("deco_rock"))
        assertEquals(Footprint.SINGLE, Footprint.forObjectId("something_unknown"))

        assertEquals(4, obj("hut", 0, 0, "struct_hut").occupiedCells().size)
        assertEquals(listOf(GridCell(3, 1), GridCell(3, 2)), obj("well", 3, 1, "struct_well").occupiedCells())
        assertEquals(StructureData.entries.size, StructureData.entries.map { it.objectId }.toSet().size)
        assertFalse(obj("hut", 0, 0, "struct_hut").isPlant)
    }

    // --- GardenGrid: occupancy ---

    @Test
    fun `an object is found from any cell of its footprint`() {
        val hut = obj("hut", 0, 1, "struct_hut")
        val tulip = obj("tulip", 3, 3)
        val grid = GardenGrid(map, listOf(hut, tulip))

        for ((x, y) in listOf(0 to 1, 1 to 1, 0 to 2, 1 to 2)) assertSame(hut, grid.objectAt(x, y))
        assertSame(tulip, grid.objectAt(3, 3))
        assertNull(grid.objectAt(0, 0))
        assertNull(grid.objectAt(2, 1)) // river, empty
        assertNull(grid.objectAt(0, 3)) // just below the hut
    }

    // --- GardenGrid: placement ---

    @Test
    fun `a single cell can be placed on free grass only`() {
        val grid = GardenGrid(map, listOf(obj("tulip", 0, 0)))

        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 1, 0))
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, 0, 0))
        assertEquals(PlacementCheck.NOT_BUILDABLE, grid.checkPlacement(Footprint.SINGLE, 2, 3))
        assertEquals(PlacementCheck.OUT_OF_BOUNDS, grid.checkPlacement(Footprint.SINGLE, 4, 0))
        assertEquals(PlacementCheck.OUT_OF_BOUNDS, grid.checkPlacement(Footprint.SINGLE, 0, -1))
    }

    @Test
    fun `a structure needs every cell of its footprint to be free grass inside the grid`() {
        val hut = Footprint(2, 2)
        val grid = GardenGrid(map, listOf(obj("tulip", 1, 3)))

        assertEquals(PlacementCheck.OK, grid.checkPlacement(hut, 0, 0))
        assertEquals(PlacementCheck.OK, grid.checkPlacement(hut, 0, 1))
        // Only the far corner (1, 3) is taken.
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(hut, 0, 2))
        // Its right column would sit in the river.
        assertEquals(PlacementCheck.NOT_BUILDABLE, grid.checkPlacement(hut, 1, 0))
        // Starts inside, but the second row / column is off the edge.
        assertEquals(PlacementCheck.OUT_OF_BOUNDS, grid.checkPlacement(hut, 0, 3))
        assertEquals(PlacementCheck.OUT_OF_BOUNDS, grid.checkPlacement(hut, 3, 0))
    }

    @Test
    fun `footprint orientation matters`() {
        val grid = GardenGrid(map, emptyList())
        // Column 3 is one cell wide between the river and the edge.
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint(width = 1, height = 2), 3, 0))
        assertEquals(PlacementCheck.OUT_OF_BOUNDS, grid.checkPlacement(Footprint(width = 2, height = 1), 3, 0))
        assertEquals(PlacementCheck.NOT_BUILDABLE, grid.checkPlacement(Footprint(width = 2, height = 1), 1, 0))
    }

    @Test
    fun `nothing can overlap any part of an existing structure, but touching it is fine`() {
        val grid = GardenGrid(map, listOf(obj("hut", 0, 0, "struct_hut")))

        for ((x, y) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) {
            assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint.SINGLE, x, y))
        }
        assertEquals(PlacementCheck.OCCUPIED, grid.checkPlacement(Footprint(1, 2), 1, 1))
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint(2, 2), 0, 2))
        assertEquals(PlacementCheck.OK, grid.checkPlacement(Footprint.SINGLE, 3, 0))
    }

    // --- Where a tap puts a footprint ---

    @Test
    fun `a single cell goes where it is tapped`() {
        for (x in 0 until 6) for (y in 0 until 6) {
            assertEquals(GridCell(x, y), Footprint.SINGLE.originFor(x, y, gardenSize = 6))
        }
    }

    @Test
    fun `a building is fitted round the tapped cell`() {
        // 2x2: the tapped cell is the back one of the middle; 3x3: it is the middle.
        assertEquals(GridCell(2, 3), Footprint(2, 2).originFor(2, 3, gardenSize = 6))
        assertEquals(GridCell(1, 2), Footprint(3, 3).originFor(2, 3, gardenSize = 6))
        assertEquals(GridCell(2, 2), Footprint(1, 2).originFor(2, 2, gardenSize = 6))
    }

    @Test
    fun `a tap on the edge of the garden slides a building back inside, never out`() {
        val size = 6
        for (footprint in listOf(Footprint(2, 2), Footprint(1, 2), Footprint(3, 3))) {
            for (x in 0 until size) for (y in 0 until size) {
                val origin = footprint.originFor(x, y, size)
                val cells = footprint.cells(origin.x, origin.y)
                assertTrue("$footprint at ($x, $y)", cells.all { it.x in 0 until size && it.y in 0 until size })
                // The building still covers the cell that was tapped.
                assertTrue("$footprint at ($x, $y)", GridCell(x, y) in cells)
                assertEquals(PlacementCheck.OK, GardenGrid(GardenMap.allGrass(size), emptyList()).checkPlacement(footprint, origin.x, origin.y))
            }
        }
        // The far edges, where a 2x2 used to be "outside the garden".
        assertEquals(GridCell(4, 4), Footprint(2, 2).originFor(5, 5, size))
        assertEquals(GridCell(4, 0), Footprint(2, 2).originFor(5, 0, size))
        assertEquals(GridCell(0, 4), Footprint(2, 2).originFor(0, 5, size))
    }
}
