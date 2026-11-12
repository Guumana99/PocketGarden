package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantGround
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherOutlook

/**
 * Beds to look at the crops of #87 stage by stage (debug builds only), loaded with
 * `--es garden crops`: a row of beds for each of the hakusai, the nanohana, the cosmos and the
 * lavender, stages 1 to 3 and withered; a row of shiitake logs, all grown, to see one hide the next;
 * and a weathervane on the open ground.
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10
 *      1  . 1 2 3 w . . . . . .      hakusai
 *      3  . 1 2 3 w . . V . . .      nanohana; V the weathervane
 *      5  . 1 2 3 w . . . . . .      cosmos
 *      7  . 1 2 3 w . . . . . .      lavender
 *      9  . 3 3 3 3 . . . . . .      shiitake
 */
object CropGarden {
    fun objects(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String) = GridObject(
            instanceId = "cg_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = now,
        )
        val rows = listOf(1 to PlantSpecies.HAKUSAI, 3 to PlantSpecies.NANOHANA, 5 to PlantSpecies.COSMOS, 7 to PlantSpecies.LAVENDER)
        val stages = rows.flatMap { (y, species) ->
            listOf(0f, 0.5f, 1f).mapIndexed { i, progress -> at(1 + i, y, species.objectId).copy(growthProgress = progress) } +
                at(4, y, species.objectId).copy(growthProgress = 1f, status = PlantStatus.WITHERED)
        }
        val logs = (1..4).map { at(it, 9, PlantSpecies.SHIITAKE.objectId).copy(growthProgress = 1f) }
        val beds = (stages + logs).map { at(it.gridX, it.gridY, GridObject.TILLED_SOIL_OBJECT_ID) }
        return beds + stages + logs + at(7, 3, WeatherOutlook.WEATHERVANE_OBJECT_ID)
    }
}

/**
 * The rose beside the crops of about its size (device feedback 11/03: the rose stood larger than
 * the rest), loaded with `--es garden roses`: grown in the front row, the stage before in the back.
 *
 *     y\x 0 1 2 3 4 5 6 7
 *      3  . R S W L T C .      the stage before the last
 *      5  . R S W L T C .      grown: rose, sunflower, wheat, lavender, tulip, cosmos
 */
object RoseGarden {
    fun objects(now: Long): List<GridObject> {
        val species = listOf(PlantSpecies.ROSE, PlantSpecies.SUNFLOWER, PlantSpecies.WHEAT, PlantSpecies.LAVENDER, PlantSpecies.TULIP, PlantSpecies.COSMOS)
        val plants = species.flatMapIndexed { i, plant ->
            // The stage before the last: 0.8 is the rose's third of four, 0.5 the second of three.
            val before = if (plant.growthStages == 4) 0.8f else 0.5f
            listOf(3 to before, 5 to 1f).map { (y, progress) ->
                GridObject(
                    instanceId = "rg_${plant.objectId}_$y", gridX = 1 + i, gridY = y, objectId = plant.objectId,
                    growthProgress = progress, lastUpdatedTimestamp = now,
                )
            }
        }
        val beds = plants.map {
            val bed = if (PlantSpecies.forObjectId(it.objectId).ground == PlantGround.FLOWER_BED) GridObject.LOOSE_SOIL_OBJECT_ID else GridObject.TILLED_SOIL_OBJECT_ID
            it.copy(instanceId = "rg_bed_${it.gridX}_${it.gridY}", objectId = bed)
        }
        return beds + plants
    }
}

/**
 * Every crop fully grown, side by side on its own ground (`--es garden lineup`, #fb-1111): flowers
 * on tilled earth, vegetables on ridges, the shiitake's log on the bare ground; a bench and the
 * cottage at the same scale, to see how big each crop stands next to them.
 *
 *     y\x 0 1 2 3 4 5 6 7 8 9 10
 *      1  . . . . C C . . . . .      the cottage (2 by 2), behind the crops
 *      2  . B . . C C . . . . .      a bench
 *      5  . c c c c c c c c c c      the first ten crops, in their order
 *      6  . c c c c c c c c c c      the rest
 */
object CropLineup {
    fun objects(now: Long): List<GridObject> {
        fun at(x: Int, y: Int, objectId: String) = GridObject(
            instanceId = "cl_${objectId}_${x}_$y", gridX = x, gridY = y, objectId = objectId, lastUpdatedTimestamp = now,
        )
        val plants = PlantSpecies.entries.mapIndexed { i, species ->
            at(1 + i % 10, 5 + i / 10, species.objectId).copy(growthProgress = 1f)
        }
        val beds = plants.mapNotNull {
            when (PlantSpecies.forObjectId(it.objectId).ground) {
                PlantGround.FLOWER_BED -> at(it.gridX, it.gridY, GridObject.LOOSE_SOIL_OBJECT_ID)
                PlantGround.LOG -> null
                else -> at(it.gridX, it.gridY, GridObject.TILLED_SOIL_OBJECT_ID)
            }
        }
        return beds + plants + at(1, 2, "deco_bench") + at(4, 1, "struct_cottage")
    }
}
