package com.water.widget

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** 将扫码结果或手动输入收敛为可存储的设备编号。 */
object DeviceIdParser {
    private val deviceKeyPattern = Regex(
        """(?:[?&#;,\s{]|^)[\"']?(?:did|deviceId|device_id|id)[\"']?\s*(?:=|:)\s*[\"']?([^&#/,}\s\"']+)""",
        RegexOption.IGNORE_CASE
    )
    private val explicitPathMarkers = setOf("device", "devices", "did", "bind", "binding", "q", "qr")
    private val strongDeviceIdPattern = Regex("""\d{8,32}""")
    private val flexibleDeviceIdPattern = Regex("""[A-Za-z0-9][A-Za-z0-9_-]{5,63}""")

    fun normalize(raw: String): String {
        val input = raw.trim()
        if (input.isBlank()) return ""

        val decoded = decode(input)
        val fromKey = deviceKeyPattern.find(decoded)?.groupValues?.getOrNull(1)
        val fromPath = if (fromKey == null) extractFromUrlPath(decoded) else null

        return (fromKey ?: fromPath ?: decoded)
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removePrefix("did:")
            .removePrefix("DID:")
            .take(160)
    }

    private fun extractFromUrlPath(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("http", "https", "ilife")) return null

        val segments = uri.path
            ?.split('/')
            ?.map(::decode)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (segments.isEmpty()) return null

        val host = uri.host?.lowercase().orEmpty()
        if (host == "hnkzy.com" || host.endsWith(".hnkzy.com")) {
            segments.asReversed().firstOrNull(::isStrongDeviceId)?.let { return it }
        }

        val markerIndex = segments.indexOfLast { it.lowercase() in explicitPathMarkers }
        if (markerIndex >= 0) {
            segments.drop(markerIndex + 1).asReversed()
                .firstOrNull(::isFlexibleDeviceId)
                ?.let { return it }
        }

        return segments.lastOrNull()?.takeIf(::isStrongDeviceId)
    }

    private fun isStrongDeviceId(value: String): Boolean = strongDeviceIdPattern.matches(value)

    private fun isFlexibleDeviceId(value: String): Boolean =
        flexibleDeviceIdPattern.matches(value) && value.any(Char::isDigit)

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
