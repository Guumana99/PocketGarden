package com.studioguu.miniaturegarden.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.studioguu.miniaturegarden.R

/** Opens the player's mail app on a message to us, with the version and the device filled in. */
fun openContactMail(context: Context, versionName: String) {
    val device = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ")
    val android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.contact_email)))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.contact_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.contact_body, versionName, device, android))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.contact_no_mail_app, Toast.LENGTH_SHORT).show()
    }
}
