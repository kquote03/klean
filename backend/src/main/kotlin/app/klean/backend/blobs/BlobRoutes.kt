package app.klean.backend.blobs

import app.klean.backend.ApiException
import app.klean.backend.device
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.put

/** `PUT /blobs/{sha256}` (201 new, 200 already there, 400 hash mismatch), `GET`/`HEAD /blobs/{sha256}`. */
fun Route.blobRoutes(blobs: BlobStore) {
    put("/blobs/{sha}") {
        call.device
        val type = call.request.contentType().takeUnless { it == ContentType.Any }?.withoutParameters()?.toString()
        val created = blobs.put(call.parameters["sha"]!!, call.receiveStream(), type)
        call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK)
    }
    get("/blobs/{sha}") {
        call.device
        val blob = blobs.find(call.parameters["sha"]!!) ?: throw ApiException(HttpStatusCode.NotFound, "no such blob")
        call.response.header(HttpHeaders.ETag, "\"${blob.sha256}\"")
        call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
        val type = blob.contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: ContentType.Application.OctetStream
        call.respond(LocalFileContent(blob.file, type))
    }
    head("/blobs/{sha}") {
        call.device
        val blob = blobs.find(call.parameters["sha"]!!) ?: throw ApiException(HttpStatusCode.NotFound, "no such blob")
        call.response.header(HttpHeaders.ContentLength, blob.size.toString())
        call.respond(HttpStatusCode.OK)
    }
}
