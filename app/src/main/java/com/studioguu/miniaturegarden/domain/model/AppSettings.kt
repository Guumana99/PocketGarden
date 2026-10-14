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
) {
    /** The same settings with both volumes forced into 0..1; a non-number falls back to the default. */
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
