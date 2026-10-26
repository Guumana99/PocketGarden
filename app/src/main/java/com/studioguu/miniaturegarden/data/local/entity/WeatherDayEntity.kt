package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One day's weather, decided once and kept, so that it never changes and the forecast always
 * comes true. Added in schema version 16.
 */
@Entity(tableName = "weather_days")
data class WeatherDayEntity(
    /** The day ([com.studioguu.miniaturegarden.domain.model.GameDay]): the epoch day of the date it began on. */
    @PrimaryKey val day: Long,
    /** A WeatherType name. */
    val weather: String,
)
