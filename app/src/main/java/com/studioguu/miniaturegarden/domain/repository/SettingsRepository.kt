package com.studioguu.miniaturegarden.domain.repository

import com.studioguu.miniaturegarden.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    /** The saved settings, then every change. Emits the defaults when nothing has been saved. */
    val settings: Flow<AppSettings>

    /**
     * Changes the saved settings. [transform] receives the current values and returns the new
     * ones; updates are applied one after another, so two quick changes cannot overwrite each other.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
