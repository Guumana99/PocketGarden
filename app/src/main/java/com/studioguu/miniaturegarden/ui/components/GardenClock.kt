package com.studioguu.miniaturegarden.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The time the garden is drawn at ([GardenCanvas]): where the visitors are, how far a job has
 * got. The game reads the device's clock ([SystemGardenClock]); something drawing the garden
 * frame by frame at its own pace (the promo build's recorder) hands in a clock of its own.
 */
interface GardenClock {
    /** Now, in epoch millis. */
    fun nowMillis(): Long

    /**
     * Whether the garden may wait between frames of wind to save battery ([GardenCanvas]). A
     * clock that is stepped one frame at a time says no: every frame it is given is drawn.
     */
    val throttles: Boolean get() = true
}

/** The device's own clock: the game's. */
object SystemGardenClock : GardenClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** The clock [GardenCanvas] draws by; the device's unless something provides another. */
val LocalGardenClock = staticCompositionLocalOf<GardenClock> { SystemGardenClock }
