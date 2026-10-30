package com.studioguu.miniaturegarden.data.repository

import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A new garden in memory, for the first steps ([com.studioguu.miniaturegarden.domain.repository.PracticeMode]). */
class PracticeGardenRepository : GardenRepository {
    private val progress = MutableStateFlow<GardenProgress?>(GardenProgress.NEW_GARDEN)
    private val profile = MutableStateFlow<UserProfile?>(UserProfile(coins = UserProfile.STARTING_COINS))
    private val objects = MutableStateFlow<List<GridObject>>(emptyList())
    private val inventory = MutableStateFlow(List(UserProfile().inventoryCapacity) { InventorySlot(it) })
    private val collection = MutableStateFlow<List<CollectionRecord>>(emptyList())
    private val notes = MutableStateFlow<List<NatureNote>>(emptyList())
    private val letters = MutableStateFlow<List<Letter>>(emptyList())

    override suspend fun ensureInitialized() = Unit
    override suspend fun hasSavedGarden(): Boolean = true
    override suspend fun deleteSavedGarden() = Unit

    override fun observeProgress(): Flow<GardenProgress?> = progress
    override suspend fun getProgress(): GardenProgress? = progress.value
    override suspend fun saveProgress(progress: GardenProgress) {
        this.progress.value = progress
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()

    override fun observeUserProfile(): Flow<UserProfile?> = profile
    override suspend fun getUserProfile(): UserProfile? = profile.value
    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile.value = profile
    }

    override fun observeGridObjects(): Flow<List<GridObject>> = objects
    override suspend fun getGridObjects(): List<GridObject> = objects.value
    override suspend fun upsertGridObject(gridObject: GridObject) = upsertGridObjects(listOf(gridObject))
    override suspend fun upsertGridObjects(gridObjects: List<GridObject>) {
        val ids = gridObjects.mapTo(HashSet()) { it.instanceId }
        objects.update { all -> all.filterNot { it.instanceId in ids } + gridObjects }
    }

    override suspend fun deleteGridObject(instanceId: String) {
        objects.update { all -> all.filterNot { it.instanceId == instanceId } }
    }

    override fun observeInventory(): Flow<List<InventorySlot>> = inventory
    override suspend fun getInventory(): List<InventorySlot> = inventory.value
    override suspend fun upsertInventorySlot(slot: InventorySlot) = upsertInventorySlots(listOf(slot))
    override suspend fun upsertInventorySlots(slots: List<InventorySlot>) {
        val byIndex = slots.associateBy { it.slotIndex }
        inventory.update { all -> all.map { byIndex[it.slotIndex] ?: it } }
    }

    override fun observeCollection(): Flow<List<CollectionRecord>> = collection
    override suspend fun getCollection(): List<CollectionRecord> = collection.value
    override suspend fun upsertCollectionRecord(record: CollectionRecord) {
        collection.update { all -> all.filterNot { it.entryId == record.entryId } + record }
    }

    override fun observeNatureNotes(): Flow<List<NatureNote>> = notes
    override suspend fun getNatureNotes(): List<NatureNote> = notes.value
    override suspend fun upsertNatureNote(note: NatureNote) {
        notes.update { all -> all.filterNot { it.noteId == note.noteId } + note }
    }

    override fun observeLetters(): Flow<List<Letter>> = letters
    override suspend fun getLetters(): List<Letter> = letters.value
    override suspend fun postLetter(letter: Letter): Boolean = false
    override suspend fun markLetterRead(letterId: String) = Unit

    override suspend fun getWeatherDays(): Map<Long, WeatherType> = emptyMap()
    override suspend fun keepWeatherDays(days: Map<Long, WeatherType>) = Unit
    override suspend fun forgetWeatherBefore(day: Long) = Unit
}
