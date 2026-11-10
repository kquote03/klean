package app.klean.shared.health

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS fix: degrees, UTC epoch millis, horizontal accuracy in metres (0 = unknown). */
data class GeoPoint(val lat: Double, val lon: Double, val timeMs: Long, val accuracyM: Float = 0f)

/**
 * Distance, pace and route encoding for GPS runs (M22). Pure, so a run's numbers are the same on the phone,
 * the web and in tests.
 */
object RouteMath {
    private const val EARTH_RADIUS_M = 6_371_000.0
    /** Fixes worse than this are ignored. */
    const val MAX_ACCURACY_M = 50f
    /** Movement under this between kept fixes is GPS jitter, not distance. */
    const val MIN_STEP_M = 2.0
    /** Faster than this (≈ 43 km/h) between fixes is a jump, not a run. */
    const val MAX_SPEED_MPS = 12.0

    fun haversineM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** The fixes that count: accurate ones, each a plausible step from the last kept one. */
    fun clean(points: List<GeoPoint>): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(points.size)
        for (p in points) {
            if (p.accuracyM > MAX_ACCURACY_M) continue
            val last = out.lastOrNull()
            if (last == null) { out += p; continue }
            val d = haversineM(last, p)
            if (d < MIN_STEP_M) continue
            val dt = (p.timeMs - last.timeMs) / 1000.0
            if (dt > 0 && d / dt > MAX_SPEED_MPS) continue
            out += p
        }
        return out
    }

    /** Total distance in metres over the [clean]ed track. */
    fun distanceM(points: List<GeoPoint>): Double = clean(points).zipWithNext { a, b -> haversineM(a, b) }.sum()

    /** Seconds per kilometre, or null under 50 m (too short to mean anything). */
    fun paceSecPerKm(distanceM: Double, durationMs: Long): Int? =
        if (distanceM < 50 || durationMs <= 0) null else (durationMs / 1000.0 / (distanceM / 1000.0)).roundToInt()

    /** Google's encoded polyline (precision 5). */
    fun encode(points: List<GeoPoint>): String = buildString {
        var pLat = 0L
        var pLon = 0L
        for (p in points) {
            val lat = (p.lat * 1e5).roundToLong()
            val lon = (p.lon * 1e5).roundToLong()
            appendSigned(lat - pLat)
            appendSigned(lon - pLon)
            pLat = lat; pLon = lon
        }
    }

    fun decode(text: String): List<GeoPoint> {
        val values = readSigned(text)
        var lat = 0L
        var lon = 0L
        return values.chunked(2).filter { it.size == 2 }.map { (a, b) ->
            lat += a; lon += b
            GeoPoint(lat / 1e5, lon / 1e5, 0)
        }
    }

    /**
     * The stored route blob: line 1 `klean-route/1`, line 2 the polyline, line 3 the start time and the
     * seconds between fixes as a polyline-style number list.
     */
    fun encodeRoute(points: List<GeoPoint>): String {
        val start = points.firstOrNull()?.timeMs ?: 0L
        val times = buildString {
            var prev = 0L
            points.forEach { p -> val s = (p.timeMs - start) / 1000; appendSigned(s - prev); prev = s }
        }
        return "klean-route/1\n${encode(points)}\n$start $times"
    }

    fun decodeRoute(blob: String): List<GeoPoint> {
        val lines = blob.lines()
        if (lines.size < 3 || lines[0] != "klean-route/1") return emptyList()
        val pts = decode(lines[1])
        val (startText, deltas) = lines[2].split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val start = startText.toLongOrNull() ?: 0L
        var acc = 0L
        val secs = readSigned(deltas).map { acc += it; acc }
        return pts.mapIndexed { i, p -> p.copy(timeMs = start + (secs.getOrElse(i) { 0L }) * 1000) }
    }

    private fun StringBuilder.appendSigned(v: Long) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        append((x.toInt() + 63).toChar())
    }

    private fun readSigned(text: String): List<Long> {
        val out = ArrayList<Long>()
        var i = 0
        while (i < text.length) {
            var shift = 0
            var result = 0L
            var b: Int
            do {
                b = text[i++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && i < text.length)
            out += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        return out
    }

    private fun rad(deg: Double) = deg * PI / 180.0
}
