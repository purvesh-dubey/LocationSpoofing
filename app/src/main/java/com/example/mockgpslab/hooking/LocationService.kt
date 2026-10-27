package com.example.mockgpslab.hooking

import com.example.mockgpslab.data.GeoPoint

/**
 * A miniature stand-in for what Android's LocationManager offers apps.
 *
 * It is an INTERFACE on purpose: java.lang.reflect.Proxy can only intercept interface calls. (Android's real
 * LocationManager is a class, which is exactly why Phase 3 has to hook its hidden Binder interface instead.)
 */
interface LocationService {
    fun getLastKnownLocation(provider: String): GeoPoint?
    fun isProviderEnabled(provider: String): Boolean
}

/** Simplified ServiceManager: the system-wide phone book that maps a service name to the object serving it. */
class ServiceRegistry {
    private val services = java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun get(name: String): Any? = services[name]

    fun put(name: String, service: Any) {
        services[name] = service
    }

    /** Typed lookup with a clear error, instead of a ClassCastException somewhere downstream. */
    fun <T : Any> get(name: String, type: Class<T>): T {
        val service = services[name] ?: error("No service registered under '$name'")
        check(type.isInstance(service)) { "Service '$name' is a ${service.javaClass.name}, not a ${type.name}" }
        return checkNotNull(type.cast(service))
    }

    companion object {
        const val LOCATION = "location"
    }
}

/**
 * A pretend third-party app. It is NEVER modified by the hook; that is the whole point of a container.
 *
 * Note the private field. Android's framework classes work the same way: LocationManager asks ServiceManager for
 * its Binder ONCE (when the app's Context is created) and keeps it for the life of the process.
 */
class DemoClientApp(private val registry: ServiceRegistry) {
    private var locationService: LocationService? = null

    private fun service(): LocationService =
        locationService ?: registry.get(ServiceRegistry.LOCATION, LocationService::class.java).also { locationService = it }

    /** Read-only peek for the Hooking Lab UI, so it can explain the stale-cache problem. Not used by the logic. */
    val hasCachedService: Boolean get() = locationService != null

    /**
     * Read-only peek for the Hooking Lab UI: is the service this client cached the one the registry serves right now?
     * False means the client holds a stale copy (it cached before the hook was installed or removed). A client that
     * has cached nothing yet, or cached the proxy straight from the registry, is not stale.
     */
    val cacheMatchesRegistry: Boolean
        get() = locationService?.let { it === registry.get(ServiceRegistry.LOCATION) } ?: true

    fun whereAmI(provider: String = "gps"): GeoPoint? = service().getLastKnownLocation(provider)

    fun isGpsEnabled(): Boolean = service().isProviderEnabled("gps")
}
