package com.studioguu.miniaturegarden.domain.repository

import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface GardenRepository {

    /**
     * Creates the default profile, empty inventory slots, the starter garden ([StarterGarden])
     * and its progress (spring, first steps ahead) on first launch. A garden saved before there
     * was any progress to store is given one with the first steps already done. No-op afterwards.
     */
    suspend fun ensureInitialized()

    /** Whether a garden is saved on this device; false before the very first start. */
    suspend fun hasSavedGarden(): Boolean

    /**
     * Throws the saved garden away entirely: profile, garden, inventory, encyclopedia, nature
     * notes, letters and progress. The next [ensureInitialized] starts a new one.
     */
    suspend fun deleteSavedGarden()

    fun observeProgress(): Flow<GardenProgress?>
    suspend fun getProgress(): GardenProgress?
    suspend fun saveProgress(progress: GardenProgress)

    /** Runs [block] atomically: either all of its writes are stored or none are. */
    suspend fun <T> runInTransaction(block: suspend () -> T): T

    fun observeUserProfile(): Flow<UserProfile?>
    suspend fun getUserProfile(): UserProfile?

    /**
     * Persists [profile]. The save timestamps and the signature are re-issued by the data layer,
     * so whatever the caller passes in those fields is ignored.
     */
    suspend fun saveUserProfile(profile: UserProfile)

    fun observeGridObjects(): Flow<List<GridObject>>
    suspend fun getGridObjects(): List<GridObject>
    suspend fun upsertGridObject(gridObject: GridObject)
    suspend fun upsertGridObjects(gridObjects: List<GridObject>)
    suspend fun deleteGridObject(instanceId: String)

    fun observeInventory(): Flow<List<InventorySlot>>
    suspend fun getInventory(): List<InventorySlot>
    suspend fun upsertInventorySlot(slot: InventorySlot)
    suspend fun upsertInventorySlots(slots: List<InventorySlot>)

    /** Pages of the garden encyclopedia whose condition has been met; the others have no record. */
    fun observeCollection(): Flow<List<CollectionRecord>>
    suspend fun getCollection(): List<CollectionRecord>
    suspend fun upsertCollectionRecord(record: CollectionRecord)

    /** The nature notes: creatures met and the combinations that brought them. */
    fun observeNatureNotes(): Flow<List<NatureNote>>
    suspend fun getNatureNotes(): List<NatureNote>
    suspend fun upsertNatureNote(note: NatureNote)

    /** The post: every letter that has come, newest first. */
    fun observeLetters(): Flow<List<Letter>>
    suspend fun getLetters(): List<Letter>

    /** Puts [letter] in the post, unless one with its id is there already. @return whether it was new. */
    suspend fun postLetter(letter: Letter): Boolean

    suspend fun markLettersRead()
}
