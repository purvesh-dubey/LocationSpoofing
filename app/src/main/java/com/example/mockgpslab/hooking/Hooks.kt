package com.example.mockgpslab.hooking

import java.lang.reflect.Proxy

/** Receiver of the `Hooks.wrap(...) { ... }` block. */
class HookBuilder internal constructor(private val iface: Class<*>) {
    internal val interceptors = linkedMapOf<String, Interceptor>()

    /**
     * Intercept every overload of the method called [method]. Unhooked methods pass straight through.
     * (By name only, to keep the lesson small; real containers also match on parameter types.)
     */
    fun on(method: String, interceptor: Interceptor) {
        val known = iface.methods.map { it.name }.toSet()
        require(method in known) {
            "${iface.simpleName} has no method named '$method'. Available: ${known.sorted().joinToString()}"
        }
        require(method !in interceptors) { "'$method' is already hooked; combine the logic in one on(\"$method\") block" }
        interceptors[method] = interceptor
    }
}

object Hooks {
    /**
     * Returns an object that implements [iface] and forwards to [target], applying the interceptors declared in
     * [configure]. The result is a drop-in replacement for [target] wherever the interface type is expected.
     *
     * Only interfaces can be proxied: the proxy class is generated at runtime and must "implement" something,
     * but a generated class can't also extend a concrete class (it already extends java.lang.reflect.Proxy).
     */
    fun <T : Any> wrap(
        iface: Class<T>,
        target: T,
        log: CallLog = CallLog(),
        configure: HookBuilder.() -> Unit = {},
    ): T {
        require(iface.isInterface) {
            "Proxy can only wrap interfaces, but ${iface.name} is a class. Extract an interface and hook that."
        }
        val builder = HookBuilder(iface).apply(configure)
        val handler = LoggingInvocationHandler(target, log, builder.interceptors, label = iface.simpleName)
        return checkNotNull(iface.cast(Proxy.newProxyInstance(iface.classLoader, arrayOf(iface), handler)))
    }

    /** True if [service] is a proxy created by [wrap]. */
    fun isHooked(service: Any): Boolean =
        Proxy.isProxyClass(service.javaClass) && Proxy.getInvocationHandler(service) is LoggingInvocationHandler
}
