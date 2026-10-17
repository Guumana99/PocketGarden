package com.studioguu.miniaturegarden.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.ActionResult

// Display names and emoji icons for domain values. Emoji stand in for icon artwork for now.

/** The string resource naming a grid object; null for an id that has none. */
@StringRes
fun objectNameRes(objectId: String): Int? = when (objectId) {
    "plant_tulip" -> R.string.object_plant_tulip
    "plant_sunflower" -> R.string.object_plant_sunflower
    "plant_rose" -> R.string.object_plant_rose
    "plant_cosmos" -> R.string.object_plant_cosmos
    "plant_snowdrop" -> R.string.object_plant_snowdrop
    "plant_mint" -> R.string.object_plant_mint
    "plant_tomato" -> R.string.object_plant_tomato
    "plant_paprika" -> R.string.object_plant_paprika
    "plant_daikon" -> R.string.object_plant_daikon
    "plant_strawberry" -> R.string.object_plant_strawberry
    "plant_watermelon" -> R.string.object_plant_watermelon
    "plant_lavender" -> R.string.object_plant_lavender
    "plant_pumpkin" -> R.string.object_plant_pumpkin
    "plant_poinsettia" -> R.string.object_plant_poinsettia
    "floor_greenhouse" -> R.string.object_floor_greenhouse
    "deco_rock" -> R.string.object_deco_rock
    "struct_hut" -> R.string.object_struct_hut
    "struct_well" -> R.string.object_struct_well
    "struct_cottage" -> R.string.object_struct_cottage
    "ground_grass" -> R.string.object_ground_grass
    "ground_pebble" -> R.string.object_ground_pebble
    "terrain_river" -> R.string.object_terrain_river
    "floor_cobblestone" -> R.string.object_floor_cobblestone
    "floor_tilled" -> R.string.object_floor_tilled
    "floor_wood" -> R.string.object_floor_wood
    "ground_flowers" -> R.string.object_ground_flowers
    "deco_bench" -> R.string.object_deco_bench
    "deco_lantern" -> R.string.object_deco_lantern
    "deco_starter_crate" -> R.string.object_deco_starter_crate
    // A fence is a fence under any of the ids it has been stored as.
    "struct_fence", "struct_fence_rail", "struct_fence_post" -> R.string.object_struct_fence
    else -> null
}

/** Name of a grid object; unknown ids are shown as-is. */
@Composable
fun objectDisplayName(objectId: String): String = objectNameRes(objectId)?.let { stringResource(it) } ?: objectId

@Composable
fun itemDisplayName(item: GardenItem): String = when (item) {
    GardenItem.SEED_TULIP -> stringResource(R.string.item_seed_tulip)
    GardenItem.SEED_SUNFLOWER -> stringResource(R.string.item_seed_sunflower)
    GardenItem.SEED_ROSE -> stringResource(R.string.item_seed_rose)
    GardenItem.SEED_COSMOS -> stringResource(R.string.item_seed_cosmos)
    GardenItem.SEED_SNOWDROP -> stringResource(R.string.item_seed_snowdrop)
    GardenItem.SEED_MINT -> stringResource(R.string.item_seed_mint)
    GardenItem.SEED_TOMATO -> stringResource(R.string.item_seed_tomato)
    GardenItem.SEED_PAPRIKA -> stringResource(R.string.item_seed_paprika)
    GardenItem.SEED_DAIKON -> stringResource(R.string.item_seed_daikon)
    GardenItem.SEED_STRAWBERRY -> stringResource(R.string.item_seed_strawberry)
    GardenItem.SEED_WATERMELON -> stringResource(R.string.item_seed_watermelon)
    GardenItem.SEED_LAVENDER -> stringResource(R.string.item_seed_lavender)
    GardenItem.SEED_PUMPKIN -> stringResource(R.string.item_seed_pumpkin)
    GardenItem.SEED_POINSETTIA -> stringResource(R.string.item_seed_poinsettia)
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
    GardenItem.GROUND_FLOWERS -> "🌼"
    GardenItem.FLOOR_WOOD -> "🟫"
    GardenItem.DECO_BENCH -> "🪑"
    GardenItem.DECO_LANTERN -> "🏮"
    GardenItem.STRUCT_COTTAGE -> "🏡"
    GardenItem.SEED_COSMOS -> "🏵️"
    GardenItem.SEED_SNOWDROP -> "🤍"
    GardenItem.SEED_MINT -> "🍃"
    GardenItem.SEED_TOMATO -> "🍅"
    GardenItem.SEED_PAPRIKA -> "🫑"
    // There is no daikon emoji; a leafy green is the closest.
    GardenItem.SEED_DAIKON -> "🥬"
    GardenItem.SEED_STRAWBERRY -> "🍓"
    GardenItem.SEED_WATERMELON -> "🍉"
    GardenItem.SEED_LAVENDER -> "💜"
    GardenItem.SEED_PUMPKIN -> "🎃"
    GardenItem.SEED_POINSETTIA -> "🌺"
    GardenItem.FLOOR_GREENHOUSE -> "🪴"
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
fun seasonLabel(season: Season): Int = when (season) {
    Season.SPRING -> R.string.season_spring
    Season.SUMMER -> R.string.season_summer
    Season.AUTUMN -> R.string.season_autumn
    Season.WINTER -> R.string.season_winter
}

fun seasonEmoji(season: Season): String = when (season) {
    Season.SPRING -> "🌸"
    Season.SUMMER -> "🌿"
    Season.AUTUMN -> "🍁"
    Season.WINTER -> "❄️"
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
    ActionResult.NEEDS_TILLED_SOIL -> R.string.msg_needs_tilled_soil
    ActionResult.NOT_WITHERED -> R.string.msg_not_withered
    ActionResult.NOT_RETURNABLE -> R.string.msg_not_returnable
    ActionResult.NOT_MOVABLE -> R.string.msg_not_movable
    ActionResult.BUSY -> R.string.msg_busy
    ActionResult.ONLY_ONE_SYMBOL -> R.string.msg_only_one_symbol
    ActionResult.EXPANSION_LOCKED -> R.string.msg_expansion_locked
    ActionResult.GARDEN_AT_MAX_SIZE -> R.string.msg_garden_at_max_size
    ActionResult.OUT_OF_SEASON -> R.string.msg_out_of_season
    ActionResult.IN_USE -> R.string.msg_in_use
}

@StringRes
fun creatureName(creature: Creature): Int = when (creature) {
    Creature.BUTTERFLY -> R.string.creature_butterfly
    Creature.LADYBUG -> R.string.creature_ladybug
    Creature.BIRD -> R.string.creature_bird
    Creature.SQUIRREL -> R.string.creature_squirrel
    Creature.FROG -> R.string.creature_frog
    Creature.DRAGONFLY -> R.string.creature_dragonfly
}

/** Said once, the first time a creature comes. */
@StringRes
fun creatureMetMessage(creature: Creature): Int = when (creature) {
    Creature.BUTTERFLY -> R.string.msg_met_butterfly
    Creature.LADYBUG -> R.string.msg_met_ladybug
    Creature.BIRD -> R.string.msg_met_bird
    Creature.SQUIRREL -> R.string.msg_met_squirrel
    Creature.FROG -> R.string.msg_met_frog
    Creature.DRAGONFLY -> R.string.msg_met_dragonfly
}

/** What the nature notes say about a creature once it has been met. */
@StringRes
fun creatureFlavor(creature: Creature): Int = when (creature) {
    Creature.BUTTERFLY -> R.string.creature_flavor_butterfly
    Creature.LADYBUG -> R.string.creature_flavor_ladybug
    Creature.BIRD -> R.string.creature_flavor_bird
    Creature.SQUIRREL -> R.string.creature_flavor_squirrel
    Creature.FROG -> R.string.creature_flavor_frog
    Creature.DRAGONFLY -> R.string.creature_flavor_dragonfly
}

/** A combination once found, in a few words. */
@StringRes
fun combinationLabel(rule: VisitRule): Int = when (rule) {
    VisitRule.BUTTERFLY_BLOOMS -> R.string.combo_butterfly_blooms
    VisitRule.LADYBUG_TULIPS -> R.string.combo_ladybug_tulips
    VisitRule.BIRD_BENCH -> R.string.combo_bird_bench
    VisitRule.BIRD_COTTAGE -> R.string.combo_bird_cottage
    VisitRule.SQUIRREL_SUNFLOWER -> R.string.combo_squirrel_sunflower
    VisitRule.FROG_RAIN -> R.string.combo_frog_rain
    VisitRule.FROG_SUMMER -> R.string.combo_frog_summer
    VisitRule.SQUIRREL_AUTUMN -> R.string.combo_squirrel_autumn
    VisitRule.DRAGONFLY_AUTUMN -> R.string.combo_dragonfly_autumn
}

/** A quiet hint for a combination not found yet: what the creature seems to like. */
@StringRes
fun combinationHint(rule: VisitRule): Int = when (rule) {
    VisitRule.BUTTERFLY_BLOOMS -> R.string.hint_butterfly_blooms
    VisitRule.LADYBUG_TULIPS -> R.string.hint_ladybug_tulips
    VisitRule.BIRD_BENCH -> R.string.hint_bird_bench
    VisitRule.BIRD_COTTAGE -> R.string.hint_bird_cottage
    VisitRule.SQUIRREL_SUNFLOWER -> R.string.hint_squirrel_sunflower
    VisitRule.FROG_RAIN -> R.string.hint_frog_rain
    VisitRule.FROG_SUMMER -> R.string.hint_frog_summer
    VisitRule.SQUIRREL_AUTUMN -> R.string.hint_squirrel_autumn
    VisitRule.DRAGONFLY_AUTUMN -> R.string.hint_dragonfly_autumn
}
