package com.water.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdParserTest {
    private val longDeviceId = "123456789012345"

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
            longDeviceId,
            DeviceIdParser.normalize("https://cloud.hnkzy.com/h5/?atype=1&id=$longDeviceId#/pages/app/index")
        )
    }

    @Test
    fun extractsDeviceIdFromHnkzyShortLinkPath() {
        assertEquals(
            longDeviceId,
            DeviceIdParser.normalize("https://i.hnkzy.com/q/1/$longDeviceId")
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
            longDeviceId,
            DeviceIdParser.normalize("https://example.test/share/$longDeviceId")
        )
    }

    @Test
    fun extractsDeviceIdFromJsonStylePayload() {
        assertEquals(
            longDeviceId,
            DeviceIdParser.normalize("""{"device_id":"$longDeviceId","type":1}""")
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
