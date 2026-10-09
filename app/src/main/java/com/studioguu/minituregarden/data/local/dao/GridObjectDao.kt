package com.studioguu.minituregarden.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studioguu.minituregarden.data.local.entity.GridObjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GridObjectDao {

    @Query("SELECT * FROM grid_objects")
    fun observeAll(): Flow<List<GridObjectEntity>>

    @Query("SELECT * FROM grid_objects")
    suspend fun getAll(): List<GridObjectEntity>

    @Upsert
    suspend fun upsert(gridObject: GridObjectEntity)

    @Upsert
    suspend fun upsertAll(gridObjects: List<GridObjectEntity>)

    @Query("DELETE FROM grid_objects WHERE instanceId = :instanceId")
    suspend fun deleteById(instanceId: String)
}
