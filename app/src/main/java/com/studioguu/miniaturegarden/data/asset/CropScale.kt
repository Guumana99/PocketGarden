package com.studioguu.miniaturegarden.data.asset

/**
 * Keeps the crops' growth stages at a natural size next to one another. The pictures come drawn
 * at whatever size they were painted, so a seedling could stand as tall as a grown tulip (tomato
 * stage 1 was 316 px against the tulip's 222 px), or a middle stage taller than the plant it
 * grows into. Here each stage is given a draw scale, never larger than it had, so that:
 *
 * - a seedling (stage 1) is no taller than [SEEDLING_HEIGHT] of a tile, and no wider than
 *   [SEEDLING_WIDTH]: a bushy seedling (the rose's, painted with a full head of leaves) is held to
 *   a slim tulip shoot's breadth, which brings it to about 0.6 of the size it was painted at;
 * - no stage is taller than [MAX_HEIGHT] or wider than [MAX_WIDTH] of a tile;
 * - every stage is at most as tall as the next one: a plant only ever grows.
 *
 * Sizes are in shares of the tile width, so they hold whatever resolution the art is packed at.
 */
object CropScale {
    /** A seedling is a small thing: about the height of the tulip's first shoots. */
    const val SEEDLING_HEIGHT = 0.47f

    /**
     * How wide a seedling spreads, at most: the tulip's first shoots (0.30 of a tile). Height
     * alone let the rose's leafy seedling stand nearly as full as a young plant.
     */
    const val SEEDLING_WIDTH = 0.30f

    /** The tallest any crop stands, fully grown. */
    const val MAX_HEIGHT = 0.9f

    /** The widest any crop spreads: a little over its own tile. */
    const val MAX_WIDTH = 1.15f

    /**
     * Crops drawn as several plants to a cell at every stage (#57): the corn's three stalks and
     * the wheat's whole field. Their seedlings are as many plants as they grow into, so they are
     * not held to one shoot's breadth ([SEEDLING_WIDTH]), only to [MAX_WIDTH].
     */
    val CLUMPS = setOf("plant_corn", "plant_wheat")

    private val STAGE = Regex("""^(plant_[a-z_]+?)_stage(\d+)$""")
    private val WITHERED = Regex("""^plant_[a-z_]+_withered$""")

    /** Another colour of a crop's stage (#58: `plant_tulip_stage3_pink`), drawn at that stage's scale. */
    private val COLOUR = Regex("""^(plant_[a-z_]+?_stage\d+)_[a-z]+$""")

    /** [manifest] with its crops' stages scaled as above; everything else as it was. */
    fun normalized(manifest: AtlasManifest): AtlasManifest {
        val tile = manifest.tileWidth.toFloat()
        val scales = HashMap<String, Float>()
        manifest.assets
            .mapNotNull { asset -> STAGE.find(asset.assetId)?.let { Triple(it.groupValues[1], it.groupValues[2].toInt(), asset) } }
            .groupBy({ it.first }, { it.second to it.third })
            .forEach { (plant, stages) ->
                // From the grown plant down to the seedling, each one held under the next.
                var nextHeight = Float.MAX_VALUE
                for ((stage, asset) in stages.sortedByDescending { it.first }) {
                    val heightCap = minOf(if (stage == 1) SEEDLING_HEIGHT * tile else MAX_HEIGHT * tile, nextHeight)
                    val widthCap = if (stage == 1 && plant !in CLUMPS) SEEDLING_WIDTH * tile else MAX_WIDTH * tile
                    val scale = capped(asset, heightCap, widthCap)
                    scales[asset.assetId] = scale
                    nextHeight = asset.height * scale
                }
            }
        manifest.assets.filter { WITHERED.matches(it.assetId) }.forEach { asset ->
            scales[asset.assetId] = capped(asset, MAX_HEIGHT * tile, MAX_WIDTH * tile)
        }
        manifest.assets.forEach { asset ->
            val stage = COLOUR.find(asset.assetId)?.groupValues?.get(1) ?: return@forEach
            scales[asset.assetId] = scales[stage] ?: capped(asset, MAX_HEIGHT * tile, MAX_WIDTH * tile)
        }
        if (scales.isEmpty()) return manifest
        return manifest.copy(assets = manifest.assets.map { asset -> scales[asset.assetId]?.let { asset.copy(drawScale = it) } ?: asset })
    }

    /** The asset's own draw scale, brought down until it fits under [maxHeight] and [maxWidth]. */
    private fun capped(asset: AssetManifest, maxHeight: Float, maxWidth: Float): Float =
        minOf(asset.drawScale, maxHeight / asset.height, maxWidth / asset.width)
}
