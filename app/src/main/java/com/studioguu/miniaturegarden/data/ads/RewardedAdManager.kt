package com.studioguu.miniaturegarden.data.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.studioguu.miniaturegarden.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RewardedAdResult {
    /** Watched to the end: the reward is due. */
    EARNED,

    /** Closed before the end: no reward. */
    CANCELLED,

    /** No ad was loaded, or it could not be shown: no reward. */
    UNAVAILABLE,
}

/**
 * Keeps one rewarded ad loaded and shows it on request. Both rewards share the same ad unit,
 * whose id comes from app/build.gradle.kts.
 *
 * Call from the main thread.
 */
@Singleton
class RewardedAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var ad: RewardedAd? = null
    private var started = false
    private var loading = false
    private var retryDelayMillis = FIRST_RETRY_MILLIS

    private val _isReady = MutableStateFlow(false)

    /** True while an ad is loaded and waiting; buttons that need one follow this. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * Initializes the ads SDK and loads the first ad. Safe to call more than once.
     *
     * The SDK is initialized on a thread of its own, as Google advises: on the main thread it held
     * up the app's start, and so the title, by a second or more (#55). The first ad is then
     * loaded back on the main thread, like every later one.
     */
    fun start() {
        if (started) return
        started = true
        Thread({ MobileAds.initialize(context) { handler.post(::load) } }, "ads-init").start()
    }

    /**
     * Shows the loaded ad.
     *
     * @param onEarned called the moment the reward is earned, while the ad is still on screen.
     *   Grant the reward here, so it is safe even if the app is closed from the ad.
     * @param onClosed called exactly once when the ad is gone and the game is visible again:
     *   the place for anything the player should see. [RewardedAdResult.EARNED] means
     *   [onEarned] ran.
     */
    fun show(activity: Activity, onEarned: () -> Unit, onClosed: (RewardedAdResult) -> Unit) {
        val current = ad
        if (current == null) {
            onClosed(RewardedAdResult.UNAVAILABLE)
            load()
            return
        }
        ad = null
        _isReady.value = false

        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed(if (earned) RewardedAdResult.EARNED else RewardedAdResult.CANCELLED)
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show (${error.code}): ${error.message}")
                onClosed(RewardedAdResult.UNAVAILABLE)
                load()
            }
        }
        current.show(activity) {
            earned = true
            onEarned()
        }
    }

    private fun load() {
        if (loading || ad != null) return
        loading = true
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loading = false
                    ad = loaded
                    retryDelayMillis = FIRST_RETRY_MILLIS
                    _isReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    Log.w(TAG, "Rewarded ad failed to load (${error.code}): ${error.message}")
                    // Offline or no fill: try again later, backing off so as not to hammer the network.
                    handler.postDelayed(::load, retryDelayMillis)
                    retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
                }
            },
        )
    }

    private companion object {
        const val TAG = "RewardedAdManager"
        const val FIRST_RETRY_MILLIS = 15_000L
        const val MAX_RETRY_MILLIS = 5 * 60_000L
    }
}
