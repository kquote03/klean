package app.klean.core.google.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes a downloaded account photo off the main thread; null while loading, without a file, or if unreadable. */
@Composable
fun rememberAccountPhoto(file: File?): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, file?.path, file?.lastModified()) {
        value = file?.let { f -> withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
    }
    return photo
}
