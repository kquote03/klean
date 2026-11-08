package app.klean.core.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.klean.shared.id.UuidV7
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores entry photos under `filesDir/photos/`. Imports are downsized to a 1600 px long edge,
 * rotated per EXIF and re-encoded as JPEG 85. Re-encoding drops all EXIF (location, device), on purpose.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val clock: Clock,
) {
    val photosDir: File get() = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun file(name: String): File = File(photosDir, name)

    /** Imports an image from any readable [uri] and returns the stored file name. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        fun open() = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE) }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException("Decode failed: $uri")
        val rotation = runCatching { open().use { ExifInterface(it).rotationDegrees } }.getOrDefault(0)

        val (w, h) = fit(decoded.width, decoded.height, MAX_EDGE)
        val matrix = Matrix().apply {
            postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val name = newName()
        val target = file(name)
        val tmp = File(photosDir, "$name.tmp")
        tmp.outputStream().use { if (!out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)) throw IOException("Encode failed") }
        if (!tmp.renameTo(target)) throw IOException("Rename failed")
        if (out !== decoded) out.recycle()
        decoded.recycle()
        name
    }

    /**
     * A FileProvider URI the camera app can write to, and the file name it maps to.
     * The camera writes full size; call [import] on it afterwards to normalise, then [delete] the original.
     */
    fun newCaptureUri(): Pair<String, Uri> {
        val name = newName()
        return name to FileProvider.getUriForFile(ctx, "${ctx.packageName}.photos", file(name))
    }

    fun delete(name: String): Boolean = file(name).delete()

    private fun newName() = UuidV7.generate(clock.now().toEpochMilliseconds()) + ".jpg"

    companion object {
        const val DIR = "photos"
        const val MAX_EDGE = 1600
        const val QUALITY = 85

        /** Largest power of two that keeps the decoded long edge at or above [maxEdge]. */
        fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
            return sample
        }

        /** Size that fits within [maxEdge] on the long edge, keeping aspect ratio. Never upscales. */
        fun fit(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
            val long = maxOf(width, height)
            if (long <= maxEdge) return width to height
            val scale = maxEdge.toDouble() / long
            return Math.round(width * scale).toInt().coerceAtLeast(1) to Math.round(height * scale).toInt().coerceAtLeast(1)
        }
    }
}
