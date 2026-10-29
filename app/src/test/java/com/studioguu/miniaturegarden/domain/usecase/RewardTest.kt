package com.studioguu.miniaturegarden.domain.usecase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.studioguu.miniaturegarden.data.reward.RewardLimitRepositoryImpl
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.model.RewardUsage
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RewardTest {

    private val t0 = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val today = 20_000L
    private val sunny = WeatherType.SUNNY

    private val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 100) }
    private val limits = FakeRewardLimits()
    private val grant = GrantRewardUseCase(repo, limits, CalculateOfflineProgressUseCase())

    private fun plant(id: String, x: Int, growth: Float, species: PlantSpecies = PlantSpecies.TULIP) = GridObject(
        instanceId = id, gridX = x, gridY = 0, objectId = species.objectId,
        growthProgress = growth, lastUpdatedTimestamp = t0,
    )

    // --- Limits and counts ---

    @Test
    fun `the daily limits are one rain and five bonuses`() {
        assertEquals(1, RewardType.BLESSED_RAIN.dailyLimit)
        assertEquals(5, RewardType.COIN_BONUS.dailyLimit)
    }

    @Test
    fun `a coin bonus is 40 + 10 a level, times a luck from 0_8 to 1_3, in whole coins`() {
        assertEquals(50, RewardType.coinBonusBase(1))
        assertEquals(240, RewardType.coinBonusBase(20))
        val atTen = (1..2_000).map { RewardType.coinBonusAmount(10, kotlin.random.Random(it)) }
        assertTrue(atTen.all { it in 112..182 })
        // The luck really spreads over the range.
        assertTrue(atTen.min() <= 115 && atTen.max() >= 178)
        // The extremes, exactly.
        val low = object : kotlin.random.Random() { override fun nextBits(bitCount: Int) = 0 }
        assertEquals(40, RewardType.coinBonusAmount(1, low))
    }

    @Test
    fun `remaining counts down for today only`() {
        val usage = RewardUsage(epochDay = today, used = mapOf(RewardType.BLESSED_RAIN to 1, RewardType.COIN_BONUS to 5))
        assertEquals(0, usage.remaining(RewardType.BLESSED_RAIN, today))
        assertEquals(0, usage.remaining(RewardType.COIN_BONUS, today))
        // The next day everything is available again.
        assertEquals(1, usage.remaining(RewardType.BLESSED_RAIN, today + 1))
        assertEquals(5, usage.remaining(RewardType.COIN_BONUS, today + 1))
        assertEquals(1, RewardUsage().remaining(RewardType.BLESSED_RAIN, today))
    }

    // --- Blessed rain ---

    @Test
    fun `blessed rain halves the growth every plant still has ahead of it`() = runBlocking {
        repo.put(plant("a", 0, growth = 0f))
        repo.put(plant("b", 1, growth = 0.2f))
        repo.put(plant("c", 2, growth = 0.9f, species = PlantSpecies.ROSE))

        assertEquals(RewardResult.GRANTED, grant.blessedRain(t0, sunny, today))

        assertEquals(0.5f, repo.objects.getValue("a").growthProgress, 1e-5f)
        assertEquals(0.6f, repo.objects.getValue("b").growthProgress, 1e-5f)
        assertEquals(0.95f, repo.objects.getValue("c").growthProgress, 1e-5f)
        assertEquals(1, limits.usedToday(RewardType.BLESSED_RAIN))
    }

    @Test
    fun `blessed rain waters every living plant to the full, and a thirsty one is thirsty no more (#61)`() = runBlocking {
        repo.put(plant("dry", 0, growth = 0.4f).copy(moistureLevel = 0.1f, status = PlantStatus.THIRSTY))
        repo.put(plant("half", 1, growth = 0.2f).copy(moistureLevel = 0.5f))
        repo.put(plant("grown", 2, growth = 1f).copy(moistureLevel = 0.15f, status = PlantStatus.THIRSTY))
        repo.put(plant("dead", 3, growth = 0.3f).copy(moistureLevel = 0f, status = PlantStatus.WITHERED, stressRate = 1f))

        grant.blessedRain(t0, sunny, today)

        for (id in listOf("dry", "half", "grown")) {
            assertEquals(id, 1f, repo.objects.getValue(id).moistureLevel, 0f)
            assertEquals(id, PlantStatus.HEALTHY, repo.objects.getValue(id).status)
        }
        assertEquals(0.7f, repo.objects.getValue("dry").growthProgress, 1e-5f)
        assertEquals(1f, repo.objects.getValue("grown").growthProgress, 0f)
        // A withered plant is not brought back by the rain.
        assertEquals(0f, repo.objects.getValue("dead").moistureLevel, 0f)
        assertEquals(PlantStatus.WITHERED, repo.objects.getValue("dead").status)
    }

    @Test
    fun `the time already passed is counted before the rain is applied`() = runBlocking {
        repo.put(plant("a", 0, growth = 0f, species = PlantSpecies.CORN))

        // 10.5 healthy hours of a 42 h corn = 25%; the rain then halves the remaining 75%.
        grant.blessedRain(t0 + 21 * hour / 2, sunny, today)

        val after = repo.objects.getValue("a")
        assertEquals(0.625f, after.growthProgress, 1e-4f)
        assertEquals(t0 + 21 * hour / 2, after.lastUpdatedTimestamp)
    }

    @Test
    fun `blessed rain leaves grown plants, withered plants and everything else alone`() = runBlocking {
        val grown = plant("grown", 0, growth = 1f)
        val withered = plant("dead", 1, growth = 0.3f).copy(status = PlantStatus.WITHERED, stressRate = 1f)
        val rock = plant("rock", 2, growth = 0f).copy(objectId = "deco_rock")
        val river = plant("river", 3, growth = 0f).copy(objectId = GridObject.RIVER_OBJECT_ID)
        listOf(grown, withered, rock, river).forEach(repo::put)

        grant.blessedRain(t0, sunny, today)

        assertEquals(1f, repo.objects.getValue("grown").growthProgress, 0f)
        assertEquals(0.3f, repo.objects.getValue("dead").growthProgress, 0f)
        assertEquals(rock, repo.objects.getValue("rock"))
        assertEquals(river, repo.objects.getValue("river"))
    }

    @Test
    fun `the second rain of the day is refused and changes nothing`() = runBlocking {
        repo.put(plant("a", 0, growth = 0f))

        assertEquals(RewardResult.GRANTED, grant.blessedRain(t0, sunny, today))
        val afterOne = repo.objects.getValue("a").growthProgress
        assertEquals(0.5f, afterOne, 1e-5f)

        assertEquals(RewardResult.LIMIT_REACHED, grant.blessedRain(t0, sunny, today))
        assertEquals(afterOne, repo.objects.getValue("a").growthProgress, 0f)

        // A new day brings new rain.
        assertEquals(RewardResult.GRANTED, grant.blessedRain(t0, sunny, today + 1))
    }

    @Test
    fun `rain does not use up the bonus count and the bonus does not use up the rain`() = runBlocking {
        grant.blessedRain(t0, sunny, today)
        assertEquals(RewardResult.GRANTED, grant.coinBonus(today).result)
        assertEquals(0, limits.usage.first().remaining(RewardType.BLESSED_RAIN, today))
        assertEquals(4, limits.usage.first().remaining(RewardType.COIN_BONUS, today))
    }

    // --- Opening the encyclopedia ---

    @Test
    fun `opening the encyclopedia pays once a day`() = runBlocking {
        assertEquals(1, RewardType.BOOK_OPEN.dailyLimit)

        assertEquals(RewardResult.GRANTED, grant.bookOpen(today))
        assertEquals(100L + RewardType.BOOK_OPEN_AMOUNT, repo.profile?.coins)

        // Opening it again and again the same day earns nothing more.
        repeat(5) { assertEquals(RewardResult.LIMIT_REACHED, grant.bookOpen(today)) }
        assertEquals(100L + RewardType.BOOK_OPEN_AMOUNT, repo.profile?.coins)
        assertEquals(1, repo.profileSaves)

        assertEquals(RewardResult.GRANTED, grant.bookOpen(today + 1))
        assertEquals(100L + 2 * RewardType.BOOK_OPEN_AMOUNT, repo.profile?.coins)
    }

    @Test
    fun `the encyclopedia bonus has its own count`() = runBlocking {
        grant.bookOpen(today)

        assertEquals(1, limits.usage.first().remaining(RewardType.BLESSED_RAIN, today))
        assertEquals(5, limits.usage.first().remaining(RewardType.COIN_BONUS, today))
        assertEquals(0, limits.usage.first().remaining(RewardType.BOOK_OPEN, today))
    }

    // --- Coin bonus ---

    @Test
    fun `the coin bonus pays what it says, for the level, through one signed save`() = runBlocking {
        repo.profile = UserProfile(coins = 100, playerLevel = 10, currentXp = LevelTable.xpForLevel(10))
        val bonus = grant.coinBonus(today, kotlin.random.Random(7))
        assertEquals(RewardResult.GRANTED, bonus.result)
        assertEquals(RewardType.coinBonusAmount(10, kotlin.random.Random(7)), bonus.coins)
        assertTrue(bonus.coins in 112..182)

        assertEquals(100L + bonus.coins, repo.profile?.coins)
        assertEquals(LevelTable.xpForLevel(10), repo.profile?.currentXp)
        assertEquals(1, repo.profileSaves)
    }

    @Test
    fun `the sixth bonus of the day is refused and pays nothing`() = runBlocking {
        val paid = (1..5).sumOf { grant.coinBonus(today).also { assertEquals(RewardResult.GRANTED, it.result) }.coins }
        assertEquals(100L + paid, repo.profile?.coins)

        assertEquals(CoinBonus(RewardResult.LIMIT_REACHED, 0), grant.coinBonus(today))
        assertEquals(100L + paid, repo.profile?.coins)
        assertEquals(5, repo.profileSaves)
    }

    @Test
    fun `an abandoned ad costs nothing, because nothing is consumed until a reward is granted`() = runBlocking {
        // The ad layer simply never calls the use case when the ad is closed early.
        assertEquals(1, limits.usage.first().remaining(RewardType.BLESSED_RAIN, today))
        assertEquals(5, limits.usage.first().remaining(RewardType.COIN_BONUS, today))
        assertEquals(100L, repo.profile?.coins)
    }

    // --- The stored counter (real DataStore file) ---

    @get:Rule
    val folder = TemporaryFolder()
    private val jobs = ArrayList<Job>()

    private fun openStore(): Pair<RewardLimitRepositoryImpl, Job> {
        val job = Job().also { jobs += it }
        val file = File(folder.root, "rewards.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        return RewardLimitRepositoryImpl(dataStore) to job
    }

    @After
    fun closeStores() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    @Test
    fun `stored counts go up to the limit and no further`() = runBlocking {
        val (store, _) = openStore()
        assertEquals(1, store.usage.first().remaining(RewardType.BLESSED_RAIN, today))

        assertTrue(store.tryConsume(RewardType.BLESSED_RAIN, today))
        assertFalse(store.tryConsume(RewardType.BLESSED_RAIN, today))
        assertFalse(store.tryConsume(RewardType.BLESSED_RAIN, today))

        val usage = store.usage.first()
        assertEquals(0, usage.remaining(RewardType.BLESSED_RAIN, today))
        assertEquals(1, usage.usedOn(today, RewardType.BLESSED_RAIN))
        assertEquals(5, usage.remaining(RewardType.COIN_BONUS, today))
    }

    @Test
    fun `stored counts survive a restart and reset on a new day`() = runBlocking {
        val (first, firstJob) = openStore()
        repeat(2) { first.tryConsume(RewardType.COIN_BONUS, today) }
        first.tryConsume(RewardType.BLESSED_RAIN, today)
        firstJob.cancelAndJoin()

        val (second, _) = openStore()
        assertEquals(3, second.usage.first().remaining(RewardType.COIN_BONUS, today))
        assertEquals(0, second.usage.first().remaining(RewardType.BLESSED_RAIN, today))

        // The first claim of the next day starts both counters over.
        assertTrue(second.tryConsume(RewardType.COIN_BONUS, today + 1))
        val nextDay = second.usage.first()
        assertEquals(4, nextDay.remaining(RewardType.COIN_BONUS, today + 1))
        assertEquals(1, nextDay.remaining(RewardType.BLESSED_RAIN, today + 1))
    }
}

/** In-memory stand-in with the same rules as the stored counter. */
private class FakeRewardLimits : RewardLimitRepository {
    private val state = MutableStateFlow(RewardUsage())
    override val usage: Flow<RewardUsage> = state

    fun usedToday(type: RewardType): Int = state.value.used[type] ?: 0

    override suspend fun tryConsume(type: RewardType, today: Long): Boolean {
        val current = state.value
        val used = current.usedOn(today, type)
        if (used >= type.dailyLimit) return false
        val base = if (current.epochDay == today) current.used else emptyMap()
        state.value = RewardUsage(today, base + (type to used + 1))
        return true
    }
}
