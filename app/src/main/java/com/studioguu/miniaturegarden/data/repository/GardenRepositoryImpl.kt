package com.studioguu.miniaturegarden.data.repository

import androidx.room.withTransaction
import com.studioguu.miniaturegarden.data.local.AppDatabase
import com.studioguu.miniaturegarden.data.local.dao.CollectionEntryDao
import com.studioguu.miniaturegarden.data.local.dao.GardenProgressDao
import com.studioguu.miniaturegarden.data.local.dao.GridObjectDao
import com.studioguu.miniaturegarden.data.local.dao.InventorySlotDao
import com.studioguu.miniaturegarden.data.local.dao.LetterDao
import com.studioguu.miniaturegarden.data.local.dao.NatureNoteDao
import com.studioguu.miniaturegarden.data.local.dao.UserProfileDao
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity
import com.studioguu.miniaturegarden.data.local.mapper.toDomain
import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.data.security.SecurityManager
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.StarterGarden
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class GardenRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val userProfileDao: UserProfileDao,
    private val gridObjectDao: GridObjectDao,
    private val inventorySlotDao: InventorySlotDao,
    private val collectionEntryDao: CollectionEntryDao,
    private val gardenProgressDao: GardenProgressDao,
    private val natureNoteDao: NatureNoteDao,
    private val letterDao: LetterDao,
    private val securityManager: SecurityManager,
) : GardenRepository {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override suspend fun ensureInitialized() = database.withTransaction {
        val existing = userProfileDao.get()
        val profile = existing
            ?: securityManager.stampAndSign(UserProfileEntity(coins = UserProfile.STARTING_COINS))
                .also { userProfileDao.upsert(it) }
        if (existing == null) {
            // A new garden: bare lawn and the crate whose seed is the first thing to plant. The
            // player starts out with an empty bag. Its season clock starts at the first sync.
            gridObjectDao.upsertAll(StarterGarden.objects(System.currentTimeMillis()).map { it.toEntity() })
            gardenProgressDao.upsert(GardenProgress.NEW_GARDEN.toEntity())
        } else if (gardenProgressDao.get() == null) {
            // Saved before there was any progress to keep: the player knows the way already.
            gardenProgressDao.upsert(GardenProgress.EXISTING_GARDEN.toEntity())
        }
        inventorySlotDao.insertIfAbsent(
            (0 until profile.inventoryCapacity).map { InventorySlotEntity(slotIndex = it) }
        )
    }

    override suspend fun hasSavedGarden(): Boolean = userProfileDao.get() != null

    override suspend fun deleteSavedGarden() = database.withTransaction {
        gridObjectDao.deleteAll()
        inventorySlotDao.deleteAll()
        collectionEntryDao.deleteAll()
        gardenProgressDao.deleteAll()
        natureNoteDao.deleteAll()
        letterDao.deleteAll()
        userProfileDao.deleteAll()
    }

    override fun observeProgress(): Flow<GardenProgress?> = gardenProgressDao.observe().map { it?.toDomain() }

    override suspend fun getProgress(): GardenProgress? = gardenProgressDao.get()?.toDomain()

    override suspend fun saveProgress(progress: GardenProgress) = gardenProgressDao.upsert(progress.toEntity())

    override fun observeUserProfile(): Flow<UserProfile?> = userProfileDao.observe().map { it?.toDomain() }

    override suspend fun getUserProfile(): UserProfile? = userProfileDao.get()?.toDomain()

    override suspend fun saveUserProfile(profile: UserProfile) =
        userProfileDao.upsert(securityManager.stampAndSign(profile.toEntity()))

    override fun observeGridObjects(): Flow<List<GridObject>> =
        gridObjectDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getGridObjects(): List<GridObject> = gridObjectDao.getAll().map { it.toDomain() }

    override suspend fun upsertGridObject(gridObject: GridObject) = gridObjectDao.upsert(gridObject.toEntity())

    override suspend fun upsertGridObjects(gridObjects: List<GridObject>) =
        gridObjectDao.upsertAll(gridObjects.map { it.toEntity() })

    override suspend fun deleteGridObject(instanceId: String) = gridObjectDao.deleteById(instanceId)

    override fun observeInventory(): Flow<List<InventorySlot>> =
        inventorySlotDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getInventory(): List<InventorySlot> = inventorySlotDao.getAll().map { it.toDomain() }

    override suspend fun upsertInventorySlot(slot: InventorySlot) = inventorySlotDao.upsert(slot.toEntity())

    override suspend fun upsertInventorySlots(slots: List<InventorySlot>) =
        inventorySlotDao.upsertAll(slots.map { it.toEntity() })

    override fun observeCollection(): Flow<List<CollectionRecord>> =
        collectionEntryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getCollection(): List<CollectionRecord> = collectionEntryDao.getAll().map { it.toDomain() }

    override suspend fun upsertCollectionRecord(record: CollectionRecord) =
        collectionEntryDao.upsert(record.toEntity())

    override fun observeNatureNotes(): Flow<List<NatureNote>> =
        natureNoteDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getNatureNotes(): List<NatureNote> = natureNoteDao.getAll().map { it.toDomain() }

    override suspend fun upsertNatureNote(note: NatureNote) = natureNoteDao.upsert(note.toEntity())

    override fun observeLetters(): Flow<List<Letter>> = letterDao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getLetters(): List<Letter> = letterDao.getAll().mapNotNull { it.toDomain() }

    override suspend fun postLetter(letter: Letter): Boolean = letterDao.insertIfAbsent(letter.toEntity()) != -1L

    override suspend fun markLettersRead() = letterDao.markAllRead()
}
