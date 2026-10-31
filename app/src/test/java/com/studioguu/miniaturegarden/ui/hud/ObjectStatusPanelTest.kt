package com.studioguu.miniaturegarden.ui.hud

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.PlantSpecies
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The selected thing's panel offers moving and taking off only where edit mode allows it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ObjectStatusPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show(objectId: String, editing: Boolean) {
        val obj = GridObject(instanceId = "x", gridX = 1, gridY = 1, objectId = objectId, lastUpdatedTimestamp = 0L)
        compose.setContent {
            GardenTheme {
                ObjectStatusDialog(
                    gridObject = obj, onWater = {}, onHarvest = {}, onRemove = {}, onRevive = {}, onMove = {}, onDismiss = {},
                    editing = editing,
                )
            }
        }
    }

    @Test
    fun `outside edit mode a fence can be neither moved nor taken off, and the panel says where`() {
        show(GridObject.FENCE_OBJECT_ID, editing = false)
        compose.onNodeWithText(text(R.string.action_move)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_remove)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.hint_edit_to_arrange)).assertExists()
    }

    @Test
    fun `in edit mode the same fence can be moved and taken off`() {
        show(GridObject.FENCE_OBJECT_ID, editing = true)
        compose.onNodeWithText(text(R.string.action_move)).assertExists()
        compose.onNodeWithText(text(R.string.action_remove)).assertExists()
        compose.onNodeWithText(text(R.string.hint_edit_to_arrange)).assertDoesNotExist()
    }

    @Test
    fun `outside edit mode a crop can be neither moved nor cleared away`() {
        show(PlantSpecies.TULIP.objectId, editing = false)
        compose.onNodeWithText(text(R.string.action_move)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_pull)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.hint_edit_to_arrange)).assertDoesNotExist()
        // A growing crop is not nagged about clearing it away.
        compose.onNodeWithText(text(R.string.hint_edit_to_clear)).assertDoesNotExist()
    }

    @Test
    fun `an empty bed outside edit mode says where it is cleared away`() {
        show(GridObject.TILLED_SOIL_OBJECT_ID, editing = false)
        compose.onNodeWithText(text(R.string.action_restore_ground)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.hint_edit_to_clear)).assertExists()
    }

    @Test
    fun `and in edit mode it moves like anything else, with the pencil's own button`() {
        show(PlantSpecies.TULIP.objectId, editing = true)
        compose.onNodeWithText(text(R.string.action_move)).assertExists()
        // A crop is pulled up (device feedback, after #75).
        compose.onNodeWithText(text(R.string.action_pull)).assertExists()
    }
}
