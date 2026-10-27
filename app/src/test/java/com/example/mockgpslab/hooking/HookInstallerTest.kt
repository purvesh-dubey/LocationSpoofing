package com.example.mockgpslab.hooking

import com.example.mockgpslab.data.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallerTest {
    private val real = GeoPoint(1.0, 2.0)
    private val fake = GeoPoint(25.5941, 85.1376)
    private val log = CallLog()
    private val stub = StubLocationService(lastKnown = real)
    private val registry = ServiceRegistry().apply { put(ServiceRegistry.LOCATION, stub) }

    // ---- registry --------------------------------------------------------------------------------------------

    @Test
    fun `registry returns what was put and fails clearly for unknown or mistyped services`() {
        assertSame(stub, registry.get(ServiceRegistry.LOCATION))
        assertEquals(null, registry.get("nope"))
        assertTrue(runCatching { registry.get("nope", LocationService::class.java) }.exceptionOrNull() is IllegalStateException)
        registry.put("wrong", "not a service")
        assertTrue(runCatching { registry.get("wrong", LocationService::class.java) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `installing swaps the registry entry for a proxy`() {
        val installation = HookInstaller.installLocationHook(registry, { fake }, log)
        val now = registry.get(ServiceRegistry.LOCATION)
        assertNotSame(stub, now)
        assertTrue(Hooks.isHooked(now!!))
        assertSame(installation.proxy, now)
        assertSame(stub, installation.original)
    }

    @Test
    fun `a client created AFTER the swap sees the fake point`() {
        HookInstaller.installLocationHook(registry, { fake }, log)
        val client = DemoClientApp(registry)
        assertEquals(fake, client.whereAmI())
        assertEquals("the real service is never consulted for the location", 0, stub.lastKnownCalls)
    }

    @Test
    fun `fake point is read on every call so selection changes apply immediately`() {
        var selected = fake
        HookInstaller.installLocationHook(registry, { selected }, log)
        val client = DemoClientApp(registry)
        assertEquals(fake, client.whereAmI())
        selected = GeoPoint(28.6139, 77.2090)
        assertEquals(GeoPoint(28.6139, 77.2090), client.whereAmI())
    }

    @Test
    fun `provider enabled combines the real answer with the fake`() {
        stub.gpsEnabled = false
        HookInstaller.installLocationHook(registry, { fake }, log)
        val client = DemoClientApp(registry)
        assertTrue("fake gps should look enabled", client.isGpsEnabled())
        assertTrue(log.entries.value.single().proceeded)
    }

    @Test
    fun `hooked calls show up in the log`() {
        HookInstaller.installLocationHook(registry, { fake }, log)
        DemoClientApp(registry).whereAmI()
        val entry = log.entries.value.single()
        assertEquals("getLastKnownLocation", entry.method)
        assertEquals(Outcome.Returned(fake), entry.outcome)
        assertEquals(Route.INTERCEPTED, entry.route)
    }

    @Test
    fun `installing twice is rejected`() {
        HookInstaller.installLocationHook(registry, { fake }, log)
        val e = runCatching { HookInstaller.installLocationHook(registry, { fake }, log) }.exceptionOrNull()
        assertTrue("was $e", e is IllegalStateException)
    }

    // ---- THE KEY LESSON: cached references -----------------------------------------------------------------

    @Test
    fun `STALE CACHE - swapping the registry is not enough once the client has cached the original`() {
        val client = DemoClientApp(registry)
        assertEquals(real, client.whereAmI()) // first use: client looks up the service and caches it

        HookInstaller.installLocationHook(registry, { fake }, log) // registry now holds the proxy...

        // ...but the client never asks the registry again, so it still talks to the original.
        assertEquals("hook has no effect on the already-initialised client", real, client.whereAmI())
        assertEquals(2, stub.lastKnownCalls)
        assertTrue("and nothing was logged because the proxy was never called", log.entries.value.isEmpty())

        // A client created now DOES see the hook, which is why the bug is so easy to miss in simple tests.
        assertEquals(fake, DemoClientApp(registry).whereAmI())
    }

    @Test
    fun `REFLECTION FIX - replacing the private field makes the stale client use the proxy`() {
        val client = DemoClientApp(registry)
        assertEquals(real, client.whereAmI())

        val installation = HookInstaller.installLocationHook(registry, { fake }, log)
        assertEquals(real, client.whereAmI()) // still stale

        val patched = installation.patchCachedReferences(client)

        assertEquals("exactly one field held the original", 1, patched)
        assertEquals(fake, client.whereAmI())
        assertEquals(Route.INTERCEPTED, log.entries.value.last().route)
    }

    @Test
    fun `patching a client that has not cached anything yet changes nothing`() {
        val installation = HookInstaller.installLocationHook(registry, { fake }, log)
        val client = DemoClientApp(registry) // field still null
        assertEquals(0, installation.patchCachedReferences(client))
        assertEquals(fake, client.whereAmI()) // works anyway, it will look up the proxy lazily
    }

    @Test
    fun `patching does not touch unrelated fields`() {
        class Holder(val a: Any?, var b: Any?, val n: Int = 5)
        val installation = HookInstaller.installLocationHook(registry, { fake }, log)
        val h = Holder(a = "keep", b = stub)
        assertEquals(1, installation.patchCachedReferences(h))
        assertEquals("keep", h.a)
        assertSame(installation.proxy, h.b)
        assertEquals(5, h.n)
    }

    // ---- uninstall -------------------------------------------------------------------------------------------

    @Test
    fun `uninstall restores the registry and every patched field`() {
        val client = DemoClientApp(registry)
        client.whereAmI()
        val installation = HookInstaller.installLocationHook(registry, { fake }, log)
        installation.patchCachedReferences(client)
        assertEquals(fake, client.whereAmI())

        installation.uninstall()

        assertSame(stub, registry.get(ServiceRegistry.LOCATION))
        assertEquals(real, client.whereAmI())
        assertFalse(Hooks.isHooked(registry.get(ServiceRegistry.LOCATION)!!))
        // and it can be installed again afterwards
        HookInstaller.installLocationHook(registry, { fake }, log)
    }

    @Test
    fun `unpatch restores only the client fields and leaves the registry hooked`() {
        val client = DemoClientApp(registry)
        client.whereAmI()
        val installation = HookInstaller.installLocationHook(registry, { fake }, log)
        installation.patchCachedReferences(client)

        installation.unpatchCachedReferences()

        assertEquals(real, client.whereAmI())
        assertTrue(Hooks.isHooked(registry.get(ServiceRegistry.LOCATION)!!))
    }
}
