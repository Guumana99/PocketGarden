package com.studioguu.miniaturegarden.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** So many of one [GardenItem], by its [GardenItem.itemId]. */
@Serializable
data class GiftItem(val itemId: String, val amount: Int)

/**
 * What a gift box in the garden holds. A box is set down with
 * [com.studioguu.miniaturegarden.domain.usecase.PlaceGiftUseCase], which takes any gift: the first
 * one after the first steps, or whatever is handed out later. The contents travel in the box
 * itself ([GridObject.gift]), so nothing else has to know about them.
 *
 * @property id a garden gets one box of each id.
 * @property title what the box calls itself; null for the first gift's name.
 * @property message what it says inside; null for the first gift's words.
 */
@Serializable
data class Gift(
    val id: String,
    val items: List<GiftItem> = emptyList(),
    val coins: Long = 0L,
    val title: String? = null,
    val message: String? = null,
) {
    val encoded: String get() = Json.encodeToString(serializer(), this)

    companion object {
        /** In the garden when the first steps are over: a few of the first seeds. */
        val FIRST = Gift(id = "first", items = listOf(GiftItem(GardenItem.SEED_TULIP.itemId, 3)))

        /** What [box] holds. A box from before gifts, or one that cannot be read, holds the first gift. */
        fun inside(box: GridObject): Gift = try {
            box.gift?.let { Json.decodeFromString(serializer(), it) } ?: FIRST
        } catch (_: SerializationException) {
            FIRST
        } catch (_: IllegalArgumentException) {
            FIRST
        }
    }
}
