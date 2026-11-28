package com.studioguu.miniaturegarden.ui.render

import androidx.compose.ui.geometry.Offset
import com.studioguu.miniaturegarden.domain.model.StructureData

/**
 * The openings that are lit at night, as each picture has them (#fb1126 2): the corners of the
 * glass inside its frame, or of the lantern's fire holes, as shares of the picture's width and
 * height. Rectangles with a slant, as they were before, stood out over the frames and left gaps
 * when zoomed in.
 */
object LampShapes {

    /** Corners in order round the opening, each (x, y) as shares of the picture. */
    class Opening(val corners: List<Pair<Float, Float>>)

    /** An opening from its corners in the pixels of a picture [width] x [height]: x1, y1, x2, y2, ... */
    private fun opening(width: Int, height: Int, vararg corners: Float): Opening =
        Opening(List(corners.size / 2) { corners[2 * it] / width to corners[2 * it + 1] / height })

    val BY_OBJECT: Map<String, List<Opening>> = mapOf(
        // The two holes of the fire box under the lantern's roof, one on each face that shows.
        "deco_lantern" to listOf(
            opening(141, 225, 47.5f, 82f, 56f, 85.5f, 64f, 87.5f, 64f, 98.5f, 56.5f, 95f, 48.5f, 90.5f),
            opening(141, 225, 77f, 88f, 85f, 85.5f, 92.5f, 82f, 92.5f, 90.5f, 85f, 94f, 77f, 98f),
        ),
        // The open window between the shutters, on its left face, which runs away down to the right.
        StructureData.HUT.objectId to listOf(
            opening(574, 621, 151f, 245.5f, 209.5f, 269.5f, 209.5f, 356.5f, 151f, 333f),
        ),
        // Two windows on the long face, one on the gable end (whose wall runs the other way).
        StructureData.COTTAGE.objectId to listOf(
            opening(612, 562, 135.5f, 226f, 161f, 233f, 161f, 281f, 135.5f, 274f),
            opening(612, 562, 213f, 253.5f, 237f, 260f, 237f, 305.5f, 213f, 299f),
            opening(612, 562, 428f, 255f, 454f, 247.5f, 454f, 294.5f, 428f, 301.5f),
        ),
        // The latticed window on its front face (which rises to the right), and the little one up in
        // the gable, whose left side a rafter cuts across.
        StructureData.WINDMILL.objectId to listOf(
            opening(334, 424, 221f, 177f, 256.5f, 165f, 256.5f, 213.5f, 221f, 223.5f),
            opening(334, 424, 235.5f, 84.5f, 243.5f, 82.5f, 243.5f, 107f, 229.5f, 108.5f, 229.5f, 97f),
        ),
    )

    /**
     * Where [opening]'s corners are on screen, for a picture whose top left is [topLeft], drawn
     * [width] x [height]; [mirrorAbout] is the x it is turned round (a building turned), or null.
     */
    fun onScreen(opening: Opening, topLeft: Offset, width: Float, height: Float, mirrorAbout: Float?): List<Offset> =
        opening.corners.map { (x, y) ->
            val across = topLeft.x + x * width
            Offset(if (mirrorAbout != null) 2 * mirrorAbout - across else across, topLeft.y + y * height)
        }
}
