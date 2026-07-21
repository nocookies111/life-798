package com.water.widget.ui

import com.water.widget.Account
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class DashboardSummaryUiState(
    val accountTitle: String,
    val accountSubtitle: String,
    val scoreTitle: String,
    val scoreSubtitle: String,
    val hasAccount: Boolean,
    val hasAppToken: Boolean,
    val hasDevices: Boolean,
    val hotDevice: String,
    val coldDevice: String,
    val accountCount: Int,
    val recentDevices: List<String> = emptyList(),
    val usage: WaterUsageUiState = WaterUsageUiState()
)

data class WaterUsageUiState(
    val todayCostText: String = "--",
    val monthCostText: String = "--",
    val yearCostText: String = "--",
    val todayWaterText: String = "--",
    val monthWaterText: String = "--",
    val yearWaterText: String = "--"
)

data class DashboardAccountUiState(
    val phone: String,
    val title: String,
    val subtitle: String,
    val tokenSummary: String,
    val deviceSummary: String,
    val isCurrent: Boolean
)

object DashboardUiStateFactory {
    fun from(
        account: Account?,
        accountCount: Int,
        validScore: Int? = null,
        scoreJson: JSONObject? = null
    ): DashboardSummaryUiState {
        if (account == null) {
            return DashboardSummaryUiState(
                accountTitle = "未登录",
                accountSubtitle = "登录后可完成积分任务、同步设备并查看用水统计",
                scoreTitle = "--",
                scoreSubtitle = "暂无积分",
                hasAccount = false,
                hasAppToken = false,
                hasDevices = false,
                hotDevice = "未分配",
                coldDevice = "未分配",
                accountCount = accountCount,
                recentDevices = emptyList(),
                usage = WaterUsageUiState()
            )
        }

        val title = account.name?.takeIf { it.isNotBlank() }
            ?: account.phone?.takeIf { it.isNotBlank() }
            ?: "已登录账户"
        val hasAppToken = account.hasAppToken()
        val hasDevices = account.hasDevices()
        val hot = account.hotOrFallback()?.takeIf { it.isNotBlank() } ?: "未分配"
        val rawCold = account.coldOrFallback()?.takeIf { it.isNotBlank() }
        val cold = when {
            rawCold.isNullOrBlank() -> "未分配"
            rawCold == hot -> "共用热水设备"
            else -> rawCold
        }

        return DashboardSummaryUiState(
            accountTitle = title,
            accountSubtitle = "$accountCount 个账户 · ${if (hasAppToken) "设备控制已开通" else "设备控制待开通"}",
            scoreTitle = validScore?.toString() ?: "刷新中",
            scoreSubtitle = validScore?.let { "≈${String.format(java.util.Locale.CHINA, "%.2f", it / 1000.0)}元可用" } ?: "当前账号积分",
            hasAccount = true,
            hasAppToken = hasAppToken,
            hasDevices = hasDevices,
            hotDevice = hot,
            coldDevice = cold,
            accountCount = accountCount,
            recentDevices = account.rememberedDevices(),
            usage = usageFrom(scoreJson)
        )
    }

    private fun usageFrom(scoreJson: JSONObject?): WaterUsageUiState {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return WaterUsageUiState()
        val records = scoreJson.optJSONArray("data") ?: return WaterUsageUiState()
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        var todaySpend = 0
        var monthSpend = 0
        var yearSpend = 0
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val time = record.optLong("ctime", 0L)
            if (time <= 0L) continue
            val data = record.optJSONObject("data")
            val amount = readSpentScore(record, data)
            if (amount <= 0 || !isConsumption(record, data)) continue
            if (time >= today) todaySpend += amount
            val recordDate = Calendar.getInstance().apply { timeInMillis = time }
            if (recordDate.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                yearSpend += amount
                if (recordDate.get(Calendar.MONTH) == now.get(Calendar.MONTH)) monthSpend += amount
            }
        }
        return WaterUsageUiState(
            todayCostText = moneyText(todaySpend),
            monthCostText = moneyText(monthSpend),
            yearCostText = moneyText(yearSpend),
            todayWaterText = waterText(todaySpend),
            monthWaterText = waterText(monthSpend),
            yearWaterText = waterText(yearSpend)
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
        val textKeys = arrayOf("msg", "direction", "typeName", "scene", "bizType", "name", "title", "desc", "remark", "memo")
        val text = buildString {
            textKeys.forEach { key -> append(record.optString(key, "")); append(' ') }
            if (data != null) textKeys.forEach { key -> append(data.optString(key, "")); append(' ') }
        }.lowercase(Locale.ROOT)
        return listOf("消费", "使用", "扣", "支出", "兑换", "water", "pay", "cost", "consume", "decrease").any(text::contains)
    }

    private fun moneyText(score: Int): String = "¥${String.format(Locale.CHINA, "%.2f", score / 1000.0)}"

    private fun waterText(score: Int): String {
        val millilitres = score * 500 / 160
        return when {
            millilitres >= 1000 -> String.format(Locale.CHINA, "%.1f L", millilitres / 1000.0)
            else -> "$millilitres ml"
        }
    }

    fun accountsFrom(accounts: List<Account>, currentPhone: String?): List<DashboardAccountUiState> =
        accounts.map { account ->
            DashboardAccountUiState(
                phone = account.phone.orEmpty(),
                title = account.name?.takeIf { it.isNotBlank() } ?: account.phone?.takeIf { it.isNotBlank() } ?: "未命名账号",
                subtitle = when {
                    account.hasToken() && account.hasAppToken() -> "日常服务和设备控制均可使用"
                    account.hasToken() -> "可查看积分并完成日常任务"
                    account.hasAppToken() -> "可使用设备控制"
                    else -> "需要重新登录或补充登录信息"
                },
                tokenSummary = buildList {
                    add(if (account.hasToken()) "日常服务已开通" else "日常服务待开通")
                    add(if (account.hasAppToken()) "设备控制已开通" else "设备控制待开通")
                }.joinToString(" · "),
                deviceSummary = if (account.hasDevices()) "设备已分配" else "未分配设备",
                isCurrent = account.phone == currentPhone
            )
        }
}
