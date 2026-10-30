package com.studioguu.miniaturegarden.ui.settings

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import com.studioguu.miniaturegarden.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The contact button opens the mail app on a message that says what the player is using. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContactMailTest {

    @Test
    fun `the mail is addressed and carries the version, the device and Android's version`() {
        val app = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        openContactMail(activity, "1.2.3")
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:", intent.dataString)
        assertArrayEquals(arrayOf(app.getString(R.string.contact_email)), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        val body = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(body, body.contains("1.2.3"))
        assertTrue(body, body.contains(Build.MODEL))
        assertTrue(body, body.contains(Build.VERSION.RELEASE))
    }
}
