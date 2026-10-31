package com.studioguu.miniaturegarden.debug

import com.studioguu.miniaturegarden.domain.model.GridObject
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
