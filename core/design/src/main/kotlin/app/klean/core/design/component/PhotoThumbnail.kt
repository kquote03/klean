package app.klean.core.design.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downsampled thumbnails with a tiny in-memory cache. No image library: rows only ever show small,
 * local JPEGs that [app.klean.core.data.photo.PhotoStore] already normalised.
 */
object Thumbnails {
    private const val CACHE_BYTES = 6 * 1024 * 1024

    // Lazy so the pure [sampleSize] stays testable on the plain JVM.
    private val cache by lazy {
        object : LruCache<String, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    }

    /** Largest power of two that keeps the short edge at or above [targetPx], so a centre crop stays sharp. */
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        val short = minOf(width, height)
        var sample = 1
        while (short / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** Decodes [path] for a [targetPx] square, from the cache when possible. Null if it isn't an image. Call off the main thread. */
    fun load(path: String, targetPx: Int): Bitmap? {
        val key = "$path@$targetPx"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx) }
        return BitmapFactory.decodeFile(path, opts)?.also { cache.put(key, it) }
    }
}

/** A rounded, centre-cropped photo thumbnail. Shows a neutral placeholder while decoding. */
@Composable
fun PhotoThumbnail(path: String, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, path, px) {
        value = withContext(Dispatchers.IO) { runCatching { Thumbnails.load(path, px) }.getOrNull()?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(8.dp)
    val m = modifier.size(size).clip(shape)
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = m)
    } else {
        Box(m.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
