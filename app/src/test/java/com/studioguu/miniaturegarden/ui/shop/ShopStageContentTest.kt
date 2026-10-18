package com.studioguu.miniaturegarden.ui.shop

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The shop as a stage: walking between shops, taking things off the shelf, buying at the counter. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
class ShopStageContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private val stats = PlayerStats(6, LevelTable.xpForLevel(6))

    /** A small shop the test can walk around in; returns what the player did. */
    private class Visit {
        var stage by mutableStateOf(ShopStage.ENTRANCE)
        var inHand by mutableStateOf<GardenItem?>(null)
        var message by mutableStateOf<ShopMessage?>(null)
        val bought = mutableListOf<GardenItem>()
        var left = false
    }

    private fun open(visit: Visit, coins: Long = 500, ownsSymbol: Boolean = false, held: Map<GardenItem, Int> = emptyMap()) {
        compose.setContent {
            val stock = ShopShelves.stock(visit.stage, Season.SPRING, stats, coins, ownsSymbol, held)
            GardenTheme {
                ShopStageContent(
                    stage = visit.stage,
                    shelves = ShopShelves.shelves(stock),
                    coins = coins,
                    season = Season.SPRING,
                    inHand = visit.inHand?.let { item -> stock.firstOrNull { it.item == item } },
                    message = visit.message,
                    bonusLabel = "bonus",
                    bonusEnabled = false,
                    onGoTo = { visit.stage = it; visit.inHand = null },
                    onInspect = { visit.inHand = it },
                    onPutBack = { visit.inHand = null },
                    onBuy = { visit.bought += it },
                    onBonus = {},
                    onLeave = { visit.left = true },
                )
            }
        }
    }

    @Test
    fun `the way in is the seed shop, with its things on the shelves and their prices`() {
        open(Visit())
        compose.onNodeWithTag(SHOP_STAGE_TAG).assertExists()
        compose.onNode(hasText(text(R.string.shop_stage_seeds))).assertExists()
        compose.onNodeWithTag(shelfTag(GardenItem.SEED_TULIP)).performScrollTo().assertExists()
        assertTrue(compose.onAllNodesWithText("🪙 ${GardenItem.SEED_TULIP.price}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // No card until something is picked up.
        compose.onNodeWithTag(ITEM_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `tapping a thing on the shelf brings its card up, and buying it goes through the counter`() {
        val visit = Visit()
        open(visit)
        compose.onNodeWithTag(shelfTag(GardenItem.SEED_TULIP)).performScrollTo().performClick()
        assertEquals(GardenItem.SEED_TULIP, visit.inHand)
        compose.onNodeWithTag(ITEM_CARD_TAG).assertExists()
        compose.onNodeWithText(text(R.string.shop_buy, GardenItem.SEED_TULIP.price)).performClick()
        assertEquals(listOf(GardenItem.SEED_TULIP), visit.bought)
        // Put back, the card goes again.
        compose.onNodeWithText(text(R.string.shop_put_back)).performClick()
        compose.waitForIdle()
        assertEquals(null, visit.inHand)
        compose.onNodeWithTag(ITEM_CARD_TAG).assertDoesNotExist()
    }

    @Test
    fun `the signboards walk to the other shops`() {
        val visit = Visit()
        open(visit)
        compose.onNodeWithText(text(R.string.shop_stage_workshop)).performClick()
        assertEquals(ShopStage.WORKSHOP, visit.stage)
        compose.waitForIdle()
        compose.onNodeWithTag(shelfTag(GardenItem.STRUCT_FENCE)).performScrollTo().assertExists()
        compose.onNodeWithTag(shelfTag(GardenItem.SEED_TULIP)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.shop_stage_sundries)).performClick()
        assertEquals(ShopStage.SUNDRIES, visit.stage)
    }

    @Test
    fun `the cottage that is already the player's has a card without a buy button`() {
        val visit = Visit().apply { stage = ShopStage.WORKSHOP; inHand = GardenItem.STRUCT_COTTAGE }
        open(visit, ownsSymbol = true)
        compose.onNodeWithTag(ITEM_CARD_TAG).assertExists()
        compose.onNodeWithText(text(R.string.shop_buy, GardenItem.STRUCT_COTTAGE.price)).assertDoesNotExist()
        assertTrue(visit.bought.isEmpty())
    }

    @Test
    fun `how many are in the bag shows on the shelf and on the card`() {
        val visit = Visit()
        open(visit, held = mapOf(GardenItem.SEED_TULIP to 3))
        compose.onNodeWithTag(heldTag(GardenItem.SEED_TULIP), useUnmergedTree = true).performScrollTo().assertTextEquals(text(R.string.shop_held, 3))
        compose.onNodeWithTag(heldTag(GardenItem.SEED_MINT), useUnmergedTree = true).performScrollTo().assertTextEquals(text(R.string.shop_held, 0))
        compose.onNodeWithTag(shelfTag(GardenItem.SEED_TULIP)).performScrollTo().performClick()
        compose.onNodeWithTag(CARD_HELD_TAG, useUnmergedTree = true).assertTextEquals(text(R.string.shop_held, 3))
    }

    @Test
    fun `a word said at the counter does not move the card or its buttons`() {
        val visit = Visit().apply { inHand = GardenItem.SEED_TULIP }
        open(visit)
        val buy = text(R.string.shop_buy, GardenItem.SEED_TULIP.price)
        val cardBefore = compose.onNodeWithTag(ITEM_CARD_TAG).getBoundsInRoot()
        val buyBefore = compose.onNodeWithText(buy).getBoundsInRoot()
        visit.message = ShopMessage(text(R.string.shop_bought, "チューリップの種"), good = true)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.shop_bought, "チューリップの種")).assertExists()
        assertEquals(cardBefore, compose.onNodeWithTag(ITEM_CARD_TAG).getBoundsInRoot())
        assertEquals(buyBefore, compose.onNodeWithText(buy).getBoundsInRoot())
    }

    @Test
    fun `back to the garden leaves the shop`() {
        val visit = Visit()
        open(visit)
        compose.onNodeWithText(text(R.string.shop_leave)).performClick()
        assertTrue(visit.left)
    }
}
