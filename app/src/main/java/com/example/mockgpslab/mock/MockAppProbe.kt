package com.example.mockgpslab.mock

/** Result of asking the system whether this app is allowed to mock. */
enum class MockAppStatus { Selected, NotSelected, Unknown }

/**
 * Answers "is this app the selected mock location app?" without starting anything.
 *
 * There is no public API that returns the setting directly. The system enforces it per call, so the
 * reliable test is to try adding a test provider: SecurityException means "not selected".
 * The probe always removes what it added so it leaves no trace.
 */
class MockAppProbe(
    private val backend: MockProviderBackend,
    private val probeProvider: String = MockProviders.NETWORK,
    private val log: EngineLog = EngineLog {},
) {
    fun check(): MockAppStatus {
        try {
            // A leftover from a crashed run would make addTestProvider fail with IllegalArgumentException.
            try {
                backend.removeTestProvider(probeProvider)
            } catch (_: IllegalArgumentException) {
                // Nothing to remove: the normal case.
            }
            backend.addTestProvider(probeProvider)
            return MockAppStatus.Selected
        } catch (e: SecurityException) {
            return MockAppStatus.NotSelected
        } catch (e: Exception) {
            log.log("Mock-app probe inconclusive: $e")
            return MockAppStatus.Unknown
        } finally {
            try {
                backend.removeTestProvider(probeProvider)
            } catch (_: Exception) {
                // Either we never added it, or we aren't allowed to; nothing more to do.
            }
        }
    }
}
