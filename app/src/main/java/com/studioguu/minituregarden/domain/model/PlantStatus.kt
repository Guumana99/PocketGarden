package com.studioguu.minituregarden.domain.model

enum class PlantStatus {
    HEALTHY,
    THIRSTY,
    UNNOURISHED,
    WITHERED;

    companion object {
        /** Unknown values (e.g. written by a newer app version) fall back to [HEALTHY]. */
        fun fromName(name: String): PlantStatus = entries.firstOrNull { it.name == name } ?: HEALTHY
    }
}
