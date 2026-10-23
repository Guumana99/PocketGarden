package com.studioguu.miniaturegarden

import android.app.Application
import com.studioguu.miniaturegarden.data.ads.RewardedAdManager
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.data.notifications.WorkManagerNotificationScheduler
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

@HiltAndroidApp
class PocketGardenApplication : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var audio: AudioManager
    @Inject lateinit var rewardedAds: RewardedAdManager
    @Inject lateinit var assets: AssetRepository

    private val applicationScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        // The artwork is the slowest thing the title waits for (the atlas is a large picture):
        // its decoding starts first, off the main thread, while the rest of the app gets ready.
        val titleArt = assets.prefetch(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        // Silent until the saved settings are read, so the music never starts at the default
        // volume, or at all for a player who muted it. They are read off the main thread: reading
        // them synchronously here held up the first frame on a cold start.
        audio.holdUntilSettings()
        // From the first value on, every saved change reaches the players as it happens.
        applicationScope.launch {
            settingsRepository.settings.flowOn(Dispatchers.IO).collect(audio::apply)
        }
        // Have a rewarded ad loading in the background, so its buttons are ready when wanted. Not
        // before the title's artwork is read: the ads SDK starting up beside it slowed the sample
        // garden's arrival by seconds on a small device (#55).
        applicationScope.launch {
            titleArt.join()
            rewardedAds.start()
        }
        // The garden's notification channel exists from the start, so the device's settings show it.
        WorkManagerNotificationScheduler.ensureChannel(this)
    }
}
