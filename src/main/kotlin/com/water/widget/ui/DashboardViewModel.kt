package com.water.widget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.water.widget.Account
import com.water.widget.AccountStore
import com.water.widget.TaskRunRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val DEFAULT_TASK_LOGS = emptyList<String>()

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
    private var taskRun = TaskRunRepository.state.value

    init {
        reloadAccounts()
        viewModelScope.launch {
            TaskRunRepository.state.collect { runState ->
                taskRun = runState
                publish()
            }
        }
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

    private fun publish() {
        val accounts = AccountStore.list(storeContext)
        val current = AccountStore.getCurrent(storeContext)
        val usage = current?.let { account ->
            val accountKey = account.phone?.takeIf(String::isNotBlank)
                ?: account.uid?.takeIf(String::isNotBlank)
                ?: return@let WaterUsageUiState()
            UsageHistoryStore.mergeAndRead(storeContext, accountKey, currentScoreLogs)
        }
        _uiState.update {
            DashboardUiState(
                summary = DashboardUiStateFactory.from(
                    current,
                    accounts.size,
                    currentScore,
                    currentScoreLogs,
                    usageOverride = usage
                ),
                accounts = DashboardUiStateFactory.accountsFrom(accounts, current?.phone),
                tasks = TaskUiStateFactory.from(
                    accounts,
                    taskRun.running,
                    taskRun.totalGained,
                    taskRun.logs
                )
            )
        }
    }

}
