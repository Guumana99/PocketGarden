package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject

/** What writing a visit into the nature notes added. */
enum class NoteResult {
    /** A creature never met before. */
    NEW_CREATURE,

    /** A creature met before, brought this time by a combination not seen before. */
    NEW_COMBINATION,

    /** Nothing new: the visit is only counted. */
    SEEN_BEFORE,
}

/**
 * Writes a visitor into the nature notes as it comes into view: the creature, and the
 * combination that brought it. Both are counted on every visit; the first visit sets the date.
 */
class RecordNatureNoteUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(rule: VisitRule, now: Long): NoteResult = gardenRepository.runInTransaction {
        val notes = gardenRepository.getNatureNotes().associateBy { it.noteId }
        val creatureKey = NatureNotes.creatureKey(rule.creature)
        val ruleKey = NatureNotes.ruleKey(rule)
        val creature = notes[creatureKey]
        val combination = notes[ruleKey]
        // A creature met for the first time waits in the book as new until its page is looked at.
        gardenRepository.upsertNatureNote(creature?.copy(count = creature.count + 1) ?: NatureNote(creatureKey, now, 1, opened = false))
        gardenRepository.upsertNatureNote(combination?.copy(count = combination.count + 1) ?: NatureNote(ruleKey, now, 1))
        when {
            creature == null -> NoteResult.NEW_CREATURE
            combination == null -> NoteResult.NEW_COMBINATION
            else -> NoteResult.SEEN_BEFORE
        }
    }
}

/** The player turned to [Creature]'s page in the nature notes: it is no longer new. */
class OpenNatureNoteUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
) {
    suspend operator fun invoke(creature: Creature) = gardenRepository.runInTransaction {
        val note = gardenRepository.getNatureNotes().firstOrNull { it.noteId == NatureNotes.creatureKey(creature) }
        if (note != null && !note.opened) gardenRepository.upsertNatureNote(note.copy(opened = true))
    }
}
