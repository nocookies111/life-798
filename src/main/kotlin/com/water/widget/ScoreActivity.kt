package com.water.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.water.widget.ui.ScoreDashboardScreen
import com.water.widget.ui.ScoreUiState
import com.water.widget.ui.ScoreUiStateFactory
import com.water.widget.ui.WaterTheme

/**
 * 积分看板：按账号展示当前积分与最近积分流水。
 * 先复用现有服务端接口，后续可以继续扩展筛选、搜索与明细页。
 */
class ScoreActivity : ComponentActivity() {
    private var states: List<ScoreUiState> = emptyList()
    private var refreshing = false
    private var refreshGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
        refreshScores()
    }

    private fun refreshScores() {
        val generation = ++refreshGeneration
        val accounts = AccountStore.list(this).filter { it.hasToken() || it.hasAppToken() }
        refreshing = accounts.isNotEmpty()
        states = accounts.map { ScoreUiStateFactory.loading(it) }
        render()

        if (accounts.isEmpty()) {
            refreshing = false
            render()
            return
        }
        accounts.forEachIndexed { index, account -> loadAccountScore(generation, index, account) }
    }

    private fun loadAccountScore(generation: Int, index: Int, account: Account) {
        val token = account.token?.takeIf { it.isNotBlank() } ?: account.appToken
        if (token.isNullOrBlank()) {
            updateState(generation, index, ScoreUiStateFactory.from(account, null, null, "缺少可用 Token"))
            return
        }

        IlifeApi.missionLstWithToken(token) { missionJson, missionErr ->
            IlifeApi.scoreLstWithToken(token) { scoreJson, scoreErr ->
                runOnUiThread {
                    val error = missionErr ?: scoreErr
                    updateState(generation, index, ScoreUiStateFactory.from(account, missionJson, scoreJson, error))
                }
            }
        }
    }

    private fun updateState(generation: Int, index: Int, state: ScoreUiState) {
        if (generation != refreshGeneration) return
        states = states.toMutableList().also { list ->
            if (index in list.indices) list[index] = state
        }
        refreshing = states.any { !it.isReady && it.message == "正在刷新积分..." }
        render()
    }

    private fun render() {
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                ScoreDashboardScreen(states = states, refreshing = refreshing, onRefresh = {
                    Toast.makeText(this, "正在刷新积分", Toast.LENGTH_SHORT).show()
                    refreshScores()
                })
            }
        }
    }
}
