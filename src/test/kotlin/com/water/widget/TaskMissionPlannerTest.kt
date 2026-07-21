package com.water.widget

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMissionPlannerTest {
    @Test
    fun `合并双平台任务时保留任务来源 token`() {
        val main = JSONArray()
            .put(JSONObject().put("refId", "A").put("name", "支付宝任务").put("score", 10).put("limit", 1))
        val app = JSONArray()
            .put(JSONObject().put("refId", "B").put("name", "App任务").put("score", 20).put("limit", 1))

        val merged = TaskMissionPlanner.merge(main, app)

        assertEquals(2, merged.size)
        assertEquals(TaskPlatform.MAIN, merged[0].platform)
        assertEquals(TaskPlatform.APP, merged[1].platform)
    }

    @Test
    fun `生成可执行任务时过滤无积分借贷和已完成任务`() {
        val missions = listOf(
            PlannedMission("A", "观看广告", 10, 2, TaskPlatform.MAIN),
            PlannedMission("B", "免费权益领取", 20, 1, TaskPlatform.MAIN),
            PlannedMission("C", "无积分", 0, 1, TaskPlatform.APP),
            PlannedMission("D", "已完成", 30, 1, TaskPlatform.APP)
        )

        val work = TaskMissionPlanner.buildWork(missions, mapOf("A" to 1, "D" to 1))

        assertEquals(1, work.size)
        assertEquals("A", work[0].refId)
        assertEquals(1, work[0].total)
        assertEquals(TaskPlatform.MAIN, work[0].platform)
    }

    @Test
    fun `任务数量过多时限制本轮最多三十次`() {
        val missions = listOf(PlannedMission("A", "广告", 1, 50, TaskPlatform.MAIN))

        val work = TaskMissionPlanner.buildWork(missions, emptyMap())

        assertEquals(30, work.size)
        assertTrue(work.all { it.refId == "A" })
    }
}
