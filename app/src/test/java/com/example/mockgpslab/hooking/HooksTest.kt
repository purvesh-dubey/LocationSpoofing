package com.example.mockgpslab.hooking

import com.example.mockgpslab.data.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HooksTest {
    private val log = CallLog()
    private val stub = StubLocationService(lastKnown = GeoPoint(1.0, 2.0), gpsEnabled = false)

    @Test
    fun `with no hooks everything passes through`() {
        val p = Hooks.wrap(LocationService::class.java, stub, log)
        assertEquals(GeoPoint(1.0, 2.0), p.getLastKnownLocation("gps"))
        assertFalse(p.isProviderEnabled("gps"))
        assertEquals(listOf(Route.PASS_THROUGH, Route.PASS_THROUGH), log.entries.value.map { it.route })
    }

    @Test
    fun `an interceptor replaces the result without touching the real service`() {
        val fake = GeoPoint(25.5941, 85.1376)
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("getLastKnownLocation") { _, _ -> fake }
        }
        assertEquals(fake, p.getLastKnownLocation("gps"))
        assertEquals("real service must not be asked", 0, stub.lastKnownCalls)
        val entry = log.entries.value.single()
        assertEquals(Route.INTERCEPTED, entry.route)
        assertFalse(entry.proceeded)
    }

    @Test
    fun `unhooked methods keep passing through next to hooked ones`() {
        stub.gpsEnabled = true
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("getLastKnownLocation") { _, _ -> GeoPoint(0.0, 0.0) }
        }
        assertTrue(p.isProviderEnabled("gps"))
        assertEquals(Route.PASS_THROUGH, log.entries.value.single().route)
    }

    @Test
    fun `proceed calls the original and returns its result`() {
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("getLastKnownLocation") { _, proceed -> proceed() }
        }
        assertEquals(GeoPoint(1.0, 2.0), p.getLastKnownLocation("gps"))
        assertEquals(1, stub.lastKnownCalls)
        assertTrue(log.entries.value.single().proceeded)
    }

    @Test
    fun `an interceptor can post process what proceed returned`() {
        stub.gpsEnabled = false
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("isProviderEnabled") { _, proceed -> !(proceed() as Boolean) }
        }
        assertTrue(p.isProviderEnabled("gps"))
    }

    @Test
    fun `proceed is optional and can be skipped conditionally`() {
        val fake = GeoPoint(9.0, 9.0)
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("getLastKnownLocation") { args, proceed -> if (args[0] == "gps") fake else proceed() }
        }
        assertEquals(fake, p.getLastKnownLocation("gps"))
        assertEquals(GeoPoint(1.0, 2.0), p.getLastKnownLocation("network"))
        assertEquals(1, stub.lastKnownCalls)
    }

    @Test
    fun `interceptors receive the call arguments`() {
        var seen: List<Any?>? = null
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("getLastKnownLocation") { args, proceed -> seen = args; proceed() }
        }
        p.getLastKnownLocation("network")
        assertEquals(listOf<Any?>("network"), seen)
    }

    @Test
    fun `proceed may be called more than once`() {
        val p = Hooks.wrap(LocationService::class.java, stub, log) {
            on("getLastKnownLocation") { _, proceed -> proceed(); proceed() }
        }
        p.getLastKnownLocation("gps")
        assertEquals(2, stub.lastKnownCalls)
    }

    @Test
    fun `hooking a method that does not exist is rejected with the available names`() {
        val e = runCatching {
            Hooks.wrap(LocationService::class.java, stub, log) { on("getLastKnownLocaton") { _, p -> p() } }
        }.exceptionOrNull()
        assertTrue("was $e", e is IllegalArgumentException)
        assertTrue(e!!.message!!.contains("getLastKnownLocation"))
    }

    @Test
    fun `hooking the same method twice is rejected`() {
        val e = runCatching {
            Hooks.wrap(LocationService::class.java, stub, log) {
                on("isProviderEnabled") { _, p -> p() }
                on("isProviderEnabled") { _, p -> p() }
            }
        }.exceptionOrNull()
        assertTrue("was $e", e is IllegalArgumentException)
    }

    @Test
    fun `wrapping a class instead of an interface explains why proxies need interfaces`() {
        val e = runCatching { Hooks.wrap(NotAnInterface::class.java, NotAnInterface(), log) }.exceptionOrNull()
        assertTrue("was $e", e is IllegalArgumentException)
        assertTrue(e!!.message!!.contains("only wrap interfaces"))
    }

    @Test
    fun `isHooked distinguishes proxies from plain objects`() {
        assertFalse(Hooks.isHooked(stub))
        assertTrue(Hooks.isHooked(Hooks.wrap(LocationService::class.java, stub, log)))
    }

    @Test
    fun `the proxy is usable wherever the interface is expected`() {
        fun useIt(s: LocationService) = s.isProviderEnabled("gps")
        assertFalse(useIt(Hooks.wrap(LocationService::class.java, stub, log)))
    }
}
