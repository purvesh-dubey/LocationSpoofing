package com.example.mockgpslab.hooking

import com.example.mockgpslab.data.GeoPoint
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * What [HookInstaller.installLocationHook] did, so it can be inspected and undone.
 *
 * Installing a hook is TWO separate problems:
 *  1. future lookups: put the proxy where new clients will find it (the registry swap, done by the installer);
 *  2. clients that already looked the service up: they hold the ORIGINAL in a private field, and no registry
 *     change can reach them (see [patchCachedReferences]).
 */
class HookInstallation internal constructor(
    private val registry: ServiceRegistry,
    private val name: String,
    val original: Any,
    val proxy: Any,
) {
    private class Patch(val client: Any, val field: Field)

    private val patches = mutableListOf<Patch>()

    /**
     * Finds every instance field of [client] that still holds the original service and points it at the proxy.
     *
     * This uses reflection on OUR OWN demo class. A container does the same trick on framework classes, which is
     * where hidden-API restrictions come in (Phase 3); here there is no such barrier.
     *
     * @return how many fields were replaced (0 means the client hadn't cached the service yet).
     */
    fun patchCachedReferences(client: Any): Int {
        var replaced = 0
        generateSequence<Class<*>>(client.javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true // getDeclaredField reaches private fields; this lets us read and write them
                if (field.get(client) === original) {
                    field.set(client, proxy)
                    patches += Patch(client, field)
                    replaced++
                }
            }
        return replaced
    }

    /** Puts the original back into every field [patchCachedReferences] changed, leaving the registry alone. */
    fun unpatchCachedReferences() {
        patches.forEach { it.field.set(it.client, original) }
        patches.clear()
    }

    /** Full undo: registry entry and every patched field. */
    fun uninstall() {
        unpatchCachedReferences()
        registry.put(name, original)
    }
}

object HookInstaller {
    /**
     * Replaces the registry's location service with a logging proxy that fakes the answers.
     *
     * @param fakePoint read on EVERY call, so a point picked in Phase 1's map takes effect without reinstalling.
     * Both phases share one "selected location" this way.
     */
    fun installLocationHook(
        registry: ServiceRegistry,
        fakePoint: () -> GeoPoint,
        log: CallLog = CallLog(),
    ): HookInstallation {
        val current = registry.get(ServiceRegistry.LOCATION, LocationService::class.java)
        check(!Hooks.isHooked(current)) { "The location service is already hooked; uninstall that hook first." }

        val proxy = Hooks.wrap(LocationService::class.java, current, log) {
            // Replacement: never ask the real service, just answer with the fake point.
            on("getLastKnownLocation") { _, _ -> fakePoint() }
            // Partial override: run the real method, then add to its answer. A fake GPS fix is only
            // believable if the provider claims to be on.
            on("isProviderEnabled") { args, proceed -> (proceed() as Boolean) || args[0] in FAKED_PROVIDERS }
        }
        registry.put(ServiceRegistry.LOCATION, proxy)
        return HookInstallation(registry, ServiceRegistry.LOCATION, original = current, proxy = proxy)
    }

    private val FAKED_PROVIDERS = setOf("gps", "network", "fused")
}
