package com.studioguu.miniaturegarden.ui.hud

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.TutorialTool
import com.studioguu.miniaturegarden.domain.usecase.CareForPlantUseCase
import com.studioguu.miniaturegarden.domain.usecase.GardenLayout
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The tool bar in edit mode, and putting a tool down with its own button (#50). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QuickActionBarTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun button(label: String) = compose.onNode(hasText(label) and hasClickAction(), useUnmergedTree = false)

    @Test
    fun `in edit mode only the shop can be tapped`() {
        compose.setContent {
            GardenTheme {
                QuickActionBar(
                    canCare = true, hoeActive = false, onToggleHoe = {}, onWater = {}, onFertilize = {},
                    onOpenInventory = {}, onOpenShop = {},
                    allows = { GardenLayout.toolAllowedInEditMode(it) },
                )
            }
        }
        compose.onNode(hasText(text(R.string.action_hoe)) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText(text(R.string.action_water)) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText(text(R.string.action_fertilize, CareForPlantUseCase.FERTILIZER_COST)) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText(text(R.string.action_inventory)) and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText(text(R.string.action_shop)) and hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `with something from the bag in hand, the bag's button is there to put it down`() {
        var taps = 0
        compose.setContent {
            GardenTheme {
                QuickActionBar(
                    canCare = false, hoeActive = false, onToggleHoe = {}, onWater = {}, onFertilize = {},
                    onOpenInventory = { taps++ }, onOpenShop = {},
                    bagActive = true,
                    allows = { it != TutorialTool.HOE },
                )
            }
        }
        val bag = compose.onNode(hasText(text(R.string.action_inventory)) and hasClickAction())
        bag.assertIsEnabled()
        bag.performClick()
        assertEquals(1, taps)
    }
}
