package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.BagExpansion
import com.studioguu.miniaturegarden.domain.model.BagOffer
import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.StandardPlayer
import com.studioguu.miniaturegarden.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FB1125 4: 99 to a slot, and the bag bought larger in the workshop: 16 -> 24 -> ... -> 56, five times at most. */
class BagExpansionTest {

    @Test
    fun `a slot holds up to 99 and a new bag has sixteen slots`() {
        assertEquals(99, InventorySlot.MAX_STACK)
        assertEquals(16, UserProfile().inventoryCapacity)
        assertEquals(56, BagExpansion.MAX_CAPACITY)
    }

    @Test
    fun `each purchase adds eight slots, costs more than the last, and there are five`() {
        assertEquals(5, BagExpansion.PURCHASES)
        assertEquals(BagExpansion.PRICES.sorted(), BagExpansion.PRICES)
        assertEquals(BagExpansion.PRICES.distinct(), BagExpansion.PRICES)
        var capacity = UserProfile.DEFAULT_INVENTORY_CAPACITY
        val seen = mutableListOf<Int>()
        while (BagExpansion.nextPrice(capacity) != null) {
            assertEquals(BagExpansion.PURCHASES - seen.size, BagExpansion.remaining(capacity))
            seen += capacity
            capacity += BagExpansion.STEP
        }
        assertEquals(listOf(16, 24, 32, 40, 48), seen)
        assertEquals(56, capacity)
        assertEquals(0, BagExpansion.remaining(56))
        // However many purchases, the total stays below what the garden costs to grow.
        assertTrue(BagExpansion.PRICES.sum() < GardenExpansion.STAGES.sumOf { it.price })
    }

    @Test
    fun `it opens at its level, then asks for the coins, and sold out says so`() {
        assertEquals(ExpansionCheck.NEEDS_LEVEL, BagExpansion.check(16, BagExpansion.UNLOCK_LEVEL - 1, 1_000_000))
        assertEquals(ExpansionCheck.NEEDS_COINS, BagExpansion.check(16, BagExpansion.UNLOCK_LEVEL, BagExpansion.PRICES[0] - 1L))
        assertEquals(ExpansionCheck.READY, BagExpansion.check(16, BagExpansion.UNLOCK_LEVEL, BagExpansion.PRICES[0].toLong()))
        assertEquals(ExpansionCheck.AT_MAX_SIZE, BagExpansion.check(56, 50, 1_000_000))
        assertEquals(BagOffer(ExpansionCheck.READY, price = BagExpansion.PRICES[1], remaining = 4), BagOffer.of(24, 20, 99_999))
    }

    @Test
    fun `buying takes the price, adds eight empty slots and keeps what the bag holds`() = runBlocking {
        val repo = FakeGardenRepository().apply {
            profile = UserProfile(coins = 10_000, playerLevel = 10)
            (0 until 16).forEach { putSlot(InventorySlot(it)) }
            putSlot(InventorySlot(3, "seed_tulip", 70))
        }
        assertEquals(ActionResult.SUCCESS, ExpandBagUseCase(repo)())
        assertEquals(24, repo.profile?.inventoryCapacity)
        assertEquals(10_000L - BagExpansion.PRICES[0], repo.profile?.coins)
        assertEquals((0 until 24).toList(), repo.slots.keys.toList())
        assertEquals(70, repo.slots.getValue(3).amount)
        // The second costs the second price.
        assertEquals(ActionResult.SUCCESS, ExpandBagUseCase(repo)())
        assertEquals(32, repo.profile?.inventoryCapacity)
        assertEquals(10_000L - BagExpansion.PRICES[0] - BagExpansion.PRICES[1], repo.profile?.coins)
    }

    @Test
    fun `five purchases fill it, and then nothing more is sold or taken`() = runBlocking {
        val repo = FakeGardenRepository().apply { profile = UserProfile(coins = 1_000_000, playerLevel = 20) }
        repeat(5) { assertEquals(ActionResult.SUCCESS, ExpandBagUseCase(repo)()) }
        assertEquals(56, repo.profile?.inventoryCapacity)
        val coins = repo.profile?.coins
        assertEquals(ActionResult.BAG_AT_MAX, ExpandBagUseCase(repo)())
        assertEquals(56, repo.profile?.inventoryCapacity)
        assertEquals(coins, repo.profile?.coins)
    }

    @Test
    fun `short of coins or of level, nothing is bought and nothing is taken`() = runBlocking {
        val poor = FakeGardenRepository().apply { profile = UserProfile(coins = 1_199, playerLevel = 10) }
        assertEquals(ActionResult.NOT_ENOUGH_COINS, ExpandBagUseCase(poor)())
        assertEquals(16, poor.profile?.inventoryCapacity)
        assertEquals(1_199L, poor.profile?.coins)
        val young = FakeGardenRepository().apply { profile = UserProfile(coins = 99_999, playerLevel = 5) }
        assertEquals(ActionResult.BAG_LOCKED, ExpandBagUseCase(young)())
        assertEquals(99_999L, young.profile?.coins)
    }

    @Test
    fun `each purchase is some days of the standard player coins at the level it is meant for`() {
        // Levels at which the five are meant to be bought (BagExpansion KDoc); two to three days of coins, no ads.
        val pace = StandardPlayer.play(20, ads = false, plan = StandardPlayer.BedPlan.GROWING)
        val levels = listOf(6, 9, 11, 14, 17)
        for ((i, level) in levels.withIndex()) {
            val days = BagExpansion.PRICES[i] / pace.coinsPerDayAt(level)
            println("purchase ${i + 1}: ${BagExpansion.PRICES[i]} coins at Lv.$level = %.1f days".format(days))
            assertTrue("purchase ${i + 1}: %.1f days".format(days), days in 1.5..3.5)
        }
    }
}
