package com.example.mockgpslab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateFormTest {
    @Test
    fun `point is null until both fields are valid`() {
        val form = CoordinateForm()
        assertNull(form.point)
        assertNull(form.editLatitude("25.5941").point)
        assertEquals(GeoPoint(25.5941, 85.1376), form.editLatitude("25.5941").editLongitude("85.1376").point)
        assertNull(form.editLatitude("95").editLongitude("85").point)
    }

    @Test
    fun `pasting a pair into the latitude field fills both fields`() {
        val form = CoordinateForm().editLatitude("25.5941, 85.1376")
        assertEquals("25.594100", form.latText)
        assertEquals("85.137600", form.lonText)
        assertEquals(GeoPoint(25.5941, 85.1376), form.point)
    }

    @Test
    fun `pasting a pair into the longitude field fills both fields too`() {
        val form = CoordinateForm().editLongitude("(28.6139 77.2090)")
        assertEquals(GeoPoint(28.6139, 77.2090), form.point)
    }

    @Test
    fun `typing normally never triggers pair parsing`() {
        var form = CoordinateForm()
        for (typed in listOf("2", "25", "25.", "25.5", "25.5 ")) form = form.editLatitude(typed)
        assertEquals("25.5 ", form.latText) // untouched, trailing space and all
        assertEquals("", form.lonText)
    }

    @Test
    fun `an invalid pasted pair is kept as typed so the user sees the error`() {
        val form = CoordinateForm().editLatitude("95, 10")
        assertEquals("95, 10", form.latText)
        assertTrue(form.lat is Parsed.Invalid)
    }

    @Test
    fun `withPoint formats with a dot regardless of locale`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val form = CoordinateForm().withPoint(GeoPoint(25.5941, 85.1376))
            assertEquals("25.594100", form.latText)
            assertEquals(GeoPoint(25.5941, 85.1376), form.point)
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun `accuracy defaults to 5 and is validated`() {
        assertEquals(5f, CoordinateForm().accuracyMeters!!, 0f)
        assertEquals(12.5f, CoordinateForm().editAccuracy("12.5").accuracyMeters!!, 0f)
        for (bad in listOf("", "0", "-3", "abc", "10001", "NaN")) {
            assertNull("'$bad'", CoordinateForm().editAccuracy(bad).accuracyMeters)
        }
    }

    @Test
    fun `withAccuracy round trips`() {
        assertEquals("5", CoordinateForm().withAccuracy(5f).accuracyText)
        assertEquals("7.5", CoordinateForm().withAccuracy(7.5f).accuracyText)
        assertEquals(7.5f, CoordinateForm().withAccuracy(7.5f).accuracyMeters!!, 0f)
    }
}
