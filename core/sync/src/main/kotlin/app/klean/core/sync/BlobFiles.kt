package app.klean.core.sync

import java.io.File
import java.security.MessageDigest

/** Blob files on this phone: `<root>/<dir>/<name>`. [root] is `filesDir` in the app, a temp dir in tests. */
class BlobFiles(private val root: File) {
    /** The file for a column value, or null when the value isn't a plain file name (never escapes [root]). */
    fun file(dir: BlobDir, name: String): File? {
        if (!isSafeName(name)) return null
        return File(File(root, dir.path), name)
    }

    /** Writes [bytes] atomically, after checking they hash to [sha256]. */
    fun write(target: File, bytes: ByteArray, sha256: String): Boolean {
        if (sha256(bytes) != sha256) return false
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".sync.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return false
        }
        return true
    }

    companion object {
        fun isSafeName(name: String) = name.isNotBlank() && name != "." && name != ".." &&
            '/' !in name && '\\' !in name && '\u0000' !in name && name.length <= 200

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun contentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "json", "geojson" -> "application/json"
            "gpx" -> "application/gpx+xml"
            else -> "application/octet-stream"
        }
    }
}
