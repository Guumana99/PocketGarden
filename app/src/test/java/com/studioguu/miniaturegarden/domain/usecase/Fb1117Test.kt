package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.CreatureAction
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.Manner
import com.studioguu.miniaturegarden.domain.model.VisitRule
import kotlin.math.hypot
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WorkKind
import kotlinx.coroutines.runBlocking
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.ui.render.FenceSnow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FB1117: the rabbit, the greenhouse, the snow on the fences, the shop's scroll end, the credits. */
class Fb1117Test {

    // --- 1. The rabbit ---

    @Test
    fun `a rabbit's hop carries it half a cell at most, and the hops vary in length`() {
        assertEquals(0.5f, Manner.RABBIT_HOP_LENGTH, 0f)
        val lawn = (2..8).flatMap { x -> (2..8).map { y -> GridObject(instanceId = "l$x$y", gridX = x, gridY = y, objectId = LawnGrowth.OBJECT_ID, growthProgress = 1f, lastUpdatedTimestamp = 0L) } }
        val grid = GardenGrid(GardenMap.DEFAULT, lawn)
        val hopLengths = mutableListOf<Float>()
        for (k in 0 until 8) {
            val arrives = 1_000_000L + k * 7_919L
            for (variant in listOf(null, "white")) {
                val route = CreatureRoutes.plan(CreatureVisit(VisitRule.RABBIT_LAWN, GridCell(5, 5), null, arrives, arrives + 240_000L, variant = variant), grid)
                var start: Pair<Float, Float>? = null
                var at = 0f to 0f
                var duration = 0L
                var last = -1L
                fun close() {
                    val s = start ?: return
                    val hops = ((duration + Manner.RABBIT_HOP_MILLIS / 2) / Manner.RABBIT_HOP_MILLIS).toInt().coerceAtLeast(1)
                    val perHop = hypot(at.first - s.first, at.second - s.second) / hops
                    assertTrue("a hop of $perHop", perHop <= 0.5f + 0.02f)
                    hopLengths += perHop
                    start = null
                }
                for (pose in (arrives until arrives + 240_000L step 20L).mapNotNull { route.poseAt(it) }) {
                    if (pose.action != CreatureAction.STEP || pose.elapsed < last) close()
                    if (pose.action == CreatureAction.STEP) {
                        if (start == null) start = pose.x to pose.y
                        at = pose.x to pose.y
                        duration = pose.duration
                        last = pose.elapsed
                    } else {
                        last = -1
                    }
                }
                close()
            }
        }
        assertTrue("some hops are shorter than a quarter cell", hopLengths.any { it < 0.25f })
        assertTrue("some hops are long", hopLengths.any { it > 0.4f })
    }

    // --- 2 / 3. The greenhouse ---

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L
    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 5000, playerLevel = 30) }
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))

    @Test
    fun `cells of a greenhouse laid together are built together, ten minutes a cell and an hour at most`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.FLOOR_GREENHOUSE.itemId, 10))
        // One cell: ten minutes.
        assertEquals(ActionResult.SUCCESS, place(0, 3, 3, t0))
        assertEquals(t0 + 10 * minute, repo.objects.values.single().workEndsAt)
        // Three more joined to it, a minute apart: one group of four, forty minutes from the first.
        for ((i, cell) in listOf(3 to 4, 4 to 3, 4 to 4).withIndex()) assertEquals(ActionResult.SUCCESS, place(0, cell.first, cell.second, t0 + (i + 1) * minute))
        assertEquals(setOf(t0 + 40 * minute), repo.objects.values.map { it.workEndsAt }.toSet())
        // A cell apart from them keeps its own ten minutes.
        assertEquals(ActionResult.SUCCESS, place(0, 8, 8, t0 + 5 * minute))
        assertEquals(t0 + 15 * minute, repo.objects.values.first { it.gridX == 8 }.workEndsAt)
        // Many cells: sixty at most.
        for (x in 5..8) assertEquals(ActionResult.SUCCESS, place(0, x, 3, t0 + 6 * minute + x))
        val group = repo.objects.values.filter { it.gridY == 3 || (it.gridX in 3..4 && it.gridY == 4) }
        assertEquals(setOf(t0 + 60 * minute), group.map { it.workEndsAt }.toSet())
    }

    @Test
    fun `a greenhouse's door turns with crops in every cell, and the crops are left as they were`() = runBlocking {
        val cells = listOf(3 to 3, 3 to 4, 4 to 3, 4 to 4)
        for ((x, y) in cells) {
            repo.put(GridObject(instanceId = "g$x$y", gridX = x, gridY = y, objectId = GridObject.GREENHOUSE_OBJECT_ID, lastUpdatedTimestamp = t0))
            repo.put(GridObject(instanceId = "c$x$y", gridX = x, gridY = y, objectId = PlantSpecies.TOMATO.objectId, growthProgress = 0.4f, moistureLevel = 0.7f, lastUpdatedTimestamp = t0))
        }
        val before = repo.objects.values.filter { it.isPlant }.associateBy { it.instanceId }
        val turn = TurnGreenhouseDoorUseCase(repo, GardenMap.DEFAULT)
        assertEquals(ActionResult.SUCCESS, turn(3, 3))
        assertEquals(before, repo.objects.values.filter { it.isPlant }.associateBy { it.instanceId })
        assertTrue(repo.objects.values.filter { it.isGreenhouse }.all { it.doorSide != null })
    }

    // --- 4 / 5. The snow on the fences ---

    @Test
    fun `the snow on a fence lies thin along its top edge and hats a point, rather than heaping over it`() {
        // A 20 x 20 picture: a flat rail top at row 10, and a point rising to row 4 at column 10.
        val w = 20
        val h = 20
        val argb = IntArray(w * h)
        for (x in 0 until w) for (y in 10 until h) argb[y * w + x] = 0xFF804020.toInt()
        for (y in 4 until 10) for (x in 9..10) argb[y * w + x] = 0xFFFFFFFF.toInt()
        val look = FenceSnow.lookOf(RunKind.WHITE_FENCE, post = false)
        val snow = FenceSnow.cap(argb, w, h, look, seed = 1)
        fun alphaAt(x: Int, y: Int) = (snow[(y + FenceSnow.PAD) * w + x] ushr 24) and 0xFF
        // Over the flat part: snow just at the top edge (row 10), none far below it, in the picture.
        assertTrue(alphaAt(3, 10) > 100)
        assertEquals(0, alphaAt(3, 18))
        // Over the point: the snow stands above its tip (row 4), a hat, and not over the flat part's height.
        assertTrue(alphaAt(10, 3) > 50)
        assertEquals(0, alphaAt(3, 3))
        // It is a thin layer: no more than ~12 rows deep anywhere in a column.
        for (x in 0 until w) assertTrue((0 until h + FenceSnow.PAD).count { y -> (snow[y * w + x] ushr 24) > 0 } <= 12)
    }
}
