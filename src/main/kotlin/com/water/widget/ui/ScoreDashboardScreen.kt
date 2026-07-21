package com.water.widget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val INITIAL_LOG_COUNT = 8
private const val LOG_PAGE_SIZE = 12

@Composable
fun ScoreDashboardScreen(
    states: List<ScoreUiState>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAvailableScore = states.filter { it.isReady }.sumOf { it.validScore }
    val readyAccountCount = states.count { it.isReady }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("积分看板", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Text("掌握账户余额与每一笔积分变动", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onRefresh, enabled = !refreshing, shape = RoundedCornerShape(16.dp)) {
                    Text(if (refreshing) "刷新中" else "刷新数据")
                }
            }
        }

        if (states.isNotEmpty()) {
            item {
                ScoreSummaryCard(
                    accountCount = states.size,
                    readyAccountCount = readyAccountCount,
                    totalAvailableScore = totalAvailableScore
                )
            }
        }

        if (states.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("暂无可查询账号", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("请先登录账号，完成后即可刷新并查看积分。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(states, key = { state -> state.accountName }) { state ->
                ScoreAccountCard(state)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ScoreSummaryCard(accountCount: Int, readyAccountCount: Int, totalAvailableScore: Int) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("积分概览", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("可用积分", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                    Text(totalAvailableScore.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    "${readyAccountCount}/${accountCount} 个账号已就绪",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.80f)
                )
            }
        }
    }
}

@Composable
private fun ScoreAccountCard(state: ScoreUiState) {
    var visibleLogCount by rememberSaveable(state.accountName, state.logs.size) {
        mutableIntStateOf(INITIAL_LOG_COUNT)
    }
    val visibleLogs = state.logs.take(visibleLogCount)
    val hasMoreLogs = visibleLogCount < state.logs.size

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(state.accountName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.isReady) state.message else "暂不可查询：${state.message}",
                        fontSize = 12.sp,
                        color = if (state.isReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.validScore.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("可用 · ${state.validMoneyText}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.isReady) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("累计积分", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("${state.totalScore} · ${state.totalMoneyText}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            if (visibleLogs.isNotEmpty()) {
                Text("最近记录", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    visibleLogs.forEachIndexed { index, log ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(log.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(log.timeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                log.scoreText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (log.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            } else if (state.isReady) {
                Text("暂时没有积分变动记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (hasMoreLogs) {
                OutlinedButton(
                    onClick = { visibleLogCount = (visibleLogCount + LOG_PAGE_SIZE).coerceAtMost(state.logs.size) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("查看更多记录（剩余 ${state.logs.size - visibleLogCount} 条）")
                }
            } else if (state.logs.size > INITIAL_LOG_COUNT) {
                Text("已展示 ${state.logs.size} 条最近记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
