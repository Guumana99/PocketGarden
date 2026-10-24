package com.studioguu.miniaturegarden.domain.model

/**
 * How a lawn comes up on bare ground, stage by stage (#54 prepared, #55 on). The garden starts as
 * wasteland; a lawn sown on it grows, the way a crop does, from seed scattered over the earth to
 * a full, soft green, rather than being laid fully grown at a tap.
 *
 * - The stage is read from [GridObject.growthProgress] (0 sown, 1 fully grown), a field a lawn
 *   does not otherwise use, the way tilled soil keeps its compost there: nothing new is stored.
 *   A lawn is placed at 0 like any seed; lawns laid before it grew were set fully grown when the
 *   database moved to version 15.
 * - It grows with time alone ([grownBy], [HOURS_TO_FULL] from seed to full), while the garden
 *   is open and while it is away (CalculateOfflineProgressUseCase). It needs no water and does
 *   not wither.
 * - The garden draws it by how far it has grown ([growthOf]): over the whole cell from the
 *   start, denser and longer as it grows (#61). The stages name the steps on the way.
 */
object LawnGrowth {
    /** The lawn's object id: "芝生" in the shop. */
    const val OBJECT_ID = "ground_grass"

    /** Whether lawns grow in stages. Off, every lawn would be drawn fully grown, as it was laid. */
    const val GROWS_IN_STAGES = true

    /** How long a lawn takes from seed to fully grown. */
    const val HOURS_TO_FULL = 48f

    /** Whether [obj] is a lawn. */
    fun isLawn(obj: GridObject): Boolean = obj.objectId == OBJECT_ID

    /** The stage a lawn [progress] of the way grown is at (0 sown, 1 fully grown). */
    fun stageFor(progress: Float): LawnStage {
        val p = progress.coerceIn(0f, 1f)
        return LawnStage.entries.last { p >= it.from }
    }

    /** The stage [obj] (a lawn) is drawn at: fully grown while lawns do not grow in stages. */
    fun stageOf(obj: GridObject, growsInStages: Boolean = GROWS_IN_STAGES): LawnStage =
        if (growsInStages) stageFor(obj.growthProgress) else LawnStage.FULL

    /**
     * How far grown [obj] (a lawn) is drawn, 0 sown to 1 grown: the blades over its cell grow
     * denser and longer with it (LawnPatches). Fully grown while lawns do not grow in stages.
     */
    fun growthOf(obj: GridObject, growsInStages: Boolean = GROWS_IN_STAGES): Float =
        if (growsInStages) obj.growthProgress.coerceIn(0f, 1f) else 1f

    /** How far a lawn [progress] of the way grown has come after [elapsedMillis] more. */
    fun grownBy(progress: Float, elapsedMillis: Long): Float {
        if (elapsedMillis <= 0L) return progress.coerceIn(0f, 1f)
        val hours = elapsedMillis / 3_600_000f
        return (progress + hours / HOURS_TO_FULL).coerceIn(0f, 1f)
    }
}

/**
 * The stages of a lawn coming up on bare earth, in order. [from]: the share of the way grown at
 * which the stage begins. [cover]: how much of its cell the green covers, for the garden to draw
 * (the rest is the earth under it). [blades]: how many tufts stand up on it.
 */
enum class LawnStage(val from: Float, val cover: Float, val blades: Int) {
    /** Just sown: seed on the earth, a first few short shoots. */
    SOWN(from = 0f, cover = 0f, blades = 6),

    /** Shoots coming up all over, thin, with the earth between them. */
    SPROUTING(from = 0.25f, cover = 0.35f, blades = 10),

    /** Patchy green, a few bare spots left, tufts standing up all over. */
    PATCHY(from = 0.6f, cover = 0.7f, blades = 12),

    /**
     * A full lawn over the whole cell, with tufts of blades standing up from it all over (#55:
     * three tufts on the flat green read as a carpet laid down, not grass growing).
     */
    FULL(from = 1f, cover = 1f, blades = 14),
}
