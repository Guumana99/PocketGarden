package com.studioguu.miniaturegarden.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens this app's page in the Play Store app, for the player to write a review; with no store
 * app, the same page in the browser. A device with neither simply does nothing.
 */
fun openStorePage(context: Context) {
    val id = context.packageName
    for (uri in storePageUris(id)) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next way in.
        }
    }
}

/** The store app's link first, then the web page. */
internal fun storePageUris(packageName: String): List<String> = listOf(
    "market://details?id=$packageName",
    "https://play.google.com/store/apps/details?id=$packageName",
)
