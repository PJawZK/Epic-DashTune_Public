package com.buttonbox.ble

import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsNoRegressionContractTest {
    @Test fun `location request cadence and priority remain unchanged`() {
        assertEquals(100L, LocationDataHub.REQUEST_INTERVAL_MS)
        assertEquals(50L, LocationDataHub.MIN_UPDATE_INTERVAL_MS)
        assertEquals(100L, LocationDataHub.MAX_UPDATE_DELAY_MS)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, LocationDataHub.REQUEST_PRIORITY)
    }

    @Test fun `BLE service and characteristic UUIDs remain unchanged`() {
        assertEquals("4fafc201-1fb5-459e-8fcc-c5c9c331914b", BleManager.SERVICE_UUID.toString())
        assertEquals("beb5483e-36e1-4688-b7f5-ea07361b26a8", BleManager.CHAR_BUTTON_UUID.toString())
        assertEquals("beb5483e-36e1-4688-b7f5-ea07361b26a9", BleManager.CHAR_VAR_DATA_UUID.toString())
        assertEquals("beb5483e-36e1-4688-b7f5-ea07361b26aa", BleManager.CHAR_VAR_REQUEST_UUID.toString())
        assertEquals("beb5483e-36e1-4688-b7f5-ea07361b26ab", BleManager.CHAR_GPS_DATA_UUID.toString())
    }
}
