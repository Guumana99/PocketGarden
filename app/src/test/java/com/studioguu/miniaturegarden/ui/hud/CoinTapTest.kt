package com.studioguu.miniaturegarden.ui.hud

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #fb-1113 (11): the coins at the top of the garden no longer lead to the shop (#fb-1110 12 is
 * undone); the purse in the shop offers the coin bonus, through the bonus's own confirmation.
 */
class CoinTapTest {

    private val ui = File(listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }, "java/com/studioguu/miniaturegarden/ui")
    private fun source(path: String) = File(ui, path).readText()

    @Test
    fun `the coins at the top of the garden take no tap`() {
        val bar = source("hud/TopStatusBar.kt")
        assertTrue(bar.contains("StatusChip(coinsText, scaled, padding)"))
        assertFalse(bar.contains("onCoinTap"))
        val screen = source("GardenScreen.kt")
        assertFalse(screen.contains("onCoinTap"))
        assertFalse(screen.contains("shopAtCoins"))
        assertTrue(screen.contains("ShopStageScreen(onLeave = { showShop = false })"))
    }

    @Test
    fun `the purse in the shop asks first, and only then plays`() {
        val shop = source("shop/ShopStageScreen.kt")
        assertFalse(shop.contains("coinGlow"))
        val tap = shop.substringAfter("onCoinTap = { ").substringBefore('}')
        // The same offer as the counter's button: the confirmation, then the ad.
        assertTrue(tap.contains("offerBonus()"))
        assertFalse(tap.contains("claimBonus"))
        assertTrue(shop.contains("CoinPill(coins, onClick = if (guiding) null else onCoinTap)"))
    }
}
