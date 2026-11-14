package com.studioguu.miniaturegarden.domain.model

import com.studioguu.miniaturegarden.data.asset.CropScale
import com.studioguu.miniaturegarden.domain.usecase.CalculateOfflineProgressUseCase
import com.studioguu.miniaturegarden.ui.render.EdgeSnow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FB1114: the book's 60 put right (the white rabbit called, moss growing in, the crops' sizes, the snow). */
class Fb1114Test {

    private val map = GardenMap.DEFAULT
    private val t0 = 1_000_000L * CreatureCalls.ROUND_MILLIS

    @Test
    fun `the white rabbit chosen alone calls the rabbit, and it comes white every time`() {
        val call = CreatureCall(on = true).toggledWhiteRabbit()
        assertEquals(listOf(Creature.RABBIT), call.creatures)
        assertTrue(call.whiteRabbit)
        val objects = CreatureCalls.thingsFor(Creature.RABBIT, CreatureCalls.freeCell(emptyList(), map)!!, t0)
        for (round in 0 until 20L) {
            val visits = CreatureCalls.visitsAt(t0 + round * CreatureCalls.ROUND_MILLIS, call.creatures, objects, map, whiteRabbit = call.whiteRabbit)
            assertTrue(visits.isNotEmpty())
            assertTrue(visits.all { it.variant == CreatureVisits.WHITE_COAT })
        }
        // Let go, the rabbit stays called in its usual coat; the rabbit let go takes the white with it.
        assertEquals(CreatureCall(listOf(Creature.RABBIT), on = true), call.toggledWhiteRabbit())
        assertFalse(call.toggled(Creature.RABBIT).whiteRabbit)
        // With the rabbit already chosen, it is only made white.
        assertEquals(listOf(Creature.RABBIT), CreatureCall(listOf(Creature.RABBIT)).toggledWhiteRabbit().creatures)
    }

    @Test
    fun `moss grows in as a lawn does, over the same time, and old moss stays grown`() {
        val moss = GridObject(instanceId = "m", gridX = 3, gridY = 4, objectId = LawnGrowth.MOSS_OBJECT_ID, lastUpdatedTimestamp = 0L)
        val lawn = moss.copy(instanceId = "l", objectId = LawnGrowth.OBJECT_ID)
        assertTrue(LawnGrowth.grows(moss))
        val useCase = CalculateOfflineProgressUseCase()
        for (hours in listOf(1L, 12L, 24L, 47L, 48L, 60L)) {
            val now = hours * 3_600_000L
            assertEquals("after $hours h", useCase(lawn, now).growthProgress, useCase(moss, now).growthProgress, 0f)
        }
        assertEquals(1f, useCase(moss, (LawnGrowth.HOURS_TO_FULL * 3_600_000L).toLong()).growthProgress, 0f)
        // The debug call sets down moss grown, for the snail.
        assertEquals(1f, CreatureCalls.thingsFor(Creature.SNAIL, GridCell(5, 5), t0).single().growthProgress, 0f)
    }

    @Test
    fun `growing moss shows a few thin patches at first, spreading until it covers its cell`() {
        val first = MossPatches.at(4, 6, 0f)
        assertTrue(first.isNotEmpty() && first.size < MossPatches.SIDE * MossPatches.SIDE)
        var count = first.size
        var reach = first.sumOf { it.radius.toDouble() }
        for (g in listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f)) {
            val patches = MossPatches.at(4, 6, g)
            assertTrue(patches.size >= count)
            val r = patches.sumOf { it.radius.toDouble() }
            assertTrue(r > reach)
            count = patches.size
            reach = r
        }
        assertEquals(MossPatches.SIDE * MossPatches.SIDE, MossPatches.at(4, 6, 0.5f).size)
        // Grown, every point of the cell is within one of its patches.
        val grown = MossPatches.at(4, 6, 1f)
        for (i in 0..10) for (j in 0..10) {
            val u = i / 10f
            val v = j / 10f
            assertTrue("($u, $v)", grown.any { (it.u - u) * (it.u - u) + (it.v - v) * (it.v - v) <= it.radius * it.radius })
        }
        // Each cell its own, and always the same.
        assertEquals(MossPatches.at(1, 2, 0.3f), MossPatches.at(1, 2, 0.3f))
        assertFalse(MossPatches.at(1, 2, 0.3f) == MossPatches.at(2, 1, 0.3f))
        assertTrue(MossPatches.thickness(0f) < MossPatches.thickness(0.5f) && MossPatches.thickness(1f) == 1f)
    }

    @Test
    fun `the sweet potato, the ornamental cabbage and the narcissus are drawn smaller`() {
        assertEquals(0.85f, CropScale.SHRINKS.getValue("plant_satsumaimo"), 0f)
        assertEquals(0.8f, CropScale.SHRINKS.getValue("plant_habotan"), 0f)
        assertEquals(0.85f, CropScale.SHRINKS.getValue("plant_suisen"), 0f)
    }

    @Test
    fun `the turnip stands on the foot of the earth heaped round it`() {
        val overrides = listOf(File("../tools/atlas_overrides.json"), File("tools/atlas_overrides.json")).first { it.isFile }.readText()
        for (id in listOf("plant_kabu_stage2", "plant_kabu_stage3", "plant_kabu_withered")) {
            assertTrue(id, Regex(""""$id": \{"anchorX": [0-9.]+, "anchorY": 0\.9[0-9]+""").containsMatchIn(overrides))
        }
    }

    @Test
    fun `no heap of snow stands out along the cottage's eave, only along its ridge`() {
        assertEquals(1, EdgeSnow.linesOf(StructureData.COTTAGE.objectId, flipped = false)!!.size)
    }
}
