package com.example.mockgpslab.hooking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

sealed interface Outcome {
    data class Returned(val value: Any?) : Outcome
    data class Threw(val error: Throwable) : Outcome
}

/** How a call was handled by the proxy. */
enum class Route {
    /** No interceptor registered for this method: forwarded to the real object untouched. */
    PASS_THROUGH,

    /** An interceptor ran and decided the result (it may or may not have called proceed()). */
    INTERCEPTED,
}

/**
 * One call that went through a proxy.
 *
 * @param proceeded true if an interceptor called proceed(), i.e. the real method ran too.
 */
data class CallRecord(
    val sequence: Long,
    val target: String,
    val method: String,
    val args: List<Any?>,
    val outcome: Outcome,
    val elapsedNanos: Long,
    val route: Route,
    val proceeded: Boolean,
) {
    /** One-line, human-readable form used by the Hooking Lab log. */
    fun format(): String {
        val argText = args.joinToString(", ") { if (it is String) "\"$it\"" else it.toString() }
        val result = when (val o = outcome) {
            is Outcome.Returned -> o.value.toString()
            is Outcome.Threw -> "threw ${o.error::class.java.simpleName}: ${o.error.message}"
        }
        val how = when {
            route == Route.PASS_THROUGH -> "pass-through"
            proceeded -> "intercepted, called original"
            else -> "intercepted, replaced"
        }
        return "#$sequence $method($argText) -> $result  [$how, ${"%.3f".format(java.util.Locale.US, elapsedNanos / 1_000_000.0)} ms]"
    }
}

/**
 * Observable, bounded log shared by any number of proxies. UI collects [entries]; tests read it directly.
 * Newest entries are last. Thread-safe: proxies may be called from any thread.
 */
class CallLog(private val capacity: Int = 200) {
    private val sequence = AtomicLong()
    private val _entries = MutableStateFlow<List<CallRecord>>(emptyList())
    val entries: StateFlow<List<CallRecord>> = _entries

    fun nextSequence(): Long = sequence.incrementAndGet()

    fun record(entry: CallRecord) {
        _entries.update { (it + entry).takeLast(capacity) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
