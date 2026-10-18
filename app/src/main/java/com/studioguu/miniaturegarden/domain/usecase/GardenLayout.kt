package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.ItemCategory

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

    /** It is a piece of the garden's layout, and those are rearranged in edit mode only. */
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
 * piece be put down from the bag, moved, put back into the bag or thrown away. Outside it the
 * garden is just looked after, and a stray tap cannot pull a fence apart.
 *
 * Pure rules; the screen keeps whether edit mode is on and asks here.
 */
object GardenLayout {

    /** Whether [obj] is a piece of the layout rather than something in the ground. */
    fun isLayoutPiece(obj: GridObject): Boolean = !obj.isPlant && !obj.isTilledSoil && !obj.isStarterCrate

    /** Whether putting [item] down from the bag needs edit mode: anything but seeds. */
    fun placingNeedsEditMode(item: GardenItem): Boolean = item.category != ItemCategory.SEED

    /**
     * Whether [obj] is the kind of thing that can be moved at all, edit mode or not: a layout
     * piece that came from an item. A greenhouse is a bed of soil and stays where it was laid.
     */
    fun isMovable(obj: GridObject): Boolean =
        isLayoutPiece(obj) && StoreObjectUseCase.itemFor(obj) != null && !obj.isGreenhouse

    /** Whether [action] may be done to [obj], with edit mode on ([editing]) or off. */
    fun check(action: LayoutAction, obj: GridObject, editing: Boolean): LayoutCheck = when {
        obj.isStarterCrate -> LayoutCheck.FIXED
        !isLayoutPiece(obj) -> if (action == LayoutAction.DISCARD) LayoutCheck.OK else LayoutCheck.IN_THE_GROUND
        action == LayoutAction.MOVE && !isMovable(obj) -> LayoutCheck.FIXED
        action == LayoutAction.STORE && StoreObjectUseCase.itemFor(obj) == null -> LayoutCheck.FIXED
        !editing -> LayoutCheck.NEEDS_EDIT_MODE
        else -> LayoutCheck.OK
    }
}
