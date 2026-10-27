package com.example.mockgpslab.hooking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy

class LoggingInvocationHandlerTest {
    private val log = CallLog()
    private val real = RealCalculator()

    private fun proxy(
        interceptors: Map<String, Interceptor> = emptyMap(),
        nanoTime: () -> Long = System::nanoTime,
    ): Calculator = Proxy.newProxyInstance(
        Calculator::class.java.classLoader,
        arrayOf(Calculator::class.java),
        LoggingInvocationHandler(real, log, interceptors, label = "Calculator", nanoTime = nanoTime),
    ) as Calculator

    @Test
    fun `pass through forwards the call and returns the real result`() {
        assertEquals(5, proxy().add(2, 3))
    }

    @Test
    fun `log records method args result and route`() {
        proxy().add(2, 3)
        val entry = log.entries.value.single()
        assertEquals("add", entry.method)
        assertEquals(listOf<Any?>(2, 3), entry.args)
        assertEquals(Outcome.Returned(5), entry.outcome)
        assertEquals(Route.PASS_THROUGH, entry.route)
        assertFalse(entry.proceeded)
        assertEquals("Calculator", entry.target)
    }

    @Test
    fun `log records elapsed time from the injected clock`() {
        val ticks = longArrayOf(1_000, 4_500).iterator()
        proxy(nanoTime = { ticks.next() }).add(1, 1)
        assertEquals(3_500, log.entries.value.single().elapsedNanos)
    }

    @Test
    fun `sequence numbers increase in call order`() {
        val p = proxy()
        p.add(1, 1)
        p.name()
        p.isReady()
        assertEquals(listOf("add", "name", "isReady"), log.entries.value.map { it.method })
        assertEquals(listOf(1L, 2L, 3L), log.entries.value.map { it.sequence })
    }

    @Test
    fun `no arg methods log an empty argument list`() {
        proxy().isReady()
        assertEquals(emptyList<Any?>(), log.entries.value.single().args)
    }

    @Test
    fun `void methods work and log a null result`() {
        proxy().ping()
        assertEquals(1, real.pings)
        assertEquals(Outcome.Returned(null), log.entries.value.single().outcome)
    }

    @Test
    fun `callers see the real exception not InvocationTargetException`() {
        val stub = StubLocationService().apply { failWith = IllegalStateException("provider exploded") }
        val p = Hooks.wrap(LocationService::class.java, stub, log)
        try {
            p.getLastKnownLocation("gps")
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("provider exploded", e.message)
        }
    }

    @Test
    fun `thrown exceptions are logged too`() {
        val boom = IllegalArgumentException("nope")
        val p = Hooks.wrap(LocationService::class.java, StubLocationService().apply { failWith = boom }, log)
        runCatching { p.getLastKnownLocation("gps") }
        val outcome = log.entries.value.single().outcome
        assertTrue(outcome is Outcome.Threw)
        assertSame(boom, (outcome as Outcome.Threw).error)
    }

    @Test
    fun `an interceptor can throw and the caller sees it`() {
        val p = proxy(mapOf("add" to { _, _ -> throw UnsupportedOperationException("blocked") }))
        try {
            p.add(1, 2)
            fail("expected exception")
        } catch (e: UnsupportedOperationException) {
            assertEquals("blocked", e.message)
        }
    }

    @Test
    fun `equals is identity based and is not forwarded or logged`() {
        val a = proxy()
        val b = proxy()
        assertTrue(a == a)
        assertFalse(a == b)
        assertFalse(a.equals(null))
        assertFalse(a.equals(real))
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun `hashCode is stable and consistent with equals`() {
        val a = proxy()
        assertEquals(a.hashCode(), a.hashCode())
        assertEquals(System.identityHashCode(a), a.hashCode())
        assertNotEquals(proxy().hashCode(), a.hashCode()) // distinct objects, distinct identity hashes (practically always)
    }

    @Test
    fun `proxies work as set members and map keys`() {
        val a = proxy()
        val set = hashSetOf(a)
        assertTrue(a in set)
        assertEquals(1, set.size)
    }

    @Test
    fun `toString names the hook rather than printing proxy internals`() {
        assertEquals("Hooked(Calculator)", proxy().toString())
        assertTrue(log.entries.value.isEmpty())
    }

    @Test
    fun `null for a primitive return type fails with a message naming the method`() {
        val p = proxy(mapOf("isReady" to { _, _ -> null }))
        val e = runCatching { p.isReady() }.exceptionOrNull()
        assertTrue("was $e", e is IllegalStateException)
        assertTrue(e!!.message!!.contains("Calculator.isReady"))
        assertTrue(e.message!!.contains("primitive 'boolean'"))
    }

    @Test
    fun `the primitive null failure is also logged`() {
        val p = proxy(mapOf("add" to { _, _ -> null }))
        runCatching { p.add(1, 2) }
        assertTrue(log.entries.value.single().outcome is Outcome.Threw)
    }

    @Test
    fun `wrong type from an interceptor gets a clear message instead of ClassCastException`() {
        val p = proxy(mapOf("add" to { _, _ -> "five" }))
        val e = runCatching { p.add(1, 2) }.exceptionOrNull()
        assertTrue("was $e", e is IllegalStateException)
        assertTrue(e!!.message!!.contains("java.lang.String"))
        assertTrue(e.message!!.contains("int"))
    }

    @Test
    fun `null is a fine answer for an object return type`() {
        val p = Hooks.wrap(LocationService::class.java, StubLocationService(), log) {
            on("getLastKnownLocation") { _, _ -> null }
        }
        assertEquals(null, p.getLastKnownLocation("gps"))
    }

    @Test
    fun `interceptor value for a void method is ignored`() {
        val p = proxy(mapOf("ping" to { _, _ -> "ignored" }))
        p.ping() // must not throw
        assertEquals(0, real.pings)
    }

    @Test
    fun `log is bounded and keeps the newest entries`() {
        val small = CallLog(capacity = 3)
        val p = Proxy.newProxyInstance(
            Calculator::class.java.classLoader, arrayOf(Calculator::class.java), LoggingInvocationHandler(real, small),
        ) as Calculator
        repeat(5) { p.add(it, 0) }
        assertEquals(listOf(2L, 3L, 4L), small.entries.value.map { (it.args[0] as Int).toLong() })
    }

    @Test
    fun `clear empties the log but sequence keeps counting`() {
        val p = proxy()
        p.name()
        log.clear()
        p.name()
        assertEquals(listOf(2L), log.entries.value.map { it.sequence })
    }
}
