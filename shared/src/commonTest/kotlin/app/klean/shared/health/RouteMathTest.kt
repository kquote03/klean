package app.klean.shared.health

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteMathTest {
    private fun p(lat: Double, lon: Double, s: Int, acc: Float = 5f) = GeoPoint(lat, lon, s * 1000L, acc)

    @Test fun oneDegreeOfLatitudeIsAbout111Km() {
        val d = RouteMath.haversineM(GeoPoint(25.0, 55.0, 0), GeoPoint(26.0, 55.0, 0))
        assertTrue(abs(d - 111_195.0) < 50, "got $d")
    }

    /** A straight 1 km jog north along a meridian in Dubai, one fix every 5 s (0.0009° ≈ 100 m). */
    @Test fun straightTrackSumsItsLegs() {
        val track = (0..10).map { p(25.2 + it * 0.000899322, 55.27, it * 30) }
        val d = RouteMath.distanceM(track)
        assertTrue(abs(d - 1000.0) < 5, "got $d")
    }

    @Test fun inaccurateFixesAndStandingStillAreIgnored() {
        val track = listOf(
            p(25.2, 55.27, 0),
            p(25.21, 55.27, 5, acc = 200f), // 1.1 km jump from a poor fix: dropped
            p(25.2000001, 55.27, 10),        // GPS jitter under 2 m: not counted
            p(25.200899322, 55.27, 40),
        )
        val d = RouteMath.distanceM(track)
        assertTrue(abs(d - 100.0) < 2, "got $d")
    }

    @Test fun teleportFasterThanAPersonIsDropped() {
        val track = listOf(p(25.2, 55.27, 0), p(25.3, 55.27, 1), p(25.200899322, 55.27, 30))
        val d = RouteMath.distanceM(track)
        assertTrue(abs(d - 100.0) < 2, "got $d")
    }

    @Test fun paceIsSecondsPerKilometre() {
        assertEquals(330, RouteMath.paceSecPerKm(distanceM = 5000.0, durationMs = 27 * 60_000L + 30_000L))
        assertEquals(null, RouteMath.paceSecPerKm(distanceM = 5.0, durationMs = 60_000L))
    }

    @Test fun polylineRoundTripsAndMatchesGooglesExample() {
        // The example from Google's encoded polyline algorithm format page.
        val pts = listOf(GeoPoint(38.5, -120.2, 0), GeoPoint(40.7, -120.95, 0), GeoPoint(43.252, -126.453, 0))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", RouteMath.encode(pts))
        val back = RouteMath.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, back.size)
        assertEquals(43.252, back[2].lat, 1e-5)
        assertEquals(-126.453, back[2].lon, 1e-5)
    }

    @Test fun routeBlobKeepsTimesToo() {
        val track = (0..5).map { p(25.2 + it * 0.0001, 55.27 + it * 0.0002, it * 7) }
        val back = RouteMath.decodeRoute(RouteMath.encodeRoute(track))
        assertEquals(track.map { it.timeMs }, back.map { it.timeMs })
        back.zip(track).forEach { (a, b) -> assertEquals(b.lat, a.lat, 1e-5); assertEquals(b.lon, a.lon, 1e-5) }
    }
}
