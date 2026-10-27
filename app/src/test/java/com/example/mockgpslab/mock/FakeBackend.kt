package com.example.mockgpslab.mock

/**
 * Records every call as a string ("add:gps", "enable:gps:true", "push:gps", ...) so tests can assert the exact
 * order, and can be told to throw on a specific call.
 */
class FakeBackend : MockProviderBackend {
    val calls = mutableListOf<String>()
    val pushed = mutableListOf<Pair<String, MockFix>>()

    /** Registered providers, so the fake enforces the same "already exists / unknown" rules as the real one. */
    val registered = linkedSetOf<String>()

    /** Return an exception to throw for a given call string, or null to let it succeed. */
    var failOn: (String) -> Throwable? = { null }

    private fun record(call: String) {
        calls += call
        failOn(call)?.let { throw it }
    }

    override fun addTestProvider(provider: String) {
        record("add:$provider")
        require(registered.add(provider)) { "Provider \"$provider\" already exists" }
    }

    override fun setTestProviderEnabled(provider: String, enabled: Boolean) = record("enable:$provider:$enabled")

    override fun setTestProviderLocation(provider: String, fix: MockFix) {
        record("push:$provider")
        pushed += provider to fix
    }

    override fun removeTestProvider(provider: String) {
        record("remove:$provider")
        require(registered.remove(provider)) { "Provider \"$provider\" unknown" }
    }
}

class FakeClock(var millis: Long = 1_000_000L, var nanos: Long = 5_000_000_000L) : MockClock {
    override fun currentTimeMillis() = millis
    override fun elapsedRealtimeNanos() = nanos
}
