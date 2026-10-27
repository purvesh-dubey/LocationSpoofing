# Mock GPS Lab

A personal learning project about how Android's location pipeline works and how system-service hooking works.
Debug builds only; it is not meant for the Play Store.

- **Phase 1: mock location app.** The standard, documented mechanism: `LocationManager` test providers fed by a
  foreground service, with an OpenStreetMap picker and an in-app proof that it works.
- **Phase 2: hooking demo.** An in-process `java.lang.reflect.Proxy` hook on our own demo classes, including the
  "cached reference" problem and its reflection fix. Nothing outside this app is touched.
- **Phase 3 (virtual container): not started.** See [docs/learning-notes.md](docs/learning-notes.md) for what changes.

Start with **[docs/learning-notes.md](docs/learning-notes.md)** if you want the explanation with diagrams.

> **Scope.** This uses only public Android APIs. The system knows the location is mocked and flags it
> (`Location.isMock()`); the user must opt in via Developer Options. Nothing here hides that from other apps.

---

## Build and install

Requirements: JDK 17+ (AGP 9 needs it; Android Studio's bundled JBR works), Android SDK with platform **36.1**
(`compileSdk` 36.1; `targetSdk` is 36, see note below), and an internet connection for the first Gradle sync.

Command line:

```bash
./gradlew installDebug
```

(Phone connected with USB debugging on, or an emulator running.) If your default `java` is older than 17, point
Gradle at Android Studio's JDK first:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Android Studio: open this folder, let Gradle sync, press **Run**. `local.properties` (with `sdk.dir`) is
machine-specific and git-ignored; Studio creates it for you.

The full check used throughout development:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

## Phone setup

Android only lets an app mock location after you opt in. The in-app **Setup** screen walks through this with live
ticks, but in short:

1. **Unlock Developer Options.** Settings > About phone > tap **Build number** 7 times (enter your screen lock).
2. **Select the mock app.** Settings > System > Developer options > **Select mock location app** > *Mock GPS Lab*.
   The app's **Open Developer Options** button jumps there. Only one app can hold this role at a time.
3. **Grant location permission** (choose *Precise* if asked) and **allow notifications** when the app asks.
4. Open the app, pick a point, press **Start mock location**.

Emulator shortcut (equivalent to step 2 and the permission dialogs, for convenience in testing):

```bash
adb shell appops set com.example.mockgpslab android:mock_location allow
adb shell pm grant com.example.mockgpslab android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.example.mockgpslab android.permission.POST_NOTIFICATIONS
```

## Using it

| Where | What |
|---|---|
| Map | Tap to choose a point; the marker shows the selection. |
| Latitude / longitude | Validated (-90..90 / -180..180). Paste `25.5941, 85.1376` into either field and both fill in. |
| Accuracy | Metres, default 5. |
| Places | Built-in presets (Patna, New Delhi, Mumbai, Bengaluru) and your own: *Save current point as...*, delete with the X. |
| Start / Stop | Big button. While running, picking another point moves the mock live. The notification has a Stop action. |
| Status card | Permissions, whether this app is the selected mock app, and whether the service runs. |
| What the system reports | Asks `LocationManager` for a location like any other app and shows lat/lon, provider and the mock flag. |
| Also mock Google Play services | Optional toggle, see below. |
| Hooking Lab | Phase 2 demo (top-right of the main screen). |

### About the Fused (Play services) toggle

Most real apps, Google Maps included, don't call `LocationManager`; they use Google Play services'
`FusedLocationProviderClient`. **That is the path most real apps use.** Play services normally consumes the platform's
`gps`/`network` providers, so the standard mock usually reaches those apps by itself: on the Android 13 emulator,
Google Maps followed the mock with this toggle **off**. The toggle is a second, explicit route: it calls
`setMockMode(true)` / `setMockLocation(...)` on Play services directly, for apps or devices where the standard mock
alone doesn't get through (try it if an app ignores you). It needs Play services; without it the toggle is disabled
and the standard mock still works. Changing the toggle takes effect the next time you press Start.

## Manual test checklist

On a real device (these are the checks that can't be done from a build server):

- [ ] **Setup screen**: steps 1-4 all show a tick.
- [ ] **In-app panel**: Start the mock, tap *Check now* under *What the system reports*. Expect your point, provider
      `gps`, **Flagged as mock: Yes**, accuracy 5.0 m.
- [ ] **Google Maps**: Start the mock, then open Maps; the blue dot should be at the chosen point. Pick another
      place here and the dot follows. Try it with *Also mock Google Play services* **off** first; turn it on if Maps
      doesn't follow. If Maps was already open (or you only just gave it location permission), force-stop and reopen it:
      it caches its last location and may not re-centre on its own.
- [ ] **Camera geotag**: in the camera app enable *Location tags* / *Save location*, take a photo, open its details;
      it should show the fake coordinates.
- [ ] **`adb shell dumpsys location`**: see below.
- [ ] **Stop**: the notification disappears, and `dumpsys location` no longer shows `[mock]` providers; real
      location returns after a moment.
- [ ] **Deselect the app** under *Select mock location app* while running: the service stops and the status card
      reports "Selected as mock app: No". Expect Android to keep serving the last fake fix until you re-select the
      app and press Start then Stop once (see the first gotcha below).

### `adb shell dumpsys location`

```bash
adb shell dumpsys location | grep -E "\[mock\]|last mock location|identity="
```

While running you should see lines like these (this is real output from the Android 13 emulator):

```
network provider [mock]:
  last location=Location[network 25.594100,85.137600 hAcc=5.0 ... mock]
  identity=10175/com.example.mockgpslab
gps provider [mock]:
  last location=Location[gps 25.594100,85.137600 hAcc=5.0 ... mock]
```

`identity=` names the app that owns the test provider. After Stop there should be no `[mock]` sections.

### Already verified on the emulator

Run on a Pixel 6 AVD (Android 13, Google APIs), not on a phone:

- Start / live point change / Stop / notification Stop, and `dumpsys location` showing three `[mock]` providers
  that disappear afterwards.
- The in-app "What the system reports" panel: your point, provider `gps`, flagged as mock.
- **Google Maps** put the blue dot at the chosen point (Patna, then Mumbai), with the Fused toggle on and with it off.
- **Camera**: with *Location tags* ticked, a photo's EXIF GPS was exactly the selected point (25.59410, 85.13760).
- Un-selecting the mock app mid-run, and recovering with one Start/Stop (see the first gotcha below).

## Tests

```bash
./gradlew testDebugUnitTest
```

94 plain-JVM tests, no emulator needed:

- Coordinate parsing/validation, the form's paste-a-pair behaviour, saved-places encoding.
- `MockLocationEngine` against a fake backend with coroutine virtual time: provider add/remove order, one push per
  second, cleanup on stop and on errors, `SecurityException` becoming `NotMockApp`, optional fused provider.
- Hooking: pass-through, interception, `proceed()`, log contents, exception unwrapping, `equals`/`hashCode`/`toString`,
  the primitive-null error, registry swap, and the stale-cache reproduction plus its reflection fix.

## Project layout

```
app/src/main/java/com/example/mockgpslab/
  mock/        engine, backend interface, LocationManager backend, mock-app probe, system-report probe
  service/     foreground service, controller, shared session state
  data/        GeoPoint, coordinate parsing + form, saved places, DataStore settings (the shared "selected location")
  fused/       optional Play services mock
  hooking/     Phase 2 core: CallLog, LoggingInvocationHandler, Hooks DSL, demo domain, HookInstaller (no Android imports)
  hooking/android/  RealLocationService, the only Android-backed piece of Phase 2
  permissions/ runtime permission state and explanatory UI
  ui/          Compose screens, ViewModels, osmdroid map
app/src/debug/AndroidManifest.xml   ACCESS_MOCK_LOCATION lives here and only here
docs/learning-notes.md              the explanations
```

## Notes and gotchas

- **Losing the mock-app role mid-run leaves the last fake fix registered.** Android only lets the *selected* mock
  app remove its test providers. If you un-select the app while it runs, the service notices (`SecurityException`),
  stops and says so, but its `removeTestProvider` calls are refused, so the system keeps the three `[mock]`
  providers and their last location. Re-select the app, press **Start** then **Stop** once, and the engine's
  "remove leftovers first" step clears them (checked on the emulator: 3 providers before, 0 after). Rebooting
  also clears them.
- **`ACCESS_MOCK_LOCATION` is debug-only.** It is declared in `app/src/debug/AndroidManifest.xml`, so a release
  build can never mock. Lint's `MockLocation` check is meant to accept that file but, with AGP 9.2's lint, still
  flags it, so the element carries a narrow `tools:ignore="MockLocation"` (explained in the file). The check still
  guards the main manifest.
- **Dependency versions are pinned to what compiles against SDK 36.1.** The newest AndroidX releases (Compose 1.12,
  navigation 2.10, lifecycle 2.11, core 1.19) need compileSdk 37. To upgrade: install *Android 37* in the SDK
  Manager, raise `compileSdk`, and bump the versions in `gradle/libs.versions.toml`.
- **AGP 9.2.1 / Gradle 9.4.1** match the AGP that Android Studio 2025.3.3 ships with; a newer AGP makes Studio
  refuse to sync. Kotlin is built into AGP 9, so there is no separate `kotlin-android` plugin.
- **Map tiles** come from OpenStreetMap with an identifying user agent (`MockGpsLab/<version> (<package>; ...)`),
  as the tile usage policy requires. For anything beyond personal use, switch to a tile provider you have an
  agreement with.
- Android lint reports a few *warnings* (newer library versions available, `targetSdk` below the newest release,
  `dataExtractionRules` on `minSdk` 26). These are informational; `lintDebug` has no errors.
