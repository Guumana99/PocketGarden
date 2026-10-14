package com.studioguu.miniaturegarden.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studioguu.miniaturegarden.data.local.entity.NatureNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NatureNoteDao {

    @Query("SELECT * FROM nature_notes")
    fun observeAll(): Flow<List<NatureNoteEntity>>

    @Query("SELECT * FROM nature_notes")
    suspend fun getAll(): List<NatureNoteEntity>

    @Upsert
    suspend fun upsert(note: NatureNoteEntity)

    @Query("DELETE FROM nature_notes")
    suspend fun deleteAll()
}
