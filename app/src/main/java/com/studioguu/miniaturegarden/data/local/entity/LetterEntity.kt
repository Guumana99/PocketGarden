package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A letter in the garden's post. Added in schema version 10. */
@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey val letterId: String,
    /** A LetterKind name. */
    val kind: String,
    /** A Season name. */
    val season: String,
    val sentAt: Long,
    val read: Boolean,
)
