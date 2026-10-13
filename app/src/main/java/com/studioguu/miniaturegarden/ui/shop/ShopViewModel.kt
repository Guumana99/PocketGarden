package com.studioguu.miniaturegarden.ui.shop

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.data.ads.RewardedAdManager
import com.studioguu.miniaturegarden.data.ads.RewardedAdResult
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.data.audio.SoundEffect
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.RewardType
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.usecase.ActionResult
import com.studioguu.miniaturegarden.domain.usecase.GrantRewardUseCase
import com.studioguu.miniaturegarden.domain.usecase.PurchaseItemUseCase
import com.studioguu.miniaturegarden.domain.usecase.RewardResult
import com.studioguu.miniaturegarden.ui.components.todayEpochDay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A line of text shown inside the shop; [good] decides its colour. */
data class ShopNotice(@param:StringRes val message: Int, val argument: Int? = null, val good: Boolean = false) {
    @Composable
    fun text(): String = if (argument == null) stringResource(message) else stringResource(message, argument)
}

/** Result of the most recent purchase attempt, shown inside the shop. */
data class PurchaseFeedback(val item: GardenItem, val result: ActionResult)

@HiltViewModel
class ShopViewModel @Inject constructor(
    gardenRepository: GardenRepository,
    private val purchaseItem: PurchaseItemUseCase,
    private val audio: AudioManager,
    private val grantReward: GrantRewardUseCase,
    private val rewardedAds: RewardedAdManager,
    rewardLimits: RewardLimitRepository,
) : ViewModel() {

    val items: List<GardenItem> = GardenItem.entries

    val coins: StateFlow<Long> = gardenRepository.observeUserProfile()
        .map { it?.coins ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Decides which items are still locked. */
    val playerStats: StateFlow<PlayerStats> =
        combine(gardenRepository.observeUserProfile(), gardenRepository.observeCollection()) { profile, pages ->
            // Some items are opened by the encyclopedia rather than by level.
            (profile?.stats ?: NEW_PLAYER).copy(collectionPages = CollectionProgress(pages).revealed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NEW_PLAYER)

    private val _feedback = MutableStateFlow<PurchaseFeedback?>(null)
    val feedback: StateFlow<PurchaseFeedback?> = _feedback.asStateFlow()

    /** Coin bonuses still available today. */
    val bonusRemaining: StateFlow<Int> = rewardLimits.usage
        .map { it.remaining(RewardType.COIN_BONUS, todayEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val adReady: StateFlow<Boolean> = rewardedAds.isReady

    private val _notice = MutableStateFlow<ShopNotice?>(null)

    /** Outcome of the latest bonus claim; shown in place of the purchase message. */
    val notice: StateFlow<ShopNotice?> = _notice.asStateFlow()

    /**
     * Shows the ad and, only if it is watched to the end, pays the coin bonus. The coins are
     * paid as soon as the reward is earned; the message waits until the ad has closed.
     */
    fun claimBonus(activity: Activity) {
        _feedback.value = null
        _notice.value = null
        var grant: Deferred<RewardResult>? = null
        rewardedAds.show(
            activity,
            onEarned = { grant = viewModelScope.async { grantReward.coinBonus(todayEpochDay()) } },
            onClosed = { result ->
                when (result) {
                    RewardedAdResult.EARNED -> viewModelScope.launch {
                        val granted = grant?.await() == RewardResult.GRANTED
                        if (granted) audio.play(SoundEffect.COIN)
                        _notice.value = if (granted) {
                            ShopNotice(R.string.msg_bonus_done, RewardType.COIN_BONUS_AMOUNT, good = true)
                        } else {
                            ShopNotice(R.string.msg_reward_limit)
                        }
                    }
                    RewardedAdResult.CANCELLED -> _notice.value = ShopNotice(R.string.msg_reward_cancelled)
                    RewardedAdResult.UNAVAILABLE -> _notice.value = ShopNotice(R.string.msg_reward_unavailable)
                }
            },
        )
    }

    fun buy(item: GardenItem) {
        _notice.value = null
        viewModelScope.launch {
            val result = purchaseItem(item)
            audio.play(if (result == ActionResult.SUCCESS) SoundEffect.COIN else SoundEffect.ERROR)
            _feedback.value = PurchaseFeedback(item, result)
        }
    }

    fun clearFeedback() {
        _feedback.value = null
        _notice.value = null
    }

    private companion object {
        val NEW_PLAYER = PlayerStats(level = 1, totalXp = 0L)
    }
}
