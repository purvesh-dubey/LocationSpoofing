package com.example.mockgpslab.mock

import com.example.mockgpslab.data.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockLocationEngineTest {
    private val patna = GeoPoint(25.5941, 85.1376)
    private val backend = FakeBackend()
    private val clock = FakeClock()
    private val logs = mutableListOf<String>()

    private val gpsAndNetwork = MockProviders.forSdk(30)
    private val withFused = MockProviders.forSdk(31)

    private fun TestScope.engine(
        providers: List<MockProviderSpec> = gpsAndNetwork,
        onFix: (MockFix) -> Unit = {},
    ) = MockLocationEngine(backend, backgroundScope, providers, clock, log = { logs += it }, onFix = onFix)

    /** advanceTimeBy stops just BEFORE the target instant, so also run tasks scheduled exactly at it. */
    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `providers are sdk dependent and fused is optional`() {
        assertEquals(listOf("gps", "network"), MockProviders.forSdk(30).map { it.name })
        val modern = MockProviders.forSdk(31)
        assertEquals(listOf("gps", "network", "fused"), modern.map { it.name })
        assertTrue(modern.last().optional)
        assertTrue(modern.take(2).none { it.optional })
    }

    @Test
    fun `start clears leftovers then adds then enables each provider in order`() = runTest {
        val engine = engine()
        engine.start(patna)
        assertEquals(
            listOf(
                "remove:gps", "add:gps", "enable:gps:true",
                "remove:network", "add:network", "enable:network:true",
            ),
            backend.calls,
        )
        assertEquals(EngineState.Running(patna.latitude, patna.longitude), engine.state.value)
    }

    @Test
    fun `an existing leftover provider is removed instead of failing the start`() = runTest {
        backend.registered += "gps" // left behind by a previous process
        val engine = engine()
        engine.start(patna)
        assertTrue(engine.state.value is EngineState.Running)
        assertEquals(setOf("gps", "network"), backend.registered)
    }

    @Test
    fun `pushes immediately and then once per second to every provider`() = runTest {
        val engine = engine()
        engine.start(patna, accuracyMeters = 5f)

        runCurrent()
        assertEquals(2, backend.pushed.size) // t = 0s: gps + network

        advance(1_000)
        assertEquals(4, backend.pushed.size) // t = 1s

        advance(2_000)
        assertEquals(8, backend.pushed.size) // t = 2s and 3s
    }

    @Test
    fun `fix carries coordinates accuracy and both timestamps`() = runTest {
        clock.millis = 1_700_000_000_000
        clock.nanos = 42_000_000_000
        val engine = engine()
        engine.start(patna, accuracyMeters = 12f)
        runCurrent()

        val (provider, fix) = backend.pushed.first()
        assertEquals("gps", provider)
        assertEquals(25.5941, fix.latitude, 0.0)
        assertEquals(85.1376, fix.longitude, 0.0)
        assertEquals(12f, fix.accuracyMeters, 0f)
        assertEquals(0f, fix.speedMetersPerSecond, 0f)
        assertEquals(0f, fix.bearingDegrees, 0f)
        assertEquals(1_700_000_000_000, fix.timeMillis)
        assertEquals(42_000_000_000, fix.elapsedRealtimeNanos)
    }

    @Test
    fun `default accuracy is 5 meters`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()
        assertEquals(5f, backend.pushed.first().second.accuracyMeters, 0f)
    }

    @Test
    fun `timestamps are re-read on every tick`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()
        clock.millis += 1_000
        clock.nanos += 1_000_000_000
        advance(1_000)
        val times = backend.pushed.map { it.second.timeMillis }.distinct()
        assertEquals(2, times.size)
    }

    @Test
    fun `updateTarget changes the next push and the reported state`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()
        engine.updateTarget(GeoPoint(10.0, 20.0))
        assertEquals(EngineState.Running(10.0, 20.0), engine.state.value)
        advance(1_000)
        assertEquals(10.0, backend.pushed.last().second.latitude, 0.0)
        assertEquals(20.0, backend.pushed.last().second.longitude, 0.0)
    }

    @Test
    fun `setAccuracy applies to later pushes`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()
        engine.setAccuracy(30f)
        advance(1_000)
        assertEquals(30f, backend.pushed.last().second.accuracyMeters, 0f)
    }

    @Test
    fun `onFix listener is called once per tick`() = runTest {
        val seen = mutableListOf<MockFix>()
        val engine = engine(onFix = { seen += it })
        engine.start(patna)
        runCurrent()
        advance(2_000)
        assertEquals(3, seen.size)
    }

    @Test
    fun `stop disables then removes every provider newest first and stops pushing`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()
        backend.calls.clear()

        engine.stop()
        assertEquals(
            listOf("enable:network:false", "remove:network", "enable:gps:false", "remove:gps"),
            backend.calls,
        )
        assertEquals(EngineState.Idle, engine.state.value)
        assertTrue(backend.registered.isEmpty())

        val pushesAtStop = backend.pushed.size
        advance(5_000)
        assertEquals(pushesAtStop, backend.pushed.size)
    }

    @Test
    fun `stop without start touches nothing and is idempotent`() = runTest {
        val engine = engine()
        engine.stop()
        engine.stop()
        assertTrue(backend.calls.isEmpty())
        assertEquals(EngineState.Idle, engine.state.value)
    }

    @Test
    fun `starting twice tears down the first run before the second`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()
        engine.start(GeoPoint(1.0, 2.0))
        runCurrent()

        assertEquals(setOf("gps", "network"), backend.registered)
        // Only one loop is alive: one more second yields exactly one push per provider.
        val before = backend.pushed.size
        advance(1_000)
        assertEquals(before + 2, backend.pushed.size)
    }

    @Test
    fun `security exception on add becomes NotMockApp and registers nothing`() = runTest {
        backend.failOn = { if (it == "add:gps") SecurityException("not mock app") else null }
        val engine = engine()
        engine.start(patna)

        assertEquals(EngineState.NotMockApp, engine.state.value)
        assertTrue(backend.registered.isEmpty())
        advance(3_000)
        assertTrue("no pushes after NotMockApp", backend.pushed.isEmpty())
    }

    @Test
    fun `security exception on a later provider cleans up the earlier one`() = runTest {
        backend.failOn = { if (it == "add:network") SecurityException("denied") else null }
        val engine = engine()
        engine.start(patna)

        assertEquals(EngineState.NotMockApp, engine.state.value)
        assertTrue(backend.registered.isEmpty())
        assertTrue("enable:gps:false" in backend.calls)
        assertTrue("remove:gps" in backend.calls)
    }

    @Test
    fun `rejected fused provider is logged and the rest keep working`() = runTest {
        backend.failOn = { if (it == "add:fused") IllegalArgumentException("fused not allowed") else null }
        val engine = engine(providers = withFused)
        engine.start(patna)
        runCurrent()

        assertEquals(EngineState.Running(patna.latitude, patna.longitude), engine.state.value)
        assertEquals(setOf("gps", "network"), backend.registered)
        assertTrue(logs.any { "fused" in it })
        assertTrue(backend.pushed.none { it.first == "fused" })
        assertEquals(2, backend.pushed.size)
    }

    @Test
    fun `fused that fails while enabling is removed again`() = runTest {
        backend.failOn = { if (it == "enable:fused:true") IllegalStateException("boom") else null }
        val engine = engine(providers = withFused)
        engine.start(patna)

        assertTrue(engine.state.value is EngineState.Running)
        assertEquals(setOf("gps", "network"), backend.registered)
    }

    @Test
    fun `a failing required provider is fatal and cleans up`() = runTest {
        backend.failOn = { if (it == "add:network") IllegalArgumentException("bad provider") else null }
        val engine = engine()
        engine.start(patna)

        assertEquals(EngineState.Error("bad provider"), engine.state.value)
        assertTrue(backend.registered.isEmpty())
    }

    @Test
    fun `push failure mid run reports Error removes providers and stops the loop`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()

        backend.failOn = { if (it.startsWith("push:")) IllegalStateException("system died") else null }
        advance(1_000)

        assertEquals(EngineState.Error("system died"), engine.state.value)
        assertTrue(backend.registered.isEmpty())

        backend.failOn = { null }
        val pushes = backend.pushed.size
        advance(5_000)
        assertEquals(pushes, backend.pushed.size)
    }

    @Test
    fun `losing the mock app role mid run becomes NotMockApp`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()

        backend.failOn = { if (it.startsWith("push:")) SecurityException("deselected") else null }
        advance(1_000)

        assertEquals(EngineState.NotMockApp, engine.state.value)
        assertTrue(backend.registered.isEmpty())
    }

    @Test
    fun `cleanup keeps going when one removal throws`() = runTest {
        val engine = engine()
        engine.start(patna)
        runCurrent()

        backend.failOn = { if (it == "remove:network") IllegalStateException("flaky") else null }
        engine.stop()

        assertEquals(EngineState.Idle, engine.state.value)
        assertTrue("gps must still be removed", "gps" !in backend.registered)
        assertTrue(logs.any { "network" in it })
    }

    @Test
    fun `a throwing onFix listener does not stop the engine`() = runTest {
        val engine = engine(onFix = { error("listener bug") })
        engine.start(patna)
        runCurrent()
        advance(1_000)
        assertTrue(engine.state.value is EngineState.Running)
        assertEquals(4, backend.pushed.size)
    }
}
