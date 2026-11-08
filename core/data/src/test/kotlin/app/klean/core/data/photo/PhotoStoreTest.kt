package app.klean.core.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.FakeClock
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val store = PhotoStore(ctx, FakeClock(1_700_000_000_000L))

    private fun sourceJpeg(w: Int, h: Int, orientation: Int? = null): Uri {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        val f = File(ctx.cacheDir, "src-${w}x$h.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) ExifInterface(f).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return Uri.fromFile(f)
    }

    private fun bounds(f: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(f.path, it) }

    @Test fun largeImageIsDownsizedToLongEdge1600AsJpeg() = runBlocking {
        val name = store.import(sourceJpeg(4000, 3000))
        val out = store.file(name)
        assertTrue(name.endsWith(".jpg"))
        assertEquals(File(ctx.filesDir, "photos"), out.parentFile)
        val b = bounds(out)
        assertEquals("image/jpeg", b.outMimeType)
        assertEquals(1600, maxOf(b.outWidth, b.outHeight))
        assertEquals(1200, minOf(b.outWidth, b.outHeight))
        assertEquals(b.outWidth > b.outHeight, true)
    }

    @Test fun exifRotationIsAppliedAndStripped() = runBlocking {
        val name = store.import(sourceJpeg(400, 200, ExifInterface.ORIENTATION_ROTATE_90))
        val out = store.file(name)
        val b = bounds(out)
        assertEquals(200, b.outWidth)
        assertEquals(400, b.outHeight)
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface(out).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test fun targetSizeMath() {
        assertEquals(2, PhotoStore.sampleSizeFor(4000, 3000, 1600)) // decode at 2000x1500, then scale
        assertEquals(1, PhotoStore.sampleSizeFor(1000, 800, 1600))
        assertEquals(1600 to 1200, PhotoStore.fit(2000, 1500, 1600))
        assertEquals(800 to 600, PhotoStore.fit(800, 600, 1600))
    }
}
