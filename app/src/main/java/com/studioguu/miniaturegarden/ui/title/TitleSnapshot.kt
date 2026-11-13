package com.studioguu.miniaturegarden.ui.title

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A picture of the title's garden as it was last drawn (#fb-1113 10), kept in the app's files, so
 * that the next start shows the garden at once instead of the empty sky while the artwork is read:
 * the real garden then fades in over it. A first start has none, and shows the sky as before.
 *
 * It is read as the process starts ([prefetch], before the artwork), and written again each time
 * the title has shown its garden for a while ([keep]).
 */
object TitleSnapshot {
    const val FILE_NAME = "title_garden.webp"

    /** How good the kept picture is: it is only seen for a moment, under the real garden's fade. */
    const val QUALITY = 85

    private var read: CompletableDeferred<ImageBitmap?>? = null

    /** Starts reading the kept picture, if there is one, off the main thread. */
    fun prefetch(context: Context, scope: CoroutineScope) {
        if (read != null) return
        val done = CompletableDeferred<ImageBitmap?>()
        read = done
        val file = File(context.filesDir, FILE_NAME)
        scope.launch(Dispatchers.IO) {
            done.complete(runCatching { if (file.isFile) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }.getOrNull())
        }
    }

    /** The kept picture, once read; null when there is none (or it could not be read). */
    suspend fun await(context: Context): ImageBitmap? {
        if (read == null) prefetch(context, CoroutineScope(Dispatchers.IO))
        return read?.await()
    }

    /** Keeps [picture] for the next start, replacing the last one whole (never half written). */
    suspend fun keep(context: Context, picture: ImageBitmap) = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = picture.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
            val part = File(context.filesDir, "$FILE_NAME.part")
            part.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out) }
            part.renameTo(File(context.filesDir, FILE_NAME))
        }
    }
}
