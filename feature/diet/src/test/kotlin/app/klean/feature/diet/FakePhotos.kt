package app.klean.feature.diet

import android.net.Uri
import app.klean.core.data.photo.PhotoStorage
import java.io.File

class FakePhotos : PhotoStorage {
    val imported = mutableListOf<Uri>()
    val deleted = mutableListOf<String>()
    override fun file(name: String) = File("/photos", name)
    override suspend fun import(uri: Uri): String {
        imported += uri
        return "imported-${imported.size}.jpg"
    }
    override fun newCaptureUri(): Pair<String, Uri> = "camera.jpg" to Uri.parse("content://test.photos/photos/camera.jpg")
    override fun delete(name: String): Boolean {
        deleted += name
        return true
    }
}
