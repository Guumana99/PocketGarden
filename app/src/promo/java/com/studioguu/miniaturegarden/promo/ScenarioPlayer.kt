package com.studioguu.miniaturegarden.promo

import android.util.Log
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureDirection
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.WeatherType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import kotlin.math.PI
import kotlin.math.roundToLong

/**
 * Plays a [Scenario]: what the garden looks like at each moment of the recording ([sceneAt]),
 * and the sound effects that go with it ([sounds]). Everything is worked out from the script and
 * the moment asked for, so the same script always comes out the same.
 *
 * Moments are asked for in order, from the start: the garden is changed as the timeline goes.
 */
class ScenarioPlayer(private val scenario: Scenario) {

    /** The epoch millis the recording starts at: fixed by the script's seed, so visitors always go the same way. */
    val baseMillis: Long = BASE_EPOCH + scenario.seed * 7_919L

    val map: GardenMap = GardenMap.allGrass(GridObject.GRID_SIZE)

    /** One sound effect: [name] (a file in res/raw, without its extension) at [at] seconds. */
    data class Sound(val at: Double, val name: String, val action: String)

    private sealed interface Step {
        val at: Double
    }

    private data class Strike(override val at: Double, val cell: GridCell) : Step
    private data class Bed(override val at: Double, val cell: GridCell, val ridged: Boolean) : Step
    private data class Sow(override val at: Double, val cell: GridCell, val species: PlantSpecies, val colour: String?) : Step
    private data class Grow(override val at: Double, val cell: GridCell, val stage: Int, val bloomSound: String?) : Step
    private data class Reap(override val at: Double, val cell: GridCell) : Step
    private data class Place(override val at: Double, val cell: GridCell, val objectId: String, val flipped: Boolean) : Step
    private data class Remove(override val at: Double, val cell: GridCell) : Step
    private data class SetWeather(override val at: Double, val weather: WeatherType) : Step
    private data class SetSeason(override val at: Double, val season: Season) : Step
    private data class SetLabel(override val at: Double, val text: String?) : Step
    private data class SetMarker(override val at: Double, val shown: Boolean) : Step

    private val steps = mutableListOf<Step>()
    private val taps = mutableListOf<Pair<Double, GridCell>>()
    private val rawSounds = mutableListOf<Sound>()
    private val glints = mutableListOf<Pair<Double, Double>>()
    private val visits = mutableListOf<CreatureVisit>()
    private val directions = HashMap<CreatureVisit, CreatureDirection>()

    /** Hour of day, as (from seconds, to seconds, from hour, to hour). */
    private val hours = mutableListOf<HourMove>()
    private data class HourMove(val at: Double, val over: Double, val to: Float)

    /** Camera moves after the first view. */
    private val cameras = mutableListOf<Pair<Double, Scenario.CameraMove>>()

    val sounds: List<Sound>
    val warnings = mutableListOf<String>()

    init {
        for (cue in scenario.timeline) expand(cue)
        steps.sortBy { it.at }
        sounds = thinned(rawSounds.sortedBy { it.at })
    }

    private fun expand(cue: Scenario.Cue) {
        val at = cue.at
        fun spread(cells: List<GridCell>, over: Double): List<Pair<Double, GridCell>> =
            cells.mapIndexed { i, cell -> (at + if (cells.size <= 1) 0.0 else over * i / cells.size) to cell }
        fun sound(defaultName: String?, t: Double) {
            val name = cue.string("sound") ?: defaultName ?: return
            if (name != "none") rawSounds += Sound(t, name, cue.action)
        }
        when (cue.action) {
            "till" -> {
                val ridged = cue.bool("ridged") ?: false
                for ((t, cell) in spread(cue.cells(), cue.overOr(0.0))) {
                    steps += Strike(t, cell)
                    steps += Bed(t + BED_AFTER, cell, ridged)
                    taps += t to cell
                    sound("se_tap", t)
                }
            }
            "plant" -> {
                // One crop (`crop`), or several mixed over the cells (`crops`: the crop of cell
                // (x, y) is crops[(x + 2y) % n], so that no two neighbours are alike).
                val mixed = (cue.element("crops") as? JsonArray)?.mapNotNull { speciesOf(it.jsonPrimitive.content) }.orEmpty()
                val single = speciesOf(cue.string("crop"))
                if (mixed.isEmpty() && single == null) return warn("unknown crop in $cue")
                // Colours: a list for the one crop, or by crop (`colours: { tulip: [red, pink] }`), taken in turn.
                val colourList = (cue.element("colours") as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOfNotNull(cue.string("colour"))
                val colourMap = (cue.element("colours") as? JsonObject)?.mapValues { (_, v) -> (v as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty() }.orEmpty()
                val used = HashMap<PlantSpecies, Int>()
                val cells = cue.cells()
                for ((t, cell) in spread(cells, cue.overOr(minOf(cells.size * 0.08, 1.0)))) {
                    val species = if (mixed.isNotEmpty()) mixed[Math.floorMod(cell.x + 2 * cell.y, mixed.size)] else single!!
                    val colours = colourMap[species.objectId.removePrefix("plant_")] ?: colourList
                    val n = used.merge(species, 1, Int::plus)!! - 1
                    steps += Sow(t, cell, species, colours.getOrNull(n % maxOf(colours.size, 1)))
                    taps += t to cell
                    sound("se_plant", t)
                }
            }
            "water" -> for ((t, cell) in spread(cue.cells(), cue.overOr(0.0))) {
                taps += t to cell
                sound("se_water", t)
            }
            "grow" -> {
                val stage = cue.int("to_stage") ?: 99
                // No sound of its own: none of the game's fits a flower opening (`sound:` gives one).
                val bloom = cue.string("sound")?.takeIf { it != "none" }
                for ((t, cell) in spread(cue.cells(), cue.overOr(0.0))) {
                    steps += Grow(t, cell, stage, bloom)
                }
            }
            "harvest" -> for ((t, cell) in spread(cue.cells(), cue.overOr(0.0))) {
                steps += Reap(t, cell)
                taps += t to cell
                sound("se_harvest", t)
            }
            "place" -> {
                val objectId = cue.string("item") ?: return warn("place without item")
                val cell = cue.element("at")?.let { Scenario.cellList(kotlinx.serialization.json.JsonArray(listOf(it))) }?.firstOrNull()
                    ?: return warn("place without at")
                steps += Place(at, cell, objectId, cue.bool("flip") ?: false)
                taps += at to cell
                sound("se_plant", at)
            }
            "remove" -> for ((t, cell) in spread(cue.cells(), cue.overOr(0.0))) {
                steps += Remove(t, cell)
                taps += t to cell
            }
            "weather" -> {
                val weather = weatherOf(cue.plain ?: cue.string("to"))
                steps += SetWeather(at, weather)
                if (weather == WeatherType.RAINY) sound("se_water", at)
            }
            "season" -> steps += SetSeason(at, seasonOf(cue.plain ?: cue.string("to")))
            "time_of_day" -> hours += HourMove(at, cue.overOr(0.0), hourOf(cue.plain ?: cue.string("to")))
            "camera" -> Scenario.cameraOf(cue.args)?.let { cameras += at to it }
            "day_label" -> steps += SetLabel(at, cue.plain?.takeIf { it.isNotBlank() && it != "null" })
            "tap_marker" -> steps += SetMarker(at, cue.plain?.toBooleanStrictOrNull() ?: true)
            "sparkle" -> {
                glints += at to cue.overOr(GLINT_SECONDS)
                sound(null, at)
            }
            "sound" -> (cue.plain ?: cue.string("name"))?.let { rawSounds += Sound(at, it, "sound") }
            "creature" -> creatures(cue)
            "ui" -> if (cue.plain == "shown") warn("ui: shown is not supported yet; the garden is recorded without its buttons")
            "end" -> Unit
            else -> warn("unknown action ${cue.action} at $at")
        }
    }

    /** `creature: { kind, count, from, land_on, stay }`. */
    private fun creatures(cue: Scenario.Cue) {
        val creature = Creature.entries.firstOrNull { it.id == cue.string("kind") } ?: return warn("unknown creature in $cue")
        val rule = VisitRule.entries.first { it.creature == creature }
        val count = cue.int("count") ?: 1
        val landOn = cue.element("land_on")?.let(Scenario::cellList).orEmpty()
        val stayMillis = ((cue.double("stay") ?: 3.0) * 1000).roundToLong()
        val angle = angleOf(cue.element("from"))
        val flies = creature == Creature.BUTTERFLY || creature == Creature.DRAGONFLY || creature == Creature.BIRD
        // `from_distance`: how many cells away it sets off (a flier), nearer than the game's usual.
        // Butterflies and dragonflies only: the bird comes down its own way.
        val fromCells = cue.double("from_distance")?.toFloat()?.takeIf { creature != Creature.BIRD }
        val travelIn = if (flies) CreatureRoutes.flightMillis(creature, map.size, fromCells) else POP_MILLIS
        val travelOut = if (flies) CreatureRoutes.flightMillis(creature, map.size) else POP_MILLIS
        for (i in 0 until count) {
            val cell = landOn.getOrNull(i % maxOf(landOn.size, 1)) ?: GridCell(map.size / 2, map.size / 2)
            // [Scenario.Cue.at] is when it comes down on its point: one that flies sets off from
            // the sky outside the garden that much earlier (a butterfly about 7 seconds), at the
            // game's own speed.
            val arrives = baseMillis + ((cue.at + i * STAGGER_SECONDS) * 1000).roundToLong() - (if (flies) travelIn else 0L)
            val visit = CreatureVisit(rule, cell, host = null, arrivesAt = arrives, leavesAt = arrives + travelIn + travelOut + stayMillis + SETTLE_MILLIS)
            visits += visit
            directions[visit] = CreatureDirection(perches = if (landOn.isEmpty()) emptyList() else listOf(cell), fromAngle = angle, stayMillis = stayMillis, fromCells = fromCells)
        }
    }

    // --- The garden as the timeline goes ---

    private val objects = LinkedHashMap<String, GridObject>()
    private val broken = LinkedHashSet<GridCell>()
    private var weather = weatherOf(scenario.start.weather)
    private var season = seasonOf(scenario.start.season)
    private var label: Pair<Double, String?> = 0.0 to null
    private var marker = scenario.start.tapMarker
    private var next = 0
    private var serial = 0

    /** What there is to draw at [seconds] into the recording; asked for in order. */
    fun sceneAt(seconds: Double): Scene {
        while (next < steps.size && steps[next].at <= seconds + 1e-9) apply(steps[next++])
        val now = baseMillis + (seconds * 1000).roundToLong()
        val camera = cameraAt(seconds)
        val shownVisits = visits.filter { now >= it.arrivesAt - VISIT_LEAD_MILLIS && now < it.leavesAt + VISIT_LEAD_MILLIS }
        val rings = if (!marker) emptyList() else taps.mapNotNull { (t, cell) ->
            val p = (seconds - t) / RING_SECONDS
            if (p in 0.0..1.0) cell to p.toFloat() else null
        }
        val glint = glints.firstNotNullOfOrNull { (t, over) -> ((seconds - t) / over).takeIf { it in 0.0..1.0 }?.toFloat() } ?: 0f
        // A label there from the very start is simply there; a later one fades in.
        val labelAlpha = label.second?.let { if (label.first <= 0.0) 1f else ((seconds - label.first) / LABEL_FADE_SECONDS).coerceIn(0.0, 1.0).toFloat() } ?: 0f
        return Scene(
            nowMillis = now,
            objects = objects.values.toList(),
            broken = broken.toSet(),
            weather = weather,
            season = season,
            hour = hourAt(seconds),
            zoom = camera.zoom,
            center = camera.center,
            lift = camera.lift,
            visitors = shownVisits,
            glint = glint,
            rings = rings,
            dayLabel = label.second,
            dayLabelAlpha = labelAlpha,
        )
    }

    fun directionOf(visit: CreatureVisit): CreatureDirection? = directions[visit]

    private fun apply(step: Step) {
        when (step) {
            is Strike -> if (soilAt(step.cell) == null) broken += step.cell
            is Bed -> {
                broken -= step.cell
                if (soilAt(step.cell) == null) put(soil(step.cell, step.ridged))
            }
            is Sow -> {
                if (plantAt(step.cell) != null) return
                if (soilAt(step.cell) == null) put(soil(step.cell, ridged = step.species.ground != PlantGround.FLOWER_BED))
                broken -= step.cell
                put(GridObject(
                    instanceId = "promo_plant_${serial++}", gridX = step.cell.x, gridY = step.cell.y, objectId = step.species.objectId,
                    growthProgress = 0f, lastUpdatedTimestamp = baseMillis, bloomColour = step.colour,
                ))
            }
            is Grow -> plantAt(step.cell)?.let { plant ->
                val stages = PlantSpecies.forObjectId(plant.objectId).growthStages
                val stage = step.stage.coerceIn(1, stages)
                val progress = if (stages <= 1) 1f else (stage - 1).toFloat() / (stages - 1)
                if (progress > plant.growthProgress) {
                    put(plant.copy(growthProgress = progress))
                    if (stage == stages && stages > 1 && step.bloomSound != null) rawSoundsLate += Sound(step.at, step.bloomSound, "bloom")
                }
            }
            is Reap -> plantAt(step.cell)?.let { objects.remove(it.instanceId) }
            is Place -> put(GridObject(
                instanceId = "promo_obj_${serial++}", gridX = step.cell.x, gridY = step.cell.y, objectId = step.objectId,
                isFlipped = step.flipped, lastUpdatedTimestamp = baseMillis,
            ))
            is Remove -> objects.values.filter { it.gridX == step.cell.x && it.gridY == step.cell.y && !it.isSoil }.forEach { objects.remove(it.instanceId) }
            is SetWeather -> weather = step.weather
            is SetSeason -> season = step.season
            is SetLabel -> label = step.at to step.text
            is SetMarker -> marker = step.shown
        }
    }

    /**
     * The flowers' sounds as they open are only known as the garden is played; [bloomSounds]
     * plays the whole timeline once more, on a copy, to find them.
     */
    private val rawSoundsLate = mutableListOf<Sound>()

    /** Every sound of the recording: those the script asks for and the flowers opening. */
    fun allSounds(): List<Sound> {
        val dry = ScenarioPlayer(scenario)
        dry.sceneAt(scenario.duration + 1.0)
        return thinned((sounds + dry.rawSoundsLate).sortedBy { it.at })
    }

    private fun put(obj: GridObject) {
        objects[obj.instanceId] = obj
    }

    private fun soil(cell: GridCell, ridged: Boolean) = GridObject(
        instanceId = "promo_soil_${cell.x}_${cell.y}", gridX = cell.x, gridY = cell.y,
        objectId = if (ridged) GridObject.TILLED_SOIL_OBJECT_ID else GridObject.LOOSE_SOIL_OBJECT_ID,
        lastUpdatedTimestamp = baseMillis,
    )

    private fun soilAt(cell: GridCell) = objects.values.firstOrNull { it.isSoil && it.gridX == cell.x && it.gridY == cell.y }
    private fun plantAt(cell: GridCell) = objects.values.firstOrNull { it.isPlant && it.gridX == cell.x && it.gridY == cell.y }

    private fun hourAt(seconds: Double): Float {
        var hour = hourOf(scenario.start.timeOfDay)
        for (move in hours) {
            if (seconds < move.at) break
            val p = if (move.over <= 0.0) 1.0 else ((seconds - move.at) / move.over).coerceIn(0.0, 1.0)
            hour += ((move.to - hour) * smooth(p)).toFloat()
        }
        return hour
    }

    private class CameraAt(val zoom: Double, val center: Pair<Double, Double>, val lift: Double)

    /** `zoom: max` as a multiple of the fitting zoom: the game's closest ([GardenCamera.MAX_SCALE]) on a 1080 x 1920 frame. */
    private val maxZoom: Double = run {
        val tile = PromoCamera.TILE_WIDTH_DP * PromoActivity.DENSITY
        val fit = minOf(PromoActivity.WIDTH / (map.size * tile), PromoActivity.HEIGHT / (map.size * tile / 2)) * GardenCamera.FIT_MARGIN
        (GardenCamera.MAX_SCALE / fit.coerceAtMost(1f)).toDouble()
    }

    private fun zoomOf(move: Scenario.CameraMove) = if (move.zoom == Scenario.MAX_ZOOM) maxZoom else move.zoom

    private fun cameraAt(seconds: Double): CameraAt {
        val middle = map.size / 2.0
        var zoom = zoomOf(scenario.camera)
        var center = scenario.camera.center?.let { it.first + 0.5 to it.second + 0.5 } ?: (middle to middle)
        var lift = scenario.camera.lift
        for ((at, move) in cameras) {
            if (seconds < at) break
            val p = smooth(if (move.over <= 0.0) 1.0 else ((seconds - at) / move.over).coerceIn(0.0, 1.0))
            val toCenter = move.center?.let { it.first + 0.5 to it.second + 0.5 } ?: (middle to middle)
            // Zoom goes evenly in ratio, so that coming in and going out feel the same speed.
            zoom *= Math.pow(zoomOf(move) / zoom, p)
            center = (center.first + (toCenter.first - center.first) * p) to (center.second + (toCenter.second - center.second) * p)
            lift += (move.lift - lift) * p
        }
        return CameraAt(zoom, center, lift)
    }

    private fun warn(message: String) {
        warnings += message
        Log.w(TAG, message)
    }

    /** One sound of a kind at a time: a run of the same sound closer than [MIN_GAP] gives the first only. */
    private fun thinned(sounds: List<Sound>): List<Sound> {
        val last = HashMap<String, Double>()
        return sounds.filter { s ->
            val gap = MIN_GAP[s.name] ?: 0.1
            val before = last[s.name]
            if (before != null && s.at - before < gap) false else true.also { last[s.name] = s.at }
        }
    }

    companion object {
        private const val TAG = "PromoPlayer"

        /** 2027-01-15, a little after noon UTC: any fixed moment will do. */
        private const val BASE_EPOCH = 1_800_000_000_000L

        /** The hoe's first strike turns the ground; this much later the bed is there. */
        private const val BED_AFTER = 0.25

        private const val RING_SECONDS = 0.45
        private const val LABEL_FADE_SECONDS = 0.25
        private const val GLINT_SECONDS = 1.2
        private const val STAGGER_SECONDS = 0.5
        private const val POP_MILLIS = 400L
        private const val SETTLE_MILLIS = 700L
        private const val VISIT_LEAD_MILLIS = 500L

        private val MIN_GAP = mapOf("se_tap" to 0.12, "se_plant" to 0.15, "se_water" to 0.4, "se_harvest" to 0.15, "se_coin" to 0.3)

        fun smooth(p: Double): Double = p * p * (3 - 2 * p)

        fun speciesOf(name: String?): PlantSpecies? =
            PlantSpecies.entries.firstOrNull { it.objectId == "plant_$name" || it.objectId == name || it.name.equals(name, ignoreCase = true) }

        fun weatherOf(name: String?): WeatherType = WeatherType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: WeatherType.SUNNY

        /** `spring`, `spring_early`, `summer_late` ...: only the season counts for the picture. */
        fun seasonOf(name: String?): Season {
            val head = name?.substringBefore('_')
            return Season.entries.firstOrNull { it.name.equals(head, ignoreCase = true) } ?: Season.SPRING
        }

        fun hourOf(name: String?): Float = when (name) {
            "morning" -> 7.5f
            "day", "noon" -> 12f
            "afternoon" -> 15f
            "evening" -> 17.8f
            "night" -> 21.5f
            else -> name?.toFloatOrNull() ?: 12f
        }

        /**
         * Which way a visitor comes in from, as [CreatureDirection.fromAngle]: `outside_left`,
         * `outside_right`, `outside_top` or `outside_bottom` of the screen, or degrees on the ground.
         * The screen's left is the grid's +y, -x.
         */
        fun angleOf(element: kotlinx.serialization.json.JsonElement?): Double? {
            val text = (element as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
            return when (text.removePrefix("outside_")) {
                "left" -> 3 * PI / 4
                "right" -> -PI / 4
                "top" -> -3 * PI / 4
                "bottom" -> PI / 4
                else -> text.toDoubleOrNull()?.let { it * PI / 180 }
            }
        }
    }
}

/** Everything drawn in one frame. */
class Scene(
    val nowMillis: Long,
    val objects: List<GridObject>,
    val broken: Set<GridCell>,
    val weather: WeatherType,
    val season: Season,
    val hour: Float,
    val zoom: Double,
    val center: Pair<Double, Double>,
    /** How far above the ground at [center] the camera looks, in tile widths. */
    val lift: Double,
    val visitors: List<CreatureVisit>,
    val glint: Float,
    /** Tap rings: the cell, and how far on its ring is (0 to 1). */
    val rings: List<Pair<GridCell, Float>>,
    val dayLabel: String?,
    val dayLabelAlpha: Float,
)
