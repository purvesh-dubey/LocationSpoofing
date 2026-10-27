package com.example.mockgpslab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatesTest {
    @Test
    fun `latitude and longitude accept normal numbers`() {
        assertEquals(25.5941, Coordinates.parseLatitude("25.5941").valueOrNull!!, 0.0)
        assertEquals(-33.8688, Coordinates.parseLatitude(" -33.8688 ").valueOrNull!!, 0.0)
        assertEquals(85.1376, Coordinates.parseLongitude("+85.1376").valueOrNull!!, 0.0)
        assertEquals(0.5, Coordinates.parseLongitude(".5").valueOrNull!!, 0.0)
        assertEquals(12.0, Coordinates.parseLatitude("12.").valueOrNull!!, 0.0)
        assertEquals(25.5, Coordinates.parseLatitude("25.5°").valueOrNull!!, 0.0)
    }

    @Test
    fun `bounds are inclusive`() {
        assertEquals(90.0, Coordinates.parseLatitude("90").valueOrNull!!, 0.0)
        assertEquals(-90.0, Coordinates.parseLatitude("-90").valueOrNull!!, 0.0)
        assertEquals(180.0, Coordinates.parseLongitude("180").valueOrNull!!, 0.0)
        assertEquals(-180.0, Coordinates.parseLongitude("-180").valueOrNull!!, 0.0)
    }

    @Test
    fun `out of range values are rejected with a helpful message`() {
        assertEquals("Latitude must be between -90 and 90", Coordinates.parseLatitude("90.0001").errorOrNull)
        assertEquals("Latitude must be between -90 and 90", Coordinates.parseLatitude("-91").errorOrNull)
        assertEquals("Longitude must be between -180 and 180", Coordinates.parseLongitude("180.5").errorOrNull)
        assertEquals("Longitude must be between -180 and 180", Coordinates.parseLongitude("999999999999").errorOrNull)
    }

    @Test
    fun `blank and non numeric input is rejected`() {
        assertEquals("Latitude is required", Coordinates.parseLatitude("  ").errorOrNull)
        for (bad in listOf("abc", "25.5.9", "NaN", "Infinity", "1e2", "0x10", "25,5941", "--5", "5-", "٢٥")) {
            assertTrue("'$bad' should be invalid", Coordinates.parseLatitude(bad) is Parsed.Invalid)
        }
    }

    @Test
    fun `pair accepts the usual separators and wrappers`() {
        val expected = GeoPoint(25.5941, 85.1376)
        for (text in listOf(
            "25.5941, 85.1376", "25.5941,85.1376", "25.5941 85.1376", "25.5941;85.1376",
            "  25.5941 ,  85.1376  ", "(25.5941, 85.1376)", "[25.5941, 85.1376]", "25.5941°, 85.1376°",
        )) {
            assertEquals("'$text'", expected, Coordinates.parsePair(text).valueOrNull)
        }
    }

    @Test
    fun `pair needs exactly two valid numbers`() {
        assertTrue(Coordinates.parsePair("25.5941") is Parsed.Invalid)
        assertTrue(Coordinates.parsePair("1, 2, 3") is Parsed.Invalid)
        assertTrue(Coordinates.parsePair("") is Parsed.Invalid)
        assertTrue(Coordinates.parsePair(", 85") is Parsed.Invalid)
        assertEquals("Latitude must be between -90 and 90", Coordinates.parsePair("95, 10").errorOrNull)
        assertEquals("Longitude must be between -180 and 180", Coordinates.parsePair("10, 190").errorOrNull)
    }

    @Test
    fun `negative coordinates in a pair`() {
        assertEquals(GeoPoint(-33.8688, 151.2093), Coordinates.parsePair("-33.8688, 151.2093").valueOrNull)
        assertEquals(GeoPoint(40.7128, -74.006), Coordinates.parsePair("40.7128 -74.0060").valueOrNull)
    }

    @Test
    fun `valueOrNull and errorOrNull are mutually exclusive`() {
        val ok = Coordinates.parseLatitude("1")
        val bad = Coordinates.parseLatitude("x")
        assertNull(ok.errorOrNull)
        assertNull(bad.valueOrNull)
    }
}
