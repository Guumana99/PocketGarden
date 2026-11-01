package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.data.asset.GreenhouseArt
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** How greenhouse cells join up, where their crops stand and what a tap on them means. */
class GreenhouseLayoutTest {
    private fun cells(xs: IntRange, ys: IntRange): Set<GridCell> = ys.flatMap { y -> xs.map { GridCell(it, y) } }.toSet()

    private val one = setOf(GridCell(3, 3))
    private val row = cells(2..4, 3..3)
    private val two = cells(2..3, 3..4)
    private val three = cells(2..4, 3..5)
    private val ell = cells(2..2, 2..4) + cells(3..4, 4..4)
    private val shapes = listOf(one, row, two, three, ell)

    @Test
    fun `walls stand on the outline only`() {
        assertEquals(4, GreenhouseLayout.of(one).walls.size)
        assertEquals(8, GreenhouseLayout.of(row).walls.size)
        assertEquals(8, GreenhouseLayout.of(two).walls.size)
        assertEquals(12, GreenhouseLayout.of(three).walls.size)
        assertEquals(12, GreenhouseLayout.of(ell).walls.size)
        for (shape in shapes) {
            for (wall in GreenhouseLayout.of(shape).walls) {
                assertFalse(GridCell(wall.cell.x + wall.side.dx, wall.cell.y + wall.side.dy) in shape)
            }
        }
        // The middle of a three by three has none.
        assertTrue(GreenhouseLayout.of(three).wallsOf(GridCell(3, 4)).isEmpty())
    }

    @Test
    fun `a wall runs along its side of the cell, its left end first`() {
        for (wall in GreenhouseLayout.of(one).walls) {
            assertTrue(wall.from.x - wall.from.y < wall.to.x - wall.to.y)
            assertEquals(1f, (wall.to - wall.from).getDistance(), 0.001f)
            val middle = (wall.from + wall.to) / 2f
            assertEquals(3.5f + wall.side.dx * 0.5f, middle.x, 0.001f)
            assertEquals(3.5f + wall.side.dy * 0.5f, middle.y, 0.001f)
        }
    }

    @Test
    fun `a greenhouse has one door, on a side that faces the viewer, in the middle of them`() {
        for (shape in shapes) {
            val doors = GreenhouseLayout.of(shape).walls.filter { it.door }
            assertEquals(1, doors.size)
            assertTrue(doors.single().side.isFront)
        }
        val door = GreenhouseLayout.of(row).walls.single { it.door }
        assertEquals(GridCell(3, 3), door.cell)
        assertEquals(GreenhouseSide.SOUTH, door.side)
        // Two apart are two greenhouses: a door each.
        assertEquals(2, GreenhouseLayout.of(one + GridCell(5, 3)).walls.count { it.door })
        // Cells touching only at a corner do not join.
        assertEquals(2, GreenhouseLayout.of(setOf(GridCell(1, 1), GridCell(2, 2))).walls.count { it.door })
    }

    @Test
    fun `a post stands where the outline turns, and none along a straight side`() {
        assertEquals(4, GreenhouseLayout.of(one).posts.size)
        assertEquals(4, GreenhouseLayout.of(row).posts.size)
        assertEquals(4, GreenhouseLayout.of(three).posts.size)
        assertEquals(6, GreenhouseLayout.of(ell).posts.size)
        // Of a rectangle only the far corner stands behind the crops.
        assertEquals(listOf(GreenhousePost(2, 3, GridCell(2, 3), back = true)), GreenhouseLayout.of(three).posts.filter { it.back })
    }

    @Test
    fun `the ridge runs along the longer side, and rises little`() {
        fun ridgeFrames(shape: Set<GridCell>) = GreenhouseLayout.of(shape).roof.flatMap { it.frames }
            .filter { (a, b) -> a.height == b.height && a.height > GreenhouseLook.WALL_HEIGHT }
        val alongX = ridgeFrames(row).single()
        assertEquals(alongX.first.y, alongX.second.y, 0f)
        assertEquals(3f, alongX.second.x - alongX.first.x, 0f)
        val alongY = ridgeFrames(cells(2..2, 3..5)).single()
        assertEquals(alongY.first.x, alongY.second.x, 0f)
        for (shape in shapes) {
            for (piece in GreenhouseLayout.of(shape).roof) {
                assertTrue(piece.panes.flatten().all { it.height in GreenhouseLook.WALL_HEIGHT..GreenhouseLook.WALL_HEIGHT + GreenhouseLook.ROOF_MAX_RISE })
            }
        }
        assertEquals(GreenhouseLook.ROOF_MAX_RISE, GreenhouseLayout.rise(9), 0f)
    }

    @Test
    fun `the roof is frames over faint glass by default, with no crop over it`() {
        assertEquals(GreenhouseRoof.FRAMES, GreenhouseLook.ROOF)
    }

    @Test
    fun `an L is roofed all over, in two rectangles`() {
        val roof = GreenhouseLayout.of(ell).roof
        assertEquals(2, roof.size)
        for (cell in ell) {
            val covered = roof.any { piece ->
                piece.panes.flatten().let { corners ->
                    cell.x >= corners.minOf { it.x } && cell.x + 1 <= corners.maxOf { it.x } && cell.y >= corners.minOf { it.y } && cell.y + 1 <= corners.maxOf { it.y }
                }
            }
            assertTrue("$cell", covered)
        }
    }

    @Test
    fun `the far walls go behind the crop of their cell, the near ones and the roof in front`() {
        assertTrue(GreenhouseLook.BACK_WALL_DEPTH < 0f && GreenhouseLook.BACK_POST_DEPTH < 0f)
        assertTrue(GreenhouseLook.FRONT_WALL_DEPTH > 0f && GreenhouseLook.FRONT_POST_DEPTH > GreenhouseLook.FRONT_WALL_DEPTH)
        assertTrue(GreenhouseLook.ROOF_DEPTH > GreenhouseLook.FRONT_POST_DEPTH && GreenhouseLook.ROOF_DEPTH < 1f)
    }

    // --- The drawing ---

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val canvas = File(main, "java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").readText().replace("\r\n", "\n")

    @Test
    fun `nothing is laid see-through over the crops, and the old picture is not drawn`() {
        assertFalse(canvas.contains("GREENHOUSE_OVER_CROP_ALPHA"))
        assertFalse(canvas.contains("atlas[GridObject.GREENHOUSE_OBJECT_ID]"))
    }

    @Test
    fun `no crop picture reaches over the walls under glass`() {
        val manifest = File(main, "assets/atlas/assets_manifest.json").readText()
        val asset = Regex(""""assetId"\s*:\s*"(plant_[^"]+)"[^}]*?"anchorY"\s*:\s*([0-9.]+)[^}]*?"height"\s*:\s*(\d+)""")
        val tile = Regex(""""tileWidth"\s*:\s*(\d+)""").find(manifest)!!.groupValues[1].toFloat()
        val found = asset.findAll(manifest).toList()
        assertTrue(found.size > 30)
        val tallest = found.maxOf { it.groupValues[2].toFloat() * it.groupValues[3].toInt() / tile * GreenhouseLook.CROP_SCALE }
        assertTrue("$tallest", tallest <= GreenhouseLook.WALL_HEIGHT)
    }

    @Test
    fun `the walls are in the atlas, with their foot on the edge of the cell`() {
        val manifest = File(main, "assets/atlas/assets_manifest.json").readText()
        for (id in GreenhouseArt.ALL) {
            assertTrue(id, manifest.contains("\"$id\""))
            assertTrue(id, id in TileSetDefinition.expectedSpriteIds)
        }
    }

    // --- Taps ---

    private val transform = IsometricTransform(tileWidth = 100f, tileHeight = 50f, heightStep = 10f, offsetX = 500f, offsetY = 100f)
    private val map = GardenMap.allGrass(11)

    private fun at(x: Int, y: Int, id: String) =
        GridObject(instanceId = "${id}_${x}_$y", gridX = x, gridY = y, objectId = id, growthProgress = 1f, lastUpdatedTimestamp = 0L)

    private fun tap(objects: List<GridObject>, screen: Offset, editing: Boolean = false) =
        TapTargets.resolve(screen, transform, GardenGrid(map, objects), objects, editing)

    private fun greenhouseAt(vararg cells: GridCell) = cells.flatMap {
        listOf(at(it.x, it.y, GridObject.GREENHOUSE_OBJECT_ID), at(it.x, it.y, PlantSpecies.TOMATO.objectId))
    }

    /** A point on the [up] share of the height of the [side] wall of ([x], [y]), half way along it. */
    private fun onWall(x: Int, y: Int, side: GreenhouseSide, up: Float): Offset {
        val wall = GreenhouseWall(GridCell(x, y), side)
        val middle = (transform.worldToScreen(wall.from.x, wall.from.y) + transform.worldToScreen(wall.to.x, wall.to.y)) / 2f
        return middle - Offset(0f, transform.tileWidth * GreenhouseLook.WALL_HEIGHT * up)
    }

    @Test
    fun `a tap on a near wall picks the crop in its cell, wherever on the wall it lands`() {
        val garden = greenhouseAt(GridCell(4, 4))
        for (up in listOf(0.05f, 0.5f, 0.95f)) {
            assertEquals(TileCoord(4, 4), tap(garden, onWall(4, 4, GreenhouseSide.SOUTH, up)))
            assertEquals(TileCoord(4, 4), tap(garden, onWall(4, 4, GreenhouseSide.EAST, up)))
        }
    }

    @Test
    fun `a tap on the ground of a cell picks its crop, and on a far wall too`() {
        val garden = greenhouseAt(GridCell(4, 4))
        assertEquals(TileCoord(4, 4), tap(garden, transform.tileCenter(4, 4)))
        assertEquals(TileCoord(4, 4), tap(garden, onWall(4, 4, GreenhouseSide.NORTH, 0.8f)))
        assertEquals(TileCoord(4, 4), tap(garden, onWall(4, 4, GreenhouseSide.WEST, 0.8f)))
    }

    @Test
    fun `a crop a row back in a wider greenhouse is still within reach`() {
        val garden = greenhouseAt(GridCell(3, 3), GridCell(4, 3), GridCell(3, 4), GridCell(4, 4))
        assertEquals(TileCoord(3, 3), tap(garden, transform.tileCenter(3, 3)))
        assertEquals(TileCoord(4, 3), tap(garden, transform.tileCenter(4, 3)))
    }

    @Test
    fun `with a seed or the hoe in hand a tap reads the ground`() {
        val garden = greenhouseAt(GridCell(4, 4))
        assertEquals(TileCoord(3, 3), tap(garden, transform.tileCenter(3, 3), editing = true))
    }

    @Test
    fun `an old greenhouse of one cell is simply one of size one`() {
        val old = at(4, 4, GridObject.GREENHOUSE_OBJECT_ID).copy(isFlipped = true)
        assertTrue(old.isGreenhouse && old.isFloor)
        assertEquals(4, GreenhouseLayout.of(setOf(GridCell(old.gridX, old.gridY))).walls.size)
    }

    @Test
    fun `a cell of greenhouse costs what the master says, and opens at the level it did`() {
        assertEquals(1500, GardenItem.FLOOR_GREENHOUSE.price)
        assertEquals(18, GardenItem.FLOOR_GREENHOUSE.unlockLevel)
    }
}
