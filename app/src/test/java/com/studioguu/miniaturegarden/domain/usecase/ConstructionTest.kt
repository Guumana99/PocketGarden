package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.LevelTable
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
    private val move = MoveObjectUseCase(repo, GardenMap.DEFAULT, CalculateOfflineProgressUseCase())
    private val unveilBuilding = UnveilBuildingUseCase(repo, RecordCollectionUseCase(repo))
    private suspend fun unveil(id: String, now: Long = t0) = unveilBuilding(id, now)

    private fun obj(id: String, objectId: String, x: Int = 0, y: Int = 0) =
        GridObject(instanceId = id, gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = t0)

    private fun only() = repo.objects.values.single()

    /** The building and taking-down times the tests run on, read from the item master. */
    private val wellBuild = GardenItem.STRUCT_WELL.buildSeconds * second
    private val hutBuild = GardenItem.STRUCT_HUT.buildSeconds * second
    private val wellDown = GardenItem.STRUCT_WELL.demolishSeconds * second
    private val hutDown = GardenItem.STRUCT_HUT.demolishSeconds * second

    // --- What takes time ---

    @Test
    fun `buildings take time to put up and a short while to take down, the rest is instant`() {
        assertEquals(20, GardenItem.STRUCT_FENCE.buildSeconds)
        assertEquals(30 * 60, GardenItem.STRUCT_WELL.buildSeconds)
        assertEquals(45 * 60, GardenItem.STRUCT_WINDMILL.buildSeconds)
        assertEquals(60 * 60, GardenItem.STRUCT_HUT.buildSeconds)
        assertEquals(2 * 60 * 60, GardenItem.STRUCT_COTTAGE.buildSeconds)
        for (item in GardenItem.entries) {
            // Half the building time, but taking down is a chore: five minutes at most.
            assertEquals(item.name, minOf(item.buildSeconds / 2, 5 * 60), item.demolishSeconds)
            if (item.category != ItemCategory.STRUCTURE) assertEquals(item.name, 0, item.buildSeconds)
        }
        assertEquals(10, GardenItem.STRUCT_FENCE.demolishSeconds)
        assertEquals(5 * 60, GardenItem.STRUCT_COTTAGE.demolishSeconds)
        // The bigger the building, the longer it takes. (Since the progression change the cottage,
        // at Lv.3, costs less than the windmill and the hut that come later.)
        val buildings = listOf(GardenItem.STRUCT_FENCE, GardenItem.STRUCT_WELL, GardenItem.STRUCT_WINDMILL, GardenItem.STRUCT_HUT, GardenItem.STRUCT_COTTAGE)
        assertEquals(buildings, buildings.sortedBy { it.buildSeconds })
    }

    @Test
    fun `no building keeps the player waiting longer than an afternoon, or as long as a crop`() {
        val shortestCrop = com.studioguu.miniaturegarden.domain.model.PlantSpecies.entries.minOf { it.growthHours } * 3_600_000L
        for (item in GardenItem.entries.filter { it.buildSeconds > 0 }) {
            assertTrue(item.name, item.buildSeconds <= 3 * 60 * 60)
            assertTrue(item.name, item.buildSeconds * 1000L < shortestCrop)
        }
    }

    // --- Building: what it earns, and when (#50) ---

    @Test
    fun `a building earns its experience and its page when it is unveiled, not when its site goes up`() = runBlocking {
        repo.profile = UserProfile(coins = 0, currentXp = 0, playerLevel = 1)
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)
        assertEquals(0L, repo.profile?.currentXp)
        assertTrue(GardenItem.STRUCT_HUT.objectId !in repo.collection)

        complete(t0 + hutBuild)
        assertEquals(0L, repo.profile?.currentXp) // finished, but not unveiled yet
        assertEquals(ActionResult.SUCCESS, unveil(only().instanceId, t0 + hutBuild + second))
        assertEquals(GardenItem.STRUCT_HUT.placeXp.toLong(), repo.profile?.currentXp)
        assertTrue(GardenItem.STRUCT_HUT.objectId in repo.collection)
    }

    @Test
    fun `something put down at once still earns and is recorded at once`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        place(0, 1, 1, t0)
        assertTrue(GardenItem.DECO_ROCK.objectId in repo.collection)
    }

    @Test
    fun `calling off a building that never earned anything takes nothing back`() = runBlocking {
        repo.profile = UserProfile(coins = 0, currentXp = 40, playerLevel = 1)
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1))
        place(0, 2, 2, t0)
        assertEquals(ActionResult.SUCCESS, startRemoval(only().instanceId, toBag = true, t0 + second))
        assertEquals(40L, repo.profile?.currentXp)
    }

    @Test
    fun `a site cannot be turned round, a finished building can`() = runBlocking {
        val flip = FlipObjectUseCase(repo, GardenMap.DEFAULT)
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_COTTAGE.itemId, 1))
        repo.profile = UserProfile(coins = 0, currentXp = LevelTable.xpForLevel(6), playerLevel = 6)
        place(0, 3, 3, t0)
        val site = only()
        assertEquals(ActionResult.BUSY, flip(site.instanceId))
        complete(t0 + GardenItem.STRUCT_COTTAGE.buildSeconds * second)
        unveil(site.instanceId, t0 + GardenItem.STRUCT_COTTAGE.buildSeconds * second)
        assertEquals(ActionResult.SUCCESS, flip(site.instanceId))
        assertTrue(only().isFlipped)
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
        assertEquals(t0 + GardenItem.STRUCT_WELL.buildSeconds * second, site.workEndsAt)
        // It takes up its place already.
        assertEquals(ActionResult.TILE_OCCUPIED, HoeUseCase(repo, GardenMap.DEFAULT)(2, 2, t0))
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
    fun `when its time is up a building waits under wraps, and not a moment before`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)

        assertTrue(complete(t0).isEmpty())
        val hutTime = GardenItem.STRUCT_HUT.buildSeconds
        assertTrue(complete(t0 + (hutTime - 1) * second).isEmpty())
        assertEquals(WorkKind.BUILDING, only().workKind)

        assertEquals(listOf(FinishedWork("struct_hut", WorkKind.READY)), complete(t0 + hutTime * second))

        // It does not turn into the finished building by itself.
        val site = only()
        assertEquals(WorkKind.READY, site.workKind)
        assertTrue(site.isWorking)
        assertEquals(1f, site.workProgress(t0), 0f)        // whatever the clock says now
        assertEquals(0L, site.workRemainingMillis(t0))
        // And however long it is left, or however often the garden is synced, it stays that way.
        assertTrue(complete(t0 + (hutTime + 1) * second).isEmpty())
        assertTrue(complete(t0 + 30L * 24 * 3_600 * second).isEmpty())
        assertEquals(WorkKind.READY, only().workKind)
    }

    @Test
    fun `the tap of the player unveils a finished building`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)
        val id = only().instanceId

        // Not while it is still going up.
        assertEquals(ActionResult.BUSY, unveil(id))
        assertEquals(WorkKind.BUILDING, only().workKind)

        complete(t0 + hutBuild)
        assertEquals(ActionResult.SUCCESS, unveil(id))

        val hut = only()
        assertFalse(hut.isWorking)
        assertEquals(WorkKind.NONE, hut.workKind)
        assertEquals(0L, hut.workStartedAt)
        assertEquals(0L, hut.workEndsAt)
        assertEquals(3 to 3, hut.gridX to hut.gridY)
        // Once is enough: there is nothing left to unveil.
        assertEquals(ActionResult.NOT_FOUND, unveil(id))
        assertEquals(ActionResult.NOT_FOUND, unveil("nothing"))
    }

    @Test
    fun `only a building under wraps can be unveiled`() = runBlocking {
        repo.put(obj("rock", "deco_rock"))
        repo.put(obj("down", "struct_well", 3, 3).copy(workKind = WorkKind.DEMOLISHING, workStartedAt = t0, workEndsAt = t0 + 60 * second))
        repo.put(obj("pack", "struct_hut", 6, 6).copy(workKind = WorkKind.PACKING, workStartedAt = t0, workEndsAt = t0 + 150 * second))

        assertEquals(ActionResult.NOT_FOUND, unveil("rock"))
        assertEquals(ActionResult.BUSY, unveil("down"))
        assertEquals(ActionResult.BUSY, unveil("pack"))
        assertEquals(WorkKind.DEMOLISHING, repo.objects.getValue("down").workKind)
    }

    @Test
    fun `a building under wraps stays put, and is called off at once like one still going up`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 2))
        place(0, 2, 2, t0)
        place(0, 6, 6, t0)
        complete(t0 + wellBuild)
        val (wrappedA, wrappedB) = repo.objects.values.toList()
        assertEquals(WorkKind.READY, wrappedA.workKind)

        assertEquals(ActionResult.BUSY, move(wrappedA.instanceId, 0, 0))

        assertEquals(ActionResult.SUCCESS, startRemoval(wrappedA.instanceId, toBag = true, t0 + wellBuild + 80 * second))
        assertEquals(1, repo.slots.getValue(0).amount)
        assertEquals(ActionResult.SUCCESS, startRemoval(wrappedB.instanceId, toBag = false, t0 + wellBuild + 80 * second))
        assertTrue(repo.objects.isEmpty())
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
        assertEquals(1, repo.objects.values.count { it.workKind == WorkKind.BUILDING })
        assertEquals(listOf(FinishedWork("struct_well", WorkKind.READY)), complete(t0 + wellBuild))
        assertTrue(repo.objects.values.all { it.workKind == WorkKind.READY })
    }

    @Test
    fun `a building that was finished while away is finished on return`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)

        val finished = complete(t0 + 3 * 24 * 3_600 * second)

        // Finished, but it has waited for the player before showing itself.
        assertEquals(listOf(FinishedWork("struct_hut", WorkKind.READY)), finished)
        assertEquals(WorkKind.READY, only().workKind)
        assertEquals(ActionResult.SUCCESS, unveil(only().instanceId))
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
    fun `discarding a finished building starts a demolition that takes its taking-down time`() = runBlocking {
        repo.put(obj("hut", "struct_hut", 2, 2))

        assertEquals(ActionResult.SUCCESS, startRemoval("hut", toBag = false, t0))

        val site = only()
        assertEquals(WorkKind.DEMOLISHING, site.workKind)
        assertEquals(t0 + hutDown, site.workEndsAt)

        assertTrue(complete(t0 + hutDown - second).isEmpty())
        assertEquals(listOf(FinishedWork("struct_hut", WorkKind.DEMOLISHING)), complete(t0 + hutDown))
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

        assertEquals(listOf(FinishedWork("struct_well", WorkKind.PACKING)), complete(t0 + wellDown))

        assertTrue(repo.objects.isEmpty())
        assertEquals(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1), repo.slots.getValue(0).contents())
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
        // Ridges go back a step at a time, to tilled earth and then to the ground.
        assertEquals(ActionResult.SUCCESS, startRemoval("soil_1_1", toBag = false, t0))
        assertEquals(ActionResult.SUCCESS, startRemoval("soil_1_1", toBag = false, t0))

        assertTrue(repo.objects.isEmpty())
        assertEquals(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1), repo.slots.getValue(0).contents())
    }

    @Test
    fun `a job in progress is not started a second time`() = runBlocking {
        repo.put(obj("hut", "struct_hut", 2, 2))
        startRemoval("hut", toBag = false, t0)

        assertEquals(ActionResult.BUSY, startRemoval("hut", toBag = false, t0 + second))
        assertEquals(ActionResult.BUSY, startRemoval("hut", toBag = true, t0 + second))

        assertEquals(t0 + hutDown, only().workEndsAt) // the clock was not reset
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

        assertTrue(complete(t0 + wellDown).isEmpty())

        val well = repo.objects.getValue("well")
        assertFalse(well.isWorking)
        assertEquals(5, repo.slots.getValue(0).amount)
    }

    // --- Time that cannot be trusted ---

    @Test
    fun `a broken save builds nothing - the job starts over`() = runBlocking {
        val security = Untrusted()
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, security), CalculateOfflineProgressUseCase(), RecordCollectionUseCase(repo))
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_HUT.itemId, 1))
        place(0, 3, 3, t0)

        // A day later, with the profile's signature broken.
        val later = t0 + 24 * 3_600 * second
        assertFalse(sync(later, WeatherType.SUNNY).isTrusted)

        val site = only()
        assertEquals(WorkKind.BUILDING, site.workKind)
        assertEquals(later, site.workStartedAt)
        assertEquals(later + hutBuild, site.workEndsAt) // the whole building time again
        assertTrue(complete(later).isEmpty())
        assertEquals(listOf(FinishedWork("struct_hut", WorkKind.READY)), complete(later + hutBuild))
    }

    @Test
    fun `an honest sync leaves the job's clock alone`() = runBlocking {
        val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, Trusted()), CalculateOfflineProgressUseCase(), RecordCollectionUseCase(repo))
        repo.putSlot(InventorySlot(0, GardenItem.STRUCT_WELL.itemId, 1))
        place(0, 3, 3, t0)

        assertTrue(sync(t0 + 50 * second, WeatherType.SUNNY).isTrusted)

        assertEquals(t0, only().workStartedAt)
        assertEquals(t0 + wellBuild, only().workEndsAt)
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
        assertEquals(ActionResult.OUT_OF_BOUNDS, move("hut", 10, 10))     // half off the garden
        assertEquals(ActionResult.OUT_OF_BOUNDS, move("hut", -1, 0))
        assertEquals(0 to 0, repo.objects.getValue("hut").let { it.gridX to it.gridY })

        // Onto paving is fine, as it is when placing.
        assertEquals(ActionResult.SUCCESS, move("rock", 3, 7))
        // Paving itself moves in its own layer: under the rock is taken (by itself), elsewhere free.
        assertEquals(ActionResult.SUCCESS, move("stone", 4, 7))
        assertEquals(ActionResult.SUCCESS, move("river", 8, 8))
    }

    @Test
    fun `beds stay in the ground, a crop is replanted rather than moved, and work sites stay put`() = runBlocking {
        repo.till(1, 1)
        repo.put(obj("tulip", PlantSpecies.TULIP.objectId, 1, 1))
        repo.put(obj("site", "struct_well", 5, 5).copy(workKind = WorkKind.BUILDING, workStartedAt = t0, workEndsAt = t0 + 120 * second))

        assertEquals(ActionResult.NOT_MOVABLE, move("tulip", 3, 3))
        assertEquals(ActionResult.NOT_MOVABLE, move("soil_1_1", 3, 3))
        assertEquals(ActionResult.BUSY, move("site", 0, 0))
        assertEquals(ActionResult.NOT_FOUND, move("nothing", 0, 0))

        assertTrue(MoveObjectUseCase.canMove(obj("t", PlantSpecies.ROSE.objectId)))
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
    fun `a building under wraps is stored as such`() {
        val waiting = obj("h", "struct_hut", 1, 1).copy(workKind = WorkKind.READY, workStartedAt = t0, workEndsAt = t0 + 300 * second)

        assertEquals("READY", waiting.toEntity().workKind)
        assertEquals(waiting, waiting.toEntity().toDomain())
    }

    @Test
    fun `a job name this version does not know counts as no job`() {
        assertEquals(WorkKind.NONE, WorkKind.fromName("RENOVATING"))
        assertEquals(WorkKind.NONE, WorkKind.fromName(""))
        for (kind in WorkKind.entries) assertEquals(kind, WorkKind.fromName(kind.name))
    }

    private class Trusted : SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = true
        override fun isTamperFlagged() = false
        override fun setTamperFlag(flagged: Boolean) = Unit
    }

    private class Untrusted : SecurityRepository {
        override fun isSignatureValid(profile: UserProfile) = false
        override fun isTamperFlagged() = true
        override fun setTamperFlag(flagged: Boolean) = Unit
    }
}
