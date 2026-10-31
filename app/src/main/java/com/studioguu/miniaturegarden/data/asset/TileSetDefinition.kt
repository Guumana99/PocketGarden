package com.studioguu.miniaturegarden.data.asset

import com.studioguu.miniaturegarden.domain.model.BloomColours
import com.studioguu.miniaturegarden.domain.model.BuildingPaint
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.FencePart
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.RunKind
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TileType

/** Ground tiles. Their sprites are anchored at the center of the tile's top face. */
enum class GroundTile(val assetId: String) {
    GRASS("tile_grass"),
    GRASS_DARK("tile_grass_dark"),
    SOIL("tile_soil"),
    WATER("tile_water"),
}

enum class Decoration(val objectId: String) {
    ROCK("deco_rock"),
    ROCK_FLAT("deco_rock_flat"),
    ROCK_MOSSY("deco_rock_mossy"),
    ROCK_STANDING("deco_rock_standing"),
    ROCK_PEBBLES("deco_rock_pebbles"),
    BENCH("deco_bench"),
    LANTERN("deco_lantern"),
    SPRINKLER("deco_sprinkler"),
    POND("deco_pond"),
}

/** Flat ground cover, drawn below plants and decorations. */
enum class GroundCover(val objectId: String) {
    PEBBLE("ground_pebble"),
    GRASS_TUFT("ground_grass"),
    FLOWERS("ground_flowers"),
}

/**
 * Maps what the garden shows to the sprites that draw it: every sprite id the game looks up is
 * named here, so artwork is added by packing a picture under the id given here (ASSETS.md) and
 * nothing else. Whatever has no sprite yet is drawn in code instead. Plants themselves are
 * [PlantSpecies].
 */
object TileSetDefinition {

    /** Every object id that can be stored in a grid cell. */
    val objectIds: List<String> =
        PlantSpecies.entries.map { it.objectId } + Decoration.entries.map { it.objectId } +
            GroundCover.entries.map { it.objectId } + StructureData.entries.map { it.objectId } +
            GridObject.RIVER_OBJECT_ID + GridObject.COBBLESTONE_OBJECT_ID + GridObject.WOOD_DECK_OBJECT_ID + GridObject.TILLED_SOIL_OBJECT_ID + GridObject.LOOSE_SOIL_OBJECT_ID + GridObject.GREENHOUSE_OBJECT_ID +
            FencePart.entries.map { it.objectId } + RunKind.entries.map { it.objectId }.drop(1) + GridObject.GIFT_BOX_OBJECT_ID

    /**
     * Ground sprite for a cell: water for the river, grass for the lawn. The lawn used to be a
     * two-tone checkerboard; since #47 it is one shade, laid as one meadow (the grid lines show
     * the cells in edit mode), and [GroundTile.GRASS_DARK] is not used.
     */
    @Suppress("UNUSED_PARAMETER")
    fun groundTileFor(type: TileType, gridX: Int, gridY: Int): GroundTile = when (type) {
        TileType.RIVER -> GroundTile.WATER
        TileType.GRASS -> GroundTile.GRASS
    }

    /**
     * Sprite id for an object in its current state. Plants resolve to a growth-stage sprite
     * (or `<objectId>_withered`); everything else uses its object id unchanged.
     */
    fun assetIdFor(gridObject: GridObject): String {
        if (!gridObject.isPlant) {
            return if (!gridObject.turned) gridObject.objectId else "${gridObject.objectId}${if (gridObject.isBridge) "_long" else "_back"}"
        }
        val species = PlantSpecies.forObjectId(gridObject.objectId)
        if (gridObject.status == PlantStatus.WITHERED && species.canWither) return witheredId(gridObject.objectId)
        val stage = growthStage(gridObject.growthProgress, species.growthStages)
        val plain = stageId(gridObject.objectId, stage)
        if (stage < species.growthStages) return plain
        val colour = BloomColours.colourOf(gridObject)?.takeIf { it != species.bloomColours.first() } ?: return plain
        return "${plain}_$colour"
    }

    private fun stageId(objectId: String, stage: Int) = "${objectId}_stage$stage"

    private fun witheredId(objectId: String) = "${objectId}_withered"

    /**
     * The flowers a crop blooms in besides its plain last stage (#58): each plant takes one, or
     * the plain one, as it opens ([BloomColours]). Drawn as `<objectId>_stage<N>_<colour>`.
     */
    val BLOOM_COLOURS: Map<String, List<String>> =
        PlantSpecies.entries.filter { it.bloomColours.size > 1 }.associate { it.objectId to it.bloomColours.drop(1) }

    /**
     * Every sprite a crop is drawn with: `<objectId>_stage1` to `_stage<N>`, the colours of its
     * last stage, then `<objectId>_withered` unless it never withers.
     */
    fun spriteIdsFor(species: PlantSpecies): List<String> =
        (1..species.growthStages).map { stageId(species.objectId, it) } +
            BLOOM_COLOURS[species.objectId].orEmpty().map { "${stageId(species.objectId, species.growthStages)}_$it" } +
            listOfNotNull(witheredId(species.objectId).takeIf { species.canWither })

    /**
     * The picture of a visitor, in the creatures' atlas ([SpriteAtlas.creatures]): `creature_<id>`,
     * standing (or, for a flier, flying with its wings spread) as the nature notes show it. Those
     * on the ground face left, standing with their anchor at their feet; the butterfly and the
     * dragonfly are seen from above, head up, anchored on their thorax.
     */
    fun assetIdFor(creature: Creature): String = "creature_${creature.id}"

    /** The squirrel running, `creature_squirrel_run`: drawn while it goes from cell to cell. Its anchor is the middle of its underside. */
    const val SQUIRREL_RUNNING = "creature_squirrel_run"

    /**
     * A flier's picture in parts ([wingIds]), the body first: the butterfly's and the dragonfly's
     * `creature_<id>_body`, and the bird's flying one, `creature_bird_fly_body` (anchored on the
     * middle of its body). Null for those that never fly.
     */
    fun wingedBodyId(creature: Creature): String? = when (creature) {
        Creature.BUTTERFLY, Creature.DRAGONFLY -> "${assetIdFor(creature)}_body"
        Creature.BIRD -> "${assetIdFor(creature)}_fly_body"
        else -> null
    }

    /**
     * The left and right wings of a flier, beating on their own: `<body without _body>_wing_left`
     * and `_wing_right`. Each is anchored where it joins the body, and its attachment point is
     * where the body's anchor lies: hung on the body by the two, it turns or folds about its root.
     */
    fun wingIds(creature: Creature): List<String> {
        val body = wingedBodyId(creature) ?: return emptyList()
        val stem = body.removeSuffix("_body")
        return listOf("${stem}_wing_left", "${stem}_wing_right")
    }

    /** Every sprite id the creatures' atlas has: each visitor's picture, the running squirrel, and the fliers in parts. */
    val expectedCreatureSpriteIds: List<String> =
        Creature.entries.flatMap { listOf(assetIdFor(it)) + listOfNotNull(wingedBodyId(it)) + wingIds(it) } + SQUIRREL_RUNNING

    /**
     * Things drawn from a picture of their own in `res/drawable` (through [TerrainTextures]),
     * not from the atlas: they are never looked up there. The ground of #74 is among them: laid
     * over a cell so that the cells run on into one another, its picture repeats, and a part of
     * an atlas page cannot.
     */
    val DRAWABLE_OBJECT_IDS: Set<String> = setOf(
        GridObject.POND_OBJECT_ID, GridObject.COBBLESTONE_OBJECT_ID, GridObject.WOOD_DECK_OBJECT_ID,
        GroundCover.PEBBLE.objectId, GroundCover.FLOWERS.objectId,
    )

    /** The windmill's sails: a sprite of their own, hung on the mill's hub and turned. */
    const val WINDMILL_SAILS = "struct_windmill_blades"

    /** Frames of a running sprinkler's water, drawn over the sprinkler one after another. */
    const val SPRINKLER_SPRAY_FRAMES = 8

    /** The sprite of one frame of the sprinkler's water, 0 to [SPRINKLER_SPRAY_FRAMES] - 1. */
    fun sprinklerSprayId(frame: Int): String = "deco_sprinkler_spray_$frame"

    /**
     * Every sprite id the garden may look up in its atlas: the ground tiles, each crop's stages,
     * every other thing that stands in a cell and the windmill's sails, whether the atlas has
     * it yet or not. Against the atlas it gives the artwork still missing; anything in the atlas
     * that is not here would never be drawn.
     */
    val expectedSpriteIds: List<String> =
        GroundTile.entries.map { it.assetId } +
            PlantSpecies.entries.flatMap(::spriteIdsFor) +
            ((objectIds + RunKind.entries.flatMap { it.spriteIds }).distinct() - PlantSpecies.entries.map { it.objectId }.toSet() - DRAWABLE_OBJECT_IDS) +
            WINDMILL_SAILS +
            (0 until SPRINKLER_SPRAY_FRAMES).map(::sprinklerSprayId) +
            "deco_bench_back" + "struct_bridge_taiko_long" + BuildingPaint.PAINTABLE_OBJECT_IDS.map { "mask_$it" }

    /** 1-based stage; the last stage is reserved for a fully grown plant (progress 1.0). */
    fun growthStage(growthProgress: Float, stages: Int): Int {
        if (stages <= 1) return 1
        val progress = growthProgress.coerceIn(0f, 1f)
        return 1 + (progress * (stages - 1)).toInt()
    }
}
