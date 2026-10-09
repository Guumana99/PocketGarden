package com.studioguu.minituregarden.data.repository

import androidx.room.withTransaction
import com.studioguu.minituregarden.data.local.AppDatabase
import com.studioguu.minituregarden.data.local.dao.GridObjectDao
import com.studioguu.minituregarden.data.local.dao.InventorySlotDao
import com.studioguu.minituregarden.data.local.dao.UserProfileDao
import com.studioguu.minituregarden.data.local.entity.InventorySlotEntity
import com.studioguu.minituregarden.data.local.entity.UserProfileEntity
import com.studioguu.minituregarden.data.local.mapper.toDomain
import com.studioguu.minituregarden.data.local.mapper.toEntity
import com.studioguu.minituregarden.data.security.SecurityManager
import com.studioguu.minituregarden.domain.model.GridObject
import com.studioguu.minituregarden.domain.model.InventorySlot
import com.studioguu.minituregarden.domain.model.UserProfile
import com.studioguu.minituregarden.domain.repository.GardenRepository
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
    private val securityManager: SecurityManager,
) : GardenRepository {

    override suspend fun ensureInitialized() = database.withTransaction {
        val profile = userProfileDao.get()
            ?: securityManager.stampAndSign(UserProfileEntity()).also { userProfileDao.upsert(it) }
        inventorySlotDao.insertIfAbsent(
            (0 until profile.inventoryCapacity).map { InventorySlotEntity(slotIndex = it) }
        )
    }

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
}
