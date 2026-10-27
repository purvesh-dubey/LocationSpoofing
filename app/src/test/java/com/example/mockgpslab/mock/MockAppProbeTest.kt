package com.example.mockgpslab.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAppProbeTest {
    private val backend = FakeBackend()

    @Test
    fun `reports Selected and cleans up after itself`() {
        assertEquals(MockAppStatus.Selected, MockAppProbe(backend).check())
        assertTrue("probe must not leave a provider behind", backend.registered.isEmpty())
    }

    @Test
    fun `security exception means NotSelected`() {
        backend.failOn = { if (it.startsWith("add:")) SecurityException("not mock app") else null }
        assertEquals(MockAppStatus.NotSelected, MockAppProbe(backend).check())
        assertTrue(backend.registered.isEmpty())
    }

    @Test
    fun `security exception from remove also means NotSelected`() {
        backend.failOn = { if (it.startsWith("remove:")) SecurityException("not mock app") else null }
        assertEquals(MockAppStatus.NotSelected, MockAppProbe(backend).check())
    }

    @Test
    fun `other failures are Unknown rather than NotSelected`() {
        backend.failOn = { if (it.startsWith("add:")) IllegalStateException("weird") else null }
        assertEquals(MockAppStatus.Unknown, MockAppProbe(backend).check())
    }

    @Test
    fun `a leftover provider does not confuse the probe`() {
        backend.registered += "network"
        assertEquals(MockAppStatus.Selected, MockAppProbe(backend).check())
        assertTrue(backend.registered.isEmpty())
    }
}
