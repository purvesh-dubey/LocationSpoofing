package com.example.mockgpslab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacesCodecTest {
    @Test
    fun `round trip preserves names and exact coordinates`() {
        val places = listOf(
            SavedPlace("Home", GeoPoint(25.5941, 85.1376)),
            SavedPlace("Café | the good one", GeoPoint(-33.8688, 151.2093)),
        )
        assertEquals(places, SavedPlacesCodec.decode(SavedPlacesCodec.encode(places)))
    }

    @Test
    fun `empty and null decode to an empty list`() {
        assertTrue(SavedPlacesCodec.decode(null).isEmpty())
        assertTrue(SavedPlacesCodec.decode("").isEmpty())
        assertEquals("", SavedPlacesCodec.encode(emptyList()))
    }

    @Test
    fun `malformed lines are skipped without losing the good ones`() {
        val raw = "garbage\n1.5|2.5|Good\n999|0|Out of range\n1|2|\n3|4"
        assertEquals(listOf(SavedPlace("Good", GeoPoint(1.5, 2.5))), SavedPlacesCodec.decode(raw))
    }

    @Test
    fun `newlines in a name cannot split one place into two`() {
        val encoded = SavedPlacesCodec.encode(listOf(SavedPlace("Line1\nLine2", GeoPoint(1.0, 2.0))))
        assertEquals(listOf(SavedPlace("Line1 Line2", GeoPoint(1.0, 2.0))), SavedPlacesCodec.decode(encoded))
    }

    @Test
    fun `built in presets include Patna at the specified coordinates`() {
        val patna = Presets.builtIn.first { it.name == "Patna" }
        assertEquals(GeoPoint(25.5941, 85.1376), patna.point)
    }
}
