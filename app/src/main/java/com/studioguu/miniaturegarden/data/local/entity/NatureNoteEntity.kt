package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A line of the nature notes: a creature met, or a combination that brought one. Added in schema version 8. */
@Entity(tableName = "nature_notes")
data class NatureNoteEntity(
    /** "creature:<id>" or "combination:<rule>". */
    @PrimaryKey val noteId: String,
    /** Epoch millis of the first time. */
    val firstSeenAt: Long,
    /** How many visits it has been seen on. */
    val count: Int,
)
