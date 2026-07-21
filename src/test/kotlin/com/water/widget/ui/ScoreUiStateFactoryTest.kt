package com.water.widget.ui

import com.water.widget.Account
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreUiStateFactoryTest {
    @Test
    fun `从接口响应生成账号积分摘要`() {
        val account = Account("示例账户").apply { name = "饮水账号" }
        val missionJson = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONObject().put(
                    "accScoreRsp",
                    JSONObject().put("validScore", 2380).put("totalScore", 4200)
                )
            )
        val scoreJson = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(JSONObject().put("ctime", 1783333000000).put("score", 30).put("data", JSONObject().put("adId", "video")))
                    .put(JSONObject().put("ctime", 1783332000000).put("score", -100).put("data", JSONObject().put("adId", "water")))
            )

        val state = ScoreUiStateFactory.from(account, missionJson, scoreJson, null)

        assertTrue(state.isReady)
        assertEquals("饮水账号", state.accountName)
        assertEquals(2380, state.validScore)
        assertEquals(4200, state.totalScore)
        assertEquals("≈2.38元", state.validMoneyText)
        assertEquals(2, state.logs.size)
        assertEquals("+30", state.logs[0].scoreText)
        assertEquals("-100", state.logs[1].scoreText)
    }

    @Test
    fun `接口失败时生成可展示错误状态`() {
        val account = Account("示例账户")

        val state = ScoreUiStateFactory.from(account, null, null, "Token 已过期")

        assertFalse(state.isReady)
        assertEquals("示例账户", state.accountName)
        assertEquals("Token 已过期", state.message)
    }

    @Test
    fun `积分流水兼容嵌套分数字段与 HTML 实体账号名`() {
        val account = Account("amp;示例账户")
        val missionJson = JSONObject()
            .put("code", 0)
            .put("data", JSONObject().put("accScoreRsp", JSONObject().put("validScore", 1000)))
        val scoreJson = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("ctime", 1783333000000)
                        .put("data", JSONObject().put("score", 15).put("title", "每日签到"))
                )
            )

        val state = ScoreUiStateFactory.from(account, missionJson, scoreJson, null)

        assertEquals("示例账户", state.accountName)
        assertEquals("+15", state.logs[0].scoreText)
        assertEquals("每日签到", state.logs[0].title)
    }

    @Test
    fun `积分流水通过消费语义识别支出`() {
        val account = Account("示例账户")
        val missionJson = JSONObject()
            .put("code", 0)
            .put("data", JSONObject().put("accScoreRsp", JSONObject().put("validScore", 1000)))
        val scoreJson = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(JSONObject().put("ctime", 1783333000000).put("score", 50).put("title", "每日签到"))
                    .put(JSONObject().put("ctime", 1783333000000).put("score", 200).put("title", "水费消费"))
            )

        val state = ScoreUiStateFactory.from(account, missionJson, scoreJson, null)

        assertTrue(state.logs[0].isIncome)
        assertEquals("+50", state.logs[0].scoreText)
        assertFalse(state.logs[1].isIncome)
        assertEquals("-200", state.logs[1].scoreText)
    }

    @Test
    fun `真实接口结构中 type107 和 spend 识别为消费`() {
        val account = Account("示例账户")
        val missionJson = JSONObject()
            .put("code", 0)
            .put("data", JSONObject().put("accScoreRsp", JSONObject().put("validScore", 1000)))
        val scoreJson = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("ctime", 1783349380753)
                            .put("msg", "用户消费积分")
                            .put("src", 105)
                            .put("type", 107)
                            .put("data", JSONObject().put("score", "200").put("spend", "200").put("type", 107))
                    )
                    .put(
                        JSONObject()
                            .put("ctime", 1783268961199)
                            .put("msg", "慧积分发放")
                            .put("src", 101)
                            .put("type", 101)
                            .put("data", JSONObject().put("score", "10").put("adName", "观看广告，获取积分").put("type", 101))
                    )
            )

        val state = ScoreUiStateFactory.from(account, missionJson, scoreJson, null)

        assertFalse(state.logs[0].isIncome)
        assertEquals("-200", state.logs[0].scoreText)
        assertEquals("用户消费积分", state.logs[0].title)
        assertTrue(state.logs[1].isIncome)
        assertEquals("+10", state.logs[1].scoreText)
        assertEquals("慧积分发放", state.logs[1].title)
    }
}
