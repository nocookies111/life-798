package com.water.widget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_VISIBLE_TASK_LOGS = 80

@Composable
fun TaskScreen(
    state: TaskUiState,
    onRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    var onlyFailures by remember { mutableStateOf(false) }
    val visibleLogs = if (onlyFailures) {
        state.logs.filter { it.contains("❌") || it.contains("失败") || it.contains("错误") }
    } else {
        state.logs
    }.takeLast(MAX_VISIBLE_TASK_LOGS)
    val actionLabel = when {
        state.running -> "任务执行中"
        state.canRun -> "运行今日任务"
        else -> "暂不可执行"
    }

    Column(
        modifier = modifier.fillMaxWidth().background(Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("积分任务", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("今日任务", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (state.running) CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.height(24.dp))
                }
                Text(
                    if (state.running) "正在执行…" else state.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (state.totalGainedText != "0") {
                    Text(
                        "本次获得 ${state.totalGainedText} 积分",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (state.running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Button(onClick = onRun, enabled = state.canRun, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(actionLabel)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("运行记录", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            when {
                                visibleLogs.isEmpty() && onlyFailures -> "没有失败记录"
                                visibleLogs.isEmpty() -> "暂无记录"
                                onlyFailures -> "${visibleLogs.size} 条失败记录"
                                else -> "最近 ${visibleLogs.size} 条"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    if (state.hasFailures) {
                        FilterChip(
                            selected = onlyFailures,
                            onClick = { onlyFailures = !onlyFailures },
                            label = { Text("仅看失败", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (visibleLogs.isEmpty()) {
                    Text(
                        if (onlyFailures) "没有失败记录" else "运行任务后显示结果",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(horizontal = 18.dp)
                    ) {
                        itemsIndexed(visibleLogs, key = { index, line -> "$index-$line" }) { index, line ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            TaskLogRow(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskLogRow(line: String) {
    val style = logStyle(line)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = CircleShape, color = style.background, modifier = Modifier.padding(top = 1.dp)) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.padding(5.dp))
        }
        Text(
            text = line.trim().removePrefix("===== ").removeSuffix(" ====="),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

private data class TaskLogStyle(val icon: ImageVector, val color: Color, val background: Color)

@Composable
private fun logStyle(line: String): TaskLogStyle = when {
    line.contains("❌") || line.contains("失败") || line.contains("错误") -> TaskLogStyle(
        Icons.Default.ErrorOutline,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.errorContainer
    )
    line.contains("✅") || line.contains("成功") || line.contains("完成") || line.contains("获得") -> TaskLogStyle(
        Icons.Default.CheckCircle,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primaryContainer
    )
    line.contains("⏳") || line.contains("等待") || line.contains("频率") || line.contains("重试") -> TaskLogStyle(
        Icons.Default.Schedule,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    else -> TaskLogStyle(
        Icons.Default.Info,
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant
    )
}
