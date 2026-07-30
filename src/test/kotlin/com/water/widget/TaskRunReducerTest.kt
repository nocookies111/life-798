package com.water.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRunReducerTest {
    private val lanes = listOf(
        TaskLaneState(laneId = 1, laneCount = 2, accountCount = 1),
        TaskLaneState(laneId = 2, laneCount = 2, accountCount = 1)
    )

    @Test
    fun accumulatesPointsPerLaneAndForWholeRun() {
        var state = TaskRunReducer.start(lanes)
        state = TaskRunReducer.addPoints(state, laneId = 1, points = 5)
        state = TaskRunReducer.addPoints(state, laneId = 2, points = 10)

        assertEquals(15, state.totalGained)
        assertEquals(5, state.lanes.first { it.laneId == 1 }.gained)
        assertEquals(10, state.lanes.first { it.laneId == 2 }.gained)
    }

    @Test
    fun preservesV511AggregationAcrossAccounts() {
        var state = TaskRunReducer.start(lanes)
        state = TaskRunReducer.appendLog(state, "[账号甲] 签到成功 +5分")
        state = TaskRunReducer.appendLog(state, "[账号乙] 签到成功 +5分")

        assertEquals(listOf("签到成功 +10分（5分 × 2）"), state.logs)
    }

    @Test
    fun tracksStructuredLaneTaskAndCompletion() {
        var state = TaskRunReducer.start(lanes)
        state = TaskRunReducer.updateLane(
            state = state,
            laneId = 2,
            accountIndex = 1,
            accountLabel = "账号乙",
            currentTask = "[APP] 浏览任务",
            phase = TaskLanePhase.RUNNING_MISSION
        )
        state = TaskRunReducer.completeLane(state, laneId = 2)

        val lane = state.lanes.first { it.laneId == 2 }
        assertEquals("账号乙", lane.accountLabel)
        assertEquals(TaskLanePhase.COMPLETED, lane.phase)
        assertEquals("本通道已完成", lane.currentTask)
    }

    @Test
    fun cancelRetainsProgressAndMakesRunRestartable() {
        var state = TaskRunReducer.start(lanes)
        state = TaskRunReducer.addPoints(state, laneId = 1, points = 5)
        state = TaskRunReducer.cancel(state, "任务达到系统运行时限，已安全停止。")

        assertFalse(state.running)
        assertEquals(5, state.totalGained)
        assertEquals(TaskRunResult.CANCELLED, state.result)
        assertTrue(state.logs.last().contains("系统运行时限"))
    }

    @Test
    fun partialFinishRetainsPointsAndMarksResult() {
        var state = TaskRunReducer.start(lanes)
        state = TaskRunReducer.addPoints(state, laneId = 1, points = 15)
        state = TaskRunReducer.finish(state, partial = true)

        assertFalse(state.running)
        assertEquals(15, state.totalGained)
        assertEquals(TaskRunResult.PARTIAL, state.result)
    }

    @Test
    fun leaseConflictFailureIsVisibleAndRestartable() {
        val message = "上一次任务请求仍在收尾，请约半分钟后重试。"
        val state = TaskRunReducer.fail(TaskRunReducer.start(lanes), message)

        assertFalse(state.running)
        assertEquals(TaskRunResult.FAILED, state.result)
        assertEquals(message, state.logs.last())
        assertTrue(state.lanes.all { it.phase == TaskLanePhase.FAILED })
    }
}
