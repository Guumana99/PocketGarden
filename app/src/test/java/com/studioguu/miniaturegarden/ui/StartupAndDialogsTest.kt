package com.studioguu.miniaturegarden.ui

import com.studioguu.miniaturegarden.domain.model.GardenMap
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.ui.effects.GardenEffect
import com.studioguu.miniaturegarden.ui.effects.plusEffect
import com.studioguu.miniaturegarden.ui.render.GardenCamera
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The garden opens at ×0.55 and only once it has been read; a building's first completion and
 * its new page of the book share one plaque; and the boards' buttons are all of one make.
 */
class StartupAndDialogsTest {

    private val main: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String) = File(main, "java/com/studioguu/miniaturegarden/$path").readText().replace("\r\n", "\n")

    // --- The default zoom ---

    @Test
    fun `a new garden and a continued one both open at x0_55`() {
        assertEquals(0.55f, GardenCamera.START_SCALE, 0f)
        // The view kept from the last visit is no longer handed to the garden, so continuing
        // opens at the default zoom too.
        val screen = source("ui/GardenScreen.kt")
        assertFalse(screen.contains("savedView"))
        assertFalse(screen.contains("onViewChanged"))
    }

    // --- No default garden at start ---

    @Test
    fun `the garden is ready only once all of it has been read`() {
        val saved = GardenProgress(gardenSize = 13)
        val map = GardenMap.allGrass(13)
        assertTrue(isGardenReady(emptyList(), saved, map, artLoaded = true))
        // Not before the objects, the progress, or the artwork have come.
        assertFalse(isGardenReady(null, saved, map, artLoaded = true))
        assertFalse(isGardenReady(emptyList(), null, map, artLoaded = true))
        assertFalse(isGardenReady(emptyList(), saved, map, artLoaded = false))
        // Nor while the lawn is still the default size and not the saved garden's.
        assertFalse(isGardenReady(emptyList(), saved, GardenMap.DEFAULT, artLoaded = true))
    }

    @Test
    fun `the garden and the title's sample are drawn only once ready`() {
        val screen = source("ui/GardenScreen.kt")
        assertTrue(screen.contains("if (gardenReady) GardenCanvas("))
        assertTrue(screen.contains("viewModel.gardenReady.collectAsStateWithLifecycle()"))
        assertTrue(source("ui/title/TitleScreen.kt").contains("if (artLoaded) {\n                    SampleGarden("))
    }

    // --- One plaque for a building and its page ---

    @Test
    fun `a new page that comes with a building joins its plaque, whichever comes first`() {
        val built = GardenEffect.WorkDone(1, "struct_hut", WorkKind.BUILDING)
        // The page after the building.
        val afterwards = listOf<GardenEffect>(built).plusEffect(GardenEffect.Discovery(2))
        assertEquals(listOf<GardenEffect>(built.copy(newPage = true)), afterwards)
        // The page before the building.
        val before = listOf<GardenEffect>(GardenEffect.Discovery(2)).plusEffect(built)
        assertEquals(listOf<GardenEffect>(built.copy(newPage = true)), before)
        // A page with no building about it floats up as before, and so do the sums of a harvest.
        val harvest = GardenEffect.Harvest(3, coins = 10)
        assertEquals(listOf(harvest, GardenEffect.Discovery(4)), listOf<GardenEffect>(harvest).plusEffect(GardenEffect.Discovery(4)))
        // Taking a building down is not a first completion.
        val demolished = GardenEffect.WorkDone(5, "struct_hut", WorkKind.DEMOLISHING)
        assertEquals(listOf(demolished, GardenEffect.Discovery(6)), listOf<GardenEffect>(demolished).plusEffect(GardenEffect.Discovery(6)))
    }

    @Test
    fun `effects of one kind merge instead of piling up in the same spot`() {
        // #59: a run of harvests adds up on one plaque, which starts afresh with the newest id.
        val harvests = listOf<GardenEffect>(GardenEffect.Harvest(1, coins = 10))
            .plusEffect(GardenEffect.Harvest(2, coins = 15))
            .plusEffect(GardenEffect.Harvest(3, coins = 5))
        assertEquals(listOf<GardenEffect>(GardenEffect.Harvest(3, coins = 30)), harvests)
        // Several pages ready at once are said once.
        val pages = listOf<GardenEffect>(GardenEffect.Discovery(4)).plusEffect(GardenEffect.Discovery(5))
        assertEquals(listOf<GardenEffect>(GardenEffect.Discovery(5)), pages)
        // Different kinds keep their own places, one above the other.
        val mixed = harvests.plusEffect(GardenEffect.Discovery(6)).plusEffect(GardenEffect.Harvest(7, coins = 1))
        assertEquals(listOf(GardenEffect.Discovery(6), GardenEffect.Harvest(7, coins = 31)), mixed)
    }

    @Test
    fun `the merged plaque says both, and stays as long as a building's`() {
        val effects = source("ui/effects/FloatingEffects.kt")
        assertTrue(effects.contains("if (effect.newPage) \"\\n\" + stringResource(R.string.effect_discovery) else \"\""))
        assertTrue(source("ui/GardenViewModel.kt").contains("_effects.update { it.plusEffect(create(nextEffectId++)) }"))
    }

    // --- The boards' buttons ---

    @Test
    fun `no board mixes a bare text button with a wooden one`() {
        for (path in listOf(
            "ui/title/TitleScreen.kt",
            "ui/components/RewardUi.kt",
            "ui/debug/DebugMenuDialog.kt",
            "ui/hud/RemoveConfirmDialog.kt",
            "ui/notifications/NotificationUi.kt",
        )) {
            val text = source(path)
            assertFalse(path, text.contains("TextButton("))
            assertFalse(path, text.contains("OutlinedButton("))
            assertTrue(path, text.contains("WoodButton("))
        }
        assertTrue(source("ui/hud/ObjectStatusDialog.kt").contains("WoodButton(text = stringResource(R.string.action_close), onClick = onDismiss)"))
        val notifications = source("ui/notifications/NotificationUi.kt")
        assertTrue(notifications.contains("WoodButton(text = stringResource(R.string.notification_invite_later), onClick = onLater)"))
    }
}
