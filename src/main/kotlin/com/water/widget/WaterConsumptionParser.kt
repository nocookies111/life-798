package com.water.widget

import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 从积分流水中识别一次饮水消费。
 *
 * 服务端当前没有返回可靠的出水毫升数字，因此水量继续沿用应用统计页的换算规则：
 * 160 积分约等于 500 ml。
 */
data class WaterConsumption(
    val spentScore: Int,
    val occurredAt: Long,
    val paymentYuan: Double? = null
) {
    val moneyText: String
        get() = "¥${String.format(Locale.CHINA, "%.2f", paymentYuan ?: spentScore / 1000.0)}"

    val estimatedWaterText: String
        get() {
            val scoreEquivalent = if (spentScore > 0) {
                spentScore
            } else {
                ((paymentYuan ?: 0.0) * 1000).roundToInt()
            }
            val millilitres = scoreEquivalent * 500 / 160
            return if (millilitres >= 1000) {
                String.format(Locale.CHINA, "%.1f L", millilitres / 1000.0)
            } else {
                "$millilitres ml"
            }
        }
}

object WaterBillParser {
    fun recordKeys(billJson: JSONObject?): Set<String> {
        if (billJson == null || billJson.optInt("code", -999) != 0) return emptySet()
        val records = WaterRecordFields.readRecords(billJson) ?: return emptySet()
        return buildSet {
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index) ?: continue
                if (record.optInt("type", Int.MIN_VALUE) == 91) {
                    add(WaterRecordFields.recordKey("bill", record, record.optJSONObject("data")))
                }
            }
        }
    }

    fun latestSince(
        billJson: JSONObject?,
        sinceMillis: Long,
        expectedDeviceId: String = "",
        excludedRecordKeys: Set<String> = emptySet()
    ): WaterConsumption? {
        if (billJson == null || billJson.optInt("code", -999) != 0) return null
        val records = WaterRecordFields.readRecords(billJson) ?: return null
        var latest: WaterConsumption? = null
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            if (record.optInt("type", Int.MIN_VALUE) != 91) continue
            // 订单 3 表示已付款；待确认、失败和取消都不能作为接水完成信号。
            if (record.optInt("status", Int.MIN_VALUE) != 3) continue
            val recordKey = WaterRecordFields.recordKey(
                "bill",
                record,
                record.optJSONObject("data")
            )
            if (recordKey in excludedRecordKeys) continue
            val updatedAt = WaterRecordFields.normalizeEpochMillis(record.optLong("utime", 0L))
            val createdAt = WaterRecordFields.normalizeEpochMillis(record.optLong("ctime", 0L))
            val occurredAt = updatedAt.takeIf { it > 0L } ?: createdAt
            if (occurredAt < sinceMillis) continue
            val payment = record.optDouble("payment", 0.0)
            if (!payment.isFinite() || payment <= 0.0) continue

            val explicitDeviceId = WaterRecordFields.readDeviceId(record, record.optJSONObject("data"))
            if (expectedDeviceId.isNotBlank() &&
                explicitDeviceId != null &&
                explicitDeviceId != expectedDeviceId
            ) {
                continue
            }

            if (latest == null || occurredAt > latest.occurredAt) {
                latest = WaterConsumption(
                    spentScore = 0,
                    occurredAt = occurredAt,
                    paymentYuan = payment
                )
            }
        }
        return latest
    }
}

object WaterConsumptionParser {
    private val amountKeys = arrayOf(
        "spend", "score", "changeScore", "change_score", "amount", "value", "num", "points"
    )

    fun recordKeys(scoreJson: JSONObject?): Set<String> {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return emptySet()
        val records = WaterRecordFields.readRecords(scoreJson) ?: return emptySet()
        return buildSet {
            for (index in 0 until records.length()) {
                val record = records.optJSONObject(index) ?: continue
                val data = record.optJSONObject("data")
                if (isConsumption(record, data)) {
                    add(WaterRecordFields.recordKey("score", record, data))
                }
            }
        }
    }

    fun latestSince(
        scoreJson: JSONObject?,
        sinceMillis: Long,
        expectedDeviceId: String = "",
        excludedRecordKeys: Set<String> = emptySet()
    ): WaterConsumption? {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return null
        val records = WaterRecordFields.readRecords(scoreJson) ?: return null
        var latest: WaterConsumption? = null
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val data = record.optJSONObject("data")
            val occurredAt = WaterRecordFields.normalizeEpochMillis(record.optLong("ctime", 0L))
            if (occurredAt < sinceMillis) continue
            if (WaterRecordFields.recordKey("score", record, data) in excludedRecordKeys) continue

            val explicitDeviceId = WaterRecordFields.readDeviceId(record, data)
            if (expectedDeviceId.isNotBlank() &&
                explicitDeviceId != null &&
                explicitDeviceId != expectedDeviceId
            ) {
                continue
            }

            val amount = readSpentScore(record, data)
            if (amount <= 0 || !isConsumption(record, data)) continue
            if (latest == null || occurredAt > latest.occurredAt) {
                latest = WaterConsumption(amount, occurredAt)
            }
        }
        return latest
    }

    private fun readSpentScore(record: JSONObject, data: JSONObject?): Int {
        // 已验证的设备消费优先使用 spend；避免顶层 score=0 遮住 data.spend。
        readNonZero(data, "spend")?.let { return abs(it) }
        readNonZero(record, "spend")?.let { return abs(it) }

        val type = record.optInt("type", data?.optInt("type", Int.MIN_VALUE) ?: Int.MIN_VALUE)
        if (type != 107) return 0
        for (key in amountKeys) {
            if (key == "spend") continue
            readNonZero(record, key)?.let { return abs(it) }
        }
        if (data != null) {
            for (key in amountKeys) {
                if (key == "spend") continue
                readNonZero(data, key)?.let { return abs(it) }
            }
        }
        return 0
    }

    private fun isConsumption(record: JSONObject, data: JSONObject?): Boolean {
        val type = record.optInt("type", data?.optInt("type", Int.MIN_VALUE) ?: Int.MIN_VALUE)
        return type == 107 ||
            readNonZero(data, "spend") != null ||
            readNonZero(record, "spend") != null
    }

    private fun readNonZero(json: JSONObject?, key: String): Int? {
        if (json == null || !json.has(key) || json.isNull(key)) return null
        return json.optInt(key, 0).takeIf { it != 0 }
    }
}

private object WaterRecordFields {
    private val deviceKeys = arrayOf("did", "deviceId", "device_id")
    private val recordIdKeys = arrayOf("id", "billId", "bill_id", "orderId", "order_id", "serialNo")

    fun readRecords(root: JSONObject): org.json.JSONArray? {
        root.optJSONArray("data")?.let { return it }
        val data = root.optJSONObject("data") ?: return null
        for (key in arrayOf("records", "items", "list", "content")) {
            data.optJSONArray(key)?.let { return it }
        }
        return null
    }

    fun recordKey(prefix: String, record: JSONObject, data: JSONObject?): String {
        for (key in recordIdKeys) {
            record.optString(key, "").trim().takeIf(String::isNotEmpty)?.let {
                return "$prefix:id:$it"
            }
        }
        if (data != null) {
            for (key in recordIdKeys) {
                data.optString(key, "").trim().takeIf(String::isNotEmpty)?.let {
                    return "$prefix:id:$it"
                }
            }
        }
        // 不包含 status/utime，确保旧的待确认订单变更状态后仍被视为同一条基线记录。
        return listOf(
            prefix,
            record.optLong("ctime", 0L).toString(),
            record.optInt("type", Int.MIN_VALUE).toString(),
            record.optString("owner", ""),
            record.optString("msg", ""),
            data?.optString("adId", "").orEmpty(),
            data?.optString("did", "").orEmpty()
        ).joinToString("|")
    }

    fun readDeviceId(record: JSONObject, data: JSONObject?): String? {
        for (key in deviceKeys) {
            record.optString(key, "").trim().takeIf(String::isNotEmpty)?.let { return it }
        }
        if (data != null) {
            for (key in deviceKeys) {
                data.optString(key, "").trim().takeIf(String::isNotEmpty)?.let { return it }
            }
        }
        return null
    }

    fun normalizeEpochMillis(value: Long): Long = when {
        value <= 0L -> 0L
        value < 10_000_000_000L -> value * 1000L
        else -> value
    }
}
