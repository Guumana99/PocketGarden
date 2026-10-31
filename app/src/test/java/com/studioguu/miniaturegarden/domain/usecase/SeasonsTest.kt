package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.AppDatabase
import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.GardenGrid
import com.studioguu.miniaturegarden.domain.model.Ground
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.GameDay
import com.studioguu.miniaturegarden.domain.model.SeasonClock
import com.studioguu.miniaturegarden.domain.model.SeasonPhase
import com.studioguu.miniaturegarden.domain.model.SeasonState
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UnlockCondition
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.after
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionCategory
import com.studioguu.miniaturegarden.data.asset.TileSetDefinition
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonsTest {

    private val day = SeasonClock.DAY_MILLIS

    /** The first day of the year in these tests ([GameDay]), and seven in the morning on it. */
    private val firstDay = 19_700L
    private val t0 = GameDay.startOf(firstDay) + 2 * 3_600_000L
    private val season = SeasonClock.SEASON_MILLIS

    /** When the season begun at [t0] ends: five in the morning on its tenth day. */
    private val end = GameDay.startOf(firstDay + SeasonClock.DAYS_PER_SEASON)

    // --- The year going round ---

    @Test
    fun `the seasons follow one another and come back round`() {
        assertEquals(listOf(Season.SUMMER, Season.AUTUMN, Season.WINTER, Season.SPRING), Season.entries.map { it.after() })
        assertEquals(Season.SPRING, Season.SPRING.after(4))
        assertEquals(Season.WINTER, Season.SPRING.after(-1))
        // One season is a number of days that is easy to change in one place.
        assertEquals(SeasonClock.DAYS_PER_SEASON * day, season)
        assertTrue(SeasonClock.DAYS_PER_SEASON in 7..14)
    }

    @Test
    fun `a season lasts nine days, then the next one comes at five in the morning`() {
        assertEquals(9, SeasonClock.DAYS_PER_SEASON)
        assertEquals(SeasonState(Season.SPRING, t0), SeasonClock.at(Season.SPRING, t0, end - 1))
        assertEquals(SeasonState(Season.SUMMER, end), SeasonClock.at(Season.SPRING, t0, end))
        assertEquals(end, SeasonClock.endOf(t0))
        // Long away: the season of now, begun when it really began.
        val winter = GameDay.startOf(firstDay + 27)
        assertEquals(SeasonState(Season.WINTER, winter), SeasonClock.at(Season.SPRING, t0, winter + 5 * day))
        val nextSummer = GameDay.startOf(firstDay + 45)
        assertEquals(SeasonState(Season.SUMMER, nextSummer), SeasonClock.at(Season.SPRING, t0, nextSummer))
    }

    @Test
    fun `a season has three phases of three days`() {
        // Late at night on the first day is still the first day: the day turns at five.
        assertEquals(1, SeasonClock.dayOfSeason(t0, GameDay.startOf(firstDay + 1) - 1))
        assertEquals(2, SeasonClock.dayOfSeason(t0, GameDay.startOf(firstDay + 1)))
        val phases = (0 until 9).map { SeasonClock.phaseAt(t0, GameDay.startOf(firstDay + it) + 3_600_000L) }
        assertEquals(List(3) { SeasonPhase.EARLY } + List(3) { SeasonPhase.MID } + List(3) { SeasonPhase.LATE }, phases)
        // The season and phase of any day, forwards and backwards.
        assertEquals(Season.SPRING to SeasonPhase.MID, SeasonClock.on(firstDay + 4, Season.SPRING, t0))
        assertEquals(Season.SUMMER to SeasonPhase.EARLY, SeasonClock.on(firstDay + 9, Season.SPRING, t0))
        assertEquals(Season.WINTER to SeasonPhase.LATE, SeasonClock.on(firstDay - 1, Season.SPRING, t0))
    }

    @Test
    fun `a garden saved on its tenth day of a ten-day season moves on to the next`() {
        // Seasons were ten days long: a spring begun nine and a half days ago is over now.
        val now = t0 + 9 * day + 12 * 3_600_000L
        assertEquals(Season.SUMMER, SeasonClock.at(Season.SPRING, t0, now).season)
    }

    @Test
    fun `a clock set back turns nothing back`() {
        assertEquals(SeasonState(Season.AUTUMN, t0), SeasonClock.at(Season.AUTUMN, t0, t0 - 40 * day))
    }

    @Test
    fun `the end of a season is near in its last days`() {
        assertFalse(SeasonClock.isEndNear(t0, end - SeasonClock.NOTICE_MILLIS - 1))
        assertTrue(SeasonClock.isEndNear(t0, end - SeasonClock.NOTICE_MILLIS))
    }

    // --- Turning the garden's year, and the post ---

    private val repo = FakeGardenRepository().apply {
        profile = UserProfile(coins = 1_000)
        progress = GardenProgress.NEW_GARDEN
        (0 until UserProfile.DEFAULT_INVENTORY_CAPACITY).forEach { putSlot(InventorySlot(it)) }
    }
    private val advance = AdvanceSeasonUseCase(repo)

    @Test
    fun `a new garden starts its clock in spring with a letter welcoming the spring`() = runBlocking {
        assertEquals(Season.SPRING, GardenProgress.NEW_GARDEN.season)
        assertNull(advance(t0))
        assertEquals(t0, repo.progress?.seasonStartedAt)
        assertEquals(listOf(Letter.arrived(Season.SPRING, t0)), repo.letters.values.toList())
        // Asking again at once changes nothing and posts nothing.
        assertNull(advance(t0 + day))
        assertEquals(1, repo.letters.size)
    }

    @Test
    fun `near the end of a season the post says which comes next, once`() = runBlocking {
        advance(t0)
        advance(t0 + season - SeasonClock.NOTICE_MILLIS)
        advance(t0 + season - SeasonClock.NOTICE_MILLIS + day)
        val coming = repo.letters.values.filter { it.kind == LetterKind.SEASON_COMING }
        assertEquals(1, coming.size)
        assertEquals(Season.SUMMER, coming.single().season)
    }

    @Test
    fun `when the season turns, the garden moves on and the post says so`() = runBlocking {
        advance(t0)
        assertEquals(Season.SUMMER, advance(end + 1))
        assertEquals(Season.SUMMER, repo.progress?.season)
        assertEquals(end, repo.progress?.seasonStartedAt)
        assertTrue(repo.letters.values.any { it.kind == LetterKind.SEASON_ARRIVED && it.season == Season.SUMMER && !it.read })
        // The tutorial is not touched by the year going round.
        assertEquals(TutorialStep.WELCOME, repo.progress?.tutorialStep)
    }

    @Test
    fun `after a long time away only the season of now is announced`() = runBlocking {
        advance(t0)
        assertEquals(Season.WINTER, advance(t0 + 3 * season + day))
        val arrivals = repo.letters.values.filter { it.kind == LetterKind.SEASON_ARRIVED }.map { it.season }
        assertEquals(listOf(Season.SPRING, Season.WINTER), arrivals)
    }

    @Test
    fun `the debug menu brings the next season at once`() = runBlocking {
        advance(t0)
        assertEquals(Season.SUMMER, advance.skipSeason(t0 + day))
        assertEquals(Season.AUTUMN, advance.skipSeason(t0 + day))
        assertEquals(Season.AUTUMN, repo.progress?.season)
    }

    @Test
    fun `skipping time in the debug menu turns the year as well`() = runBlocking {
        advance(t0)
        SkipTimeUseCase(repo, CalculateOfflineProgressUseCase())(season, t0, com.studioguu.miniaturegarden.domain.model.WeatherType.SUNNY)
        assertEquals(Season.SUMMER, advance(t0))
    }

    @Test
    fun `opening a letter marks that one read, and the post keeps every letter`() = runBlocking {
        advance(t0)
        advance(t0 + season)
        val letters = repo.getLetters()
        assertTrue(letters.all { !it.read })
        ReadLettersUseCase(repo).markRead(letters.first().letterId)
        assertTrue(repo.getLetters().first().read)
        // The others keep their mark until they are opened, and nothing is thrown away.
        assertTrue(repo.getLetters().drop(1).none { it.read })
        assertEquals(letters.size, repo.getLetters().size)
        // Newest first.
        assertEquals(Season.SUMMER, repo.getLetters().first().season)
    }

    // --- Which crops go with which season ---

    @Test
    fun `every season has something to sow from the start, and mint grows in all of them`() {
        val seeds = GardenItem.entries.filter { it.category == ItemCategory.SEED }
        for (s in Season.entries) {
            assertTrue(s.name, seeds.any { it.isInSeason(s) && it.unlock == UnlockCondition.None })
        }
        // Every season has a crop of its own from the start: summer's is the tomato.
        for (s in Season.entries) {
            assertTrue(s.name, seeds.any { s in it.seasons && it.unlock == UnlockCondition.None })
        }
        assertTrue(GardenItem.SEED_MINT.isAllSeasons)
        assertEquals(setOf(Season.SPRING), GardenItem.SEED_TULIP.seasons)
        assertEquals(setOf(Season.SUMMER), GardenItem.SEED_SUNFLOWER.seasons)
        assertEquals(setOf(Season.AUTUMN), GardenItem.SEED_COSMOS.seasons)
        assertEquals(setOf(Season.WINTER), GardenItem.SEED_SNOWDROP.seasons)
        assertEquals(setOf(Season.SUMMER), GardenItem.SEED_TOMATO.seasons)
        assertEquals(UnlockCondition.None, GardenItem.SEED_TOMATO.unlock)
        assertEquals(setOf(Season.SPRING), GardenItem.SEED_STRAWBERRY.seasons)
        assertEquals(setOf(Season.SUMMER), GardenItem.SEED_WATERMELON.seasons)
        assertEquals(setOf(Season.AUTUMN), GardenItem.SEED_SHIITAKE.seasons)
        assertEquals(setOf(Season.WINTER), GardenItem.SEED_POINSETTIA.seasons)
        assertTrue(GardenItem.SEED_LAVENDER.isAllSeasons)
        // Everything that is not a crop is good all year.
        assertTrue(GardenItem.entries.filter { it.category != ItemCategory.SEED }.all { it.isAllSeasons })
    }

    // --- The shop ---

    @Test
    fun `the seed shelf holds what is in season and what grows all year`() {
        assertEquals(
            setOf(GardenItem.SEED_MINT, GardenItem.SEED_LAVENDER, GardenItem.SEED_TULIP, GardenItem.SEED_STRAWBERRY, GardenItem.SEED_ROSE, GardenItem.SEED_CARROT, GardenItem.SEED_NANOHANA),
            GardenItem.shelf(ItemCategory.SEED, Season.SPRING).toSet(),
        )
        assertEquals(
            setOf(
                GardenItem.SEED_MINT, GardenItem.SEED_LAVENDER, GardenItem.SEED_TOMATO, GardenItem.SEED_SUNFLOWER, GardenItem.SEED_WATERMELON,
                GardenItem.SEED_PAPRIKA, GardenItem.SEED_EGGPLANT, GardenItem.SEED_CORN,
            ),
            GardenItem.shelf(ItemCategory.SEED, Season.SUMMER).toSet(),
        )
        assertEquals(
            setOf(
                GardenItem.SEED_MINT, GardenItem.SEED_LAVENDER, GardenItem.SEED_ROSE, GardenItem.SEED_COSMOS, GardenItem.SEED_SHIITAKE, GardenItem.SEED_DAIKON,
                GardenItem.SEED_CARROT, GardenItem.SEED_WHEAT,
            ),
            GardenItem.shelf(ItemCategory.SEED, Season.AUTUMN).toSet(),
        )
        assertEquals(
            setOf(GardenItem.SEED_MINT, GardenItem.SEED_LAVENDER, GardenItem.SEED_SNOWDROP, GardenItem.SEED_POINSETTIA, GardenItem.SEED_DAIKON, GardenItem.SEED_HAKUSAI),
            GardenItem.shelf(ItemCategory.SEED, Season.WINTER).toSet(),
        )
        // The other shelves are the same in every season.
        for (s in Season.entries) {
            assertEquals(GardenItem.shelf(ItemCategory.STRUCTURE), GardenItem.shelf(ItemCategory.STRUCTURE, s))
            assertEquals(GardenItem.shelf(ItemCategory.DECORATION), GardenItem.shelf(ItemCategory.DECORATION, s))
        }
    }

    @Test
    fun `seeds can be stocked up on in season and not bought out of it`() = runBlocking {
        val purchase = PurchaseItemUseCase(repo)
        repeat(5) { assertEquals(ActionResult.SUCCESS, purchase(GardenItem.SEED_TULIP)) }
        assertEquals(ActionResult.OUT_OF_SEASON, purchase(GardenItem.SEED_COSMOS))
        assertEquals(ActionResult.SUCCESS, purchase(GardenItem.SEED_MINT))
        assertEquals(1_000L - 5 * 10 - 5, repo.profile?.coins)

        // The season turns: the tulips bought are still in the bag.
        repo.progress = repo.progress!!.copy(season = Season.SUMMER)
        assertEquals(ActionResult.OUT_OF_SEASON, purchase(GardenItem.SEED_TULIP))
        assertEquals(5, repo.slots.values.single { it.itemId == GardenItem.SEED_TULIP.itemId }.amount)
    }

    // --- Sowing, and the greenhouse ---

    private val place = PlaceItemUseCase(repo, GardenMap.DEFAULT, RecordCollectionUseCase(repo))

    private fun sow(item: GardenItem, x: Int, y: Int, inGreenhouse: Boolean = false): ActionResult = runBlocking {
        val slot = repo.slots.values.first { it.isEmpty }.slotIndex
        repo.putSlot(InventorySlot(slot, item.itemId, 1))
        if (inGreenhouse) {
            repo.put(GridObject("glass_${x}_$y", x, y, objectId = GridObject.GREENHOUSE_OBJECT_ID, lastUpdatedTimestamp = t0))
        } else {
            repo.till(x, y)
        }
        place(slot, x, y, t0)
    }

    @Test
    fun `out of its season a crop cannot be sown in the open, and stays in the bag`() = runBlocking {
        repo.progress = repo.progress!!.copy(season = Season.WINTER)
        assertEquals(ActionResult.OUT_OF_SEASON, sow(GardenItem.SEED_TULIP, 1, 1))
        assertTrue(repo.plants.isEmpty())
        assertEquals(1, repo.slots.values.single { it.itemId == GardenItem.SEED_TULIP.itemId }.amount)
        // In season, or a crop for all seasons, it goes in.
        assertEquals(ActionResult.SUCCESS, sow(GardenItem.SEED_SNOWDROP, 2, 1))
        assertEquals(ActionResult.SUCCESS, sow(GardenItem.SEED_MINT, 3, 1))
    }

    @Test
    fun `under glass anything can be sown in any season`() = runBlocking {
        for ((i, s) in Season.entries.withIndex()) {
            repo.progress = repo.progress!!.copy(season = s)
            // Anything sown in a bed, that is: the shiitake's log goes on the open earth, never under glass (#68).
            val sown = GardenItem.entries.filter {
                it.inShop && it.category == ItemCategory.SEED && PlantSpecies.forObjectId(it.objectId).ground.allows(Ground.GREENHOUSE)
            }
            assertFalse(GardenItem.SEED_SHIITAKE in sown)
            for ((j, seed) in sown.withIndex()) {
                assertEquals("$seed in $s", ActionResult.SUCCESS, sow(seed, j % 11, i + 4 * (j / 11), inGreenhouse = true))
                repo.objects.values.removeIf { it.isPlant }
            }
        }
    }

    @Test
    fun `the greenhouse is soil under glass and opens late`() {
        val glass = GridObject("g", 4, 4, objectId = GridObject.GREENHOUSE_OBJECT_ID, lastUpdatedTimestamp = t0)
        val grid = GardenGrid(GardenMap.DEFAULT, listOf(glass))
        assertTrue(grid.isTilled(4, 4))
        assertTrue(grid.isUnderGlass(4, 4))
        assertFalse(GardenGrid(GardenMap.DEFAULT, listOf(GridObject("t", 4, 4, objectId = GridObject.TILLED_SOIL_OBJECT_ID, lastUpdatedTimestamp = t0))).isUnderGlass(4, 4))
        assertEquals(UnlockCondition.PlayerLevel(18), GardenItem.FLOOR_GREENHOUSE.unlock)
        assertTrue(GridObject.GREENHOUSE_OBJECT_ID in TileSetDefinition.objectIds)
        assertEquals(CollectionCategory.STRUCTURES, CollectionEntry.GREENHOUSE.category)
        // It can be put back in the bag, but not moved: what grows in it would be left behind.
        assertEquals(GardenItem.FLOOR_GREENHOUSE, StoreObjectUseCase.itemFor(glass))
        assertFalse(MoveObjectUseCase.canMove(glass))
    }

    @Test
    fun `a greenhouse with something growing in it stays until that is gone`() = runBlocking {
        assertEquals(ActionResult.SUCCESS, sow(GardenItem.SEED_MINT, 5, 5, inGreenhouse = true))
        val removal = StartRemovalUseCase(repo, StoreObjectUseCase(repo), RemoveObjectUseCase(repo))
        assertEquals(ActionResult.IN_USE, removal("glass_5_5", toBag = true, t0))
        assertEquals(ActionResult.IN_USE, removal("glass_5_5", toBag = false, t0))
        repo.objects.values.removeIf { it.isPlant }
        assertEquals(ActionResult.SUCCESS, removal("glass_5_5", toBag = true, t0))
        assertTrue(repo.slots.values.any { it.itemId == GardenItem.FLOOR_GREENHOUSE.itemId })
    }

    @Test
    fun `the new crops are crops like the others`() {
        for (species in PlantSpecies.entries - PlantSpecies.TULIP - PlantSpecies.SUNFLOWER - PlantSpecies.ROSE) {
            assertTrue(GardenItem.entries.any { it.objectId == species.objectId && it.category == ItemCategory.SEED })
            // The hakusai and the nanohana get their page and drawing along with their pictures.
            if (!GardenItem.entries.single { it.objectId == species.objectId }.inShop) continue
            assertTrue(CollectionEntry.fromObjectId(species.objectId)?.category == CollectionCategory.CROPS)
            assertTrue(species.objectId in TileSetDefinition.objectIds)
        }
    }

    // --- Storage ---

    @Test
    fun `progress and letters go into the database and come back unchanged`() {
        val progress = GardenProgress(Season.AUTUMN, TutorialStep.DONE, gardenSize = 11, seasonStartedAt = t0)
        assertEquals(progress, progress.toEntity().toDomain())
        val letter = Letter.coming(Season.WINTER, t0 + season, t0).copy(read = true)
        assertEquals(letter, letter.toEntity().toDomain())
        // A letter of a kind this version does not know is left out rather than shown wrong.
        assertNull(letter.toEntity().copy(kind = "PARCEL").toDomain())
    }

    @Test
    fun `the v10 migration creates the post and the season clock as Room expects them`() {
        val schemas = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val v10 = File(schemas, "com.studioguu.miniaturegarden.data.local.AppDatabase/10.json").readText()
        val expected = Regex("\"tableName\": \"letters\",\\s*\"createSql\": \"([^\"]+)\"").find(v10)?.groupValues?.get(1)
            ?.replace("\\u0060", "`")
            ?.replace("\${TABLE_NAME}", "letters")
        assertEquals(expected, AppDatabase.CREATE_LETTERS)
        val progress = v10.substringAfter("\"tableName\": \"garden_progress\"").substringBefore("\"tableName\"")
        assertTrue(progress.contains("`seasonStartedAt` INTEGER NOT NULL DEFAULT 0"))
    }
}
