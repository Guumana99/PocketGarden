package com.studioguu.miniaturegarden.domain.model

/** The parts of a building that can be painted. */
enum class PaintPart { ROOF, WALL }

/** One colour of paint. [argb] is what it is mixed from, and the colour of its swatch. */
data class PaintColour(val id: String, val argb: Long)

/**
 * What the player can paint: the roofs and walls of the cottage and the hut, free, at any time,
 * every colour from the first. Each building has one picture; its colours are laid over the parts
 * a mask marks (tools/prepare_building_art.py, [com.studioguu.miniaturegarden.ui.render.BuildingPainter]).
 * The first colour of each list is the picture's own: stored as no colour at all, so that a
 * building never painted is drawn from the atlas like any other.
 */
object BuildingPaint {
    private val RED = PaintColour("red", 0xFFB5403A)
    private val BLUE = PaintColour("blue", 0xFF3E6FB0)
    private val GREEN = PaintColour("green", 0xFF4E8A5E)
    private val DARK_BROWN = PaintColour("dark_brown", 0xFF5A3A28)
    private val GREY = PaintColour("grey", 0xFF9A9A98)
    private val WHITE = PaintColour("white", 0xFFF4F0E8)
    private val CREAM = PaintColour("cream", 0xFFF2E3BE)
    private val PALE_BLUE = PaintColour("pale_blue", 0xFFCFE3EE)
    private val PALE_PINK = PaintColour("pale_pink", 0xFFF3D6D6)
    private val WOOD = PaintColour("wood", 0xFF8A6A4C)
    private val PALE_GREEN = PaintColour("pale_green", 0xFFCFE2C8)

    /** What has a mask to be painted through. */
    val PAINTABLE_OBJECT_IDS: List<String> = listOf(StructureData.COTTAGE.objectId, StructureData.HUT.objectId)

    /** The colours of [part] of the building [objectId], its own first; empty for what cannot be painted. */
    fun colours(objectId: String, part: PaintPart): List<PaintColour> = when (objectId) {
        StructureData.COTTAGE.objectId -> when (part) {
            PaintPart.ROOF -> listOf(RED, BLUE, GREEN, DARK_BROWN)
            PaintPart.WALL -> listOf(WHITE, CREAM, PALE_BLUE, PALE_PINK)
        }
        StructureData.HUT.objectId -> when (part) {
            PaintPart.ROOF -> listOf(GREY, DARK_BROWN, GREEN, BLUE)
            PaintPart.WALL -> listOf(WOOD, DARK_BROWN, WHITE, PALE_GREEN)
        }
        else -> emptyList()
    }

    fun canPaint(objectId: String): Boolean = colours(objectId, PaintPart.ROOF).isNotEmpty()

    /** The colour [part] of [building] is painted now. */
    fun colourOf(building: GridObject, part: PaintPart): PaintColour {
        val all = colours(building.objectId, part)
        val stored = if (part == PaintPart.ROOF) building.roofColour else building.wallColour
        return all.firstOrNull { it.id == stored } ?: all.first()
    }

    /** [colourOf], but null for the picture's own colour: nothing to lay over it. */
    fun paintOf(building: GridObject, part: PaintPart): PaintColour? {
        val own = colours(building.objectId, part).firstOrNull() ?: return null
        return colourOf(building, part).takeIf { it != own }
    }

    /** Whether [building] has been painted at all. */
    fun isPainted(building: GridObject): Boolean = PaintPart.entries.any { paintOf(building, it) != null }
}
