# Learning notes

What this project teaches, in the order you'd want to understand it. The code comments say *why* a line exists;
these notes explain how the pieces fit together.

- [Phase 1: how mock location works end to end](#phase-1-how-mock-location-works-end-to-end)
- [Phase 2: how a Proxy-based hook works](#phase-2-how-a-proxy-based-hook-works)
- [What changes in Phase 3](#what-changes-in-phase-3)

---

## Phase 1: how mock location works end to end

### The cast

| Piece | Where it runs | Job |
|---|---|---|
| `MockLocationService` + `MockLocationEngine` | this app's process | owns the 1-second loop |
| `LocationManager` | this app's process (a thin client class) | the public API we call |
| `LocationManagerService` | Android's `system_server` process | the real location service: keeps the list of providers and the last fix of each |
| "other apps" (Maps, Camera, ...) | their own processes | ask `system_server` for location |

### The flow

```
   Mock GPS Lab (our process)                 system_server                       Other apps
  ┌─────────────────────────────┐        ┌──────────────────────────────┐      ┌───────────────┐
  │ MockLocationService         │        │ LocationManagerService       │      │ Google Maps   │
  │  └ MockLocationEngine       │        │                              │      │ Camera        │
  │      every 1 s, per provider│        │   gps      provider [TEST] ◄─┼──┐   │ any app that  │
  │                             │ Binder │   network  provider [TEST]   │  │   │ asks for      │
  │ LocationManager.            │ ─────► │   fused    provider [TEST]   │  │   │ location      │
  │   addTestProvider(...)      │  IPC   │                              │  │   │               │
  │   setTestProviderEnabled    │        │  The "real" GPS/Wi-Fi/cell   │  └───┤ getLastKnown, │
  │   setTestProviderLocation   │        │  providers are replaced by   │ Binder│ requestUpdates│
  │   removeTestProvider        │        │  the test providers while    │ ◄─────┤ ...           │
  └─────────────────────────────┘        │  they exist                  │      └───────────────┘
              ▲                          └──────────────────────────────┘        gets OUR fix,
              │                                                                    flagged isMock = true
     allowed only if the user chose this app under
     Developer Options > "Select mock location app"
     (stored as the AppOps entry MOCK_LOCATION)
```

Step by step:

1. **Opt-in.** Android checks the *AppOps* `MOCK_LOCATION` entry on every test-provider call. The user grants it
   by choosing the app in Developer Options. There is no runtime dialog and no API to grant it. If it is missing the
   call throws `SecurityException`, which is exactly how `MockAppProbe` and the engine detect "not the mock app".
2. **Register.** `addTestProvider("gps", ...)` tells the system "pretend a provider with these capabilities exists",
   `setTestProviderEnabled(..., true)` turns it on. Leftovers from a crashed run make `addTestProvider` fail with
   `IllegalArgumentException`, which is why the engine removes before adding.
3. **Feed.** Once per second `setTestProviderLocation` hands the system a `Location`. It must carry `time` *and*
   `elapsedRealtimeNanos`: Android 12+ rejects "incomplete" mock locations, and consumers use the monotonic clock
   to decide how fresh a fix is.
4. **Consume.** Any app asking `LocationManager` for `gps` / `network` / `fused` now gets our fix. The system marks it
   as mock (`Location.isMock()` on API 31+, `isFromMockProvider()` before). Our "What the system reports" panel is
   an ordinary client making that same request.
5. **Unregister.** `setTestProviderEnabled(false)` then `removeTestProvider` for everything we added, always, even
   after errors, so the real providers come back. One caveat: the same AppOps check guards *removal*. If the user
   un-selects the app while it runs, our cleanup is refused with `SecurityException` and the system keeps serving the
   last fake fix. Nothing in the public API can fix that from the app side; re-selecting the app and doing one
   start/stop cycle does, because the engine always removes leftovers before adding.

### Why a foreground service

The loop has to keep running while you use Maps or the camera. A background process can be killed within minutes.
A foreground service of type `location` is the sanctioned way to do ongoing location work; its notification is the
user's always-visible off switch. Android 14+ also refuses to start a location-type foreground service unless
location permission is already granted, which is why Start is disabled until it is.

### The other road: Google Play services' fused provider

Most real apps (Google Maps included) don't call `LocationManager`. They use `FusedLocationProviderClient` from
Google Play services, which blends GPS, Wi-Fi and cell data *inside Play services*. It has its own mock switch:
`setMockMode(true)` followed by `setMockLocation(...)`. The optional toggle (`FusedMockController`) uses it. It
needs the same Developer Options opt-in.

### What `adb shell dumpsys location` shows

With the mock running, the provider sections are tagged `[mock]` and name this app as the owner. This is real output
captured from the Android 13 emulator while testing this project:

```
network provider [mock]:
  last location=Location[network 25.594100,85.137600 hAcc=5.0 ... mock]
  identity=10175/com.example.mockgpslab
fused provider [mock]:
  last location=Location[fused 25.594100,85.137600 hAcc=5.0 ... mock]
gps provider [mock]:
  last location=Location[gps 25.594100,85.137600 hAcc=5.0 ... mock]
```

After Stop there are no `[mock]` providers left.

### What this does and does not do

Everything here goes through the public, documented mock-location API, so the system *knows* the location is fake
and says so: every consumer can read the `isMock` flag, and the user has to opt in through Developer Options. The
project makes no attempt to hide that, and Phase 3 isn't about hiding it either. Apps that choose to reject mock
locations (banking, some games, ride-hailing) will do so, and that is their right.

---

## Phase 2: how a Proxy-based hook works

Goal: understand *mechanically* how a container can intercept calls to system services, using only our own classes.
Nothing in Phase 2 touches the real system or another app.

### java.lang.reflect.Proxy in one picture

```
  DemoClientApp.whereAmI()
        │   locationService.getLastKnownLocation("gps")
        ▼
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │ $Proxy0   (class generated at runtime, "implements LocationService")           │
  │   every method body is the same:  handler.invoke(this, method, args)           │
  └──────────────────────────────────────┬───────────────────────────────────────┘
                                         ▼
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │ LoggingInvocationHandler.invoke(proxy, method, args)                           │
  │                                                                                │
  │   equals / hashCode / toString ─► answered locally (identity semantics)        │
  │                                                                                │
  │   interceptor registered for method.name?                                      │
  │     yes ─► interceptor(args, proceed)                                          │
  │              ├─ returns a replacement   (real method never runs)               │
  │              └─ calls proceed() ─────────────────────┐                         │
  │     no  ─► method.invoke(target, args)  (pass-through)│                         │
  │                                         ◄─────────────┘                         │
  │   unwrap InvocationTargetException · check the return type · log the call      │
  └──────────────────────────────────────┬───────────────────────────────────────┘
                                         ▼
                              RealLocationService  (the "target")
```

The caller can't tell: it holds a `LocationService` and calls methods on it. The Proxy *is* a `LocationService`.
That is the whole trick, and `Hooks.wrap(LocationService::class.java, target) { on("...") { args, proceed -> ... } }`
is the friendly face on it.

### Why Proxy only works on interfaces

`Proxy.newProxyInstance` generates a new class at runtime. That class has to *be* the thing the caller expects,
and the only way a generated class can do that is by implementing the interface. It cannot extend a concrete class
instead, because every generated proxy already extends `java.lang.reflect.Proxy`, and Java has single inheritance.
Even if it could, `final` classes and methods can't be overridden.

So `Hooks.wrap` refuses a class with an error that says this. It matters for Phase 3: Android's `LocationManager` is a
**class**, so you can't wrap it with Proxy at all (see below).

### Step 2.3: the registry swap

`ServiceRegistry` is a miniature `ServiceManager`: a name-to-object phone book. Installing the hook replaces
`registry["location"]` with a proxy wrapping the original. A client created *after* the swap looks the service up,
receives the proxy, and is fooled without a single line of the client changing.

### Step 2.4: the cached-reference problem (the key lesson)

Real framework classes don't look a service up on every call. `LocationManager` fetches its Binder once when the app
starts and keeps it in a field for the life of the process. `DemoClientApp` does the same:

```
 1. Before the hook          registry["location"] ──► Real        client.field ──► Real

 2. Registry swap only       registry["location"] ──► Proxy ──┐   client.field ──► Real      ◄── STALE
                                                              └─► Real   (client never asks the registry again)

 3. Swap + patch the field   registry["location"] ──► Proxy ──┐   client.field ──► Proxy ──┐
    (reflection)                                              └─► Real                     └─► Real
```

Step 2 is why hooks that "work in a test" fail in real apps: the test creates the client *after* installing the hook.
`HookInstallerTest` reproduces both the failure and the fix. The fix finds every private field of the client that still
holds the original and overwrites it with the proxy (`getDeclaredField` + `setAccessible(true)`, wrapped up in
`HookInstallation.patchCachedReferences`). On our own demo class there is nothing stopping that. On framework classes
the same trick runs into hidden-API restrictions, which is a Phase 3 topic.

You can watch all of this in the app: open the **Hooking Lab**, turn "Patch the client's cached reference" off,
switch the hook on, and the Hooked column stays stale while the log stays empty (the proxy is never called).
Then switch patching on and the column and log spring to life.

### Details worth reading in the code

- `LoggingInvocationHandler.callTarget` unwraps `InvocationTargetException`. `Method.invoke` wraps whatever the real
  method threw; without unwrapping, callers would see a reflection exception instead of the real one.
- `checkReturnValue` turns the classic "interceptor returned null for a primitive" failure from an anonymous
  `NullPointerException` into a message naming the hook and method.
- `equals`/`hashCode`/`toString` are answered by the handler with identity semantics. Delegating `equals` to the
  target would make `proxy.equals(target)` true but `target.equals(proxy)` false.
- The fake point is a `() -> GeoPoint` read on **every** call, backed by the same DataStore value Phase 1 mocks, so one
  map tap drives both phases.

---

## What changes in Phase 3

Phase 3 (not started) is a **virtual container**: an app that runs *other* apps inside itself and hooks their calls.
Phase 2 is the right warm-up, but three things are very different from the demo.

**1. The real thing is not an interface you can see.** In Phase 2 we own `LocationService`. In Android,
`LocationManager` (what apps call) is a concrete class. It does not do the work itself: it holds a field referencing
the hidden `ILocationManager` interface, an AIDL-generated **Binder** interface whose implementation lives in
`system_server`. Every `LocationManager` method is a thin wrapper that forwards to that field.

```
 Guest app code ─► LocationManager (class, public)  ─► ILocationManager (interface, HIDDEN) ─► Binder IPC ─► system_server
                    can't be Proxy'd                      CAN be Proxy'd, it's an interface
                    ▲ guest calls this                     ▲ this is where a container hooks
```

So a container hooks one level down: it wraps `ILocationManager` (and the `IBinder` it came from) with exactly the
kind of Proxy we built in Phase 2, then does the "cached reference" fix on the framework's own fields: the service
cache inside `ServiceManager`, and the `mService` field of each `LocationManager` the guest already created. Same
two problems as Phase 2, but the objects now live in the Android framework.

**2. That is why hidden-API access is required.** `ILocationManager`, `ServiceManager` and those private fields are
not part of the public SDK. Since Android 9, the platform restricts reflective access to them (lists of
"unsupported" APIs, tightened every release). A container needs a way to reach them, and which approach works
changes from one Android version to the next. Phase 1 and 2 deliberately use none of this.

**3. Guest apps must be *runnable*, which needs stub components.** Android only starts an activity or service that
is declared in the manifest of an **installed** app. A guest app isn't installed, so the container pre-declares a
pool of generic "stub" activities, services and providers in its own manifest, redirects the guest's start requests
to a stub, and swaps the guest's real component in when the system hands control back. It also has to give guests a
separate class loader, resources and data directory, and keep its own process model straight. Intercepting
location is a small part of the job; making a foreign app believe it is installed is most of it.

What carries over unchanged from this project: the Proxy-and-handler pattern, the interceptor DSL with `proceed()`,
the call log for seeing what a guest actually asks the system, the cached-reference lesson, and the shared "selected
location" store.
