package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.entity.NatureNoteEntity
import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisits
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenSnapshot
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.VisitCondition
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The seasons in the visitor table, and the nature notes remembering what is new. */
class NatureNotesEngineTest {

    private val t0 = 1_700_000_000_000L
    private val sunny = WeatherType.SUNNY
    private var nextId = 0

    private fun at(x: Int, y: Int, objectId: String) =
        GridObject(instanceId = "o${nextId++}", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = 0L)

    private fun river(vararg xs: Int) = xs.map { at(it, 4, GridObject.RIVER_OBJECT_ID) }

    private fun visitors(objects: List<GridObject>, season: Season, weather: WeatherType = sunny, periods: Int = 300) =
        (0 until periods).flatMap { CreatureVisits.visitorsAt(it * CreatureVisits.PERIOD_MILLIS + 1, objects, weather, GardenMap.DEFAULT, season) }

    // --- Seasons in the table ---

    @Test
    fun `a season condition holds in its seasons only`() {
        val autumn = VisitCondition.SeasonIs(setOf(Season.AUTUMN))
        assertTrue(autumn.isMetBy(GardenSnapshot(emptyList(), sunny, Season.AUTUMN)))
        for (s in Season.entries - Season.AUTUMN) assertFalse(s.name, autumn.isMetBy(GardenSnapshot(emptyList(), sunny, s)))
        // A garden seen without a season is in spring, as a new garden is.
        assertEquals(Season.SPRING, GardenSnapshot(emptyList(), sunny).season)
    }

    @Test
    fun `the seasonal combinations come only in their season`() {
        val rock = listOf(at(5, 5, "deco_rock"))
        val stream = river(0, 1, 2)
        for (s in Season.entries) {
            assertEquals(s.name, s == Season.SUMMER, VisitRule.FROG_SUMMER.isMetBy(GardenSnapshot(stream, sunny, s)))
            assertEquals(s.name, s == Season.AUTUMN, VisitRule.SQUIRREL_AUTUMN.isMetBy(GardenSnapshot(rock, sunny, s)))
            assertEquals(s.name, s == Season.AUTUMN, VisitRule.DRAGONFLY_AUTUMN.isMetBy(GardenSnapshot(stream, sunny, s)))
        }
        // The dragonfly keeps away from the rain; the frog of summer does not mind it.
        assertFalse(VisitRule.DRAGONFLY_AUTUMN.isMetBy(GardenSnapshot(stream, WeatherType.RAINY, Season.AUTUMN)))
        assertTrue(VisitRule.FROG_SUMMER.isMetBy(GardenSnapshot(stream, WeatherType.RAINY, Season.SUMMER)))
    }

    @Test
    fun `a river in autumn brings the dragonfly, and in spring it never comes`() {
        val stream = river(0, 1, 2)
        val autumn = visitors(stream, Season.AUTUMN)
        assertTrue(autumn.any { it.creature == Creature.DRAGONFLY })
        // It hovers over the water itself.
        autumn.filter { it.creature == Creature.DRAGONFLY }.forEach { assertEquals(GridObject.RIVER_OBJECT_ID, it.host?.objectId) }
        assertTrue(visitors(stream, Season.SPRING).none { it.creature == Creature.DRAGONFLY })
    }

    @Test
    fun `a rock alone draws the squirrel in autumn, and nobody in spring`() {
        val rock = listOf(at(5, 5, "deco_rock"))
        assertTrue(visitors(rock, Season.AUTUMN).any { it.rule == VisitRule.SQUIRREL_AUTUMN })
        assertTrue(visitors(rock, Season.SPRING).isEmpty())
    }

    @Test
    fun `the older combinations keep their places, so who comes when does not change`() {
        // A visit is rolled from the combination's place in the table: new ones go at the end.
        assertEquals(
            listOf(
                VisitRule.BUTTERFLY_BLOOMS, VisitRule.LADYBUG_TULIPS, VisitRule.BIRD_BENCH, VisitRule.BIRD_COTTAGE,
                VisitRule.SQUIRREL_SUNFLOWER, VisitRule.FROG_RAIN,
            ),
            VisitRule.entries.take(6),
        )
        assertEquals(Creature.FROG, Creature.entries[4])
        assertEquals(Creature.DRAGONFLY, Creature.entries.last())
    }

    @Test
    fun `every creature has a season it can be met in, and every season has someone`() {
        for (s in Season.entries) {
            assertTrue(s.name, VisitRule.entries.any { rule -> rule.conditions.none { it is VisitCondition.SeasonIs && s !in it.seasons } })
        }
        for (creature in Creature.entries) {
            assertTrue(creature.name, VisitRule.forCreature(creature).isNotEmpty())
        }
    }

    // --- New in the book ---

    private val repo = FakeGardenRepository().apply { profile = UserProfile() }
    private val record = RecordNatureNoteUseCase(repo)
    private val open = OpenNatureNoteUseCase(repo)

    private fun notes() = NatureNotes(repo.natureNotes.values.toList())

    @Test
    fun `a creature met for the first time is new until its page is opened`() = runBlocking {
        record(VisitRule.FROG_SUMMER, t0)
        assertTrue(notes().isNew(Creature.FROG))
        assertEquals(1, notes().unopened)

        open(Creature.FROG)
        assertFalse(notes().isNew(Creature.FROG))
        assertEquals(0, notes().unopened)
        // Opening keeps the rest of the note as it was.
        assertEquals(NatureNote(NatureNotes.creatureKey(Creature.FROG), t0, 1, opened = true), notes().noteOf(Creature.FROG))
    }

    @Test
    fun `meeting it again, or for another reason, does not make it new again`() = runBlocking {
        record(VisitRule.FROG_RAIN, t0)
        open(Creature.FROG)
        assertEquals(NoteResult.SEEN_BEFORE, record(VisitRule.FROG_RAIN, t0 + 1_000))
        assertEquals(NoteResult.NEW_COMBINATION, record(VisitRule.FROG_SUMMER, t0 + 2_000))
        assertFalse(notes().isNew(Creature.FROG))
        assertEquals(3, notes().noteOf(Creature.FROG)?.count)
    }

    @Test
    fun `each new creature is counted once, and opening one leaves the others new`() = runBlocking {
        record(VisitRule.DRAGONFLY_AUTUMN, t0)
        record(VisitRule.SQUIRREL_AUTUMN, t0)
        record(VisitRule.SQUIRREL_SUNFLOWER, t0)
        assertEquals(2, notes().unopened)
        open(Creature.SQUIRREL)
        assertEquals(1, notes().unopened)
        assertTrue(notes().isNew(Creature.DRAGONFLY))
    }

    @Test
    fun `opening a page never met, or one already opened, writes nothing`() = runBlocking {
        open(Creature.BIRD)
        assertTrue(repo.natureNotes.isEmpty())
        record(VisitRule.BIRD_BENCH, t0)
        open(Creature.BIRD)
        val before = repo.natureNotes.toMap()
        open(Creature.BIRD)
        assertEquals(before, repo.natureNotes.toMap())
    }

    // --- Storage and the move to schema 11 ---

    @Test
    fun `whether a note is new goes into the database and comes back`() {
        val fresh = NatureNote("creature:dragonfly", t0, 1, opened = false)
        assertEquals(NatureNoteEntity("creature:dragonfly", t0, 1, opened = false), fresh.toEntity())
        assertEquals(fresh, fresh.toEntity().toDomain())
        // A line written before schema 11 counts as looked at.
        assertTrue(NatureNoteEntity("creature:bird", t0, 3).toDomain().opened)
    }

    private fun mainSource(path: String) =
        listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }.resolve(path).readText()

    @Test
    fun `the move to schema 11 adds the column, with everything met before counted as looked at`() {
        val database = mainSource("java/com/studioguu/miniaturegarden/data/local/AppDatabase.kt")
        assertTrue(database.contains("version = 11"))
        assertTrue(database.contains("MIGRATION_10_11"))
        assertTrue(database.contains("ALTER TABLE nature_notes ADD COLUMN opened INTEGER NOT NULL DEFAULT 1"))
        assertTrue(mainSource("java/com/studioguu/miniaturegarden/di/DatabaseModule.kt").contains("MIGRATION_10_11"))

        // Room's own record of schema 11 agrees with the migration: the same column, the same default.
        val schemas = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val v11 = File(schemas, "com.studioguu.miniaturegarden.data.local.AppDatabase/11.json").readText()
        val createSql = Regex("\"tableName\": \"nature_notes\",\\s*\"createSql\": \"([^\"]+)\"").find(v11)?.groupValues?.get(1)
        assertTrue(createSql, createSql!!.contains("`opened` INTEGER NOT NULL DEFAULT 1"))
    }
}
