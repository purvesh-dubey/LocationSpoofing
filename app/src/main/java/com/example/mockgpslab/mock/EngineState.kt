package com.example.mockgpslab.mock

/** What the engine is doing right now. The UI and the notification are driven purely from this. */
sealed class EngineState {
    /** Nothing registered with the system. */
    data object Idle : EngineState()

    /** Test providers are registered and a fix is pushed every second for this point. */
    data class Running(val latitude: Double, val longitude: Double) : EngineState()

    /**
     * The system refused with a SecurityException: this app is not chosen under
     * Developer Options > "Select mock location app" (or the choice was removed while running).
     */
    data object NotMockApp : EngineState()

    /** Anything else went wrong; everything that was registered has been cleaned up. */
    data class Error(val message: String) : EngineState()
}
