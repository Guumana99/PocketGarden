package com.studioguu.miniaturegarden.domain.model

/**
 * The player's preferences. Volumes run from 0.0 (silent) to 1.0 (full); the enabled flags are
 * the mute switches and leave the chosen volume untouched.
 */
data class AppSettings(
    val bgmEnabled: Boolean = true,
    val bgmVolume: Float = DEFAULT_BGM_VOLUME,
    val soundEffectsEnabled: Boolean = true,
    val soundEffectVolume: Float = DEFAULT_SOUND_EFFECT_VOLUME,
    /**
     * The player's own switch for the garden's notifications. On by default; on Android 13 and
     * later they also need the device's permission (see [NotificationAccess]).
     */
    val notificationsEnabled: Boolean = true,
    /** Whether the one-time invitation to turn notifications on has been shown. */
    val notificationInviteShown: Boolean = false,
    /** How the garden was last zoomed and panned; null until the player has looked around. */
    val gardenView: GardenView? = null,
    /** The order the bag was last sorted in; kept for next time. */
    val inventorySort: InventorySort = InventorySort.DEFAULT,
    /** The first-meeting tips already given ([GardenTip]), by name. */
    val seenTips: Set<String> = emptySet(),
    /** How many different days the garden has been opened on. */
    val playDays: Int = 0,
    /** The last of them ([GameDay]); 0 before the first. */
    val lastPlayDay: Long = 0L,
    /** When the store's review sheet was last asked for; 0 if it never has been. */
    val reviewAskedAt: Long = 0L,
) {
    /** The same settings with both volumes forced into 0..1; a non-number falls back to the default. */
    /** These settings with the garden opened on [day]: a new day counts, the same day does not. */
    fun onDay(day: Long): AppSettings = if (day == lastPlayDay) this else copy(playDays = playDays + 1, lastPlayDay = day)

    fun normalized(): AppSettings = copy(
        bgmVolume = bgmVolume.asVolumeOr(DEFAULT_BGM_VOLUME),
        soundEffectVolume = soundEffectVolume.asVolumeOr(DEFAULT_SOUND_EFFECT_VOLUME),
    )

    private fun Float.asVolumeOr(default: Float): Float = if (isNaN()) default else coerceIn(0f, 1f)

    companion object {
        /** The music is mastered loud, so it starts well below the effects. */
        const val DEFAULT_BGM_VOLUME = 0.35f
        const val DEFAULT_SOUND_EFFECT_VOLUME = 0.9f
    }
}

/**
 * How the garden is looked at: its zoom, and how far its middle sits from the screen's middle
 * in pixels. Kept so that the garden opens as the player left it.
 */
data class GardenView(val scale: Float, val panX: Float, val panY: Float) {
    /** Whether every value is a usable number (a damaged file could hold anything). */
    val isUsable: Boolean get() = scale > 0f && scale.isFinite() && panX.isFinite() && panY.isFinite()
}
