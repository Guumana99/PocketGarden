package com.studioguu.miniaturegarden.ui.onboarding

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.Gift
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A gift box's popup: two framed buttons of one size, each doing what it says. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GiftDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `later and receive are both buttons, side by side and the same size`() {
        var received = 0
        var later = 0
        compose.setContent { GardenTheme { GiftDialog(Gift.FIRST, onReceive = { received++ }, onDismiss = { later++ }) } }

        val laterButton = compose.onNodeWithTag(GIFT_LATER_TAG, useUnmergedTree = false)
        val receiveButton = compose.onNodeWithTag(GIFT_RECEIVE_TAG, useUnmergedTree = false)
        laterButton.assertHasClickAction()
        receiveButton.assertHasClickAction()
        val a = laterButton.getBoundsInRoot()
        val b = receiveButton.getBoundsInRoot()
        assertEquals((a.right - a.left).value, (b.right - b.left).value, 1f)

        laterButton.performClick()
        receiveButton.performClick()
        assertEquals(1, later)
        assertEquals(1, received)
        // The words are the ones the strings give.
        compose.onNodeWithTag(GIFT_LATER_TAG, useUnmergedTree = true).assertExists()
        assertEquals("後で", text(R.string.gift_later))
        assertEquals("受け取る", text(R.string.gift_receive))
    }
}
