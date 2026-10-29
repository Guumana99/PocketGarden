package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
import com.studioguu.miniaturegarden.domain.model.LevelTable
import com.studioguu.miniaturegarden.domain.model.PostOffice
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.ui.shop.ExpansionOffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The garden says when it can grow (#45, reworked): a letter once per size when the level for it
 * is reached, and the workshop's board, where it is grown for its price.
 */
class ExpansionGuideTest {

    private val t0 = 1_700_000_000_000L

    private fun source(path: String): String {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        return java.io.File(main, path).readText().replace("\r\n", "\n")
    }

    // --- The letter ---

    @Test
    fun `a letter comes when the level for the next size is reached, once for each size, coins or not`() {
        assertNull(PostOffice.gardenCanGrow(11, 7, emptyList(), Season.SPRING, t0))
        val letter = PostOffice.gardenCanGrow(11, 8, emptyList(), Season.SPRING, t0)
        assertNotNull(letter)
        assertEquals(LetterKind.GARDEN_CAN_GROW, letter!!.kind)
        assertEquals(13, letter.growSize)
        // Not again for the same size, and nothing once it is as large as it gets for now.
        assertNull(PostOffice.gardenCanGrow(11, 9, listOf(letter), Season.SPRING, t0 + 1))
        assertNull(PostOffice.gardenCanGrow(13, 99, listOf(letter), Season.SPRING, t0 + 2))
    }

    @Test
    fun `a garden begun at 9 x 9 hears it can grow to 11 at once, for nothing`() {
        val first = PostOffice.gardenCanGrow(GridObject.LEGACY_GRID_SIZE, 1, emptyList(), Season.SPRING, t0)
        assertEquals(11, first?.growSize)
        assertEquals(0, GardenExpansion.nextStage(GridObject.LEGACY_GRID_SIZE)?.price)
        val second = PostOffice.gardenCanGrow(11, 8, listOf(first!!), Season.SPRING, t0 + 1)
        assertNotEquals(first.letterId, second!!.letterId)
    }

    @Test
    fun `other letters do not count as having said it`() {
        val tip = Letter.newStock(7, 8, Season.SPRING, t0)
        assertNotNull(PostOffice.gardenCanGrow(11, 8, listOf(tip), Season.SPRING, t0))
        assertNull(tip.growSize)
    }

    @Test
    fun `the post office posts it once, from what is stored, with no cottage`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 0, playerLevel = 8, currentXp = LevelTable.xpForLevel(8))
            progress = GardenProgress(gardenSize = 11)
        }
        val post = PostLettersUseCase(repo)
        assertEquals(13, post.gardenCanGrowIfDue(t0)?.growSize)
        assertNull(post.gardenCanGrowIfDue(t0 + 1_000))
        assertEquals(1, repo.letters.values.count { it.kind == LetterKind.GARDEN_CAN_GROW })
    }

    @Test
    fun `the letter only comes by post, and nothing pops up on screen`() {
        val model = source("java/com/studioguu/miniaturegarden/ui/GardenViewModel.kt")
        val announce = model.substringAfter("private suspend fun announceGrowthIfDue").substringBefore("\n    }")
        assertTrue(announce.contains("postLetters.gardenCanGrowIfDue(now)"))
        assertFalse(announce.contains("_messages"))
    }

    // --- The workshop ---

    @Test
    fun `the workshop's board says the level, then the price, and never the size or what is missing`() {
        assertEquals(ExpansionOffer(ExpansionCheck.NEEDS_LEVEL, 8, 3_000), ExpansionOffer.of(11, 7, 1_000_000))
        assertEquals(ExpansionOffer(ExpansionCheck.NEEDS_COINS, 8, 3_000), ExpansionOffer.of(11, 8, 2_999))
        assertEquals(ExpansionOffer(ExpansionCheck.READY, 8, 3_000), ExpansionOffer.of(11, 8, 3_000))
        assertEquals(ExpansionCheck.AT_MAX_SIZE, ExpansionOffer.of(13, 99, 1_000_000).check)
        val shop = source("java/com/studioguu/miniaturegarden/ui/shop/ShopStageScreen.kt")
        val board = shop.substringAfter("private fun ExpansionBoard").substringBefore("\n}\n")
        assertTrue(board.contains("R.string.expand_needs_level, offer.level"))
        assertTrue(board.contains("R.string.expand_price, offer.price"))
        assertTrue(board.contains("enabled = offer.check == ExpansionCheck.READY"))
        assertFalse(board.contains("coins"))
        assertTrue(shop.contains("if (shown == ShopStage.WORKSHOP && expansion != null)"))
    }

    @Test
    fun `growing is asked about with its price, and pays it`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 3_500, playerLevel = 8, currentXp = LevelTable.xpForLevel(8))
            progress = GardenProgress(gardenSize = 11)
            put(GridObject(instanceId = "rock", gridX = 2, gridY = 3, objectId = "deco_rock", lastUpdatedTimestamp = t0))
        }
        assertEquals(ActionResult.SUCCESS, ExpandGardenUseCase(repo)())
        assertEquals(13, repo.progress?.gardenSize)
        assertEquals(500L, repo.profile?.coins)
        assertEquals(3 to 4, repo.objects.getValue("rock").let { it.gridX to it.gridY })
        // As large as it gets for now: nothing more is taken.
        assertEquals(ActionResult.GARDEN_AT_MAX_SIZE, ExpandGardenUseCase(repo)())
        assertEquals(500L, repo.profile?.coins)
        assertTrue(source("java/com/studioguu/miniaturegarden/ui/shop/ShopStageScreen.kt").contains("price = expansion.price,"))
    }

    @Test
    fun `short of coins or of level, nothing grows and nothing is taken`() = runBlocking {
        val poor = FakeGardenRepository().apply {
            profile = UserProfile(coins = 2_999, playerLevel = 8, currentXp = LevelTable.xpForLevel(8))
            progress = GardenProgress(gardenSize = 11)
        }
        assertEquals(ActionResult.NOT_ENOUGH_COINS, ExpandGardenUseCase(poor)())
        assertEquals(11, poor.progress?.gardenSize)
        assertEquals(2_999L, poor.profile?.coins)
        val young = FakeGardenRepository().apply {
            profile = UserProfile(coins = 9_999, playerLevel = 7, currentXp = LevelTable.xpForLevel(7))
            progress = GardenProgress(gardenSize = 11)
        }
        assertEquals(ActionResult.EXPANSION_LOCKED, ExpandGardenUseCase(young)())
        assertEquals(9_999L, young.profile?.coins)
    }

    @Test
    fun `the cottage no longer has anything to do with growing`() {
        assertFalse(source("java/com/studioguu/miniaturegarden/ui/hud/ObjectStatusDialog.kt").contains("expand"))
        assertFalse(source("java/com/studioguu/miniaturegarden/ui/components/GardenCanvas.kt").contains("growable"))
        assertFalse(source("java/com/studioguu/miniaturegarden/ui/render/TapTargets.kt").contains("growable"))
        assertTrue(StructureData.isSymbol(StructureData.COTTAGE.objectId))
    }
}
