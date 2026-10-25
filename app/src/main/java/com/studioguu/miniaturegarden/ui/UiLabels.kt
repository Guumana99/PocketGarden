package com.studioguu.miniaturegarden.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.VisitRule
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.usecase.ActionResult

// Display names and icons for domain values. Emoji stand in for icon artwork where there is none.

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
    "plant_carrot" -> R.string.object_plant_carrot
    "plant_eggplant" -> R.string.object_plant_eggplant
    "plant_wheat" -> R.string.object_plant_wheat
    "plant_corn" -> R.string.object_plant_corn
    "plant_strawberry" -> R.string.object_plant_strawberry
    "plant_watermelon" -> R.string.object_plant_watermelon
    "plant_lavender" -> R.string.object_plant_lavender
    "plant_shiitake" -> R.string.object_plant_shiitake
    "plant_poinsettia" -> R.string.object_plant_poinsettia
    "floor_greenhouse" -> R.string.object_floor_greenhouse
    "deco_rock" -> R.string.object_deco_rock
    "struct_hut" -> R.string.object_struct_hut
    "struct_well" -> R.string.object_struct_well
    "struct_cottage" -> R.string.object_struct_cottage
    "struct_windmill" -> R.string.object_struct_windmill
    "ground_grass" -> R.string.object_ground_grass
    "ground_pebble" -> R.string.object_ground_pebble
    "terrain_river" -> R.string.object_terrain_river
    "floor_cobblestone" -> R.string.object_floor_cobblestone
    "floor_tilled" -> R.string.object_floor_tilled
    "floor_wood" -> R.string.object_floor_wood
    "ground_flowers" -> R.string.object_ground_flowers
    "deco_bench" -> R.string.object_deco_bench
    "deco_lantern" -> R.string.object_deco_lantern
    "deco_sprinkler" -> R.string.object_deco_sprinkler
    "deco_pond" -> R.string.object_deco_pond
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
    GardenItem.SEED_CARROT -> stringResource(R.string.item_seed_carrot)
    GardenItem.SEED_EGGPLANT -> stringResource(R.string.item_seed_eggplant)
    GardenItem.SEED_WHEAT -> stringResource(R.string.item_seed_wheat)
    GardenItem.SEED_CORN -> stringResource(R.string.item_seed_corn)
    GardenItem.SEED_STRAWBERRY -> stringResource(R.string.item_seed_strawberry)
    GardenItem.SEED_WATERMELON -> stringResource(R.string.item_seed_watermelon)
    GardenItem.SEED_LAVENDER -> stringResource(R.string.item_seed_lavender)
    GardenItem.SEED_SHIITAKE -> stringResource(R.string.item_seed_shiitake)
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
    GardenItem.DECO_SPRINKLER -> "💦"
    GardenItem.DECO_POND -> "⛲"
    GardenItem.STRUCT_COTTAGE -> "🏡"
    // There is no windmill emoji; the wind it turns in stands for it.
    GardenItem.STRUCT_WINDMILL -> "🌬️"
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
    GardenItem.SEED_SHIITAKE -> "🍄"
    GardenItem.SEED_POINSETTIA -> "🌺"
    GardenItem.SEED_CARROT -> "🥕"
    GardenItem.SEED_EGGPLANT -> "🍆"
    GardenItem.SEED_WHEAT -> "🌾"
    GardenItem.SEED_CORN -> "🌽"
    GardenItem.FLOOR_GREENHOUSE -> "🪴"
}

/**
 * The picture standing for [item] (#57: the harvest of a crop, `res/drawable-nodpi/icon_*.png`),
 * or null for an item that is shown by its emoji ([itemEmoji]).
 */
@DrawableRes
fun itemIconRes(item: GardenItem): Int? = when (item) {
    GardenItem.SEED_TOMATO -> R.drawable.icon_tomato
    GardenItem.SEED_STRAWBERRY -> R.drawable.icon_strawberry
    GardenItem.SEED_CARROT -> R.drawable.icon_carrot
    GardenItem.SEED_EGGPLANT -> R.drawable.icon_eggplant
    GardenItem.SEED_WHEAT -> R.drawable.icon_wheat
    GardenItem.SEED_CORN -> R.drawable.icon_corn
    GardenItem.SEED_MINT -> R.drawable.icon_mint
    GardenItem.SEED_COSMOS -> R.drawable.icon_cosmos
    GardenItem.SEED_WATERMELON -> R.drawable.icon_watermelon
    GardenItem.SEED_SNOWDROP -> R.drawable.icon_snowdrop
    GardenItem.SEED_LAVENDER -> R.drawable.icon_lavender
    GardenItem.SEED_SHIITAKE -> R.drawable.icon_shiitake
    GardenItem.SEED_POINSETTIA -> R.drawable.icon_poinsettia
    GardenItem.SEED_TULIP -> R.drawable.icon_tulip
    GardenItem.SEED_SUNFLOWER -> R.drawable.icon_sunflower
    GardenItem.SEED_ROSE -> R.drawable.icon_rose
    GardenItem.SEED_PAPRIKA -> R.drawable.icon_paprika
    GardenItem.SEED_DAIKON -> R.drawable.icon_daikon
    else -> null
}

/**
 * [item]'s icon at about the size of an emoji set in [size]: its picture where it has one
 * ([itemIconRes]), its emoji otherwise. Decorative: the name is given by whatever holds it.
 */
@Composable
fun ItemIcon(item: GardenItem, size: TextUnit, modifier: Modifier = Modifier) {
    val picture = itemIconRes(item)
    if (picture != null) {
        // An emoji's glyph is about a third again as tall as its font size.
        Image(painterResource(picture), contentDescription = null, modifier = modifier.size((size.value * ICON_PER_SP).dp))
    } else {
        Text(itemEmoji(item), fontSize = size, modifier = modifier)
    }
}

/** dp of picture to an sp of emoji. */
private const val ICON_PER_SP = 1.3f

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
    ActionResult.NEEDS_OPEN_GROUND -> R.string.msg_needs_open_ground
    ActionResult.NOT_WITHERED -> R.string.msg_not_withered
    ActionResult.NOT_RETURNABLE -> R.string.msg_not_returnable
    ActionResult.NOT_MOVABLE -> R.string.msg_not_movable
    ActionResult.NOT_FLIPPABLE -> R.string.msg_not_flippable
    ActionResult.BUSY -> R.string.msg_busy
    ActionResult.ONLY_ONE_SYMBOL -> R.string.msg_only_one_symbol
    ActionResult.EXPANSION_LOCKED -> R.string.msg_expansion_locked
    ActionResult.GARDEN_AT_MAX_SIZE -> R.string.msg_garden_at_max_size
    ActionResult.NOT_THIS_STEP -> R.string.msg_not_this_step
    ActionResult.ALREADY_FED -> R.string.msg_already_fed
    ActionResult.OUT_OF_SEASON -> R.string.msg_out_of_season
    ActionResult.IN_USE -> R.string.msg_in_use
    ActionResult.HOE_STRUCK -> R.string.msg_hoe_struck
    ActionResult.NOT_SWITCHABLE -> R.string.msg_not_switchable
}

/** A tool's level as a rank, "見習い" (1) to "名人" (5). */
@StringRes
fun toolRank(level: Int): Int = when (level.coerceIn(1, 5)) {
    1 -> R.string.tool_rank_1
    2 -> R.string.tool_rank_2
    3 -> R.string.tool_rank_3
    4 -> R.string.tool_rank_4
    else -> R.string.tool_rank_5
}

/** A tool's level as the stars on its button: one for each step past the first, none for a new tool. */
fun toolStars(level: Int): String = "★".repeat((level - 1).coerceAtLeast(0))

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
    VisitRule.BUTTERFLY_LAVENDER -> R.string.combo_butterfly_lavender
    VisitRule.LADYBUG_STRAWBERRY -> R.string.combo_ladybug_strawberry
    VisitRule.BIRD_HUT -> R.string.combo_bird_hut
    VisitRule.BIRD_WINTER_WELL -> R.string.combo_bird_winter_well
    VisitRule.SQUIRREL_SHIITAKE -> R.string.combo_squirrel_shiitake
    VisitRule.FROG_WELL -> R.string.combo_frog_well
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
    VisitRule.BUTTERFLY_LAVENDER -> R.string.hint_butterfly_lavender
    VisitRule.LADYBUG_STRAWBERRY -> R.string.hint_ladybug_strawberry
    VisitRule.BIRD_HUT -> R.string.hint_bird_hut
    VisitRule.BIRD_WINTER_WELL -> R.string.hint_bird_winter_well
    VisitRule.SQUIRREL_SHIITAKE -> R.string.hint_squirrel_shiitake
    VisitRule.FROG_WELL -> R.string.hint_frog_well
}
