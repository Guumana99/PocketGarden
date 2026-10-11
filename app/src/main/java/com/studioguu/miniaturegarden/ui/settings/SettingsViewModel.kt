package com.studioguu.miniaturegarden.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.BuildConfig
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.data.audio.SoundEffect
import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val audio: AudioManager,
) : ViewModel() {

    // Kept here rather than read straight from the repository so that a slider follows the
    // finger at once instead of waiting for each value to be written and read back.
    private val _settings = MutableStateFlow<AppSettings?>(null)

    /** Null until the saved settings have been read. */
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    val versionName: String = BuildConfig.VERSION_NAME

    init {
        viewModelScope.launch { _settings.value = settingsRepository.settings.first() }
    }

    fun setBgmVolume(volume: Float) = change { it.copy(bgmVolume = volume) }

    fun setBgmEnabled(enabled: Boolean) = change { it.copy(bgmEnabled = enabled) }

    fun setSoundEffectVolume(volume: Float) = change { it.copy(soundEffectVolume = volume) }

    fun setSoundEffectsEnabled(enabled: Boolean) = change { it.copy(soundEffectsEnabled = enabled) }

    fun setNotificationsEnabled(enabled: Boolean) = change { it.copy(notificationsEnabled = enabled) }

    /** Lets the player hear the effect volume they just chose. */
    fun previewSoundEffect() = audio.play(SoundEffect.COIN)

    private fun change(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value ?: return).normalized()
        _settings.value = updated
        // Heard immediately; the save below confirms it a moment later.
        audio.apply(updated)
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
