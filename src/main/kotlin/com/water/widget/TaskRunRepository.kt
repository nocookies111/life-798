package com.water.widget

import android.os.Handler
import android.os.Looper
import com.water.widget.ui.TaskLogFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 任务批次的进程级权威状态。
 *
 * 所有写入最终都归一到主线程，Activity、前台服务和 Compose 只观察同一个 StateFlow，
 * 避免页面重建或离开前台后丢失执行状态。
 */
enum class TaskLanePhase {
    PREPARING,
    LOADING,
    SIGNING_IN,
    LOADING_MISSIONS,
    RUNNING_MISSION,
    WAITING,
    COMPLETED,
    FAILED
}

enum class TaskRunResult {
    NONE,
    COMPLETED,
    PARTIAL,
    CANCELLED,
    FAILED
}

data class TaskLaneState(
    val laneId: Int,
    val laneCount: Int,
    val accountIndex: Int = 0,
    val accountCount: Int,
    val accountLabel: String = "",
    val currentTask: String = "准备运行",
    val phase: TaskLanePhase = TaskLanePhase.PREPARING,
    val gained: Int = 0
)

data class TaskRunState(
    val running: Boolean = false,
    val totalGained: Int = 0,
    val logs: List<String> = emptyList(),
    val lanes: List<TaskLaneState> = emptyList(),
    val result: TaskRunResult = TaskRunResult.NONE
)

/** 纯状态归约器，Android 之外的 JVM 测试可直接验证任务状态与日志聚合。 */
object TaskRunReducer {
    fun start(lanes: List<TaskLaneState>): TaskRunState = TaskRunState(
        running = true,
        lanes = lanes,
        result = TaskRunResult.NONE
    )

    fun appendLog(state: TaskRunState, rawLine: String): TaskRunState {
        val logs = TaskLogFormatter.append(state.logs, rawLine)
        return if (logs === state.logs) state else state.copy(logs = logs)
    }

    fun updateLane(
        state: TaskRunState,
        laneId: Int,
        accountIndex: Int? = null,
        accountLabel: String? = null,
        currentTask: String? = null,
        phase: TaskLanePhase? = null
    ): TaskRunState = state.copy(
        lanes = state.lanes.map { lane ->
            if (lane.laneId != laneId) {
                lane
            } else {
                lane.copy(
                    accountIndex = accountIndex ?: lane.accountIndex,
                    accountLabel = accountLabel ?: lane.accountLabel,
                    currentTask = currentTask ?: lane.currentTask,
                    phase = phase ?: lane.phase
                )
            }
        }
    )

    fun addPoints(state: TaskRunState, laneId: Int, points: Int): TaskRunState {
        if (points <= 0) return state
        return state.copy(
            totalGained = state.totalGained + points,
            lanes = state.lanes.map { lane ->
                if (lane.laneId == laneId) lane.copy(gained = lane.gained + points) else lane
            }
        )
    }

    fun completeLane(state: TaskRunState, laneId: Int): TaskRunState =
        updateLane(
            state = state,
            laneId = laneId,
            currentTask = "本通道已完成",
            phase = TaskLanePhase.COMPLETED
        )

    fun finish(state: TaskRunState, partial: Boolean = false): TaskRunState = state.copy(
        running = false,
        lanes = state.lanes.map {
            it.copy(currentTask = "已完成", phase = TaskLanePhase.COMPLETED)
        },
        result = if (partial) TaskRunResult.PARTIAL else TaskRunResult.COMPLETED
    )

    fun cancel(state: TaskRunState, message: String): TaskRunState {
        val withMessage = appendLog(state, message)
        return withMessage.copy(
            running = false,
            result = TaskRunResult.CANCELLED
        )
    }

    fun fail(state: TaskRunState, message: String): TaskRunState {
        val withMessage = appendLog(state, message)
        return withMessage.copy(
            running = false,
            lanes = withMessage.lanes.map {
                if (it.phase == TaskLanePhase.COMPLETED) it
                else it.copy(currentTask = "运行失败", phase = TaskLanePhase.FAILED)
            },
            result = TaskRunResult.FAILED
        )
    }
}

object TaskRunRepository {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(TaskRunState())
    val state: StateFlow<TaskRunState> = _state.asStateFlow()

    fun start(lanes: List<TaskLaneState>) = mutate { TaskRunReducer.start(lanes) }

    fun appendLog(line: String) = mutate { TaskRunReducer.appendLog(it, line) }

    fun updateLane(
        laneId: Int,
        accountIndex: Int? = null,
        accountLabel: String? = null,
        currentTask: String? = null,
        phase: TaskLanePhase? = null
    ) = mutate {
        TaskRunReducer.updateLane(
            state = it,
            laneId = laneId,
            accountIndex = accountIndex,
            accountLabel = accountLabel,
            currentTask = currentTask,
            phase = phase
        )
    }

    fun addPoints(laneId: Int, points: Int) = mutate {
        TaskRunReducer.addPoints(it, laneId, points)
    }

    fun completeLane(laneId: Int) = mutate { TaskRunReducer.completeLane(it, laneId) }

    fun finish(partial: Boolean = false) = mutate { TaskRunReducer.finish(it, partial) }

    fun cancel(message: String) = mutate { TaskRunReducer.cancel(it, message) }

    fun fail(message: String) = mutate { TaskRunReducer.fail(it, message) }

    private fun mutate(reducer: (TaskRunState) -> TaskRunState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _state.value = reducer(_state.value)
        } else {
            mainHandler.post { _state.value = reducer(_state.value) }
        }
    }
}
