package com.studioguu.miniaturegarden.ui.debug

import com.studioguu.miniaturegarden.BuildConfig
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A fixed look of the garden for promotional pictures (docs/promo). Only the look changes: the
 * stored season, the clock and the weather diary go on as they are.
 *
 * @property season shown in place of the garden's own; null for its own.
 * @property timeOfDay the garden is lit for; null for the clock's.
 * @property weather in place of the day's; null for the day's own.
 * @property hideUi nothing on screen but the garden: no figures, buttons, panels or system bars.
 * @property creatures which kinds may visit; null for any.
 * @property view the zoom and pan to show the garden at; null for the usual.
 * @property butterfly a butterfly that comes now for the plant at this cell, whatever the dice say.
 * @property butterflyAt when it sets off; set by [ShowcaseControls.set].
 */
data class Showcase(
    val season: Season? = null,
    val timeOfDay: TimeOfDay? = null,
    val weather: WeatherType? = null,
    val hideUi: Boolean = false,
    val creatures: Set<Creature>? = null,
    val view: GardenView? = null,
    val butterfly: GridCell? = null,
    val butterflyAt: Long = 0L,
) {
    /**
     * The visitors to show: [visits] of the kinds allowed, and the butterfly asked for, if there
     * is a plant at its cell in [objects].
     */
    fun visitors(visits: List<CreatureVisit>, objects: List<GridObject>): List<CreatureVisit> {
        val allowed = creatures?.let { kinds -> visits.filter { it.creature in kinds } } ?: visits
        val cell = butterfly ?: return allowed
        val host = objects.firstOrNull { it.isPlant && it.gridX == cell.x && it.gridY == cell.y } ?: return allowed
        val summoned = CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, cell, host, butterflyAt, butterflyAt + BUTTERFLY_STAY_MILLIS)
        return allowed.filter { it.creature != Creature.BUTTERFLY } + summoned
    }

    companion object {
        /**
         * How long the summoned butterfly is about, from setting off outside the garden to having
         * left it: some 6.5 seconds each way in an 11 x 11 garden, and a little time at the flower.
         */
        const val BUTTERFLY_STAY_MILLIS = 20_000L
    }
}

/** Where the showcase is set: by the debug build's receiver only (src/debug), never in a release build. */
object ShowcaseControls {
    private val _showcase = MutableStateFlow<Showcase?>(null)
    val showcase: StateFlow<Showcase?> = _showcase.asStateFlow()

    /** Sets the showcase (null for the garden as it is). Does nothing in a release build. */
    fun set(showcase: Showcase?, now: Long = System.currentTimeMillis()) {
        if (!BuildConfig.DEBUG) return
        _showcase.value = showcase?.let { if (it.butterfly != null) it.copy(butterflyAt = now) else it }
    }
}
