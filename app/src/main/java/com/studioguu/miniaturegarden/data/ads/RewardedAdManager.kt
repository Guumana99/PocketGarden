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

/** The two places an ad is offered; each has its own AdMob ad unit (ids in app/build.gradle.kts). */
enum class AdSlot(val unitId: String) {
    /** The rain cloud ("amagoi"). */
    RAIN(BuildConfig.ADMOB_RAIN_AD_UNIT_ID),

    /** The shop's coin bonus. */
    COIN(BuildConfig.ADMOB_COIN_AD_UNIT_ID),
}

/**
 * Keeps one rewarded ad loaded per [AdSlot] and shows it on request. The reward itself is
 * whatever the game says; the amount AdMob returns with the ad is never read.
 *
 * Call from the main thread.
 */
@Singleton
class RewardedAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private val states = AdSlot.entries.associateWith { SlotState() }

    private class SlotState {
        var ad: RewardedAd? = null
        var loading = false
        var retryDelayMillis = FIRST_RETRY_MILLIS
        var failuresInARow = 0
        val availability = MutableStateFlow(AdAvailability.LOADING)
    }

    /** Whether there is an ad to show for [slot]; buttons that need one follow this. */
    fun availability(slot: AdSlot): StateFlow<AdAvailability> = states.getValue(slot).availability.asStateFlow()

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
        Thread({ MobileAds.initialize(context) { handler.post { AdSlot.entries.forEach(::load) } } }, "ads-init").start()
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
    fun show(slot: AdSlot, activity: Activity, onEarned: () -> Unit, onClosed: (RewardedAdResult) -> Unit) {
        val state = states.getValue(slot)
        val current = state.ad
        if (current == null) {
            onClosed(RewardedAdResult.UNAVAILABLE)
            load(slot)
            return
        }
        state.ad = null
        publish(slot)

        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed(if (earned) RewardedAdResult.EARNED else RewardedAdResult.CANCELLED)
                load(slot)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad ($slot) failed to show (${error.code}): ${error.message}")
                onClosed(RewardedAdResult.UNAVAILABLE)
                load(slot)
            }
        }
        current.show(activity) {
            earned = true
            onEarned()
        }
    }

    private fun load(slot: AdSlot) {
        val state = states.getValue(slot)
        if (state.loading || state.ad != null) return
        state.loading = true
        RewardedAd.load(
            context,
            slot.unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    state.loading = false
                    state.ad = loaded
                    state.retryDelayMillis = FIRST_RETRY_MILLIS
                    state.failuresInARow = 0
                    publish(slot)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    state.loading = false
                    state.failuresInARow++
                    publish(slot)
                    Log.w(TAG, "Rewarded ad ($slot) failed to load (${error.code}): ${error.message}")
                    // Offline or no fill: try again later, backing off so as not to hammer the network.
                    handler.postDelayed({ load(slot) }, state.retryDelayMillis)
                    state.retryDelayMillis = (state.retryDelayMillis * 2).coerceAtMost(MAX_RETRY_MILLIS)
                }
            },
        )
    }

    private fun publish(slot: AdSlot) {
        val state = states.getValue(slot)
        state.availability.value = AdAvailability.of(state.ad != null, state.failuresInARow)
    }

    private companion object {
        const val TAG = "RewardedAdManager"
        const val FIRST_RETRY_MILLIS = 15_000L
        const val MAX_RETRY_MILLIS = 5 * 60_000L
    }
}
