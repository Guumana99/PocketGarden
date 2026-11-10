package com.studioguu.miniaturegarden.ui.hud

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #fb-1110 (12): the coins at the top lead to the shop's coin counter, and play no ad by themselves. */
class CoinTapTest {

    private val ui = File(listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }, "java/com/studioguu/miniaturegarden/ui")
    private fun source(path: String) = File(ui, path).readText()

    @Test
    fun `tapping the coins opens the shop at its counter, not during the first steps`() {
        val bar = source("hud/TopStatusBar.kt")
        assertTrue(bar.contains("StatusChip(coinsText, scaled, padding, onClick = onCoinTap)"))
        val screen = source("GardenScreen.kt")
        val tap = screen.substringAfter("onCoinTap = ").substringBefore("},\n            ) }")
        assertTrue(tap.contains("tutorialStep?.isGuiding == true) null"))
        assertTrue(tap.contains("shopAtCoins = true; showShop = true"))
        // Only the shop opens: no ad, no bonus claimed.
        assertFalse(tap.contains("claimBonus"))
        assertFalse(tap.contains("Bonus"))
        assertTrue(screen.contains("ShopStageScreen(onLeave = { showShop = false; shopAtCoins = false }, pointToCoins = shopAtCoins)"))
    }

    @Test
    fun `the counter only glows, the ad still waits for its button and the confirmation`() {
        val shop = source("shop/ShopStageScreen.kt")
        assertTrue(shop.contains("Modifier.padding(bottom = 8.dp).coinGlow(pointToCoins)"))
        val glow = shop.substringAfter("private fun Modifier.coinGlow(").substringBefore("private const val COIN_GLOW_PULSES")
        assertFalse(glow.contains("onBonus"))
        assertFalse(glow.contains("claimBonus"))
        assertFalse(shop.substringAfter("val offerBonus").substringBefore("\n").contains("pointToCoins"))
    }
}
