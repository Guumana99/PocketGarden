package com.studioguu.miniaturegarden.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.InventorySort
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Settings kept in a Preferences DataStore file; a missing key means "use the default". */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        // An unreadable file should cost the player their preferences, not the app.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(read(prefs)).normalized()
            prefs[BGM_ENABLED] = updated.bgmEnabled
            prefs[BGM_VOLUME] = updated.bgmVolume
            prefs[SE_ENABLED] = updated.soundEffectsEnabled
            prefs[SE_VOLUME] = updated.soundEffectVolume
            prefs[NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[NOTIFICATION_INVITE_SHOWN] = updated.notificationInviteShown
            prefs[INVENTORY_SORT] = updated.inventorySort.name
            val view = updated.gardenView
            if (view == null) {
                prefs.remove(VIEW_SCALE)
                prefs.remove(VIEW_PAN_X)
                prefs.remove(VIEW_PAN_Y)
            } else {
                prefs[VIEW_SCALE] = view.scale
                prefs[VIEW_PAN_X] = view.panX
                prefs[VIEW_PAN_Y] = view.panY
            }
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            bgmEnabled = prefs[BGM_ENABLED] ?: defaults.bgmEnabled,
            bgmVolume = prefs[BGM_VOLUME] ?: defaults.bgmVolume,
            soundEffectsEnabled = prefs[SE_ENABLED] ?: defaults.soundEffectsEnabled,
            soundEffectVolume = prefs[SE_VOLUME] ?: defaults.soundEffectVolume,
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: defaults.notificationsEnabled,
            notificationInviteShown = prefs[NOTIFICATION_INVITE_SHOWN] ?: defaults.notificationInviteShown,
            gardenView = prefs[VIEW_SCALE]?.let { scale -> GardenView(scale, prefs[VIEW_PAN_X] ?: 0f, prefs[VIEW_PAN_Y] ?: 0f) }
                ?.takeIf { it.isUsable },
            inventorySort = InventorySort.fromName(prefs[INVENTORY_SORT]),
        ).normalized()
    }

    companion object {
        const val FILE_NAME = "settings"

        private val BGM_ENABLED = booleanPreferencesKey("bgm_enabled")
        private val BGM_VOLUME = floatPreferencesKey("bgm_volume")
        private val SE_ENABLED = booleanPreferencesKey("se_enabled")
        private val SE_VOLUME = floatPreferencesKey("se_volume")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val NOTIFICATION_INVITE_SHOWN = booleanPreferencesKey("notification_invite_shown")
        private val VIEW_SCALE = floatPreferencesKey("garden_view_scale")
        private val VIEW_PAN_X = floatPreferencesKey("garden_view_pan_x")
        private val VIEW_PAN_Y = floatPreferencesKey("garden_view_pan_y")
        private val INVENTORY_SORT = stringPreferencesKey("inventory_sort")
    }
}
