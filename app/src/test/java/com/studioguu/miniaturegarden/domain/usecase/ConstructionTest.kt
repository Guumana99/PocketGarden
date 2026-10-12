package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionTest {

    private val t0 = 1_700_000_000_000L
    private val second = 1_000L

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 100)
        repeat(4) { putSlot(InventorySlot(it)) }
    }
    private val record = RecordCollectionUseCase(repo)
    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, record)
    private val store = StoreObjectUseCase(repo)
    private val remove = RemoveObjectUseCase(repo)
    private val startRemoval = StartRemovalUseCase(repo, store, remove)
    private val complete = CompleteWorkUseCase(repo, store, remove)
    private val move = MoveObjectUseCase(repo, GardenMap.DEFAULT)

    private fun obj(id: String, objectId: String, x: Int = 0, y: Int = 0) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    private fun only() = repo.objects.values.single()

    // --- What takes time ---

    @Test
    fun `buildings take time to put up and half as long to take down, the rest is instant`() {
        assertEquals(300, GardenItem.STRUCT_HUT.buildSeconds)
        assertEquals(120, GardenItem.STRUCT_WELL.buildSeconds)
        assertEquals(20, GardenItem.STRUCT_FENCE.buildSeconds)
        for (item in GardenItem.entries) {
            assertEquals(item.name, item.buildSeconds / 2, item.demolishSeconds)
            if (item.category != ItemCategory.STRUCTURE) assertEquals(item.name, 0, item.buildSeconds)
        }
        // The bigger the building, the longer it takes.
        assertTrue(GardenItem.STRUCT_FENCE.buildSeconds < GardenItem.STRUCT_WELL.buildSeconds)
        assertTrue(GardenItem.STRUCT_WELL.buildSeconds < GardenItem.STRUCT_HUT.buildSeconds)
    }

    // --- Building ---

    @Test
    fun `a building is a work site from the moment it is placed`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1))

        assertEquals(ActionResult.SUCCESS, place(0, 2, 2, t0))

        val site = only()
        assertEquals(WorkKind.BUILDING, site.workKind)
        assertTrue(site.isWorking)
        assertEquals(t0, site.workStartedAt)
        assertEquals(t0 + 120 * second, site.workEndsAt)
        // It takes up its place already.
        assertEquals(ActionResult.TILE_OCCUPIED, HoeUseCase(repo, GardenMap.DEFAULT)(2, 3, t0))
    }

    @Test
    fun `things that are simply put down are there at once`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        repo.putSlot(InventorySlot(1, GardenItem.FLOOR_COBBLESTONE.itemId, 1))
        repo.putSlot(InventorySlot(2, GardenItem.SEED_TULIP.itemId, 1))
        repo.till(5, 5)

        place(0, 0, 0, t0)
        place(1, 1, 1, t0)
        place(2, 5, 5, t0)

        assertTrue(repo.objects.values.none { it.isWorking })
        assertTrue(complete(t0 + 3_600 * second).isEmpty())
    }

    @Test
    fun `progress runs from nothing to done over the building time`() {
        val site = obj("w", "struct_well").copy(workKind = WorkKind.BUILDING, workStartedAt = t0, workEndsAt = t0 + 120 * second)

        assertEquals(0f, site.workProgress(t0), 0f)
        assertEquals(0.25f, site.workProgress(t0 + 30 * second), 1e-4f)
        assertEquals(0.5f, site.workProgress(t0 + 60 * second), 1e-4f)
        assertEquals(1f, site.workProgress(t0 + 120 * second), 0f)
        // Never outside 0..1, whatever the clock says.
        assertEquals(0f, site.workProgress(t0 - 999 * second), 0f)
        assertEquals(1f, site.workProgress(t0 + 999 * second), 0f)

        assertEquals(90 * second, site.workRemainingMillis(t0 + 30 * second))
        assertEquals(0L, site.workRemainingMillis(t0 + 500 * second))
        // Something with no job on it is simply finished.
        assertEquals(1f, obj("r", "deco_rock").workProgress(t0), 0f)
        assertEquals(0L, obj("r", "deco_rock").workRemainingMillis(t0))
    }

    @Test
    fun `a building is finished when its time is up and not a moment before`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)

        assertTrue(complete(t0).isEmpty())
        assertTrue(complete(t0 + 299 * second).isEmpty())
        assertTrue(only().isWorking)

        assertEquals(listOf(FinishedWork("struct_hut", WorkKind.BUILDING)), complete(t0 + 300 * second))

        val hut = only()
        assertFalse(hut.isWorking)
        assertEquals(0L, hut.workStartedAt)
        assertEquals(0L, hut.workEndsAt)
        assertEquals(3 to 3, hut.gridX to hut.gridY)
        // Done is done: asking again finishes nothing.
        assertTrue(complete(t0 + 9_999 * second).isEmpty())
    }

    @Test
    fun `several jobs are each finished in their own time`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_FENCE.itemId, 2))
        repo.putSlot(InventorySlot(1, GardenItem.STRUCT_WELL.itemId, 1))
        place(0, 0, 0, t0)
        place(1, 4, 4, t0)
        place(0, 1, 0, t0 + 10 * second)

        assertEquals(1, complete(t0 + 20 * second).size)   // the first fence
        assertEquals(1, complete(t0 + 30 * second).size)   // the second
        assertEquals(1, repo.objects.values.count { it.isWorking })
        assertEquals(listOf(FinishedWork("struct_well", WorkKind.BUILDING)), complete(t0 + 120 * second))
        assertTrue(repo.objects.values.none { it.isWorking })
    }

    @Test
    fun `a building that was finished while away is finished on return`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)

        val finished = complete(t0 + 3 * 24 * 3_600 * second)

        assertEquals(1, finished.size)
        assertFalse(only().isWorking)
    }

    @Test
    fun `fences going up already join their neighbours`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_FENCE.itemId, 2))
        place(0, 2, 2, t0)
        place(0, 3, 2, t0)

        assertTrue(repo.objects.values.all { it.isFence && it.workKind == WorkKind.BUILDING })
        val grid = GardenGrid(GardenMap.DEFAULT, repo.objects.values.toList())
        assertTrue(grid.occupantAt(2, 2)!!.isFence && grid.occupantAt(3, 2)!!.isFence)
    }

    // --- Taking down ---

    @Test
    fun `discarding a finished building starts a demolition that takes half the building time`() = runBlocking {
        repo.put(obj("hut", "struct_hut", 2, 2))

        assertEquals(ActionResult.SUCCESS, startRemoval("hut", toBag = false, t0))

        val site = only()
        assertEquals(WorkKind.DEMOLISHING, site.workKind)
        assertEquals(t0 + 150 * second, site.workEndsAt)

        assertTrue(complete(t0 + 149 * second).isEmpty())
        assertEquals(listOf(FinishedWork("struct_hut", WorkKind.DEMOLISHING)), complete(t0 + 150 * second))
        assertTrue(repo.objects.isEmpty())
        assertTrue(repo.slots.values.all { it.isEmpty }) // thrown away, not put back
    }

    @Test
    fun `putting a finished building away packs it up and then it is in the bag`() = runBlocking {
        repo.profile = UserProfile(coins = 0, currentXp = 110, playerLevel = 2)
        repo.put(obj("well", "struct_well", 2, 2))

        assertEquals(ActionResult.SUCCESS, startRemoval("well", toBag = true, t0))
        assertEquals(WorkKind.PACKING, only().workKind)
        assertTrue(repo.slots.values.all { it.isEmpty }) // not yet
        assertEquals(110L, repo.profile?.currentXp)

        assertEquals(listOf(FinishedWork("struct_well", WorkKind.PACKING)), complete(t0 + 60 * second))

        assertTrue(repo.objects.isEmpty())
        assertEquals(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1), repo.slots.getValue(0))
        // The experience for building it goes back with it, as when anything is put away.
        assertEquals(80L, repo.profile?.currentXp)
    }

    @Test
    fun `a building still going up is simply called off, at once`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 2))
        place(0, 2, 2, t0)
        place(0, 6, 6, t0)
        val (wellA, wellB) = repo.objects.values.toList()

        assertEquals(ActionResult.SUCCESS, startRemoval(wellA.instanceId, toBag = true, t0 + 5 * second))
        assertEquals(1, repo.slots.getValue(0).amount)

        assertEquals(ActionResult.SUCCESS, startRemoval(wellB.instanceId, toBag = false, t0 + 5 * second))
        assertTrue(repo.objects.isEmpty())
        assertTrue(complete(t0 + 999 * second).isEmpty())
    }

    @Test
    fun `things that are not buildings still go at once`() = runBlocking {
        repo.till(1, 1)
        repo.put(obj("rock", "deco_rock"))
        repo.put(obj("tulip", PlantSpecies.TULIP.objectId, 1, 1))
        repo.put(obj("stone", GridObject.COBBLESTONE_OBJECT_ID, 3, 3))

        assertEquals(ActionResult.SUCCESS, startRemoval("rock", toBag = true, t0))
        assertEquals(ActionResult.SUCCESS, startRemoval("tulip", toBag = false, t0))
        assertEquals(ActionResult.SUCCESS, startRemoval("stone", toBag = false, t0))
        assertEquals(ActionResult.SUCCESS, startRemoval("soil_1_1", toBag = false, t0))

        assertTrue(repo.objects.isEmpty())
        assertEquals(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1), repo.slots.getValue(0))
    }

    @Test
    fun `a job in progress is not started a second time`() = runBlocking {
        repo.put(obj("hut", "struct_hut", 2, 2))
        startRemoval("hut", toBag = false, t0)

        assertEquals(ActionResult.BUSY, startRemoval("hut", toBag = false, t0 + second))
        assertEquals(ActionResult.BUSY, startRemoval("hut", toBag = true, t0 + second))

        assertEquals(t0 + 150 * second, only().workEndsAt) // the clock was not reset
    }

    @Test
    fun `what cannot go into the bag is refused before any work starts`() = runBlocking {
        repo.put(obj("tulip", PlantSpecies.TULIP.objectId))
        repo.put(obj("hut", "struct_hut", 4, 4))

        assertEquals(ActionResult.NOT_RETURNABLE, startRemoval("tulip", toBag = true, t0))
        assertEquals(ActionResult.NOT_FOUND, startRemoval("nothing", toBag = true, t0))

        // No room in the bag: the building is left alone rather than packed up for nothing.
        repo.profile = UserProfile(coins = 100, inventoryCapacity = 4)
        repeat(4) { repo.putSlot(InventorySlot(it, GardenItem.SEED_TULIP.itemId, InventorySlot.MAX_STACK)) }
        assertEquals(ActionResult.INVENTORY_FULL, startRemoval("hut", toBag = true, t0))
        assertFalse(repo.objects.getValue("hut").isWorking)
    }

    @Test
    fun `if the bag fills up during packing, the building stays, whole`() = runBlocking {
        repo.profile = UserProfile(coins = 100, inventoryCapacity = 1)
        repo.put(obj("well", "struct_well", 2, 2))
        startRemoval("well", toBag = true, t0)
        // Meanwhile the one free slot is taken.
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 5))

        assertTrue(complete(t0 + 60 * second).isEmpty())

        val well = repo.objects.getValue("well")
        assertFalse(well.isWorking)
        assertEquals(5, repo.slots.getValue(0).amount)
    }

    // --- Time that cannot be trusted ---

    @Test
    fun `a clock that was tampered with builds nothing - the job starts over`() = runBlocking {
        val security = Untrusted()
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, security), CalculateOfflineProgressUseCase())
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)

        // The clock jumps a day ahead.
        val later = t0 + 24 * 3_600 * second
        assertFalse(sync(later, WeatherType.SUNNY).isTrusted)

        val site = only()
        assertEquals(WorkKind.BUILDING, site.workKind)
        assertEquals(later, site.workStartedAt)
        assertEquals(later + 300 * second, site.workEndsAt) // the whole building time again
        assertTrue(complete(later).isEmpty())
        assertEquals(1, complete(later + 300 * second).size)
    }

    @Test
    fun `an honest sync leaves the job's clock alone`() = runBlocking {
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, Trusted()), CalculateOfflineProgressUseCase())
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1))
        place(0, 3, 3, t0)

        assertTrue(sync(t0 + 50 * second, WeatherType.SUNNY).isTrusted)

        assertEquals(t0, only().workStartedAt)
        assertEquals(t0 + 120 * second, only().workEndsAt)
    }

    // --- Moving ---

    @Test
    fun `a building moves in one step, without going through the bag`() = runBlocking {
        repo.put(obj("hut", "struct_hut", 1, 1))

        assertEquals(ActionResult.SUCCESS, move("hut", 5, 6))

        val hut = only()
        assertEquals(5 to 6, hut.gridX to hut.gridY)
        assertEquals(obj("hut", "struct_hut", 5, 6), hut) // nothing else about it changed
        assertFalse(hut.isWorking)                          // and it is not built again
        assertTrue(repo.slots.values.all { it.isEmpty })
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `a building may be moved onto ground it partly stands on already`() = runBlocking {
        // A 2x2 hut at (2,2) slides one cell: three of the four new cells are its own old ones.
        repo.put(obj("hut", "struct_hut", 2, 2))

        assertEquals(ActionResult.SUCCESS, move("hut", 3, 2))
        assertEquals(ActionResult.SUCCESS, move("hut", 3, 3))
        assertEquals(3 to 3, only().let { it.gridX to it.gridY })
        // Moving it to where it is changes nothing and is not an error.
        assertEquals(ActionResult.SUCCESS, move("hut", 3, 3))
    }

    @Test
    fun `the new place must be free by the rules of placing`() = runBlocking {
        repo.put(obj("hut", "struct_hut", 0, 0))
        repo.put(obj("rock", "deco_rock", 5, 5))
        repo.put(obj("river", GridObject.RIVER_OBJECT_ID, 7, 2))
        repo.put(obj("stone", GridObject.COBBLESTONE_OBJECT_ID, 3, 7))

        assertEquals(ActionResult.TILE_OCCUPIED, move("hut", 4, 4))       // its far corner hits the rock
        assertEquals(ActionResult.TILE_OCCUPIED, move("hut", 5, 5))
        assertEquals(ActionResult.TILE_NOT_BUILDABLE, move("hut", 6, 2)) // into the river
        assertEquals(ActionResult.OUT_OF_BOUNDS, move("hut", 8, 8))       // half off the garden
        assertEquals(ActionResult.OUT_OF_BOUNDS, move("hut", -1, 0))
        assertEquals(0 to 0, repo.objects.getValue("hut").let { it.gridX to it.gridY })

        // Onto paving is fine, as it is when placing.
        assertEquals(ActionResult.SUCCESS, move("rock", 3, 7))
        // Paving itself moves in its own layer: under the rock is taken (by itself), elsewhere free.
        assertEquals(ActionResult.SUCCESS, move("stone", 4, 7))
        assertEquals(ActionResult.SUCCESS, move("river", 8, 8))
    }

    @Test
    fun `plants and beds stay in the ground, and work sites stay put`() = runBlocking {
        repo.till(1, 1)
        repo.put(obj("tulip", PlantSpecies.TULIP.objectId, 1, 1))
        repo.put(obj("site", "struct_well", 5, 5).copy(workKind = WorkKind.BUILDING, workStartedAt = t0, workEndsAt = t0 + 120 * second))

        assertEquals(ActionResult.NOT_MOVABLE, move("tulip", 3, 3))
        assertEquals(ActionResult.NOT_MOVABLE, move("soil_1_1", 3, 3))
        assertEquals(ActionResult.BUSY, move("site", 0, 0))
        assertEquals(ActionResult.NOT_FOUND, move("nothing", 0, 0))

        assertFalse(MoveObjectUseCase.canMove(obj("t", PlantSpecies.ROSE.objectId)))
        assertTrue(MoveObjectUseCase.canMove(obj("f", GridObject.FENCE_OBJECT_ID)))
        assertTrue(MoveObjectUseCase.canMove(obj("h", "struct_hut")))
    }

    @Test
    fun `a moved fence leaves its old neighbours and joins its new ones`() = runBlocking {
        listOf(2, 3, 4).forEach { repo.put(obj("f$it", GridObject.FENCE_OBJECT_ID, it, 2)) }
        repo.put(obj("lone", GridObject.FENCE_OBJECT_ID, 6, 6))

        assertEquals(ActionResult.SUCCESS, move("f3", 6, 5))

        val grid = GardenGrid(GardenMap.DEFAULT, repo.objects.values.toList())
        assertNull(grid.occupantAt(3, 2))
        assertTrue(grid.occupantAt(6, 5)!!.isFence && grid.occupantAt(6, 6)!!.isFence)
    }

    // --- Storage ---

    @Test
    fun `a job survives being stored and read back`() {
        val site = obj("w", "struct_well", 2, 3).copy(workKind = WorkKind.PACKING, workStartedAt = t0, workEndsAt = t0 + 60 * second)

        assertEquals(site, site.toEntity().toDomain())
        assertEquals("PACKING", site.toEntity().workKind)
        assertEquals(obj("r", "deco_rock"), obj("r", "deco_rock").toEntity().toDomain())
        assertEquals("NONE", obj("r", "deco_rock").toEntity().workKind)
    }

    @Test
    fun `a job name this version does not know counts as no job`() {
        assertEquals(WorkKind.NONE, WorkKind.fromName("RENOVATING"))
        assertEquals(WorkKind.NONE, WorkKind.fromName(""))
        for (kind in WorkKind.entries) assertEquals(kind, WorkKind.fromName(kind.name))
    }

    private class Trusted : SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = true
        override fun isTimeTampered(profile: UserProfile) = false
        override fun isTamperFlagged() = false
        override fun setTamperFlag(flagged: Boolean) = Unit
    }

    private class Untrusted : SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = true
        override fun isTimeTampered(profile: UserProfile) = true
        override fun isTamperFlagged() = true
        override fun setTamperFlag(flagged: Boolean) = Unit
    }
}
