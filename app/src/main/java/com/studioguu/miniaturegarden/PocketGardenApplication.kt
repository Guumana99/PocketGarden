package com.studioguu.miniaturegarden

import android.app.Application
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class PocketGardenApplication : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var audio: AudioManager

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        // Read once, synchronously, before any screen exists: otherwise the music could start at
        // the default volume, or start at all for a player who muted it, until the saved settings
        // arrive a moment later. It is one small file.
        audio.apply(runBlocking { settingsRepository.settings.first() })
        // From here on every saved change reaches the players as it happens.
        applicationScope.launch { settingsRepository.settings.collect(audio::apply) }
    }
}
