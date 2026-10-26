package com.studioguu.miniaturegarden.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.studioguu.miniaturegarden.data.local.entity.WeatherDayEntity

@Dao
interface WeatherDayDao {

    @Query("SELECT * FROM weather_days ORDER BY day")
    suspend fun getAll(): List<WeatherDayEntity>

    /** A day already decided keeps its weather. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(days: List<WeatherDayEntity>)

    @Query("DELETE FROM weather_days WHERE day < :day")
    suspend fun deleteBefore(day: Long)

    @Query("DELETE FROM weather_days")
    suspend fun deleteAll()
}
