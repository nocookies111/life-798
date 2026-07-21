package com.water.widget.ui

import com.water.widget.Account
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScoreUiState(
    val accountName: String,
    val validScore: Int,
    val totalScore: Int,
    val validMoneyText: String,
    val totalMoneyText: String,
    val logs: List<ScoreLogUiState>,
    val isReady: Boolean,
    val message: String
)

data class ScoreLogUiState(
    val timeText: String,
    val scoreText: String,
    val title: String,
    val isIncome: Boolean
)

object ScoreUiStateFactory {
    fun loading(account: Account): ScoreUiState = empty(account, "正在刷新积分...")

    fun from(
        account: Account,
        missionJson: JSONObject?,
        scoreJson: JSONObject?,
        error: String?
    ): ScoreUiState {
        if (error != null) return empty(account, error)
        if (missionJson == null || missionJson.optInt("code", -999) != 0) {
            return empty(account, missionJson?.optString("msg")?.takeIf { it.isNotBlank() } ?: "积分信息获取失败")
        }

        val accScore = missionJson.optJSONObject("data")?.optJSONObject("accScoreRsp")
        val validScore = accScore?.optInt("validScore", 0) ?: 0
        val totalScore = accScore?.optInt("totalScore", validScore) ?: validScore
        val logs = parseLogs(scoreJson)

        return ScoreUiState(
            accountName = displayName(account),
            validScore = validScore,
            totalScore = totalScore,
            validMoneyText = moneyText(validScore),
            totalMoneyText = moneyText(totalScore),
            logs = logs,
            isReady = true,
            message = if (logs.isEmpty()) "暂无积分使用日志" else "最近 ${logs.size} 条积分记录"
        )
    }

    private fun empty(account: Account, message: String): ScoreUiState = ScoreUiState(
        accountName = displayName(account),
        validScore = 0,
        totalScore = 0,
        validMoneyText = "≈0.00元",
        totalMoneyText = "≈0.00元",
        logs = emptyList(),
        isReady = false,
        message = message
    )

    private fun parseLogs(scoreJson: JSONObject?): List<ScoreLogUiState> {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return emptyList()
        val arr = scoreJson.optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<ScoreLogUiState>()
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        for (i in 0 until minOf(arr.length(), 20)) {
            val item = arr.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data")
            val score = readScore(item, data)
            val isIncome = readIncome(item, data, score)
            val signedScore = if (score == 0) 0 else if (isIncome) kotlin.math.abs(score) else -kotlin.math.abs(score)
            val title = readTitle(item, data, signedScore)
            out += ScoreLogUiState(
                timeText = fmt.format(Date(item.optLong("ctime", 0L))),
                scoreText = if (signedScore > 0) "+$signedScore" else signedScore.toString(),
                title = title,
                isIncome = isIncome
            )
        }
        return out
    }

    private fun readScore(item: JSONObject, data: JSONObject?): Int {
        val keys = arrayOf("score", "spend", "changeScore", "change_score", "amount", "value", "num", "points")
        for (key in keys) {
            if (item.has(key)) return item.optInt(key, 0)
        }
        if (data != null) {
            for (key in keys) {
                if (data.has(key)) return data.optInt(key, 0)
            }
        }
        return 0
    }

    private fun readIncome(item: JSONObject, data: JSONObject?, score: Int): Boolean {
        val type = item.optInt("type", data?.optInt("type", Int.MIN_VALUE) ?: Int.MIN_VALUE)
        val src = item.optInt("src", Int.MIN_VALUE)
        if (type == 107 || data?.has("spend") == true) return false
        if (type == 101 || src == 101) return true

        val explicit = readDirectionText(item, data)
        if (explicit != null) {
            val text = explicit.lowercase(Locale.ROOT)
            if (listOf("消费", "使用", "扣", "支出", "兑换", "water", "pay", "cost", "consume", "decrease").any { text.contains(it) }) {
                return false
            }
            if (listOf("获得", "收入", "增加", "签到", "任务", "广告", "gain", "income", "increase", "add").any { text.contains(it) }) {
                return true
            }
        }
        return score >= 0
    }

    private fun readDirectionText(item: JSONObject, data: JSONObject?): String? {
        val keys = arrayOf("msg", "direction", "typeName", "scene", "bizType", "name", "title", "desc", "remark", "memo")
        for (key in keys) item.optString(key, "").takeIf { it.isNotBlank() }?.let { return it }
        if (data != null) for (key in keys + "adId") data.optString(key, "").takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun readTitle(item: JSONObject, data: JSONObject?, score: Int): String {
        val keys = arrayOf("msg", "name", "title", "desc", "remark", "memo", "typeName")
        for (key in keys) {
            val value = item.optString(key, "").takeIf { it.isNotBlank() }
            if (value != null) return cleanText(value)
        }
        if (data != null) {
            for (key in keys + arrayOf("adName", "adId")) {
                val value = data.optString(key, "").takeIf { it.isNotBlank() }
                if (value != null) return cleanText(value)
            }
        }
        return if (score >= 0) "积分获得" else "积分使用"
    }

    private fun displayName(account: Account): String = account.name?.takeIf { it.isNotBlank() }?.let { cleanText(it) }
        ?: account.phone?.takeIf { it.isNotBlank() }?.let { cleanText(it) }
        ?: "未命名账号"

    private fun cleanText(raw: String): String = raw
        .replace("&amp;", "&")
        .replace("amp;", "")
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        .trim()

    private fun moneyText(score: Int): String = "≈${String.format(Locale.CHINA, "%.2f", score / 1000.0)}元"
}
