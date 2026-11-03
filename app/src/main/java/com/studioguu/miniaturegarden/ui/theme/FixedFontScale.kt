package com.studioguu.miniaturegarden.ui.theme

import android.content.Context
import android.content.res.Configuration

/**
 * The game's words are the size it draws them at, whatever the device's type setting
 * (device feedback 11/03): at 1.3 times the book's buttons folded into a column of characters and
 * its twelve-character lines broke again. [MainActivity][com.studioguu.miniaturegarden.MainActivity]
 * takes its context through [wrap], so every screen, every dialog in a window of its own, and
 * every word drawn from the app's resources read [FONT_SCALE]. Notifications are the system's.
 */
object FixedFontScale {
    const val FONT_SCALE = 1f

    /** [base] with its type scale set to [FONT_SCALE]. */
    fun configuration(base: Configuration): Configuration = Configuration(base).apply { fontScale = FONT_SCALE }

    /** [context] reading [FONT_SCALE] instead of the device's own setting. */
    fun wrap(context: Context): Context = context.createConfigurationContext(configuration(context.resources.configuration))
}
