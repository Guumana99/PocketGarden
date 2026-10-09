package com.studioguu.miniaturegarden.domain.repository

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface GardenRepository {

    /** Creates the default profile and empty inventory slots on first launch; no-op afterwards. */
    suspend fun ensureInitialized()

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
}
