package com.water.widget

data class TaskNotificationText(
    val title: String,
    val text: String
)

/** 只负责把结构化任务状态压缩成通知栏可读的单行/双通道摘要。 */
object TaskNotificationTextFormatter {
    fun format(state: TaskRunState): TaskNotificationText {
        if (!state.running) {
            return when (state.result) {
                TaskRunResult.COMPLETED -> TaskNotificationText(
                    title = "积分任务已完成",
                    text = "本次共获得 ${state.totalGained} 分"
                )
                TaskRunResult.PARTIAL -> TaskNotificationText(
                    title = "积分任务已结束 · 部分未完成",
                    text = "本次已获得 ${state.totalGained} 分，可在运行记录中查看详情"
                )
                TaskRunResult.CANCELLED -> TaskNotificationText(
                    title = "积分任务已停止",
                    text = "停止前共获得 ${state.totalGained} 分"
                )
                TaskRunResult.FAILED -> TaskNotificationText(
                    title = "积分任务运行失败",
                    text = state.logs.lastOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?: "本次已获得 ${state.totalGained} 分"
                )
                TaskRunResult.NONE -> TaskNotificationText(
                    title = "积分任务",
                    text = "等待开始"
                )
            }
        }

        val active = state.lanes.filter {
            it.phase != TaskLanePhase.COMPLETED && it.phase != TaskLanePhase.FAILED
        }
        val detail = when {
            active.isEmpty() -> "正在收尾"
            active.size == 1 -> laneText(active.first(), includeLane = false)
            else -> {
                val shown = active.take(2).joinToString("；") { laneText(it, includeLane = true) }
                if (active.size > 2) "$shown；另有 ${active.size - 2} 个通道" else shown
            }
        }
        return TaskNotificationText(
            title = "积分任务运行中 · 已获得 ${state.totalGained} 分",
            text = detail
        )
    }

    private fun laneText(lane: TaskLaneState, includeLane: Boolean): String {
        val prefix = if (includeLane) "通道${lane.laneId}：" else ""
        val account = lane.accountLabel.takeIf(String::isNotBlank)?.let { "$it · " }.orEmpty()
        return "$prefix$account${lane.currentTask}"
    }
}
