package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GreenhouseDoor
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide.EAST
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide.NORTH
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide.SOUTH
import com.studioguu.miniaturegarden.domain.model.GreenhouseSide.WEST
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.ui.render.GreenhouseLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which way a greenhouse's door faces, and how it is turned. */
class GreenhouseDoorTest {
    private val map = GardenMap.allGrass(11)
    private val inside: (Int, Int) -> Boolean = map::isInside

    private fun cells(xs: IntRange, ys: IntRange): Set<GridCell> = ys.flatMap { y -> xs.map { GridCell(it, y) } }.toSet()

    private fun glass(x: Int, y: Int, door: GreenhouseSide? = null) = GridObject(
        instanceId = "gh_${x}_$y", gridX = x, gridY = y, objectId = GridObject.GREENHOUSE_OBJECT_ID, lastUpdatedTimestamp = 0L, doorSide = door,
    )

    private fun garden(cells: Set<GridCell>, door: GreenhouseSide? = null) = FakeGardenRepository().apply { for (c in cells) put(glass(c.x, c.y, door)) }

    private fun doorIn(repo: FakeGardenRepository) = runBlocking { GreenhouseLayout.of(repo.getGridObjects(), inside).walls.single { it.door } }

    @Test
    fun `a door can face any way but the frame`() {
        assertEquals(GreenhouseSide.entries, GreenhouseDoor.waysOpen(cells(4..5, 4..5), inside))
        assertEquals(listOf(NORTH, SOUTH, WEST), GreenhouseDoor.waysOpen(cells(9..10, 4..5), inside))
        assertEquals(listOf(EAST, SOUTH, WEST), GreenhouseDoor.waysOpen(cells(4..5, 0..1), inside))
        assertEquals(listOf(NORTH, WEST), GreenhouseDoor.waysOpen(cells(9..10, 9..10), inside))
    }

    @Test
    fun `at first it is in the middle of the sides facing the viewer, unless that is against the frame`() {
        assertTrue(GreenhouseDoor.doorOf(cells(4..5, 4..5), null, inside).side.isFront)
        assertEquals(SOUTH, GreenhouseDoor.doorOf(cells(9..10, 4..5), null, inside).side)
        assertEquals(EAST, GreenhouseDoor.doorOf(cells(4..5, 9..10), null, inside).side)
        assertEquals(setOf(NORTH, WEST).contains(GreenhouseDoor.doorOf(cells(9..10, 9..10), null, inside).side), true)
    }

    @Test
    fun `a chosen way puts the door in the middle of that side`() {
        val door = GreenhouseDoor.doorOf(cells(3..5, 3..3), NORTH, inside)
        assertEquals(GridCell(4, 3), door.cell)
        assertEquals(NORTH, door.side)
    }

    @Test
    fun `the button goes round the ways there are, and is kept on every cell`() = runBlocking {
        val repo = garden(cells(9..10, 4..5))
        val turn = TurnGreenhouseDoorUseCase(repo, map)
        val seen = listOf(doorIn(repo).side) + (1..3).map {
            assertEquals(ActionResult.SUCCESS, turn(9, 4))
            assertEquals(1, repo.getGridObjects().map { it.doorSide }.distinct().size)
            doorIn(repo).side
        }
        assertEquals(listOf(SOUTH, WEST, NORTH, SOUTH), seen)
    }

    @Test
    fun `the door can be turned from any cell of the greenhouse, and only there`() = runBlocking {
        val repo = garden(cells(3..4, 3..3))
        val turn = TurnGreenhouseDoorUseCase(repo, map)
        assertEquals(ActionResult.SUCCESS, turn(4, 3))
        assertEquals(ActionResult.NOT_FOUND, turn(7, 7))
    }

    @Test
    fun `a greenhouse with the frame on every side has nowhere else to turn it`() {
        val all = cells(0 until 11, 0 until 11)
        assertEquals(emptyList<GreenhouseSide>(), GreenhouseDoor.waysOpen(all, inside))
        assertNull(GreenhouseDoor.nextWay(all, SOUTH, inside))
        assertTrue(GreenhouseDoor.doorOf(all, null, inside).side.isFront)
    }

    @Test
    fun `a way chosen stays when the shape allows it, and gives way to the first choice when it does not`() {
        val objects = cells(7..8, 4..5).map { glass(it.x, it.y, EAST) }
        val door = { list: List<GridObject> -> GreenhouseLayout.of(list, inside).walls.single { it.door }.side }
        assertEquals(EAST, door(objects))
        // Grown up to the frame, there is no east side left to have a door in.
        assertEquals(SOUTH, door(objects + cells(9..10, 4..5).map { glass(it.x, it.y) }))
        // A cell added on the other side changes nothing.
        assertEquals(EAST, door(objects + glass(6, 4)))
    }

    @Test
    fun `greenhouses that are joined take the first choice among them`() {
        val joined = listOf(glass(3, 3, NORTH), glass(4, 3, WEST))
        assertEquals(NORTH, GreenhouseLayout.of(joined, inside).walls.single { it.door }.side)
    }

    @Test
    fun `an old greenhouse, with nothing chosen, opens at the first choice`() {
        val old = listOf(glass(4, 4).copy(isFlipped = true))
        assertEquals(SOUTH, GreenhouseLayout.of(old, inside).walls.single { it.door }.side)
    }
}
