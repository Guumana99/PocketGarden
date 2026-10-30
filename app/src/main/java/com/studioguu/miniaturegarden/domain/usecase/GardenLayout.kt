package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.TutorialTool

/** What the player wants to do with something that stands in the garden. */
enum class LayoutAction {
    /** Pick it up and put it down somewhere else ([MoveObjectUseCase]). */
    MOVE,

    /** Take it off the garden and back into the bag ([StoreObjectUseCase], through [StartRemovalUseCase]). */
    STORE,

    /** Take it off the garden and throw it away ([RemoveObjectUseCase], through [StartRemovalUseCase]). */
    DISCARD,
}

/** Whether a [LayoutAction] may be done, and if not, why. */
enum class LayoutCheck {
    OK,

    /** It is done in edit mode only: arranging the layout, and (since #49) clearing anything away. */
    NEEDS_EDIT_MODE,

    /** It is in the ground (a crop, tilled soil): it stays where it was sown or dug. */
    IN_THE_GROUND,

    /** It was never an item and cannot be lifted (the starter crate), or it cannot move (the greenhouse bed). */
    FIXED,
}

/**
 * The rules for arranging the garden.
 *
 * The garden has two kinds of things in it. What is **in the ground** (crops, and the tilled soil
 * they grow in) belongs to the everyday work of the garden: sown, tended, harvested or cleared
 * away at any time, but never moved. Everything else that came out of the bag is the garden's
 * **layout**: lawn, pebbles, flowers, paving, decking, fences, the river, decorations and the
 * buildings. The layout is arranged in edit mode, where the grid shows: only there can such a
 * piece be moved, put back into the bag or thrown away. New things are put down from the bag in
 * the everyday garden (#50), and outside edit mode a stray tap cannot pull a fence apart.
 *
 * Clearing away is edit mode's for everything (#49): a crop, withered or not, and a bed of
 * tilled soil are cleared there too, so that nothing in the garden goes at a stray tap. What is in
 * the ground still never moves and never goes back into the bag.
 *
 * Pure rules; the screen keeps whether edit mode is on and asks here.
 */
object GardenLayout {

    /** Whether [obj] is a piece of the layout rather than something in the ground. */
    fun isLayoutPiece(obj: GridObject): Boolean = !obj.isPlant && !obj.isHoedSoil && !obj.isStarterCrate

    /**
     * Whether [tool] is in hand in edit mode: only the shop. Edit mode is for rearranging the
     * garden (moving, putting back, clearing away), so the hoe, watering, feeding and the bag
     * (putting new things down) wait outside it (#50; from #42 to #49 things from the bag were put
     * down in edit mode instead).
     */
    fun toolAllowedInEditMode(tool: TutorialTool): Boolean = tool == TutorialTool.SHOP

    /**
     * Whether [obj] is the kind of thing that can be moved at all, edit mode or not: a layout
     * piece that came from an item. A greenhouse is a bed of soil and stays where it was laid.
     */
    fun isMovable(obj: GridObject): Boolean =
        isLayoutPiece(obj) && StoreObjectUseCase.itemFor(obj) != null && !obj.isGreenhouse

    /** Whether [action] may be done to [obj], with edit mode on ([editing]) or off. */
    fun check(action: LayoutAction, obj: GridObject, editing: Boolean): LayoutCheck = when {
        obj.isStarterCrate -> LayoutCheck.FIXED
        !isLayoutPiece(obj) -> when {
            action != LayoutAction.DISCARD -> LayoutCheck.IN_THE_GROUND
            editing -> LayoutCheck.OK
            else -> LayoutCheck.NEEDS_EDIT_MODE
        }
        action == LayoutAction.MOVE && !isMovable(obj) -> LayoutCheck.FIXED
        action == LayoutAction.STORE && StoreObjectUseCase.itemFor(obj) == null -> LayoutCheck.FIXED
        !editing -> LayoutCheck.NEEDS_EDIT_MODE
        else -> LayoutCheck.OK
    }
}
