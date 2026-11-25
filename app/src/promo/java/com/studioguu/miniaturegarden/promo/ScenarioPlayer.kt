package com.studioguu.miniaturegarden.promo

import android.util.Log
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.CreatureDirection
import com.studioguu.miniaturegarden.domain.model.CreatureRoutes
import com.studioguu.miniaturegarden.domain.model.CreatureVisit
import com.studioguu.miniaturegarden.domain.model.CreatureVisits
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
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
    private data class Sow(override val at: Double, val cell: GridCell, val species: PlantSpecies, val colour: String?, val dry: Boolean = false) : Step
    private data class Water(override val at: Double, val cell: GridCell) : Step
    private data class Grow(override val at: Double, val cell: GridCell, val stage: Int, val bloomSound: String?) : Step
    private data class Reap(override val at: Double, val cell: GridCell) : Step
    private data class Place(override val at: Double, val obj: GridObject) : Step
    private data class Remove(override val at: Double, val cell: GridCell) : Step
    private data class SetWeather(override val at: Double, val weather: WeatherType) : Step
    private data class SetSeason(override val at: Double, val season: Season) : Step
    private data class SetLabel(override val at: Double, val text: String?) : Step
    private data class SetMarker(override val at: Double, val shown: Boolean) : Step

    private val steps = mutableListOf<Step>()
    private var placed = 0
    private val taps = mutableListOf<Pair<Double, GridCell>>()
    private val rawSounds = mutableListOf<Sound>()
    private val glints = mutableListOf<Pair<Double, Double>>()
    private val visits = mutableListOf<CreatureVisit>()
    private val directions = HashMap<CreatureVisit, CreatureDirection>()

    /** Hour of day, as (from seconds, to seconds, from hour, to hour). */
    private val hours = mutableListOf<HourMove>()
    /** `label_opacity` moves (declared before `init`, which fills it). */
    private val labelDims = mutableListOf<HourMove>()
    private data class HourMove(val at: Double, val over: Double, val to: Float)

    /** Camera moves after the first view. */
    private val cameras = mutableListOf<Pair<Double, Scenario.CameraMove>>()

    val sounds: List<Sound>
    val warnings = mutableListOf<String>()

    init {
        // `setup:` first: its steps are done before 0 s, without a sound, a tap ring or a sparkle.
        for (cue in scenario.setup) {
            if (cue.action in NOT_IN_SETUP) {
                warn("${cue.action} is not done in setup; put it in the timeline")
                continue
            }
            val (tapCount, soundCount, glintCount) = Triple(taps.size, rawSounds.size, glints.size)
            expand(cue)
            while (taps.size > tapCount) taps.removeAt(taps.lastIndex)
            while (rawSounds.size > soundCount) rawSounds.removeAt(rawSounds.lastIndex)
            while (glints.size > glintCount) glints.removeAt(glints.lastIndex)
        }
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
                    // `dry: true`: sown thirsty, with the game's blue drop over it until it is watered.
                    steps += Sow(t, cell, species, colours.getOrNull(n % maxOf(colours.size, 1)), cue.bool("dry") ?: false)
                    taps += t to cell
                    sound("se_plant", t)
                }
            }
            "water" -> for ((t, cell) in spread(cue.cells(), cue.overOr(0.0))) {
                steps += Water(t, cell)
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
                // One thing `at` a cell, or the same thing on each of `cells` / `area` (a lawn, a path).
                val one = cue.element("at")?.let { Scenario.cellList(kotlinx.serialization.json.JsonArray(listOf(it))) }?.firstOrNull()
                val cells = listOfNotNull(one) + cue.cells()
                if (cells.isEmpty()) return warn("place without at")
                for ((t, cell) in spread(cells, cue.overOr(0.0))) {
                    // Made here, not when it is put down, so that a visitor can be sent to it (the bird to a roof).
                    steps += Place(t, GridObject(
                        instanceId = "promo_obj_${placed++}", gridX = cell.x, gridY = cell.y, objectId = objectId,
                        isFlipped = cue.bool("flip") ?: false, lastUpdatedTimestamp = baseMillis,
                        // Lawn is grown when it is put down, as the game's is (AppDatabase migration).
                        growthProgress = if (objectId == "ground_grass") 1f else 0f,
                    ))
                    taps += t to cell
                    sound("se_plant", t)
                }
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
            // `label_opacity: { to: 0.35, over: 0.3 }`: the label board fainter for a while (something passes behind it), `to: 1` back.
            "label_opacity" -> labelDims += HourMove(at, cue.overOr(0.3), (cue.double("to") ?: cue.plain?.toDoubleOrNull() ?: 1.0).toFloat())
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
            // `coat: white` (#book60): the rabbit in its white winter coat.
            val variant = cue.string("coat")?.takeIf { it == CreatureVisits.WHITE_COAT && creature == Creature.RABBIT }
            // `roam: true` (the bird only): the game's own route, with no direction, so that it goes from place to
            // place as it does in the game (FB1125); `stay` is then how long the whole visit lasts, in seconds.
            val roam = creature == Creature.BIRD && cue.bool("roam") == true
            val visit = if (roam) {
                val comes = baseMillis + ((cue.at + i * STAGGER_SECONDS) * 1000).roundToLong()
                CreatureVisit(rule, cell, host = null, arrivesAt = comes, leavesAt = comes + stayMillis, variant = variant)
            } else {
                CreatureVisit(rule, cell, host = roofAt(creature, cell, cue.at), arrivesAt = arrives, leavesAt = arrives + travelIn + travelOut + stayMillis + SETTLE_MILLIS, variant = variant)
            }
            visits += visit
            if (!roam) directions[visit] = CreatureDirection(perches = if (landOn.isEmpty()) emptyList() else listOf(cell), fromAngle = angle, stayMillis = stayMillis, fromCells = fromCells)
        }
    }

    /**
     * The building with a roof (the cottage, the hut) standing on [cell] by [at] seconds, when the
     * bird is sent there: the bird then comes down on its ridge, as it does in the game when it
     * comes for that building. Sent to a cell with nothing on it, it comes down on the ground.
     */
    private fun roofAt(creature: Creature, cell: GridCell, at: Double): GridObject? {
        if (creature != Creature.BIRD) return null
        return steps.filterIsInstance<Place>().lastOrNull { step ->
            step.at <= at && step.obj.objectId in ROOFED && cell in step.obj.occupiedCells()
        }?.obj
    }

    // --- The garden as the timeline goes ---

    private val objects = LinkedHashMap<String, GridObject>()
    private val broken = LinkedHashSet<GridCell>()
    private var weather = weatherOf(scenario.start.weather)
    private var season = seasonOf(scenario.start.season)
    private var label: Pair<Double, String?> = 0.0 to null
    /** The label before the latest, for the second line's change ([Scene.dayLabelBefore]). */
    private var labelBefore: String? = null
    /** When the label came up (true) or only its second line changed (false): each shows it full size for a while. */
    private val labelShows = mutableListOf<Pair<Double, Boolean>>()
    private var marker = scenario.start.tapMarker
    private var next = 0
    /** Each harvest so far: when, and the coins it paid. */
    private val harvests = mutableListOf<Pair<Double, Int>>()
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
        // The coins of the latest harvest, floating up as long as the game's do (one at a time: a
        // later harvest takes the place of the one before, FloatingEffects.plusEffect).
        val coins = harvests.lastOrNull()?.takeIf { (t, _) -> seconds - t < COINS_SECONDS }
        val glint = glints.firstNotNullOfOrNull { (t, over) -> ((seconds - t) / over).takeIf { it in 0.0..1.0 }?.toFloat() } ?: 0f
        // A label there from the very start is simply there; a later one fades in.
        // Only the second line changing (the same first line: the time of day, the hours gone by):
        // the board stays and that line cross-fades instead.
        val firstLine = { text: String? -> text?.lineSequence()?.firstOrNull() }
        val swapping = label.first > 0.0 && label.second != null && labelBefore != null && firstLine(labelBefore) == firstLine(label.second)
        val labelChange = ((seconds - label.first) / (if (swapping) LABEL_SWAP_SECONDS else LABEL_FADE_SECONDS)).coerceIn(0.0, 1.0).toFloat()
        val labelAlpha = label.second?.let { if (label.first <= 0.0 || swapping) 1f else labelChange } ?: 0f
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
            dayLabelBefore = if (swapping && labelChange < 1f) labelBefore else null,
            dayLabelSwap = if (swapping) labelChange else 1f,
            dayLabelFull = labelFullAt(seconds),
            dayLabelDim = labelDimAt(seconds),
            coins = coins?.second,
            coinsProgress = coins?.let { ((seconds - it.first) / COINS_SECONDS).toFloat() } ?: 0f,
            backdrop = scenario.start.backdrop != "none",
        )
    }

    fun directionOf(visit: CreatureVisit): CreatureDirection? = directions[visit]

    private fun apply(step: Step) {
        when (step) {
            is Strike -> if (soilAt(step.cell) == null) broken += step.cell
            is Bed -> {
                broken -= step.cell
                // The hoe over turned earth again makes it a ridged bed (the earth stays as it is otherwise).
                val there = soilAt(step.cell)
                if (there == null || (step.ridged && there.isLooseSoil)) put(soil(step.cell, step.ridged))
            }
            // Watered: the drop over a thirsty plant goes, as in the game.
            is Water -> plantAt(step.cell)?.let { plant ->
                if (plant.status == PlantStatus.THIRSTY) put(plant.copy(moistureLevel = 1f, status = PlantStatus.HEALTHY))
            }
            is Sow -> {
                if (plantAt(step.cell) != null) return
                if (soilAt(step.cell) == null) put(soil(step.cell, ridged = step.species.ground != PlantGround.FLOWER_BED))
                broken -= step.cell
                put(GridObject(
                    instanceId = "promo_plant_${serial++}", gridX = step.cell.x, gridY = step.cell.y, objectId = step.species.objectId,
                    growthProgress = 0f, lastUpdatedTimestamp = baseMillis, bloomColour = step.colour,
                    moistureLevel = if (step.dry) DRY_MOISTURE else 1f,
                    status = if (step.dry) PlantStatus.THIRSTY else PlantStatus.HEALTHY,
                ))
            }
            is Grow -> plantAt(step.cell)?.let { plant ->
                val stages = PlantSpecies.forObjectId(plant.objectId).growthStages
                val stage = step.stage.coerceIn(1, stages)
                val progress = if (stages <= 1) 1f else (stage - 1).toFloat() / (stages - 1)
                if (progress > plant.growthProgress) {
                    put(plant.copy(growthProgress = progress))
                    if (stage == stages && stages > 1 && step.bloomSound != null && step.at >= 0.0) rawSoundsLate += Sound(step.at, step.bloomSound, "bloom")
                }
            }
            // As the game does (GardenActionUseCases.afterHarvest / endsAtHarvest): a crop that bears
            // again stays, back at its second stage, while it is in season; any other goes. Its coins
            // float up as the game's do.
            is Reap -> plantAt(step.cell)?.let { plant ->
                val species = PlantSpecies.forObjectId(plant.objectId)
                harvests += step.at to species.harvestReward(plant.harvestCount).coins
                val inSeason = GardenItem.entries.firstOrNull { it.objectId == plant.objectId }?.isInSeason(season) != false
                if (species.isRepeatable && inSeason) {
                    put(plant.copy(growthProgress = species.regrowFrom, harvestCount = plant.harvestCount + 1))
                } else {
                    objects.remove(plant.instanceId)
                }
            }
            is Place -> put(step.obj)
            is Remove -> objects.values.filter { it.gridX == step.cell.x && it.gridY == step.cell.y && !it.isSoil }.forEach { objects.remove(it.instanceId) }
            is SetWeather -> weather = step.weather
            is SetSeason -> season = step.season
            is SetLabel -> {
                labelBefore = label.second
                label = step.at to step.text
                if (step.text != null) {
                    val firstLine = { text: String? -> text?.lineSequence()?.firstOrNull() }
                    labelShows += step.at to (labelBefore == null || firstLine(labelBefore) != firstLine(step.text))
                }
            }
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

    /**
     * How far the label board is at its full size (1) rather than its small, faint resting size (0):
     * full for [LABEL_FULL_SECONDS] after it comes up, then shrinking over [LABEL_SHRINK_SECONDS];
     * when its second line changes, back to full ([LABEL_GROW_SECONDS]) for [LABEL_SWAP_FULL_SECONDS], then shrinking again.
     */
    private fun labelFullAt(seconds: Double): Float = labelShows.filter { it.first <= seconds }.maxOfOrNull { (at, first) ->
        val t = seconds - at
        val hold = if (first) LABEL_FULL_SECONDS else LABEL_GROW_SECONDS + LABEL_SWAP_FULL_SECONDS
        when {
            !first && at > 0.0 && t < LABEL_GROW_SECONDS -> t / LABEL_GROW_SECONDS
            t < hold -> 1.0
            else -> 1.0 - (t - hold) / LABEL_SHRINK_SECONDS
        }.coerceIn(0.0, 1.0)
    }?.toFloat() ?: 1f

    private fun labelDimAt(seconds: Double): Float {
        var dim = 1f
        for (move in labelDims) {
            if (seconds < move.at) break
            val p = if (move.over <= 0.0) 1.0 else ((seconds - move.at) / move.over).coerceIn(0.0, 1.0)
            dim += ((move.to - dim) * p).toFloat()
        }
        return dim
    }

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
        /** A plant sown `dry: true`: under the game's thirsty line (CalculateOfflineProgressUseCase.THIRSTY_THRESHOLD, 0.2). */
        private const val DRY_MOISTURE = 0.15f
        /** As long as the game's harvest sum is up (FloatingEffects: 2.6 s). */
        private const val COINS_SECONDS = 2.6
        private const val LABEL_FADE_SECONDS = 0.25
        private const val LABEL_SWAP_SECONDS = 0.6
        private const val LABEL_FULL_SECONDS = 3.0
        private const val LABEL_SHRINK_SECONDS = 0.5
        private const val LABEL_GROW_SECONDS = 0.3
        private const val LABEL_SWAP_FULL_SECONDS = 2.0
        private const val GLINT_SECONDS = 1.2
        private const val STAGGER_SECONDS = 0.5
        private const val POP_MILLIS = 400L
        private const val SETTLE_MILLIS = 700L
        private const val VISIT_LEAD_MILLIS = 500L

        /** What the bird can come down on the roof of ([com.studioguu.miniaturegarden.domain.model.PerchKind.ROOF]). */
        private val ROOFED = setOf(StructureData.COTTAGE.objectId, StructureData.HUT.objectId)

        /** What `setup:` does not take: what only means something while it is seen. */
        private val NOT_IN_SETUP = setOf("creature", "sparkle", "sound", "end")

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
    /** While only the label's second line changes: the label before, fading out under the new one ([dayLabelSwap], 0 to 1). */
    val dayLabelBefore: String? = null,
    val dayLabelSwap: Float = 1f,
    /** How far the label board is at full size (1) rather than small and faint (0). */
    val dayLabelFull: Float = 1f,
    /** The board's opacity on top of that (`label_opacity`), while something passes behind it. */
    val dayLabelDim: Float = 1f,
    /** The coins of the latest harvest while they float up (null when none), and how far on they are (0 to 1). */
    val coins: Int? = null,
    val coinsProgress: Float = 0f,
    /** The sky behind the garden; false leaves the frame transparent there (`start.backdrop: none`). */
    val backdrop: Boolean = true,
)
