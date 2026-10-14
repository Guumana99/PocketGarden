package com.studioguu.miniaturegarden.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studioguu.miniaturegarden.data.local.entity.GardenProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GardenProgressDao {

    @Query("SELECT * FROM garden_progress WHERE id = 1")
    fun observe(): Flow<GardenProgressEntity?>

    @Query("SELECT * FROM garden_progress WHERE id = 1")
    suspend fun get(): GardenProgressEntity?

    @Upsert
    suspend fun upsert(progress: GardenProgressEntity)

    @Query("DELETE FROM garden_progress")
    suspend fun deleteAll()
}
