package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.graphics.drawscope.DrawScope

/** Draw order, back to front. */
enum class RenderLayer(val zIndex: Int) {
    /** Garden base and ground tiles. */
    BASE_TERRAIN(0),

    /** Grid lines and tile highlights. */
    GRID_OVERLAY(1),

    /** Translucent object shadows. */
    SHADOWS(2),

    /** Flat ground cover: pebbles, grass. */
    BOTTOM_OBJECTS(3),

    /** Plants and decorations, depth-sorted. */
    GRID_OBJECTS(4),

    /** Watering effects, sparkles, speech bubbles. */
    TOP_EFFECTS(5),

    /** Touch feedback drawn over everything. */
    HUD_OVERLAY(6),
}

class RenderCommand(
    val layer: RenderLayer,
    /** Order within the layer; larger draws later (nearer the viewer). */
    val depth: Float,
    val draw: DrawScope.() -> Unit,
)

/**
 * Collects one frame's draw commands and replays them sorted by layer, then by depth.
 * Submission order therefore does not matter, except as the tie-break for equal depth.
 */
class LayerPipeline {
    private val commands = ArrayList<RenderCommand>()

    fun submit(layer: RenderLayer, depth: Float = 0f, draw: DrawScope.() -> Unit) {
        commands += RenderCommand(layer, depth, draw)
    }

    fun clear() = commands.clear()

    fun sortedCommands(): List<RenderCommand> = commands.sortedWith(DRAW_ORDER)

    /** Draws everything submitted since the last call, then empties the pipeline. */
    fun render(scope: DrawScope) {
        commands.sortWith(DRAW_ORDER)
        for (command in commands) command.draw(scope)
        commands.clear()
    }

    companion object {
        private val DRAW_ORDER = compareBy<RenderCommand>({ it.layer.zIndex }, { it.depth })

        /** Back-to-front depth for an object standing on tile (gridX, gridY). */
        fun depthOf(gridX: Int, gridY: Int, heightLevel: Int = 0): Float =
            (gridX + gridY) + heightLevel * 0.01f
    }
}
