package com.studioguu.miniaturegarden.domain.model

/**
 * How a lawn comes up on bare ground, stage by stage (#54, prepared). The garden starts as
 * wasteland; a lawn sown on it is meant to grow, the way a crop does, from seed scattered over
 * the earth to a full, soft green — not to be laid fully grown at a tap.
 *
 * Today every lawn stands fully grown ([GROWS_IN_STAGES] is off): there is no rule yet for what
 * makes it grow (time, water, the season) or for what it is worth, and a lawn that never left
 * its first stage would be worse than one laid ready. What is here is the shape of it, so that
 * the screen already draws a lawn by its stage ([LawnStage]) and the rules can be added in one
 * place:
 *
 * - the stage is read from [GridObject.growthProgress] (0 sown, 1 fully grown), a field a lawn
 *   does not otherwise use, the way tilled soil keeps its compost there: nothing new is stored;
 * - [grownBy] is how far a lawn comes on in a while ([HOURS_TO_FULL] from seed to full);
 * - how much of its cell the grass covers at each stage ([LawnStage.cover]) is what the garden draws.
 *
 * To turn it on: set [GROWS_IN_STAGES], have placing a lawn start it at 0, and bring lawns on
 * with [grownBy] where the crops are brought up to date (CalculateOfflineProgressUseCase).
 */
object LawnGrowth {
    /** The lawn's object id: "芝生" in the shop. */
    const val OBJECT_ID = "ground_grass"

    /** Whether lawns grow in stages yet. Off: every lawn is drawn fully grown, as it was laid. */
    const val GROWS_IN_STAGES = false

    /** How long a lawn takes from seed to fully grown, once it grows. */
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
    /** Just sown: seed on the earth, a first few shoots. */
    SOWN(from = 0f, cover = 0f, blades = 4),

    /** Shoots coming up all over, thin, with the earth between them. */
    SPROUTING(from = 0.25f, cover = 0.35f, blades = 7),

    /** Patchy green, a few bare spots left. */
    PATCHY(from = 0.6f, cover = 0.7f, blades = 6),

    /** A full, soft lawn over the whole cell. */
    FULL(from = 1f, cover = 1f, blades = 3),
}
