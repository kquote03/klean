package app.klean.core.capture

import android.net.Uri
import app.klean.core.data.photo.PhotoStorage
import java.io.File

class FakePhotoStorage : PhotoStorage {
    val imported = mutableListOf<Uri>()
    val deleted = mutableListOf<String>()
    var captures = 0
    override fun file(name: String) = File("/photos", name)
    override suspend fun import(uri: Uri): String {
        imported += uri
        return "imported-${imported.size}.jpg"
    }
    override fun newCaptureUri(): Pair<String, Uri> {
        captures++
        val name = "camera-$captures.jpg"
        return name to Uri.parse("content://test.photos/photos/$name")
    }
    override fun delete(name: String): Boolean {
        deleted += name
        return true
    }
}
