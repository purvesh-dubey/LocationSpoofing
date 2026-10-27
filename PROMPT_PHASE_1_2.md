# Task: Build "Mock GPS Lab", Phase 1 (mock location app) and Phase 2 (hooking demo)

## Context
I'm learning how Android's location pipeline and system-service hooking work. This is a personal learning project. I will install a debug build only on my own Android phone (and the emulator). It will not be published to the Play Store. This repo (the current folder) is empty. I'm on a MacBook Air (Apple silicon) with Android Studio.

Roadmap:
- Phase 1: standard mock-location app (this task)
- Phase 2: in-process hooking demo using `java.lang.reflect.Proxy` (this task)
- Phase 3: virtual container that hooks guest apps (LATER, do NOT start it)

## Ground rules
- Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL with a version catalog, single `app` module. minSdk 26. compileSdk/targetSdk = newest stable platform installed. Package `com.example.mockgpslab`.
- Map: osmdroid (OpenStreetMap) so no API key is needed. Set a proper user agent for tile requests.
- Use only public, documented Android APIs. No hidden-API reflection, no root. Do NOT add anything that hides the mock from other apps or evades Developer Options / mock-location detection. That is out of scope.
- Check the environment first: JDK 17+ (AGP needs it; if system `java` is older, use Android Studio's bundled JBR), `ANDROID_HOME` / `local.properties`, installed SDK platforms and build-tools. Ask me before installing anything large. If you can't generate the Gradle wrapper, tell me exactly what is missing.
- Before coding, show a short plan (file layout and steps). Then implement it without waiting for approval.
- `git init` if needed, add a proper `.gitignore`, and commit after each numbered step with a clear message.
- Before calling a step done, run `./gradlew assembleDebug testDebugUnitTest lintDebug` and fix failures. You can't test on my phone, so don't claim device behaviour you haven't verified. List it under "unverified" at the end.
- Keep code small and readable with comments that explain WHY, since the goal is learning.

## Phase 1: Mock location app

1.1 **Project scaffold.** Compose app "Mock GPS Lab" that builds and runs. Navigation: Main screen, Setup checklist, and (later, Phase 2) Hooking Lab.

1.2 **Manifest and permissions.**
- Put `ACCESS_MOCK_LOCATION` in `app/src/debug/AndroidManifest.xml` only. Lint rejects it in main, and I only need debug builds.
- Main manifest: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`.
- Handle runtime permissions (location, notifications) with clear rationale text.

1.3 **MockLocationEngine.**
- Wrap `LocationManager` behind a small interface (`MockProviderBackend`) so the logic can be unit tested with a fake.
- Providers: `GPS_PROVIDER` and `NETWORK_PROVIDER`. On API 31+, also try `FUSED_PROVIDER` and log and continue if the platform rejects it.
- Start: remove any leftover test provider (catch `IllegalArgumentException`), call `addTestProvider` (use `ProviderProperties` on API 31+, the legacy overload below that), `setTestProviderEnabled(true)`.
- Push a `Location` every 1s via `setTestProviderLocation`. Set provider, lat, lon, accuracy (default 5 m, configurable), altitude, speed 0, bearing 0, `time = System.currentTimeMillis()`, and `elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()`.
- Stop: `setTestProviderEnabled(false)` and `removeTestProvider` for every provider that was added, always, even on errors.
- Detect "this app is not selected as the mock location app". Probe by trying `addTestProvider`/`removeTestProvider` and catching `SecurityException`. Expose engine state as a sealed class/Flow: `Idle`, `Running(lat, lon)`, `NotMockApp`, `Error(msg)`.

1.4 **Foreground service.**
- `MockLocationService` with `foregroundServiceType="location"` hosts the engine, with a coroutine scope for the 1s loop.
- Ongoing notification: "Mock location active: <lat>, <lon>" with a Stop action.
- Start it only after location permission is granted.
- Update the target coordinates while running when the user picks a new point.
- Clean up in `onDestroy`.
- Persist the last selected point (DataStore).

1.5 **UI.**
- Map: tap to pick a point; marker shows the selection.
- Latitude/longitude text fields. Validate lat -90..90 and lon -180..180. Accept a pasted "25.5941, 85.1376" in one field.
- Preset list with Patna (25.5941, 85.1376) and a way to save and delete my own places.
- Large Start / Stop button.
- Status card showing: permissions, mock-app selected or not, service running or not.
- "Open Developer Options" button using `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`, with fallback to `ACTION_SETTINGS` if it can't resolve.
- Setup checklist screen explaining: enable Developer Options, choose this app under "Select mock location app", grant permissions, allow notifications.
- "What the system reports" panel: request a location from `LocationManager` inside the app and display lat/lon, provider, and whether it is flagged mock (`isMock` on API 31+, `isFromMockProvider` below). This is my in-app proof that the pipeline works.

1.6 **Optional: Fused provider.** Behind a toggle and a separate class: use `play-services-location` `FusedLocationProviderClient.setMockMode(true)` / `setMockLocation()` so apps that use Google Play Services location also see the mock. Degrade gracefully if Play Services is missing. Add a README note that this is the path most real apps use.

1.7 **Tests (plain JVM).**
- Coordinate parsing/validation.
- Engine with the fake backend: provider add/remove order, pushes at 1s using coroutine test virtual time, cleanup on stop and on error, `SecurityException` becomes `NotMockApp`.

## Phase 2: Hooking demo with java.lang.reflect.Proxy

Goal: understand exactly how a container intercepts system calls, using ONLY our own classes and interfaces. Package `com.example.mockgpslab.hooking`. Keep the core free of Android imports so tests run on the plain JVM.

2.1 **LoggingInvocationHandler.** Generic `InvocationHandler` wrapping a target. Records method name, args, result or exception, and elapsed time to an observable log. It must:
- unwrap `InvocationTargetException` so callers see the real exception;
- handle `equals` / `hashCode` / `toString` sensibly;
- fail with a clear message if an interceptor returns null for a primitive return type.

2.2 **Hook DSL.** Something like `Hooks.wrap(LocationService::class.java, target) { on("getLastKnownLocation") { args, proceed -> ... } }`. Per-method interceptors can call `proceed()` to hit the original or return a replacement. Unhooked methods pass through.

2.3 **Demo domain that mimics the framework.**
- `interface LocationService { fun getLastKnownLocation(provider: String): GeoPoint?; fun isProviderEnabled(provider: String): Boolean }`.
- `RealLocationService` backed by Android's `LocationManager` (the JVM tests use a stub).
- `ServiceRegistry`, a simplified stand-in for Android's `ServiceManager` (`get(name)` / `put(name, service)`).
- `DemoClientApp` that fetches the service from the registry and uses it, and is NOT modified by the hook.
- `HookInstaller.installLocationHook(registry, fakePoint)` replaces the registry entry with a Proxy wrapping the original. The fake point comes from the same store Phase 1 uses, so both phases share the "selected location".

2.4 **The cached-reference problem (key lesson).**
- Make `DemoClientApp` cache the service in a private field, like framework classes cache their service binders.
- Show that swapping the registry entry is NOT enough once the client has cached the original.
- Fix it by replacing the private field via reflection (`getDeclaredField` + `setAccessible(true)`). This is on OUR OWN demo class, not a framework class.
- Add tests that demonstrate both the failure and the fix.

2.5 **Hooking Lab screen.**
- Toggle: hook on / off.
- Side-by-side "Real" and "Hooked" results for `getLastKnownLocation`.
- Live scrolling log of intercepted calls.
- Handle a missing permission or null last-known location without crashing.

2.6 **Tests (plain JVM).** Cover:
- pass-through;
- interception;
- `proceed()`;
- logging contents;
- exception unwrapping;
- Object methods;
- primitive-null error;
- registry swap;
- stale cache reproduction plus the reflection fix.

2.7 **docs/learning-notes.md.** Explain, with simple text diagrams:
- how Phase 1 works end to end (app, `LocationManager`, system, other apps);
- how Phase 2 works, and why Proxy only works on interfaces;
- a short "What changes in Phase 3" section: Android's `LocationManager` is a class that talks to the hidden `ILocationManager` Binder interface, which is why a container needs hidden-API access and stub components. Explanation only, no code.

## Definition of done
- `./gradlew assembleDebug testDebugUnitTest lintDebug` passes.
- README covers: build and install (`./gradlew installDebug` or Android Studio), phone setup (tap Build number 7 times, Developer Options, Select mock location app), and a manual test checklist (Google Maps shows the fake position, camera geotag with "location tags" enabled, the in-app "what the system reports" panel, and `adb shell dumpsys location` output).
- Both phases visible in the app; the log shows hooks firing.
- Final message lists: what was built, commands to run, what I need to verify on a real device, and any deviations from this spec.
