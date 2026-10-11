package com.studioguu.miniaturegarden.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studioguu.miniaturegarden.data.local.entity.CollectionEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionEntryDao {

    @Query("SELECT * FROM collection_entries")
    fun observeAll(): Flow<List<CollectionEntryEntity>>

    @Query("SELECT * FROM collection_entries")
    suspend fun getAll(): List<CollectionEntryEntity>

    @Upsert
    suspend fun upsert(entry: CollectionEntryEntity)
}
