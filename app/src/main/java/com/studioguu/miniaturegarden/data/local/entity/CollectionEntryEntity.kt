package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One page of the garden encyclopedia whose condition has been met. Added in schema version 3. */
@Entity(tableName = "collection_entries")
data class CollectionEntryEntity(
    /** Object id the page is about, e.g. "plant_tulip". */
    @PrimaryKey val entryId: String,
    /** Times harvested / placed. */
    val count: Int,
    /** Epoch millis of the first time. */
    val firstObtainedAt: Long,
    /** Whether the player has filled the page in. Added in schema version 4. */
    val revealed: Boolean = false,
)
