package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

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
    /** Something in the garden, kept inside its frame when drawn ([GardenClip]). */
    val inGarden: Boolean = false,
)

/**
 * What keeps the things in the garden inside its wooden frame (device feedback, after #75): what
 * lies on the ground ([ground], the garden's diamond) and what stands on it ([standing]: the
 * diamond and everything straight above it, so that a tall thing still rises over the far edge
 * but nothing reaches out over the near boards or past the side corners). Effects over the
 * garden (bubbles, sparkles) and the visitors, who come from outside it, are not kept in.
 */
class GardenClip(val ground: Path, val standing: Path) {
    fun pathFor(command: RenderCommand): Path? = when {
        !command.inGarden -> null
        command.layer == RenderLayer.GRID_OBJECTS -> standing
        command.layer == RenderLayer.BASE_TERRAIN || command.layer == RenderLayer.SHADOWS ||
            command.layer == RenderLayer.BOTTOM_OBJECTS -> ground
        else -> null
    }
}

/**
 * Collects one frame's draw commands and replays them sorted by layer, then by depth.
 * Submission order therefore does not matter, except as the tie-break for equal depth.
 */
class LayerPipeline {
    private val commands = ArrayList<RenderCommand>()

    fun submit(layer: RenderLayer, depth: Float = 0f, inGarden: Boolean = false, draw: DrawScope.() -> Unit) {
        commands += RenderCommand(layer, depth, draw, inGarden)
    }

    fun clear() = commands.clear()

    fun sortedCommands(): List<RenderCommand> = commands.sortedWith(DRAW_ORDER)

    /**
     * Draws everything submitted since the last call, each thing of the garden inside [clip] when
     * one is given, then empties the pipeline.
     *
     * @return what was drawn, in the order it was drawn.
     */
    fun render(scope: DrawScope, clip: GardenClip? = null): List<RenderCommand> {
        commands.sortWith(DRAW_ORDER)
        val drawn = commands.toList()
        for (command in drawn) draw(scope, command, clip)
        commands.clear()
        return drawn
    }

    companion object {
        private val DRAW_ORDER = compareBy<RenderCommand>({ it.layer.zIndex }, { it.depth })

        /** Draws [command] as [render] does: inside [clip], if it keeps it in. */
        fun draw(scope: DrawScope, command: RenderCommand, clip: GardenClip?) {
            val path = clip?.pathFor(command)
            if (path == null) command.draw(scope) else scope.clipPath(path) { command.draw(this) }
        }

        /** Back-to-front depth for an object standing on tile (gridX, gridY). */
        fun depthOf(gridX: Int, gridY: Int, heightLevel: Int = 0): Float =
            (gridX + gridY) + heightLevel * 0.01f
    }
}
