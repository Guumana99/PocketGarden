package com.studioguu.miniaturegarden.data.ads

/**
 * Where the rewarded ads stand, for the buttons that need one. Only [UNAVAILABLE] changes how a
 * button looks: the next ad is always on its way for a moment after one has been shown (and at
 * the start), and a button that followed that would flicker with every ad.
 */
enum class AdAvailability {
    /** An ad is loaded and waiting. */
    READY,

    /** The next ad is on its way, as far as anyone can tell. */
    LOADING,

    /** Loading has failed [FAILURES_BEFORE_UNAVAILABLE] times in a row. It goes on trying. */
    UNAVAILABLE;

    companion object {
        /** A single failure says little (no fill, a dropped connection): some 45 seconds of them say more. */
        const val FAILURES_BEFORE_UNAVAILABLE = 3

        fun of(loaded: Boolean, failuresInARow: Int): AdAvailability = when {
            loaded -> READY
            failuresInARow >= FAILURES_BEFORE_UNAVAILABLE -> UNAVAILABLE
            else -> LOADING
        }
    }
}
