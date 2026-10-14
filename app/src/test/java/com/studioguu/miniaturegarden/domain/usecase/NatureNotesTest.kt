package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.data.local.AppDatabase
import com.studioguu.miniaturegarden.data.local.entity.NatureNoteEntity
import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.VisitRule
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NatureNotesTest {

    private val t0 = 1_700_000_000_000L
    private val repo = FakeGardenRepository().apply { profile = UserProfile() }
    private val record = RecordNatureNoteUseCase(repo)

    private fun notes() = NatureNotes(repo.natureNotes.values.toList())

    // --- Writing visits down ---

    @Test
    fun `the first visit of a creature writes it and its combination down`() = runBlocking {
        assertEquals(NoteResult.NEW_CREATURE, record(VisitRule.BUTTERFLY_BLOOMS, t0))

        val notes = notes()
        assertTrue(notes.hasMet(Creature.BUTTERFLY))
        assertTrue(notes.hasFound(VisitRule.BUTTERFLY_BLOOMS))
        assertFalse(notes.hasMet(Creature.BIRD))
        assertEquals(1, notes.met)
        assertEquals(NatureNote(NatureNotes.creatureKey(Creature.BUTTERFLY), t0, 1), notes.noteOf(Creature.BUTTERFLY))
    }

    @Test
    fun `later visits are counted, and the first date stays`() = runBlocking {
        record(VisitRule.BUTTERFLY_BLOOMS, t0)
        assertEquals(NoteResult.SEEN_BEFORE, record(VisitRule.BUTTERFLY_BLOOMS, t0 + 1_000))
        assertEquals(NoteResult.SEEN_BEFORE, record(VisitRule.BUTTERFLY_BLOOMS, t0 + 2_000))

        val note = notes().noteOf(Creature.BUTTERFLY)!!
        assertEquals(t0, note.firstSeenAt)
        assertEquals(3, note.count)
        assertEquals(3, repo.natureNotes[NatureNotes.ruleKey(VisitRule.BUTTERFLY_BLOOMS)]?.count)
    }

    @Test
    fun `a known creature coming for a new reason adds that combination`() = runBlocking {
        record(VisitRule.BIRD_BENCH, t0)
        assertFalse(notes().hasFound(VisitRule.BIRD_COTTAGE))

        assertEquals(NoteResult.NEW_COMBINATION, record(VisitRule.BIRD_COTTAGE, t0 + 5_000))

        val notes = notes()
        assertTrue(notes.hasFound(VisitRule.BIRD_BENCH) && notes.hasFound(VisitRule.BIRD_COTTAGE))
        assertEquals(1, notes.met)
        assertEquals(2, notes.noteOf(Creature.BIRD)?.count)
    }

    @Test
    fun `nothing written means nothing met`() {
        val notes = NatureNotes(emptyList())
        assertEquals(0, notes.met)
        assertNull(notes.noteOf(Creature.FROG))
        Creature.entries.forEach { assertFalse(notes.hasMet(it)) }
        VisitRule.entries.forEach { assertFalse(notes.hasFound(it)) }
    }

    @Test
    fun `every creature and combination has a key of its own`() {
        val keys = Creature.entries.map(NatureNotes::creatureKey) + VisitRule.entries.map(NatureNotes::ruleKey)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `starting over forgets the nature notes too`() = runBlocking {
        record(VisitRule.FROG_RAIN, t0)
        repo.deleteSavedGarden()
        assertEquals(0, notes().met)
    }

    // --- How they are stored ---

    @Test
    fun `a note goes into the database and comes back unchanged`() {
        val note = NatureNote("creature:bird", t0, 7)
        assertEquals(NatureNoteEntity("creature:bird", t0, 7), note.toEntity())
        assertEquals(note, note.toEntity().toDomain())
    }

    @Test
    fun `the migration creates the table exactly as Room expects it`() {
        val schemas = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        val v8 = File(schemas, "com.studioguu.miniaturegarden.data.local.AppDatabase/8.json").readText()
        val expected = Regex("\"tableName\": \"nature_notes\",\\s*\"createSql\": \"([^\"]+)\"").find(v8)?.groupValues?.get(1)
            ?.replace("\\u0060", "`")
            ?.replace("\${TABLE_NAME}", "nature_notes")
        assertEquals(expected, AppDatabase.CREATE_NATURE_NOTES)
    }
}
