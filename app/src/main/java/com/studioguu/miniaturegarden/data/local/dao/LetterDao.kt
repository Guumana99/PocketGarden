package com.studioguu.miniaturegarden.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.studioguu.miniaturegarden.data.local.entity.LetterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LetterDao {

    @Query("SELECT * FROM letters ORDER BY sentAt DESC, letterId")
    fun observeAll(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters ORDER BY sentAt DESC, letterId")
    suspend fun getAll(): List<LetterEntity>

    /** @return the new row id, or -1 when a letter with the same id is there already. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(letter: LetterEntity): Long

    @Query("UPDATE letters SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM letters")
    suspend fun deleteAll()
}
