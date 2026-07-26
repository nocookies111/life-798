package com.water.widget.ui

import com.water.widget.Account

data class TaskUiState(
    val totalAccounts: Int,
    val runnableAccounts: Int,
    val dualPlatformAccounts: Int,
    val running: Boolean,
    val totalGainedText: String,
    val canRun: Boolean,
    val summary: String,
    val logs: List<String>,
    val hasFailures: Boolean
)

object TaskUiStateFactory {
    fun from(accounts: List<Account>, running: Boolean, totalGained: Int, logs: List<String>): TaskUiState {
        val runnable = accounts.count { it.hasToken() }
        val dual = accounts.count { it.hasToken() && it.hasAppToken() }
        return TaskUiState(
            totalAccounts = accounts.size,
            runnableAccounts = runnable,
            dualPlatformAccounts = dual,
            running = running,
            totalGainedText = totalGained.toString(),
            canRun = !running && runnable > 0,
            summary = if (runnable == 0) "请先登录账号" else "$runnable 个账号可运行",
            logs = logs,
            hasFailures = logs.any { it.contains("❌") || it.contains("失败") || it.contains("错误") }
        )
    }
}
