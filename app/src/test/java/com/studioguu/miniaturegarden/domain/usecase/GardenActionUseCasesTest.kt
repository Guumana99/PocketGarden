package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenActionUseCasesTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val security = FakeSecurityRepository()
    private val calc = CalculateOfflineProgressUseCase()
    private val care = CareForPlantUseCase(repo, calc)
    private val place = PlaceItemUseCase(repo)
    private val harvest = HarvestPlantUseCase(repo, calc)
    private val remove = RemoveObjectUseCase(repo)
    private val purchase = PurchaseItemUseCase(repo)
    private val skip = SkipTimeUseCase(repo, calc)
    private val sync = SyncGardenUseCase(repo, VerifyIntegrityUseCase(repo, security), calc)

    private fun tulip(
        id: String = "p1",
        x: Int = 0,
        y: Int = 0,
        growth: Float = 0f,
        moisture: Float = 1f,
        nutrition: Float = 1f,
        stress: Float = 0f,
        status: PlantStatus = PlantStatus.HEALTHY,
    ) = GridObject(id, x, y, objectId = "plant_tulip", growthProgress = growth, moistureLevel = moisture,
        nutritionLevel = nutrition, stressRate = stress, status = status, lastUpdatedTimestamp = t0)

    private fun stored(id: String = "p1") = repo.objects.getValue(id)

    // --- Watering / fertilizing ---

    @Test
    fun `watering refills moisture after applying the time already passed`() = runBlocking {
        repo.put(tulip())

        assertEquals(ActionResult.SUCCESS, care.water("p1", t0 + 12 * hour, sunny))

        val watered = stored()
        assertEquals(1f, watered.moistureLevel, 0f)
        assertEquals(0.25f, watered.growthProgress, 1e-4f)  // the 12 h before watering still counted
        assertEquals(t0 + 12 * hour, watered.lastUpdatedTimestamp)
    }

    @Test
    fun `time before watering is not counted again by later offline progress`() = runBlocking {
        repo.put(tulip())
        care.water("p1", t0 + 12 * hour, sunny)

        val later = calc(stored(), t0 + 24 * hour, sunny)

        // 24 healthy hours in total -> half grown; 12 h since the refill -> half dry.
        assertEquals(0.5f, later.growthProgress, 1e-4f)
        assertEquals(0.5f, later.moistureLevel, 1e-4f)
        assertEquals(1f - 24f / 72f, later.nutritionLevel, 1e-4f)
    }

    @Test
    fun `watering a thirsty plant makes it healthy again and stops the stress`() = runBlocking {
        repo.put(tulip(moisture = 0.1f, status = PlantStatus.THIRSTY))

        care.water("p1", t0 + 6 * hour, sunny)

        val watered = stored()
        assertEquals(PlantStatus.HEALTHY, watered.status)
        assertEquals(0.25f, watered.stressRate, 1e-4f)  // 6 thirsty hours stay on the record
        val later = calc(watered, t0 + 12 * hour, sunny)
        assertTrue(later.stressRate < watered.stressRate)
    }

    @Test
    fun `fertilizer refills nutrition and boosts growth, capped at fully grown`() = runBlocking {
        repo.put(tulip(growth = 0.5f, nutrition = 0.1f, status = PlantStatus.UNNOURISHED))
        repo.put(tulip(id = "p2", x = 1, growth = 0.99f))

        assertEquals(ActionResult.SUCCESS, care.fertilize("p1", t0, sunny))
        assertEquals(ActionResult.SUCCESS, care.fertilize("p2", t0, sunny))

        assertEquals(1f, stored("p1").nutritionLevel, 0f)
        assertEquals(0.5f + 2f / 48f, stored("p1").growthProgress, 1e-4f)  // two of a tulip's 48 hours
        assertEquals(PlantStatus.HEALTHY, stored("p1").status)
        assertEquals(1f, stored("p2").growthProgress, 0f)
    }

    @Test
    fun `care is refused for withered plants, non-plants and unknown ids`() = runBlocking {
        repo.put(tulip(stress = 1f, status = PlantStatus.WITHERED))
        repo.put(tulip(id = "rock").copy(objectId = "deco_rock"))
        // Alive in the database, but it dried out and withered while the app was closed.
        repo.put(tulip(id = "p3", x = 2, moisture = 0f, stress = 0.9f, status = PlantStatus.THIRSTY))

        assertEquals(ActionResult.WITHERED, care.water("p1", t0, sunny))
        assertEquals(ActionResult.NOT_A_PLANT, care.water("rock", t0, sunny))
        assertEquals(ActionResult.NOT_FOUND, care.fertilize("nope", t0, sunny))
        assertEquals(ActionResult.WITHERED, care.water("p3", t0 + 24 * hour, sunny))
        assertEquals(PlantStatus.WITHERED, stored("p3").status)
        assertEquals(0f, stored("p3").moistureLevel, 0f)
    }

    @Test
    fun `watering is refused while the soil is still full`() = runBlocking {
        repo.put(tulip())

        assertEquals(ActionResult.ALREADY_MOIST, care.water("p1", t0, sunny))
        // 96% an hour later: still above the 80% line.
        assertEquals(ActionResult.ALREADY_MOIST, care.water("p1", t0 + hour, sunny))
        assertEquals(0L, repo.profile?.currentXp)
        assertEquals(0, repo.profileSaves)

        // 75% after six hours.
        assertEquals(ActionResult.SUCCESS, care.water("p1", t0 + 6 * hour, sunny))
    }

    @Test
    fun `watering and fertilizing earn experience`() = runBlocking {
        repo.put(tulip(moisture = 0.5f))

        care.water("p1", t0, sunny)
        assertEquals(5L, repo.profile?.currentXp)
        care.fertilize("p1", t0, sunny)
        assertEquals(15L, repo.profile?.currentXp)
    }

    @Test
    fun `fertilizer costs coins and is refused without them`() = runBlocking {
        repo.profile = UserProfile(coins = 20)
        repo.put(tulip(growth = 0.2f))

        assertEquals(ActionResult.SUCCESS, care.fertilize("p1", t0, sunny))
        assertEquals(5L, repo.profile?.coins)
        assertEquals(ActionResult.NOT_ENOUGH_COINS, care.fertilize("p1", t0, sunny))

        assertEquals(5L, repo.profile?.coins)
        assertEquals(0.2f + 2f / 48f, stored().growthProgress, 1e-4f)
        assertEquals(10L, repo.profile?.currentXp)
    }

    // --- Levels ---

    @Test
    fun `level thresholds follow 100 x N to the power of 1_5`() {
        assertEquals(0L, LevelTable.xpForLevel(1))
        assertEquals(282L, LevelTable.xpForLevel(2))
        assertEquals(519L, LevelTable.xpForLevel(3))
        assertEquals(800L, LevelTable.xpForLevel(4))

        assertEquals(1, LevelTable.levelForXp(0))
        assertEquals(1, LevelTable.levelForXp(281))
        assertEquals(2, LevelTable.levelForXp(282))
        assertEquals(3, LevelTable.levelForXp(799))
        assertEquals(4, LevelTable.levelForXp(800))
        assertEquals(LevelTable.MAX_LEVEL, LevelTable.levelForXp(Long.MAX_VALUE))
    }

    @Test
    fun `gaining enough experience raises the level, possibly by several`() {
        val almost = UserProfile(currentXp = 280)
        assertEquals(1, almost.gainXp(1).playerLevel)
        assertEquals(2, almost.gainXp(5).playerLevel)
        assertEquals(285L, almost.gainXp(5).currentXp)
        assertEquals(4, UserProfile().gainXp(800).playerLevel)
    }

    @Test
    fun `harvest pays the species' own coins and experience and can level the player up`() = runBlocking {
        repo.profile = UserProfile(coins = 0, currentXp = 200)
        repo.put(tulip(growth = 1f).copy(objectId = PlantSpecies.ROSE.objectId))

        assertEquals(ActionResult.SUCCESS, harvest("p1", t0, sunny))

        assertEquals(160L, repo.profile?.coins)
        assertEquals(340L, repo.profile?.currentXp)
        assertEquals(2, repo.profile?.playerLevel)
    }

    // --- Species ---

    @Test
    fun `each species grows and dries at its own pace`() {
        fun after12h(species: PlantSpecies) =
            calc(tulip().copy(objectId = species.objectId), t0 + 12 * hour, sunny)

        for (species in PlantSpecies.entries) {
            val plant = after12h(species)
            assertEquals(species.name, 12f / species.growthHours, plant.growthProgress, 1e-4f)
            assertEquals(species.name, 1f - 12f / species.dryOutHours, plant.moistureLevel, 1e-4f)
        }
        // Slower plants are worth more.
        val byGrowth = PlantSpecies.entries.sortedBy { it.growthHours }
        assertEquals(byGrowth, PlantSpecies.entries.sortedBy { it.harvestCoins })
        assertEquals(byGrowth, PlantSpecies.entries.sortedBy { it.harvestXp })
    }

    @Test
    fun `every seed in the shop grows into a known species`() {
        val seeds = GardenItem.entries.filter { it.objectId.startsWith(GridObject.PLANT_ID_PREFIX) }
        assertEquals(PlantSpecies.entries.toSet(), seeds.map { PlantSpecies.fromObjectId(it.objectId) }.toSet())
        assertEquals(GardenItem.entries.size, GardenItem.entries.map { it.itemId }.toSet().size)
    }

    @Test
    fun `watering is allowed only below 80 percent moisture`() = runBlocking {
        repo.put(tulip(moisture = 0.8f))
        repo.put(tulip(id = "p2", x = 1, moisture = 0.79f))

        assertEquals(ActionResult.ALREADY_MOIST, care.water("p1", t0, sunny))
        assertEquals(ActionResult.SUCCESS, care.water("p2", t0, sunny))

        assertEquals(0.8f, stored("p1").moistureLevel, 0f)
        assertEquals(1f, stored("p2").moistureLevel, 0f)
    }

    @Test
    fun `fertilizer is worth two hours of growth, so less on slower plants`() = runBlocking {
        repo.profile = UserProfile(coins = 1_000)
        PlantSpecies.entries.forEachIndexed { index, species ->
            repo.put(tulip(id = species.name, x = index).copy(objectId = species.objectId))
            care.fertilize(species.name, t0, sunny)
            assertEquals(species.name, 2f / species.growthHours, stored(species.name).growthProgress, 1e-5f)
        }
        assertEquals(2f / 120f, stored("ROSE").growthProgress, 1e-5f)

        // Fertilizing a rose from seed to harvest costs far more than the harvest pays.
        val usesToMature = PlantSpecies.ROSE.growthHours / CareForPlantUseCase.FERTILIZER_GROWTH_HOURS
        assertTrue(usesToMature * CareForPlantUseCase.FERTILIZER_COST > PlantSpecies.ROSE.harvestCoins)
    }

    // --- Debug time skip ---

    @Test
    fun `skipping time advances plants exactly like waiting`() = runBlocking {
        repo.put(tulip())
        repo.put(tulip(id = "p2", x = 1, moisture = 0.3f))
        val waited = listOf(calc(stored("p1"), t0 + 6 * hour, sunny), calc(stored("p2"), t0 + 6 * hour, sunny))

        assertEquals(ActionResult.SUCCESS, skip(6 * hour, t0, sunny))

        // Same levels as waiting six hours, but anchored at the present.
        assertEquals(waited.map { it.copy(lastUpdatedTimestamp = t0) }, listOf(stored("p1"), stored("p2")))
        assertEquals(0.125f, stored("p1").growthProgress, 1e-4f)
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `repeated skips add up and stop at the seven day cap per skip`() = runBlocking {
        repo.put(tulip())

        skip(hour, t0, sunny)
        skip(hour, t0, sunny)
        assertEquals(2f / 48f, stored().growthProgress, 1e-4f)

        skip(30 * 24 * hour, t0, sunny)
        assertEquals(PlantStatus.WITHERED, stored().status)
    }

    // --- Placing ---

    @Test
    fun `placing a seed creates a plant and uses up one item`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 2))

        assertEquals(ActionResult.SUCCESS, place(0, 3, 5, t0))

        val planted = repo.objects.values.single()
        assertEquals("plant_tulip", planted.objectId)
        assertEquals(3 to 5, planted.gridX to planted.gridY)
        assertEquals(0f, planted.growthProgress, 0f)
        assertEquals(1f, planted.moistureLevel, 0f)
        assertEquals(t0, planted.lastUpdatedTimestamp)
        assertEquals(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1), repo.slots.getValue(0))
    }

    @Test
    fun `placing the last item empties the slot`() = runBlocking {
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))

        place(0, 0, 0, t0)

        assertEquals(InventorySlot(0), repo.slots.getValue(0))
        assertTrue(repo.slots.getValue(0).isEmpty)
    }

    @Test
    fun `invalid placements change nothing`() = runBlocking {
        repo.put(tulip(x = 4, y = 4))
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, 1))
        repo.putSlot(InventorySlot(1))
        repo.putSlot(InventorySlot(2, "mystery_item", 5))

        assertEquals(ActionResult.TILE_OCCUPIED, place(0, 4, 4, t0))
        assertEquals(ActionResult.OUT_OF_BOUNDS, place(0, 9, 0, t0))
        assertEquals(ActionResult.OUT_OF_BOUNDS, place(0, 0, -1, t0))
        assertEquals(ActionResult.SLOT_EMPTY, place(1, 0, 0, t0))
        assertEquals(ActionResult.SLOT_EMPTY, place(7, 0, 0, t0))
        assertEquals(ActionResult.SLOT_EMPTY, place(2, 0, 0, t0))

        assertEquals(1, repo.objects.size)
        assertEquals(1, repo.slots.getValue(0).amount)
    }

    // --- Harvest / remove ---

    @Test
    fun `harvesting a grown plant removes it and pays coins through a re-signed save`() = runBlocking {
        repo.profile = UserProfile(coins = 10)
        repo.put(tulip(growth = 1f))

        assertEquals(ActionResult.SUCCESS, harvest("p1", t0, sunny))

        assertTrue(repo.objects.isEmpty())
        assertEquals(60L, repo.profile?.coins)
        assertEquals(1, repo.profileSaves)
    }

    @Test
    fun `a plant that finishes growing while away can be harvested`() = runBlocking {
        repo.profile = UserProfile()
        repo.put(tulip(growth = 0.9f))

        assertEquals(ActionResult.NOT_READY, harvest("p1", t0 + hour, sunny))
        assertEquals(ActionResult.SUCCESS, harvest("p1", t0 + 6 * hour, sunny))
    }

    @Test
    fun `unripe and withered plants cannot be harvested`() = runBlocking {
        repo.profile = UserProfile(coins = 10)
        repo.put(tulip(growth = 0.5f))
        repo.put(tulip(id = "p2", x = 1, growth = 1f, stress = 1f, status = PlantStatus.WITHERED))

        assertEquals(ActionResult.NOT_READY, harvest("p1", t0, sunny))
        assertEquals(ActionResult.WITHERED, harvest("p2", t0, sunny))

        assertEquals(2, repo.objects.size)
        assertEquals(10L, repo.profile?.coins)
        assertEquals(0, repo.profileSaves)
    }

    @Test
    fun `removing deletes the object without paying anything`() = runBlocking {
        repo.profile = UserProfile(coins = 10)
        repo.put(tulip(growth = 1f))

        assertEquals(ActionResult.SUCCESS, remove("p1"))
        assertEquals(ActionResult.NOT_FOUND, remove("p1"))

        assertTrue(repo.objects.isEmpty())
        assertEquals(10L, repo.profile?.coins)
    }

    // --- Shop ---

    @Test
    fun `purchase spends coins and fills the first empty slot`() = runBlocking {
        repo.profile = UserProfile(coins = 100, inventoryCapacity = 3)
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        repo.putSlot(InventorySlot(1))
        repo.putSlot(InventorySlot(2))

        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.SEED_TULIP))

        assertEquals(InventorySlot(1, GardenItem.SEED_TULIP.itemId, 1), repo.slots.getValue(1))
        assertEquals(80L, repo.profile?.coins)
        assertEquals(1, repo.profileSaves)
    }

    @Test
    fun `purchase stacks onto an existing slot until it is full`() = runBlocking {
        repo.profile = UserProfile(coins = 100, inventoryCapacity = 3)
        repo.putSlot(InventorySlot(0, GardenItem.SEED_TULIP.itemId, InventorySlot.MAX_STACK))
        repo.putSlot(InventorySlot(1))
        repo.putSlot(InventorySlot(2, GardenItem.SEED_TULIP.itemId, 5))

        purchase(GardenItem.SEED_TULIP)

        assertEquals(InventorySlot.MAX_STACK, repo.slots.getValue(0).amount)
        assertTrue(repo.slots.getValue(1).isEmpty)
        assertEquals(6, repo.slots.getValue(2).amount)
    }

    @Test
    fun `purchase fails without charging when coins or space run out`() = runBlocking {
        repo.profile = UserProfile(coins = 19, inventoryCapacity = 1)
        repo.putSlot(InventorySlot(0, GardenItem.DECO_ROCK.itemId, 1))
        // A slot beyond the unlocked capacity must not be used.
        repo.putSlot(InventorySlot(1))

        assertEquals(ActionResult.NOT_ENOUGH_COINS, purchase(GardenItem.SEED_TULIP))
        repo.profile = UserProfile(coins = 500, inventoryCapacity = 1)
        assertEquals(ActionResult.INVENTORY_FULL, purchase(GardenItem.SEED_TULIP))

        assertEquals(500L, repo.profile?.coins)
        assertTrue(repo.slots.getValue(1).isEmpty)
        assertEquals(0, repo.profileSaves)
    }

    // --- Sync ---

    @Test
    fun `sync applies offline progress and re-stamps the profile`() = runBlocking {
        repo.profile = UserProfile()
        repo.put(tulip())

        val integrity = sync(t0 + 12 * hour, sunny)

        assertTrue(integrity.isTrusted)
        assertEquals(0.25f, stored().growthProgress, 1e-4f)
        assertEquals(0.5f, stored().moistureLevel, 1e-4f)
        assertEquals(1, repo.profileSaves)
    }

    @Test
    fun `sync does not credit time when the clock was tampered with`() = runBlocking {
        repo.profile = UserProfile()
        repo.put(tulip())
        security.timeTampered = true

        val integrity = sync(t0 + 48 * hour, sunny)

        assertFalse(integrity.timeConsistent)
        assertEquals(tulip().copy(lastUpdatedTimestamp = t0 + 48 * hour), stored())
        // Still re-stamped, so the next honest session is measured from here.
        assertEquals(1, repo.profileSaves)
    }

    @Test
    fun `sync does not credit time when the profile signature is broken`() = runBlocking {
        repo.profile = UserProfile()
        repo.put(tulip())
        security.signatureValid = false

        sync(t0 + 48 * hour, sunny)

        assertEquals(0f, stored().growthProgress, 0f)
        assertTrue(security.flagged)
    }

    @Test
    fun `sync before the profile exists is harmless`() = runBlocking {
        repo.profile = null
        assertTrue(sync(t0, sunny).isTrusted)
        assertEquals(0, repo.profileSaves)
    }
}

private class FakeGardenRepository : GardenRepository {
    val objects = LinkedHashMap<String, GridObject>()
    val slots = sortedMapOf<Int, InventorySlot>()
    var profile: UserProfile? = null
    var profileSaves = 0

    fun put(gridObject: GridObject) {
        objects[gridObject.instanceId] = gridObject
    }

    fun putSlot(slot: InventorySlot) {
        slots[slot.slotIndex] = slot
    }

    override suspend fun ensureInitialized() = Unit
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()

    override fun observeUserProfile(): Flow<UserProfile?> = MutableStateFlow(profile)
    override suspend fun getUserProfile(): UserProfile? = profile
    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile = profile
        profileSaves++
    }

    override fun observeGridObjects(): Flow<List<GridObject>> = MutableStateFlow(objects.values.toList())
    override suspend fun getGridObjects(): List<GridObject> = objects.values.toList()
    override suspend fun upsertGridObject(gridObject: GridObject) = put(gridObject)
    override suspend fun upsertGridObjects(gridObjects: List<GridObject>) = gridObjects.forEach(::put)
    override suspend fun deleteGridObject(instanceId: String) {
        objects.remove(instanceId)
    }

    override fun observeInventory(): Flow<List<InventorySlot>> = MutableStateFlow(slots.values.toList())
    override suspend fun getInventory(): List<InventorySlot> = slots.values.toList()
    override suspend fun upsertInventorySlot(slot: InventorySlot) = putSlot(slot)
    override suspend fun upsertInventorySlots(slots: List<InventorySlot>) = slots.forEach(::putSlot)
}

private class FakeSecurityRepository : SecurityRepository {
    var signatureValid = true
    var timeTampered = false
    var flagged = false

    override fun isSignatureValid(profile: UserProfile): Boolean = signatureValid
    override fun isTimeTampered(profile: UserProfile): Boolean = timeTampered.also { if (it) this.flagged = true }
    override fun isTamperFlagged(): Boolean = flagged
    override fun setTamperFlag(flagged: Boolean) {
        this.flagged = flagged
    }
}
