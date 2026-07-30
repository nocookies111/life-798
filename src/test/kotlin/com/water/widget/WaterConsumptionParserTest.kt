package com.water.widget

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterConsumptionParserTest {
    @Test
    fun readsLatestConsumptionForCurrentDevice() {
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("ctime", 1_800_000_001_000L)
                            .put("type", 107)
                            .put("spend", 80)
                            .put("data", JSONObject().put("did", "other-device"))
                    )
                    .put(
                        JSONObject()
                            .put("ctime", 1_800_000_002_000L)
                            .put("type", 107)
                            .put("spend", 160)
                            .put("data", JSONObject().put("did", "selected-device"))
                    )
            )

        val result = WaterConsumptionParser.latestSince(
            json,
            sinceMillis = 1_800_000_000_000L,
            expectedDeviceId = "selected-device"
        )

        assertEquals(160, result?.spentScore)
        assertEquals("¥0.16", result?.moneyText)
        assertEquals("500 ml", result?.estimatedWaterText)
    }

    @Test
    fun supportsSecondBasedTimestampsForVerifiedConsumption() {
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("ctime", 1_800_000_005L)
                        .put("type", 107)
                        .put("score", -320)
                        .put("title", "饮水消费")
                )
            )

        val result = WaterConsumptionParser.latestSince(json, 1_800_000_000_000L)

        assertEquals(320, result?.spentScore)
        assertEquals("1.0 L", result?.estimatedWaterText)
    }

    @Test
    fun ignoresIncomeAndOldConsumption() {
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("ctime", 1_700_000_000_000L)
                            .put("type", 107)
                            .put("spend", 160)
                    )
                    .put(
                        JSONObject()
                            .put("ctime", 1_800_000_001_000L)
                            .put("score", 10)
                            .put("title", "任务奖励")
                    )
            )

        assertNull(WaterConsumptionParser.latestSince(json, 1_800_000_000_000L))
    }

    @Test
    fun readsActualPaymentFromDeviceBill() {
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "recharge")
                            .put("type", 2)
                            .put("ctime", 1_800_000_002_000L)
                            .put("payment", 10.0)
                    )
                    .put(
                        JSONObject()
                            .put("id", "water")
                            .put("type", 91)
                            .put("ctime", 1_800_000_003_000L)
                            .put("utime", 1_800_000_004_000L)
                            .put("status", 3)
                            .put("payment", 0.16)
                    )
            )

        val result = WaterBillParser.latestSince(json, 1_800_000_000_000L)

        assertEquals("¥0.16", result?.moneyText)
        assertEquals("500 ml", result?.estimatedWaterText)
    }

    @Test
    fun onlyAcceptsNewCompletedBill() {
        val oldId = "old-water"
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", oldId)
                            .put("type", 91)
                            .put("ctime", 1_800_000_001_000L)
                            .put("utime", 1_800_000_010_000L)
                            .put("status", 3)
                            .put("payment", 0.16)
                    )
                    .put(
                        JSONObject()
                            .put("id", "pending-water")
                            .put("type", 91)
                            .put("ctime", 1_800_000_011_000L)
                            .put("status", 2)
                            .put("payment", 0.32)
                    )
                    .put(
                        JSONObject()
                            .put("id", "new-water")
                            .put("type", 91)
                            .put("ctime", 1_800_000_012_000L)
                            .put("status", 3)
                            .put("payment", 0.48)
                    )
            )

        val baseline = setOf("bill:id:$oldId")
        val result = WaterBillParser.latestSince(
            json,
            sinceMillis = 1_800_000_000_000L,
            excludedRecordKeys = baseline
        )

        assertEquals("¥0.48", result?.moneyText)
    }

    @Test
    fun baselineKeepsOldPendingBillExcludedAfterCompletion() {
        val pending = JSONObject()
            .put("id", "same-order")
            .put("type", 91)
            .put("ctime", 1_800_000_001_000L)
            .put("status", 2)
            .put("payment", 0.16)
        val before = JSONObject().put("code", 0).put("data", JSONArray().put(pending))
        val baseline = WaterBillParser.recordKeys(before)

        pending.put("status", 3).put("utime", 1_800_000_010_000L)
        val after = JSONObject().put("code", 0).put("data", JSONArray().put(pending))

        assertNull(
            WaterBillParser.latestSince(
                after,
                sinceMillis = 1_800_000_000_000L,
                excludedRecordKeys = baseline
            )
        )
    }

    @Test
    fun nestedSpendWinsOverZeroTopLevelScore() {
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("id", "score-water")
                        .put("ctime", 1_800_000_001_000L)
                        .put("score", 0)
                        .put("data", JSONObject().put("spend", 160))
                )
            )

        assertEquals(
            160,
            WaterConsumptionParser.latestSince(json, 1_800_000_000_000L)?.spentScore
        )
    }

    @Test
    fun taskRewardTextIsNotTreatedAsConsumption() {
        val json = JSONObject()
            .put("code", 0)
            .put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("ctime", 1_800_000_001_000L)
                        .put("score", 20)
                        .put("title", "使用支付宝完成任务")
                )
            )

        assertNull(WaterConsumptionParser.latestSince(json, 1_800_000_000_000L))
    }
}
