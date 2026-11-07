package com.studioguu.miniaturegarden.ui.debug

import com.studioguu.miniaturegarden.BuildConfig
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenView
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TimeOfDay
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.data.ads.AdAvailability
import com.studioguu.miniaturegarden.ui.hud.RainCloudState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A fixed look of the garden for promotional pictures (docs/promo). Only the look changes: the
 * stored season, the clock and the weather diary go on as they are.
 *
 * @property season shown in place of the garden's own; null for its own.
 * @property timeOfDay the garden is lit for; null for the clock's.
 * @property clockHour what the device's clock is taken to say; null for what it says. Unlike
 *   [timeOfDay] it gives way to what the game fixes, as the first steps' morning.
 * @property weather in place of the day's; null for the day's own.
 * @property hideUi nothing on screen but the garden: no figures, buttons, panels or system bars.
 * @property creatures which kinds may visit; null for any.
 * @property view the zoom and pan to show the garden at; null for the usual.
 * @property butterfly a butterfly that comes now for the plant at this cell, whatever the dice say.
 * @property butterflyAt when it sets off; set by [ShowcaseControls.set].
 * @property adReady whether an ad is taken to be waiting; null for what is so.
 * @property bonusCoins a coin bonus taken to have just been paid in the shop; null for none.
 * @property cloud what the rain cloud shows, in place of what is so.
 * @property rainGlint how far on the crops' glint after a shower stands, 0 to 1; null for none.
 * @property bird a bird that comes now, whatever the dice say: to the first building of this id (the well or
 *   the cottage), or onto the ground about the cell "x,y".
 * @property birdAt when it comes; set by [ShowcaseControls.set].
 * @property hoe the hoe in hand, so that taps on the garden till it with nothing on screen but the garden.
 */
data class Showcase(
    val season: Season? = null,
    val timeOfDay: TimeOfDay? = null,
    val clockHour: Float? = null,
    val weather: WeatherType? = null,
    val hideUi: Boolean = false,
    val creatures: Set<Creature>? = null,
    val view: GardenView? = null,
    val butterfly: GridCell? = null,
    val butterflyAt: Long = 0L,
    val adReady: Boolean? = null,
    val bonusCoins: Int? = null,
    val cloud: RainCloudState? = null,
    val rainGlint: Float? = null,
    val hoe: Boolean = false,
    val bird: String? = null,
    val birdAt: Long = 0L,
) {
    /**
     * The visitors to show: [visits] of the kinds allowed, and the butterfly asked for, if there
     * is a plant at its cell in [objects].
     */
    fun visitors(visits: List<CreatureVisit>, objects: List<GridObject>): List<CreatureVisit> {
        val kept = creatures?.let { kinds -> visits.filter { it.creature in kinds } } ?: visits
        val allowed = calledBird(objects)?.let { called -> kept.filter { it.creature != Creature.BIRD } + called } ?: kept
        val cell = butterfly ?: return allowed
        val host = objects.firstOrNull { it.isPlant && it.gridX == cell.x && it.gridY == cell.y } ?: return allowed
        val summoned = CreatureVisit(VisitRule.BUTTERFLY_BLOOMS, cell, host, butterflyAt, butterflyAt + BUTTERFLY_STAY_MILLIS)
        return allowed.filter { it.creature != Creature.BUTTERFLY } + summoned
    }

    /**
     * The bird asked for by [bird]: at the first building of that id in [objects], or walking on
     * the ground about a cell given as "x,y"; null for none.
     */
    private fun calledBird(objects: List<GridObject>): CreatureVisit? {
        val id = bird ?: return null
        val cell = id.split(',').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 }
        if (cell != null) return CreatureVisit(VisitRule.BIRD_FENCE, GridCell(cell[0], cell[1]), null, birdAt, birdAt + BIRD_STAY_MILLIS)
        val host = objects.firstOrNull { it.objectId == id } ?: return null
        val rule = if (id == StructureData.COTTAGE.objectId) VisitRule.BIRD_COTTAGE else VisitRule.BIRD_WELL
        return CreatureVisit(rule, GridCell(host.gridX, host.gridY), host, birdAt, birdAt + BIRD_STAY_MILLIS)
    }

    companion object {
        /** How long the called bird stays. */
        const val BIRD_STAY_MILLIS = 120_000L

        /**
         * How long the summoned butterfly is about, from setting off outside the garden to having
         * left it: some 6.5 seconds each way in an 11 x 11 garden, and a little time at the flower.
         */
        const val BUTTERFLY_STAY_MILLIS = 20_000L
    }
}

/** Where the ads stand on a showcase's picture: as it says, or else as they really do. */
fun Showcase?.adsOr(real: AdAvailability): AdAvailability = when (this?.adReady) {
    true -> AdAvailability.READY
    false -> AdAvailability.UNAVAILABLE
    null -> real
}

/**
 * Debug menu: the book's pages shown as if all were registered. Only what the pages show changes:
 * nothing is written, and what counts (the badges, the number collected, the level) goes on
 * reading the saved records, so switching it off leaves the book as it was.
 */
object BookPreview {
    private val _allRegistered = MutableStateFlow(false)
    val allRegistered: StateFlow<Boolean> = _allRegistered.asStateFlow()

    /** Does nothing in a release build. */
    fun set(on: Boolean) {
        if (BuildConfig.DEBUG) _allRegistered.value = on
    }
}

/** Whether the garden shows every point a visitor may settle on (debug builds only). */
object PerchPreview {
    private val _shown = MutableStateFlow(false)
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    /** Does nothing in a release build. */
    fun set(on: Boolean) {
        if (BuildConfig.DEBUG) _shown.value = on
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
            ?.let { if (it.bird != null) it.copy(birdAt = now) else it }
    }
}
