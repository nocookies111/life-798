package com.water.widget.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AccountCardState(
    val phone: String,
    val title: String,
    val current: Boolean,
    val alipayToken: String,
    val appToken: String,
    val deviceSummary: String
)

@Composable
fun AccountsScreen(
    accounts: List<AccountCardState>,
    onSmsLogin: () -> Unit,
    onAddToken: () -> Unit,
    onSetCurrent: (String) -> Unit,
    onSetAppToken: (String) -> Unit,
    onRevealToken: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { AccountsHero(accounts.size) }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("添加账户", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("推荐使用短信登录；如已有登录信息，也可以手动添加。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Button(onClick = onSmsLogin, modifier = Modifier.fillMaxWidth()) { Text("短信登录") }
                    OutlinedButton(onClick = onAddToken, modifier = Modifier.fillMaxWidth()) { Text("手动添加登录信息") }
                }
            }
        }
        if (accounts.isEmpty()) {
            item { EmptyAccountsCard() }
        } else {
            item {
                Text(
                    "已保存账户",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            items(accounts, key = { it.phone }) { account ->
                AccountCard(account, onSetCurrent, onSetAppToken, onRevealToken, onDelete)
            }
        }
    }
}

@Composable
private fun AccountsHero(accountCount: Int) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("账户管理", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                if (accountCount == 0) "还没有保存账户，从下方开始添加。" else "已安全保存 $accountCount 个账户的登录信息。",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("暂无账户", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "可通过短信登录或手动填写登录信息来添加。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountCardState,
    onSetCurrent: (String) -> Unit,
    onSetAppToken: (String) -> Unit,
    onRevealToken: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (account.current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (account.current) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (account.current) 2.dp else 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(account.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(account.phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (account.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        if (account.current) "当前账户" else "备用账户",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (account.current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AccountInfoRow("日常登录信息", maskToken(account.alipayToken))
            AccountInfoRow("设备控制信息", maskToken(account.appToken))
            AccountInfoRow("设备", account.deviceSummary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onSetCurrent(account.phone) }, enabled = !account.current, modifier = Modifier.weight(1f)) {
                    Text(if (account.current) "正在使用" else "设为当前")
                }
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.weight(1f)) { Text("更多设置") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("查看或复制日常登录信息") }, enabled = account.alipayToken.isNotBlank(), onClick = { expanded = false; onRevealToken(account.phone, false) })
                    DropdownMenuItem(text = { Text("查看或复制设备控制信息") }, enabled = account.appToken.isNotBlank(), onClick = { expanded = false; onRevealToken(account.phone, true) })
                    DropdownMenuItem(text = { Text("设置设备控制信息") }, onClick = { expanded = false; onSetAppToken(account.phone) })
                    DropdownMenuItem(text = { Text("删除账户") }, onClick = { expanded = false; onDelete(account.phone) })
                }
            }
        }
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
fun TextEntryDialog(
    title: String,
    message: String,
    fields: List<Pair<String, String>>,
    multiline: Boolean = false,
    sensitive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var values by remember(fields) { mutableStateOf(fields.map { it.second }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { new -> values = values.toMutableList().also { it[index] = new } },
                        label = { Text(field.first) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (multiline) 5 else 1,
                        visualTransformation = if (sensitive) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(values) }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

fun maskToken(token: String): String = when {
    token.isBlank() -> "未设置"
    token.length <= 8 -> "••••••••"
    else -> token.take(4) + "••••••••" + token.takeLast(4)
}
