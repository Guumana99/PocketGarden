package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.Footprint
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.LawnGrowth
import com.studioguu.miniaturegarden.domain.model.PlacementCheck
import com.studioguu.miniaturegarden.domain.model.PlacementKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.Sprinkler
import com.studioguu.miniaturegarden.domain.model.StructureEffect
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.ui.render.SoilJoins
import com.studioguu.miniaturegarden.ui.render.SoilTexture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the grounds meet, and the rocks that stand on a lawn (device feedback of 11/1). */
class GroundsAndRocksTest {
    private val map = GardenMap.allGrass()
    private val rocks = GardenItem.ROCK_KINDS.map { it.objectId }

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    // --- Beds and what lies beside them ---

    @Test
    fun `a bed runs right up to whatever is laid on the ground beside it`() {
        val grid = GardenGrid(
            map,
            listOf(
                at(1, 0, LawnGrowth.OBJECT_ID),
                at(2, 0, GridObject.COBBLESTONE_OBJECT_ID),
                at(3, 0, GridObject.WOOD_DECK_OBJECT_ID),
                at(4, 0, "ground_pebble"),
                at(5, 0, "ground_flowers"),
                at(6, 0, GridObject.TILLED_SOIL_OBJECT_ID),
                at(7, 0, GridObject.LOOSE_SOIL_OBJECT_ID),
            ),
        )
        for (x in 1..7) assertTrue("x = $x", grid.meetsBed(x, 0))
    }

    @Test
    fun `open earth, water and the garden's edge leave a bed its own rim`() {
        val grid = GardenGrid(map, listOf(at(1, 0, GridObject.RIVER_OBJECT_ID), at(2, 0, GridObject.POND_OBJECT_ID)))
        assertFalse(grid.meetsBed(0, 0))
        assertFalse(grid.meetsBed(1, 0))
        assertFalse(grid.meetsBed(2, 0))
        assertFalse(grid.meetsBed(-1, 0))
        assertFalse(grid.meetsBed(0, map.size))
    }

    @Test
    fun `what stands on a lawn does not leave a gap beside the bed`() {
        val grid = GardenGrid(map, listOf(at(1, 0, LawnGrowth.OBJECT_ID), at(1, 0, "deco_rock"), at(2, 0, GridObject.COBBLESTONE_OBJECT_ID), at(2, 0, "deco_bench")))
        assertTrue(grid.meetsBed(1, 0))
        assertTrue(grid.meetsBed(2, 0))
    }

    @Test
    fun `the side of a bed beside a lawn lies on the edge of its cell, with no earth spilled over it`() {
        val grid = GardenGrid(map, listOf(at(4, 3, LawnGrowth.OBJECT_ID), at(3, 3, GridObject.LOOSE_SOIL_OBJECT_ID)))
        val joins = SoilJoins.at(3, 3, grid::meetsBed)
        assertEquals(SoilJoins(east = true), joins)
        assertTrue(SoilTexture.outline(3, 3, joins).subList(5, 10).all { it.first == 4f })
        assertTrue(SoilTexture.drift(3, 3, joins).none { it.x > 4f })
    }

    // --- Rocks ---

    @Test
    fun `every rock may stand on a lawn, and a lawn be sown under it`() {
        assertEquals(5, rocks.size)
        for (rock in rocks) {
            val lawn = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
            assertEquals(rock, PlacementCheck.OK, lawn.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, rock))
            assertEquals(rock, ActionResult.SUCCESS, lawn.fits(rock, 3, 3))
            val standing = GardenGrid(map, listOf(at(3, 3, rock)))
            assertEquals(rock, PlacementCheck.OK, standing.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, LawnGrowth.OBJECT_ID))
        }
    }

    @Test
    fun `a rock on a lawn is what stands there, with the lawn under it`() {
        val grid = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID), at(3, 3, "deco_rock")))
        assertEquals("deco_rock", grid.occupantAt(3, 3)?.objectId)
        assertEquals("deco_rock", grid.objectAt(3, 3)?.objectId)
        assertNotNull(grid.lawnAt(3, 3))
    }

    @Test
    fun `two rocks do not share a cell, nor a hut a lawn`() {
        val rock = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID), at(3, 3, "deco_rock")))
        assertEquals(PlacementCheck.OCCUPIED, rock.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, "deco_rock_flat"))
        val lawn = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
        assertEquals(PlacementCheck.OCCUPIED, lawn.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, GardenItem.STRUCT_HUT.objectId))
    }

    @Test
    fun `a rock put on a lawn leaves the lawn behind when it goes`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 100)
            put(at(3, 3, LawnGrowth.OBJECT_ID))
            putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        }
        assertEquals(ActionResult.SUCCESS, PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))(0, 3, 3, 0L))
        val rock = repo.objects.values.first { it.objectId == "deco_rock" }
        assertEquals(setOf("deco_rock", LawnGrowth.OBJECT_ID), repo.getGridObjects().map { it.objectId }.toSet())

        // As read back from the save: the two stand apart, each with its own record.
        val saved = GardenGrid(map, repo.getGridObjects())
        assertEquals(rock.instanceId, saved.occupantAt(3, 3)?.instanceId)
        assertNotNull(saved.lawnAt(3, 3))

        assertEquals(ActionResult.SUCCESS, RemoveObjectUseCase(repo)(rock.instanceId))
        val left = GardenGrid(map, repo.getGridObjects())
        assertNotNull(left.lawnAt(3, 3))
        assertEquals(LawnGrowth.OBJECT_ID, left.occupantAt(3, 3)?.objectId)
    }

    @Test
    fun `a rock can be moved from one lawn to another and leaves both as they were`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            put(at(3, 3, LawnGrowth.OBJECT_ID))
            put(at(5, 5, LawnGrowth.OBJECT_ID))
            put(at(3, 3, "deco_rock"))
        }
        val move = MoveObjectUseCase(repo, map, CalculateOfflineProgressUseCase())
        assertEquals(ActionResult.SUCCESS, move("deco_rock_3_3", 5, 5))
        val grid = GardenGrid(map, repo.getGridObjects())
        assertEquals("deco_rock", grid.occupantAt(5, 5)?.objectId)
        assertNotNull(grid.lawnAt(3, 3))
        assertNotNull(grid.lawnAt(5, 5))
        assertNull(grid.occupantAt(4, 4))
    }

    // --- The small ornaments, which stand on a lawn as a rock does ---

    private val ornaments = listOf(GardenItem.DECO_BENCH, GardenItem.DECO_LANTERN, GardenItem.STRUCT_WELL, GardenItem.DECO_SPRINKLER, GardenItem.DECO_WEATHERVANE)

    @Test
    fun `each small ornament may stand on a lawn, and a lawn be sown under it`() {
        for (item in ornaments) {
            val thing = item.objectId
            val lawn = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
            assertEquals(thing, PlacementCheck.OK, lawn.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, thing))
            assertEquals(thing, ActionResult.SUCCESS, lawn.fits(thing, 3, 3))
            val standing = GardenGrid(map, listOf(at(3, 3, thing)))
            assertEquals(thing, PlacementCheck.OK, standing.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, LawnGrowth.OBJECT_ID))
        }
    }

    @Test
    fun `the buildings and the shiitake log stay off a lawn, and a lawn stays out from under them`() {
        val lawn = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID)))
        for (thing in listOf(GardenItem.STRUCT_HUT, GardenItem.STRUCT_COTTAGE, GardenItem.STRUCT_WINDMILL, GardenItem.SEED_SHIITAKE).map { it.objectId }) {
            assertFalse(thing, GardenGrid.sharesCellWithLawn(thing))
            assertTrue(thing, lawn.fits(thing, 3, 3) != ActionResult.SUCCESS)
            val standing = GardenGrid(map, listOf(at(3, 3, thing)))
            assertEquals(thing, PlacementCheck.OCCUPIED, standing.checkPlacement(Footprint.SINGLE, 3, 3, PlacementKind.OBJECT, LawnGrowth.OBJECT_ID))
        }
    }

    @Test
    fun `an ornament goes on a lawn, moves, goes back to the bag and is cleared, and the lawn stays each time`() = runBlocking {
        for (item in ornaments) {
            val repo = FakeGardenRepository().apply {
                profile = UserProfile(coins = 100)
                put(at(3, 3, LawnGrowth.OBJECT_ID))
                put(at(5, 5, LawnGrowth.OBJECT_ID))
                putSlot(InventorySlot(0, item.itemId, 2))
            }
            val name = item.objectId
            assertEquals(name, ActionResult.SUCCESS, PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))(0, 3, 3, 0L))
            fun thing() = repo.objects.values.first { it.objectId == item.objectId }
            // A well goes up over its building time; the rest are there at once.
            repo.put(thing().withoutWork())

            // As read back from the save: the two stand apart, each with its own record.
            val saved = GardenGrid(map, repo.getGridObjects())
            assertEquals(name, thing().instanceId, saved.occupantAt(3, 3)?.instanceId)
            assertNotNull(name, saved.lawnAt(3, 3))

            val move = MoveObjectUseCase(repo, map, CalculateOfflineProgressUseCase())
            assertEquals(name, ActionResult.SUCCESS, move(thing().instanceId, 5, 5))
            val moved = GardenGrid(map, repo.getGridObjects())
            assertEquals(name, LawnGrowth.OBJECT_ID, moved.occupantAt(3, 3)?.objectId)
            assertEquals(name, item.objectId, moved.occupantAt(5, 5)?.objectId)
            assertNotNull(name, moved.lawnAt(5, 5))

            assertEquals(name, ActionResult.SUCCESS, StoreObjectUseCase(repo)(thing().instanceId))
            assertEquals(name, LawnGrowth.OBJECT_ID, GardenGrid(map, repo.getGridObjects()).occupantAt(5, 5)?.objectId)
            assertEquals(name, 2, repo.getInventory().first { it.itemId == item.itemId }.amount)

            assertEquals(name, ActionResult.SUCCESS, PlaceItemUseCase(repo, map, RecordCollectionUseCase(repo))(0, 5, 5, 0L))
            assertEquals(name, ActionResult.SUCCESS, RemoveObjectUseCase(repo)(thing().instanceId))
            val left = GardenGrid(map, repo.getGridObjects())
            assertEquals(name, LawnGrowth.OBJECT_ID, left.occupantAt(5, 5)?.objectId)
            assertEquals(name, LawnGrowth.OBJECT_ID, left.occupantAt(3, 3)?.objectId)
        }
    }

    @Test
    fun `creatures walk round an ornament on a lawn, and a crop stays out of its cell`() {
        val grid = GardenGrid(map, listOf(at(3, 3, LawnGrowth.OBJECT_ID), at(3, 3, "deco_bench"), at(4, 3, LawnGrowth.OBJECT_ID)))
        assertFalse(CreatureRoutes.canWalk(Creature.SQUIRREL, grid, 3, 3))
        assertTrue(CreatureRoutes.canWalk(Creature.SQUIRREL, grid, 4, 3))
        assertEquals(ActionResult.NO_ROOM_TO_PLANT, grid.fits(PlantSpecies.TULIP.objectId, 3, 3))
    }

    @Test
    fun `a sprinkler on a lawn waters the same ring as on bare ground, and only the beds in it`() {
        val sprinkler = at(3, 3, Sprinkler.OBJECT_ID)
        val inReach = listOf(at(2, 2, PlantSpecies.TULIP.objectId), at(3, 4, PlantSpecies.TULIP.objectId), at(4, 3, PlantSpecies.TULIP.objectId))
        val outOfReach = listOf(at(5, 3, PlantSpecies.TULIP.objectId), at(3, 1, PlantSpecies.TULIP.objectId))
        val plants = inReach + outOfReach
        for (lawned in listOf(false, true)) {
            val garden = plants + sprinkler + if (lawned) listOf(at(3, 3, LawnGrowth.OBJECT_ID)) else emptyList()
            for (plant in inReach) assertTrue(Sprinkler.waters(plant, garden))
            for (plant in outOfReach) assertFalse(Sprinkler.waters(plant, garden))
            assertEquals(Sprinkler.MOISTURE_DRAIN_FACTOR, PlantSurroundings.of(inReach.first(), garden).moistureDrainFactor, 0f)
            // Switched off, it does nothing.
            val off = plants + sprinkler.copy(isOn = false)
            assertFalse(Sprinkler.waters(inReach.first(), off))
        }
    }

    @Test
    fun `a well on a lawn keeps the soil round it damp once it is finished`() {
        val well = at(3, 3, GardenItem.STRUCT_WELL.objectId)
        val plant = at(4, 3, PlantSpecies.TULIP.objectId)
        val garden = listOf(at(3, 3, LawnGrowth.OBJECT_ID), well, plant)
        assertEquals(StructureEffect.WELL.moistureDrainFactor, PlantSurroundings.of(plant, garden).moistureDrainFactor, 0f)
    }
}
