package com.water.widget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.water.widget.Account
import com.water.widget.AccountStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val DEFAULT_TASK_LOGS = listOf("任务策略已就绪：签到优先、跳过无积分/借贷任务、频率限制自动等待重试。")

data class DashboardUiState(
    val summary: DashboardSummaryUiState = DashboardUiStateFactory.from(null, 0),
    val accounts: List<DashboardAccountUiState> = emptyList(),
    val tasks: TaskUiState = TaskUiStateFactory.from(emptyList(), false, 0, DEFAULT_TASK_LOGS)
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val storeContext = application.applicationContext
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var currentScore: Int? = null
    private var currentScoreLogs: JSONObject? = null
    private var taskRunning = false
    private var taskGained = 0
    private var taskLogs = DEFAULT_TASK_LOGS

    init {
        reloadAccounts()
    }

    fun reloadAccounts(resetScore: Boolean = false) {
        if (resetScore) {
            currentScore = null
            currentScoreLogs = null
        }
        publish()
    }

    fun setCurrentScore(score: Int?) {
        currentScore = score
        publish()
    }

    fun setCurrentScoreData(score: Int?, logs: JSONObject?) {
        currentScore = score
        currentScoreLogs = logs
        publish()
    }

    fun selectAccount(phone: String): Account? {
        val account = AccountStore.get(storeContext, phone) ?: return null
        AccountStore.setCurrent(storeContext, account.phone)
        currentScore = null
        currentScoreLogs = null
        publish()
        return account
    }

    fun beginTasks(): List<Account>? {
        if (taskRunning) return null
        val accounts = AccountStore.list(storeContext).filter { it.hasToken() }
        if (accounts.isEmpty()) return emptyList()
        taskRunning = true
        taskGained = 0
        taskLogs = emptyList()
        publish()
        return accounts
    }

    fun appendTaskLog(line: String) {
        taskLogs = (taskLogs + line).takeLast(160)
        publish()
    }

    fun addTaskGained(gained: Int) {
        taskGained += gained
        publish()
    }

    fun finishTasks() {
        taskRunning = false
        publish()
    }

    fun cancelTasks() {
        if (!taskRunning) return
        taskRunning = false
        taskLogs = (taskLogs + "任务已因页面关闭而中止，可重新运行。").takeLast(160)
        publish()
    }

    val isTaskRunning: Boolean
        get() = taskRunning

    val totalTaskGained: Int
        get() = taskGained

    private fun publish() {
        val accounts = AccountStore.list(storeContext)
        val current = AccountStore.getCurrent(storeContext)
        _uiState.update {
            DashboardUiState(
                summary = DashboardUiStateFactory.from(current, accounts.size, currentScore, currentScoreLogs),
                accounts = DashboardUiStateFactory.accountsFrom(accounts, current?.phone),
                tasks = TaskUiStateFactory.from(accounts, taskRunning, taskGained, taskLogs)
            )
        }
    }

}
