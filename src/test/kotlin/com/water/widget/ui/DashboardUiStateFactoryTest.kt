package com.water.widget.ui

import com.water.widget.Account
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateFactoryTest {
    @Test
    fun `聚合状态承载主页账号数据`() {
        val account = Account("账号A").apply { token = "token" }
        val summary = DashboardUiStateFactory.from(account, 1, 1200)
        val accounts = DashboardUiStateFactory.accountsFrom(listOf(account), account.phone)
        val state = DashboardUiState(summary, accounts)

        assertEquals("1200", state.summary.scoreTitle)
        assertEquals(1, state.accounts.size)
        assertTrue(state.accounts.single().isCurrent)
    }

    @Test
    fun `未登录时展示空状态并禁用设备能力`() {
        val state = DashboardUiStateFactory.from(null, 0)

        assertEquals("未登录", state.accountTitle)
        assertEquals("登录后可完成积分任务、同步设备并查看用水统计", state.accountSubtitle)
        assertEquals("--", state.scoreTitle)
        assertEquals("暂无积分", state.scoreSubtitle)
        assertFalse(state.hasAccount)
        assertFalse(state.hasAppToken)
        assertFalse(state.hasDevices)
        assertEquals("未分配", state.hotDevice)
        assertEquals("未分配", state.coldDevice)
        assertEquals(0, state.accountCount)
    }

    @Test
    fun `已登录且只有一个设备时冷热水共用同一设备`() {
        val account = Account("示例账户").apply {
            name = "测试账户"
            token = "token"
            appToken = "app-token"
            hotDid = "device-001"
            coldDid = "device-001"
        }

        val state = DashboardUiStateFactory.from(account, 2, 2380)

        assertEquals("测试账户", state.accountTitle)
        assertEquals("2 个账户 · 设备控制已开通", state.accountSubtitle)
        assertEquals("2380", state.scoreTitle)
        assertEquals("≈2.38元可用", state.scoreSubtitle)
        assertTrue(state.hasAccount)
        assertTrue(state.hasAppToken)
        assertTrue(state.hasDevices)
        assertEquals("device-001", state.hotDevice)
        assertEquals("共用热水设备", state.coldDevice)
        assertEquals(listOf("device-001"), state.recentDevices)
        assertEquals(2, state.accountCount)
    }

    @Test
    fun `消费流水可按今日本月本年汇总并换算预计饮水量`() {
        val account = Account("示例账户").apply { token = "token" }
        val now = java.util.Calendar.getInstance().timeInMillis
        val earlierThisYear = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -1) }.timeInMillis
        val scoreJson = JSONObject()
            .put("code", 0)
            .put("data", JSONArray()
                .put(
                    JSONObject()
                        .put("ctime", now)
                        .put("type", 107)
                        .put("msg", "饮水消费")
                        .put("data", JSONObject().put("spend", 250))
                )
                .put(
                    JSONObject()
                        .put("ctime", earlierThisYear)
                        .put("type", 107)
                        .put("msg", "饮水消费")
                        .put("data", JSONObject().put("spend", 160))
                )
            )

        val state = DashboardUiStateFactory.from(account, 1, 750, scoreJson)

        assertEquals("¥0.25", state.usage.todayCostText)
        assertEquals("¥0.25", state.usage.monthCostText)
        assertEquals("¥0.41", state.usage.yearCostText)
        assertEquals("781 ml", state.usage.todayWaterText)
        assertEquals("781 ml", state.usage.monthWaterText)
        assertEquals("1.3 L", state.usage.yearWaterText)
    }

    @Test
    fun `账号列表展示每个账号的平台与设备状态`() {
        val a = Account("账号A").apply { token = "token"; appToken = "app"; hotDid = "hot" }
        val b = Account("账号B").apply { appToken = "app" }

        val states = DashboardUiStateFactory.accountsFrom(listOf(a, b), "账号A")

        assertEquals(2, states.size)
        assertTrue(states[0].isCurrent)
        assertEquals("日常服务和设备控制均可使用", states[0].subtitle)
        assertEquals("日常服务已开通 · 设备控制已开通", states[0].tokenSummary)
        assertEquals("设备已分配", states[0].deviceSummary)
        assertFalse(states[1].isCurrent)
        assertEquals("可使用设备控制", states[1].subtitle)
    }
}
