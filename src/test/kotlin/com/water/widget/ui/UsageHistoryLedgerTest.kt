package com.water.widget.ui

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class UsageHistoryLedgerTest {
    @Test
    fun `重复同步同一流水不会重复累计`() {
        val now = Calendar.getInstance()
        val response = response(now.timeInMillis, 160, "order-1")
        val ledger = UsageHistoryLedger()

        assertTrue(ledger.merge(response))
        assertFalse(ledger.merge(response))
        assertEquals("¥0.16", ledger.toUiState(now).todayCostText)
    }

    @Test
    fun `服务端后续窗口缩短时本地历史仍保留`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lastMonth = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val ledger = UsageHistoryLedger()
        ledger.merge(response(lastMonth.timeInMillis, 320, "old-order"))
        ledger.merge(response(now.timeInMillis, 160, "new-order"))

        val restored = UsageHistoryLedger.fromJson(ledger.toJson())
        val state = restored.toUiState(now)

        assertEquals("¥0.16", state.monthCostText)
        assertEquals("¥0.48", state.yearCostText)
    }

    private fun response(time: Long, spend: Int, id: String) = JSONObject()
        .put("code", 0)
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", id)
                    .put("ctime", time)
                    .put("type", 107)
                    .put("data", JSONObject().put("spend", spend))
            )
        )
}
