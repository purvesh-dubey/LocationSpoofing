package com.example.mockgpslab.hooking

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Per-method override. [args] are the call's arguments; [proceed] runs the REAL method with the original
 * arguments and returns its result. Return a replacement value, or return/forward what proceed() gave you.
 */
typealias Interceptor = (args: List<Any?>, proceed: () -> Any?) -> Any?

/**
 * The heart of Phase 2. java.lang.reflect.Proxy builds an object that implements an interface and sends EVERY
 * method call to one [invoke] function. That single choke point is how a "virtual container" can intercept
 * calls to system services, without the app being modified.
 *
 * This handler forwards to [target], optionally lets an [Interceptor] override a method, and records each call.
 */
class LoggingInvocationHandler(
    private val target: Any,
    private val log: CallLog,
    private val interceptors: Map<String, Interceptor> = emptyMap(),
    private val label: String = target.javaClass.simpleName,
    private val nanoTime: () -> Long = System::nanoTime,
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        // Proxy routes equals/hashCode/toString here too. Answer them ourselves: they are about the PROXY,
        // not the target, and logging them would drown the interesting calls (collections call them constantly).
        objectMethodResult(proxy, method, args)?.let { return it.value }

        val argList = args?.toList().orEmpty()
        val interceptor = interceptors[method.name]
        var proceeded = false
        val started = nanoTime()

        fun record(outcome: Outcome) = log.record(
            CallRecord(
                sequence = log.nextSequence(),
                target = label,
                method = method.name,
                args = argList,
                outcome = outcome,
                elapsedNanos = nanoTime() - started,
                route = if (interceptor == null) Route.PASS_THROUGH else Route.INTERCEPTED,
                proceeded = proceeded,
            ),
        )

        try {
            val result = if (interceptor == null) {
                callTarget(method, argList)
            } else {
                val replacement = interceptor(argList) {
                    proceeded = true
                    callTarget(method, argList)
                }
                checkReturnValue(method, replacement)
            }
            record(Outcome.Returned(result))
            return result
        } catch (t: Throwable) {
            record(Outcome.Threw(t))
            throw t
        }
    }

    private fun callTarget(method: Method, args: List<Any?>): Any? {
        try {
            return method.invoke(target, *args.toTypedArray())
        } catch (e: InvocationTargetException) {
            // Method.invoke wraps whatever the real method threw. Callers of an interface must see the real
            // exception, exactly as if there were no proxy, so unwrap it.
            throw e.targetException ?: e
        }
    }

    /**
     * Without this check, a null for a primitive return type fails inside Proxy with a bare NullPointerException
     * that doesn't say which hook or method is responsible.
     */
    private fun checkReturnValue(method: Method, value: Any?): Any? {
        val type = method.returnType
        if (type == Void.TYPE) return null
        if (value == null) {
            check(!type.isPrimitive) {
                "Interceptor for ${label}.${method.name} returned null, but the method returns primitive " +
                    "'${type.name}', which cannot be null. Return a ${type.name} value instead."
            }
            return null
        }
        val boxed = if (type.isPrimitive) wrapperOf(type) else type
        check(boxed.isInstance(value)) {
            "Interceptor for ${label}.${method.name} returned ${value::class.java.name}, " +
                "but the method returns ${type.name}."
        }
        return value
    }

    private class Answer(val value: Any?)

    private fun objectMethodResult(proxy: Any, method: Method, args: Array<out Any?>?): Answer? {
        if (method.declaringClass != Any::class.java) return null
        return when (method.name) {
            // Identity semantics: a proxy is only equal to itself. Delegating to the target would make
            // proxy.equals(target) true while target.equals(proxy) is false, which breaks symmetry.
            "equals" -> Answer(proxy === args?.firstOrNull())
            "hashCode" -> Answer(System.identityHashCode(proxy))
            "toString" -> Answer("Hooked($label)")
            else -> null
        }
    }

    private fun wrapperOf(primitive: Class<*>): Class<*> = when (primitive) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> primitive
    }
}
