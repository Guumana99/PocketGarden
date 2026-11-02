package com.studioguu.miniaturegarden.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debug menu's "call the creatures": who comes, when, and what the garden gets for them. */
class CreatureCallTest {

    private val map = GardenMap.DEFAULT
    private val t0 = 1_000_000L * CreatureCalls.ROUND_MILLIS

    private fun at(x: Int, y: Int, objectId: String, growth: Float = 0f) =
        GridObject(instanceId = "$objectId-$x-$y", gridX = x, gridY = y, objectId = objectId, growthProgress = growth, lastUpdatedTimestamp = t0)

    private fun withThingsFor(creature: Creature): List<GridObject> =
        CreatureCalls.thingsFor(creature, CreatureCalls.freeCell(emptyList(), map)!!, t0)

    @Test
    fun `one or two can be chosen, and a third takes the place of the oldest`() {
        val two = CreatureCall().toggled(Creature.FROG).toggled(Creature.BIRD)
        assertEquals(listOf(Creature.FROG, Creature.BIRD), two.creatures)
        assertEquals(listOf(Creature.BIRD, Creature.SQUIRREL), two.toggled(Creature.SQUIRREL).creatures)
        assertEquals(listOf(Creature.BIRD), two.toggled(Creature.FROG).creatures)
    }

    @Test
    fun `nobody is called with the switch off or with nobody chosen`() {
        assertFalse(CreatureCall(listOf(Creature.FROG), on = false).calling)
        assertFalse(CreatureCall(emptyList(), on = true).calling)
        assertTrue(CreatureCall(listOf(Creature.FROG), on = true).calling)
    }

    @Test
    fun `whatever is called has a thing of its own to come to, in any weather, season or hour`() {
        for (creature in Creature.entries) {
            val objects = withThingsFor(creature)
            val visit = CreatureCalls.visitsAt(t0, listOf(creature), objects, map).single()
            assertEquals(creature, visit.creature)
            assertTrue(visit.isPresentAt(t0 + CreatureCalls.ARRIVES_AFTER_MILLIS))
        }
    }

    @Test
    fun `a garden with nothing for it sees nobody until the thing is set down`() {
        for (creature in Creature.entries) assertTrue(CreatureCalls.visitsAt(t0, listOf(creature), emptyList(), map).isEmpty())
    }

    @Test
    fun `when it has gone it comes back in the next round`() {
        val objects = withThingsFor(Creature.BIRD)
        val first = CreatureCalls.visitsAt(t0, listOf(Creature.BIRD), objects, map).single()
        val second = CreatureCalls.visitsAt(t0 + CreatureCalls.ROUND_MILLIS, listOf(Creature.BIRD), objects, map).single()
        assertFalse(first.isPresentAt(first.leavesAt))
        assertTrue(first.leavesAt < second.arrivesAt)
        assertEquals(CreatureCalls.ROUND_MILLIS, second.arrivesAt - first.arrivesAt)
    }

    @Test
    fun `two of them never take the same spot`() {
        val objects = listOf(at(5, 5, GridObject.POND_OBJECT_ID))
        val visits = CreatureCalls.visitsAt(t0, listOf(Creature.FROG, Creature.DRAGONFLY), objects, map)
        assertEquals(2, visits.size)
        assertEquals(2, visits.map { it.cell }.toSet().size)
    }

    @Test
    fun `one called comes at once and stays a whole round, however late in the clock's round`() {
        val objects = withThingsFor(Creature.FROG)
        val since = t0 + CreatureCalls.ROUND_MILLIS - 2_000L
        val visit = CreatureCalls.visitsAt(since, listOf(Creature.FROG), objects, map, since).single()
        assertEquals(since + CreatureCalls.ARRIVES_AFTER_MILLIS, visit.arrivesAt)
        assertEquals(since + CreatureCalls.ROUND_MILLIS - CreatureCalls.AWAY_MILLIS, visit.leavesAt)
        assertEquals(1L, CreatureCalls.roundOf(since + CreatureCalls.ROUND_MILLIS, since))
    }

    @Test
    fun `a garden laid with lawn has room, and a frog comes to water with lawn round it`() {
        val lawn = (0 until map.size).flatMap { y -> (0 until map.size).map { x -> at(x, y, "ground_grass", growth = 1f) } }
        val cell = CreatureCalls.freeCell(lawn, map)
        assertEquals(GridCell(5, 5), cell)
        val taken = CreatureCalls.coverAt(cell!!, lawn)
        assertEquals(1, taken.size)
        val objects = lawn - taken.toSet() + CreatureCalls.thingsFor(Creature.FROG, cell, t0)
        val visit = CreatureCalls.visitsAt(t0, listOf(Creature.FROG), objects, map).single()
        assertTrue(visit.cell != cell)
    }

    @Test
    fun `things are set down on a free cell near the middle, with room beside it`() {
        assertEquals(GridCell(5, 5), CreatureCalls.freeCell(emptyList(), map))
        val taken = CreatureCalls.freeCell(listOf(at(5, 5, "deco_bench")), map)
        assertNotNull(taken)
        assertFalse(taken == GridCell(5, 5))
        // On bare ground only: not on a bed, which is someone's crop.
        assertFalse(CreatureCalls.freeCell(listOf(at(5, 5, GridObject.TILLED_SOIL_OBJECT_ID)), map) == GridCell(5, 5))
        val full = (0 until map.size).flatMap { y -> (0 until map.size).map { x -> at(x, y, "deco_bench") } }
        assertNull(CreatureCalls.freeCell(full, map))
    }

    @Test
    fun `a crop set down for a creature stands grown on its own bed`() {
        val things = withThingsFor(Creature.BUTTERFLY)
        assertTrue(things.any { it.isFloor })
        assertEquals(1f, things.single { it.isPlant }.growthProgress, 0f)
    }
}
