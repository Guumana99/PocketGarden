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
import com.studioguu.miniaturegarden.domain.repository.PracticeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The garden everyone else talks to: the saved one, or a practice garden while [PracticeMode] is
 * on. What belongs to the save as a whole (making it, finding it, deleting it) always goes to the
 * saved one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SwitchingGardenRepository @Inject constructor(
    private val saved: GardenRepositoryImpl,
) : GardenRepository, PracticeMode {
    private val session = MutableStateFlow<PracticeGardenRepository?>(null)
    private val _practising = MutableStateFlow(false)

    private val current: GardenRepository get() = session.value ?: saved

    override val practising: StateFlow<Boolean> = _practising.asStateFlow()

    override fun enter() {
        session.value = PracticeGardenRepository()
        _practising.value = true
    }

    override fun leave() {
        session.value = null
        _practising.value = false
    }

    private fun <T> following(observe: GardenRepository.() -> Flow<T>): Flow<T> =
        session.flatMapLatest { (it ?: saved).observe() }

    override suspend fun ensureInitialized() = saved.ensureInitialized()
    override suspend fun hasSavedGarden(): Boolean = saved.hasSavedGarden()
    override suspend fun deleteSavedGarden() {
        leave()
        saved.deleteSavedGarden()
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = current.runInTransaction(block)

    override fun observeProgress(): Flow<GardenProgress?> = following { observeProgress() }
    override suspend fun getProgress(): GardenProgress? = current.getProgress()
    override suspend fun saveProgress(progress: GardenProgress) = current.saveProgress(progress)

    override fun observeUserProfile(): Flow<UserProfile?> = following { observeUserProfile() }
    override suspend fun getUserProfile(): UserProfile? = current.getUserProfile()
    override suspend fun saveUserProfile(profile: UserProfile) = current.saveUserProfile(profile)

    override fun observeGridObjects(): Flow<List<GridObject>> = following { observeGridObjects() }
    override suspend fun getGridObjects(): List<GridObject> = current.getGridObjects()
    override suspend fun upsertGridObject(gridObject: GridObject) = current.upsertGridObject(gridObject)
    override suspend fun upsertGridObjects(gridObjects: List<GridObject>) = current.upsertGridObjects(gridObjects)
    override suspend fun deleteGridObject(instanceId: String) = current.deleteGridObject(instanceId)

    override fun observeInventory(): Flow<List<InventorySlot>> = following { observeInventory() }
    override suspend fun getInventory(): List<InventorySlot> = current.getInventory()
    override suspend fun upsertInventorySlot(slot: InventorySlot) = current.upsertInventorySlot(slot)
    override suspend fun upsertInventorySlots(slots: List<InventorySlot>) = current.upsertInventorySlots(slots)

    override fun observeCollection(): Flow<List<CollectionRecord>> = following { observeCollection() }
    override suspend fun getCollection(): List<CollectionRecord> = current.getCollection()
    override suspend fun upsertCollectionRecord(record: CollectionRecord) = current.upsertCollectionRecord(record)

    override fun observeNatureNotes(): Flow<List<NatureNote>> = following { observeNatureNotes() }
    override suspend fun getNatureNotes(): List<NatureNote> = current.getNatureNotes()
    override suspend fun upsertNatureNote(note: NatureNote) = current.upsertNatureNote(note)

    override fun observeLetters(): Flow<List<Letter>> = following { observeLetters() }
    override suspend fun getLetters(): List<Letter> = current.getLetters()
    override suspend fun postLetter(letter: Letter): Boolean = current.postLetter(letter)
    override suspend fun markLetterRead(letterId: String) = current.markLetterRead(letterId)

    override suspend fun getWeatherDays(): Map<Long, WeatherType> = current.getWeatherDays()
    override suspend fun keepWeatherDays(days: Map<Long, WeatherType>) = current.keepWeatherDays(days)
    override suspend fun forgetWeatherBefore(day: Long) = current.forgetWeatherBefore(day)
}
