package com.water.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdParserTest {
    @Test
    fun keepsPlainDeviceId() {
        assertEquals("device-001", DeviceIdParser.normalize("  device-001  "))
    }

    @Test
    fun extractsDeviceIdFromQrLink() {
        assertEquals("device-001", DeviceIdParser.normalize("https://example.test/device?deviceId=device-001&source=qr"))
    }

    @Test
    fun extractsDeviceIdFromHnkzyQrLink() {
        assertEquals(
            "863781051219856",
            DeviceIdParser.normalize("https://cloud.hnkzy.com/h5/?atype=1&id=863781051219856#/pages/app/index")
        )
    }

    @Test
    fun extractsDeviceIdFromHnkzyShortLinkPath() {
        assertEquals(
            "863781051219856",
            DeviceIdParser.normalize("https://i.hnkzy.com/q/1/863781051219856")
        )
    }

    @Test
    fun extractsDeviceIdFromKnownDevicePath() {
        assertEquals(
            "A-123456",
            DeviceIdParser.normalize("https://example.test/devices/A-123456")
        )
    }

    @Test
    fun extractsLongNumericIdFromGenericUrlPath() {
        assertEquals(
            "863781051219856",
            DeviceIdParser.normalize("https://example.test/share/863781051219856")
        )
    }

    @Test
    fun extractsDeviceIdFromJsonStylePayload() {
        assertEquals(
            "863781051219856",
            DeviceIdParser.normalize("{\"device_id\":\"863781051219856\",\"type\":1}")
        )
    }

    @Test
    fun extractsDidFromEncodedQrLink() {
        assertEquals("A-123", DeviceIdParser.normalize("ilife://bind?did=A-123%26ignored"))
    }

    @Test
    fun doesNotTreatOrdinaryShortPathSegmentAsDeviceId() {
        assertEquals(
            "https://example.test/q/1/help",
            DeviceIdParser.normalize("https://example.test/q/1/help")
        )
    }
}
