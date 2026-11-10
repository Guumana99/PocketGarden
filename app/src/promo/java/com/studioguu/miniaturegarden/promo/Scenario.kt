package com.studioguu.miniaturegarden.promo

import com.studioguu.miniaturegarden.domain.model.GridCell
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A script for the promo recorder (promo/README.md), as `tools/promo_shoot.py` hands it over: the
 * YAML of `promo/scenarios/` turned into JSON, nothing else changed.
 */
class Scenario(
    val id: String,
    val title: String,
    val fps: Int,
    val seed: Long,
    val start: Start,
    val camera: CameraMove,
    val timeline: List<Cue>,
    val hookFromEnd: Double,
) {
    /** How long the recording runs, in seconds: up to `end`, or a second after the last cue. */
    val duration: Double = timeline.firstOrNull { it.action == "end" }?.at
        ?: ((timeline.maxOfOrNull { it.at + it.overOr(0.0) } ?: 0.0) + 1.0)

    class Start(
        val garden: String,
        val season: String,
        val timeOfDay: String,
        val weather: String,
        val ui: String,
        val tapMarker: Boolean,
    )

    /** One line of the timeline: at [at] seconds, [action] with [args] (the value under its key). */
    class Cue(val at: Double, val action: String, val args: JsonElement) {
        private val obj: JsonObject? get() = args as? JsonObject

        fun string(key: String): String? = obj?.get(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString || p.content != "null" }?.content }
        fun double(key: String): Double? = (obj?.get(key) as? JsonPrimitive)?.doubleOrNull
        fun int(key: String): Int? = (obj?.get(key) as? JsonPrimitive)?.intOrNull
        fun bool(key: String): Boolean? = (obj?.get(key) as? JsonPrimitive)?.booleanOrNull
        fun element(key: String): JsonElement? = obj?.get(key)
        fun overOr(default: Double): Double = double("over") ?: default

        /** The value itself when it is a plain one (`weather: rainy`). */
        val plain: String? get() = (args as? JsonPrimitive)?.takeIf { args !is JsonNull }?.content

        /** The cells it names: `cells: [[x, y], ...]`, or `area: [x1, y1, x2, y2]` (both ends in) gone over row by row, to and fro. */
        fun cells(): List<GridCell> {
            val listed = element("cells")?.let(::cellList).orEmpty()
            val area = (element("area") as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: 0 }
            val swept = if (area != null && area.size == 4) {
                val (x1, y1, x2, y2) = area
                (minOf(y1, y2)..maxOf(y1, y2)).flatMapIndexed { row, y ->
                    val xs = (minOf(x1, x2)..maxOf(x1, x2)).toList()
                    (if (row % 2 == 0) xs else xs.reversed()).map { x -> GridCell(x, y) }
                }
            } else {
                emptyList()
            }
            return listed + swept
        }
    }

    /** Where the camera looks: [zoom] times the zoom that fits the whole garden, at [center] (a cell; null for the middle). */
    class CameraMove(val zoom: Double, val center: Pair<Double, Double>?, val over: Double)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Scenario {
            val root = json.parseToJsonElement(text) as JsonObject
            val start = root["start"] as? JsonObject ?: JsonObject(emptyMap())
            fun JsonObject.str(key: String, default: String) = (this[key] as? JsonPrimitive)?.content ?: default
            val cues = (root["timeline"] as? JsonArray).orEmpty().mapNotNull { entry ->
                val line = entry as? JsonObject ?: return@mapNotNull null
                val at = (line["at"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val (action, args) = line.entries.firstOrNull { it.key != "at" } ?: return@mapNotNull null
                Cue(at, action, args)
            }.sortedBy { it.at }
            return Scenario(
                id = root.str("id", "T-000"),
                title = root.str("title", ""),
                fps = (root["fps"] as? JsonPrimitive)?.intOrNull ?: 30,
                seed = (root["seed"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
                start = Start(
                    garden = start.str("garden", "new_game"),
                    season = start.str("season", "spring"),
                    timeOfDay = start.str("time_of_day", "day"),
                    weather = start.str("weather", "sunny"),
                    ui = start.str("ui", "hidden"),
                    tapMarker = (start["tap_marker"] as? JsonPrimitive)?.booleanOrNull ?: true,
                ),
                camera = cameraOf(root["camera"]) ?: CameraMove(1.0, null, 0.0),
                timeline = cues,
                hookFromEnd = (root["hook_from_end"] as? JsonPrimitive)?.doubleOrNull ?: 1.0,
            )
        }

        /** `{ zoom: fit | 1.6, center: [x, y], over: 1.0 }`. */
        fun cameraOf(element: JsonElement?): CameraMove? {
            val obj = element as? JsonObject ?: return null
            val zoomValue = obj["zoom"] as? JsonPrimitive
            val zoom = if (zoomValue == null || zoomValue.content == "fit") 1.0 else zoomValue.doubleOrNull ?: 1.0
            val center = (obj["center"] as? JsonArray)?.let { c ->
                val x = c.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                val y = c.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                if (x != null && y != null) x to y else null
            }
            return CameraMove(zoom, center, (obj["over"] as? JsonPrimitive)?.doubleOrNull ?: 0.0)
        }

        fun cellList(element: JsonElement): List<GridCell> = (element as? JsonArray).orEmpty().mapNotNull { pair ->
            val xy = (pair as? JsonArray)?.jsonArray ?: return@mapNotNull null
            val x = xy.getOrNull(0)?.jsonPrimitive?.intOrNull
            val y = xy.getOrNull(1)?.jsonPrimitive?.intOrNull
            if (x != null && y != null) GridCell(x, y) else null
        }
    }
}
