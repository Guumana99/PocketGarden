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
        // Only a debug build's showcase (docs/promo) hands it a view.
        val screen = source("ui/GardenScreen.kt")
        assertEquals(listOf("savedView = showcase?.view"), Regex("savedView[^,\n]*").findAll(screen).map { it.value }.toList())
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

    // --- What floats up ---

    @Test
    fun `harvests merge instead of piling up in the same spot`() {
        // #59: a run of harvests adds up on one plaque, which starts afresh with the newest id.
        val harvests = listOf<GardenEffect>(GardenEffect.Harvest(1, coins = 10))
            .plusEffect(GardenEffect.Harvest(2, coins = 15))
            .plusEffect(GardenEffect.Harvest(3, coins = 5))
        assertEquals(listOf<GardenEffect>(GardenEffect.Harvest(3, coins = 30)), harvests)
        // Different kinds keep their own places, one above the other.
        val built = GardenEffect.WorkDone(6, "struct_hut", WorkKind.BUILDING)
        val mixed = harvests.plusEffect(built).plusEffect(GardenEffect.Harvest(7, coins = 1))
        assertEquals(listOf(built, GardenEffect.Harvest(7, coins = 31)), mixed)
    }

    @Test
    fun `a page made ready floats nothing up, the red dot on the book says it`() {
        val effects = source("ui/effects/FloatingEffects.kt")
        assertFalse(effects.contains("effect_discovery"))
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
            "ui/hud/ExpandConfirmDialog.kt",
            "ui/notifications/NotificationUi.kt",
            "ui/onboarding/FirstSteps.kt",
            "ui/post/PostDialog.kt",
            "ui/hud/LevelUpDialog.kt",
        )) {
            val text = source(path)
            assertFalse(path, text.contains("TextButton("))
            assertFalse(path, text.contains("OutlinedButton("))
            // Every dialog's buttons are laid out by the one piece, on its own bare terms.
            assertFalse(path, text.contains("AlertDialog("))
            assertTrue(path, text.contains("DialogAction("))
        }
        assertTrue(source("ui/hud/ObjectStatusDialog.kt").contains("WoodButton(text = stringResource(R.string.action_close), onClick = onDismiss)"))
        val cozy = source("ui/components/Cozy.kt")
        assertTrue(cozy.contains("for (action in actions) DialogButton(action, Modifier.weight(1f))"))
    }
}
