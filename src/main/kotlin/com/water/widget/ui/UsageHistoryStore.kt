package com.water.widget.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar
import java.util.LinkedHashSet
import java.util.Locale

/**
 * 将服务端当前可见的消费流水增量合并到本地日汇总。
 * 服务端缩短流水窗口时，已经记录到本地的历史不会随之减少。
 */
object UsageHistoryStore {
    private const val PREFS = "usage_history_v1"

    fun mergeAndRead(context: Context, accountKey: String, scoreJson: JSONObject?): WaterUsageUiState {
        if (accountKey.isBlank()) return WaterUsageUiState()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "account_${digest(accountKey)}"
        val ledger = UsageHistoryLedger.fromJson(
            runCatching { JSONObject(prefs.getString(key, "{}").orEmpty()) }.getOrDefault(JSONObject())
        )
        if (ledger.merge(scoreJson)) {
            prefs.edit().putString(key, ledger.toJson().toString()).apply()
        }
        return ledger.toUiState()
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal class UsageHistoryLedger(
    private val seen: LinkedHashSet<String> = LinkedHashSet(),
    private val dayTotals: MutableMap<String, Int> = linkedMapOf()
) {
    fun merge(scoreJson: JSONObject?): Boolean {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return false
        val records = scoreJson.optJSONArray("data") ?: return false
        var changed = false
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val time = record.optLong("ctime", 0L)
            val data = record.optJSONObject("data")
            val amount = readSpentScore(record, data)
            if (time <= 0L || amount <= 0 || !isConsumption(record, data)) continue
            val fingerprint = recordFingerprint(record)
            if (!seen.add(fingerprint)) continue
            val day = dayKey(time)
            dayTotals[day] = (dayTotals[day] ?: 0) + amount
            changed = true
        }
        return changed
    }

    fun toUiState(now: Calendar = Calendar.getInstance()): WaterUsageUiState {
        if (dayTotals.isEmpty()) return WaterUsageUiState()
        val todayKey = dayKey(now.timeInMillis)
        val monthPrefix = "%04d-%02d".format(
            Locale.ROOT,
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1
        )
        val yearPrefix = "%04d".format(Locale.ROOT, now.get(Calendar.YEAR))
        val today = dayTotals[todayKey] ?: 0
        val month = dayTotals.filterKeys { it.startsWith(monthPrefix) }.values.sum()
        val year = dayTotals.filterKeys { it.startsWith(yearPrefix) }.values.sum()
        return WaterUsageUiState(
            todayCostText = moneyText(today),
            monthCostText = moneyText(month),
            yearCostText = moneyText(year),
            todayWaterText = waterText(today),
            monthWaterText = waterText(month),
            yearWaterText = waterText(year)
        )
    }

    fun toJson(): JSONObject {
        val days = JSONObject()
        dayTotals.forEach { (day, total) -> days.put(day, total) }
        return JSONObject()
            .put("seen", JSONArray(seen.toList()))
            .put("days", days)
    }

    companion object {
        fun fromJson(json: JSONObject): UsageHistoryLedger {
            val seen = LinkedHashSet<String>()
            json.optJSONArray("seen")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf(String::isNotBlank)?.let(seen::add)
                }
            }
            val days = linkedMapOf<String, Int>()
            json.optJSONObject("days")?.let { values ->
                val keys = values.keys()
                while (keys.hasNext()) {
                    val day = keys.next()
                    values.optInt(day, 0).takeIf { it > 0 }?.let { days[day] = it }
                }
            }
            return UsageHistoryLedger(seen, days)
        }

        private fun recordFingerprint(record: JSONObject): String {
            val explicitId = arrayOf("id", "sid", "serialNo", "orderId", "bizId")
                .firstNotNullOfOrNull { key -> record.optString(key, "").takeIf(String::isNotBlank) }
            val raw = explicitId ?: record.toString()
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        private fun dayKey(time: Long): String {
            val date = Calendar.getInstance().apply { timeInMillis = time }
            return "%04d-%02d-%02d".format(
                Locale.ROOT,
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH)
            )
        }

        private fun readSpentScore(record: JSONObject, data: JSONObject?): Int {
            val keys = arrayOf("spend", "score", "changeScore", "change_score", "amount", "value", "num", "points")
            for (key in keys) if (record.has(key)) return kotlin.math.abs(record.optInt(key, 0))
            if (data != null) for (key in keys) if (data.has(key)) return kotlin.math.abs(data.optInt(key, 0))
            return 0
        }

        private fun isConsumption(record: JSONObject, data: JSONObject?): Boolean {
            if (record.optInt("type", data?.optInt("type", Int.MIN_VALUE) ?: Int.MIN_VALUE) == 107) return true
            if (data?.has("spend") == true) return true
            val keys = arrayOf("msg", "direction", "typeName", "scene", "bizType", "name", "title", "desc", "remark", "memo")
            val text = buildString {
                keys.forEach { append(record.optString(it, "")); append(' ') }
                if (data != null) keys.forEach { append(data.optString(it, "")); append(' ') }
            }.lowercase(Locale.ROOT)
            return listOf("消费", "使用", "扣", "支出", "兑换", "water", "pay", "cost", "consume", "decrease")
                .any(text::contains)
        }

        private fun moneyText(score: Int): String = "¥${String.format(Locale.CHINA, "%.2f", score / 1000.0)}"

        private fun waterText(score: Int): String {
            val millilitres = score * 500 / 160
            return if (millilitres >= 1000) {
                String.format(Locale.CHINA, "%.1f L", millilitres / 1000.0)
            } else {
                "$millilitres ml"
            }
        }
    }
}
