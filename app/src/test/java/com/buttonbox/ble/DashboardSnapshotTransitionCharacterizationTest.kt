package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure characterization of the transport/connection identity rules currently implemented by
 * DashboardDataHub.snapshotJson(). This intentionally does not introduce a new runtime state owner.
 */
class DashboardSnapshotTransitionCharacterizationTest {
    private data class Input(
        val preference: String,
        val usbStreaming: Boolean,
        val usbSessionSelected: Boolean,
        val usbSessionId: Long,
        val usbState: String,
        val bleConnected: Boolean,
        val blePhase: String,
        val revision: Long
    )

    private data class Result(
        val useUsb: Boolean,
        val transport: String,
        val connected: Boolean,
        val sessionId: Long,
        val phase: String,
        val revision: Long
    )

    private fun characterize(input: Input): Result {
        val useUsb = input.preference != "ble" &&
            (input.usbStreaming || input.usbSessionSelected)
        return Result(
            useUsb = useUsb,
            transport = if (useUsb) "usb" else "ble",
            connected = if (useUsb) input.usbStreaming else input.bleConnected,
            sessionId = if (useUsb) input.usbSessionId else -1L,
            phase = if (useUsb) input.usbState else input.blePhase,
            revision = input.revision
        )
    }

    @Test
    fun activeUsbStreamingOwnsSnapshotIdentity() {
        val result = characterize(
            Input("auto", true, true, 12L, "streaming", true, "online", 250L)
        )

        assertTrue(result.useUsb)
        assertEquals("usb", result.transport)
        assertTrue(result.connected)
        assertEquals(12L, result.sessionId)
        assertEquals("streaming", result.phase)
        assertEquals(250L, result.revision)
    }

    @Test
    fun selectedUsbHandshakeRemainsUsbButDisconnected() {
        val result = characterize(
            Input("auto", false, true, 13L, "handshake", true, "online", 251L)
        )

        assertTrue(result.useUsb)
        assertEquals("usb", result.transport)
        assertFalse(result.connected)
        assertEquals(13L, result.sessionId)
        assertEquals("handshake", result.phase)
    }

    @Test
    fun forcedBleIgnoresPreviouslySelectedUsbSession() {
        val result = characterize(
            Input("ble", true, true, 14L, "streaming", true, "online", 252L)
        )

        assertFalse(result.useUsb)
        assertEquals("ble", result.transport)
        assertTrue(result.connected)
        assertEquals(-1L, result.sessionId)
        assertEquals("online", result.phase)
    }

    @Test
    fun autoFallsBackToBleBeforeUsbIsSelected() {
        val result = characterize(
            Input("auto", false, false, 15L, "waiting_device", false, "scanning", 253L)
        )

        assertFalse(result.useUsb)
        assertEquals("ble", result.transport)
        assertFalse(result.connected)
        assertEquals(-1L, result.sessionId)
        assertEquals("scanning", result.phase)
    }

    @Test
    fun disconnectAndReconnectSequenceKeepsAuthoritativeSessionAndRevision() {
        val live = characterize(
            Input("usb", true, true, 20L, "streaming", false, "offline", 300L)
        )
        val disconnected = characterize(
            Input("usb", false, true, 21L, "disabled", false, "offline", 301L)
        )
        val reconnected = characterize(
            Input("usb", true, true, 22L, "streaming", false, "offline", 302L)
        )

        assertTrue(live.connected)
        assertFalse(disconnected.connected)
        assertTrue(reconnected.connected)
        assertTrue(disconnected.sessionId > live.sessionId)
        assertTrue(reconnected.sessionId > disconnected.sessionId)
        assertTrue(disconnected.revision > live.revision)
        assertTrue(reconnected.revision > disconnected.revision)
    }
}
