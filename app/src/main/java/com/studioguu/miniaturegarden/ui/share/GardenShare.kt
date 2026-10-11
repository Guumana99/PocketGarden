package com.studioguu.miniaturegarden.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.studioguu.miniaturegarden.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHARE_DIRECTORY = "shared"
private const val SHARE_FILE = "garden.png"

/**
 * Writes [image] to the app's cache and opens the system share sheet for it, so it can go
 * straight to X, LINE or any other app that accepts a picture.
 *
 * The file is handed over through a FileProvider with read permission for that one URI; no
 * storage permission is involved. Each share overwrites the previous picture.
 *
 * @return false if the picture could not be written or nothing can receive it.
 */
suspend fun shareGardenImage(context: Context, image: Bitmap): Boolean {
    val file = runCatching { withContext(Dispatchers.IO) { writePng(context, image) } }.getOrNull() ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
        // The clip data is what the share sheet previews, and what the permission flag applies to.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.share_title))
    return runCatching { context.startActivity(chooser) }.isSuccess
}

private fun writePng(context: Context, image: Bitmap): File {
    val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
    val file = File(directory, SHARE_FILE)
    // A picture captured from the screen may live in graphics memory, which cannot be encoded.
    val encodable = if (image.config == Bitmap.Config.HARDWARE) image.copy(Bitmap.Config.ARGB_8888, false) else image
    file.outputStream().use { check(encodable.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encoding failed" } }
    return file
}
