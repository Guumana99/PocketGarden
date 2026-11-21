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
}
