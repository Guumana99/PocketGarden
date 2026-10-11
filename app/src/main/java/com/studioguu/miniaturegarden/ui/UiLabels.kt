package com.studioguu.miniaturegarden.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.ActionResult

// Display names and emoji icons for domain values. Emoji stand in for icon artwork for now.

/** Name of a grid object; unknown ids are shown as-is. */
@Composable
fun objectDisplayName(objectId: String): String = when (objectId) {
    "plant_tulip" -> stringResource(R.string.object_plant_tulip)
    "plant_sunflower" -> stringResource(R.string.object_plant_sunflower)
    "plant_rose" -> stringResource(R.string.object_plant_rose)
    "deco_rock" -> stringResource(R.string.object_deco_rock)
    "struct_hut" -> stringResource(R.string.object_struct_hut)
    "struct_well" -> stringResource(R.string.object_struct_well)
    "ground_grass" -> stringResource(R.string.object_ground_grass)
    "ground_pebble" -> stringResource(R.string.object_ground_pebble)
    "terrain_river" -> stringResource(R.string.object_terrain_river)
    "floor_cobblestone" -> stringResource(R.string.object_floor_cobblestone)
    "struct_fence" -> stringResource(R.string.object_struct_fence)
    else -> objectId
}

@Composable
fun itemDisplayName(item: GardenItem): String = when (item) {
    GardenItem.SEED_TULIP -> stringResource(R.string.item_seed_tulip)
    GardenItem.SEED_SUNFLOWER -> stringResource(R.string.item_seed_sunflower)
    GardenItem.SEED_ROSE -> stringResource(R.string.item_seed_rose)
    GardenItem.TILE_RIVER -> stringResource(R.string.item_tile_river)
    else -> objectDisplayName(item.objectId)
}

fun itemEmoji(item: GardenItem): String = when (item) {
    GardenItem.SEED_TULIP -> "🌷"
    GardenItem.SEED_SUNFLOWER -> "🌻"
    GardenItem.SEED_ROSE -> "🌹"
    GardenItem.DECO_ROCK -> "🪨"
    GardenItem.STRUCT_HUT -> "🛖"
    GardenItem.STRUCT_WELL -> "🪣"
    GardenItem.GROUND_GRASS -> "🌿"
    GardenItem.GROUND_PEBBLE -> "⚪"
    GardenItem.TILE_RIVER -> "🌊"
    GardenItem.FLOOR_COBBLESTONE -> "🧱"
    GardenItem.STRUCT_FENCE -> "🪵"
}

fun weatherEmoji(weather: WeatherType): String = when (weather) {
    WeatherType.SUNNY -> "☀️"
    WeatherType.CLOUDY -> "☁️"
    WeatherType.RAINY -> "🌧️"
}

@StringRes
fun weatherLabel(weather: WeatherType): Int = when (weather) {
    WeatherType.SUNNY -> R.string.weather_sunny
    WeatherType.CLOUDY -> R.string.weather_cloudy
    WeatherType.RAINY -> R.string.weather_rainy
}

@StringRes
fun statusLabel(status: PlantStatus): Int = when (status) {
    PlantStatus.HEALTHY -> R.string.status_healthy
    PlantStatus.THIRSTY -> R.string.status_thirsty
    PlantStatus.UNNOURISHED -> R.string.status_unnourished
    PlantStatus.WITHERED -> R.string.status_withered
}

/** Message explaining a failed action; null for [ActionResult.SUCCESS]. */
@StringRes
fun actionResultMessage(result: ActionResult): Int? = when (result) {
    ActionResult.SUCCESS -> null
    ActionResult.NOT_FOUND -> R.string.msg_not_found
    ActionResult.NOT_A_PLANT -> R.string.msg_not_a_plant
    ActionResult.WITHERED -> R.string.msg_withered
    ActionResult.NOT_READY -> R.string.msg_not_ready
    ActionResult.ALREADY_MOIST -> R.string.msg_already_moist
    ActionResult.TILE_OCCUPIED -> R.string.msg_tile_occupied
    ActionResult.TILE_NOT_BUILDABLE -> R.string.msg_tile_not_buildable
    ActionResult.OUT_OF_BOUNDS -> R.string.msg_out_of_bounds
    ActionResult.SLOT_EMPTY -> R.string.msg_slot_empty
    ActionResult.NOT_ENOUGH_COINS -> R.string.msg_not_enough_coins
    ActionResult.INVENTORY_FULL -> R.string.msg_inventory_full
    ActionResult.ITEM_LOCKED -> R.string.msg_item_locked
}
