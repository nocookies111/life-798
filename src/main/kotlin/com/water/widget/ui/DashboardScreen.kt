package com.water.widget.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.water.widget.AppThemeMode
import com.water.widget.BuildConfig
import com.water.widget.R

private enum class DashboardTab(val label: String) {
    CONTROL("控制"),
    TASK("任务"),
    MINE("我的")
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onLogin: () -> Unit,
    onAccounts: () -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onRunTasks: () -> Unit,
    onScores: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onFetchDevices: () -> Unit,
    onAddDevice: (String) -> Unit,
    onAssignDevice: (String, String) -> Unit,
    onHotWater: () -> Unit,
    onColdWater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.CONTROL) }
    var showAccountSwitcher by rememberSaveable { mutableStateOf(false) }
    var showAppearanceSettings by rememberSaveable { mutableStateOf(false) }
    var showSupportDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                DashboardTab.CONTROL -> HomeTab(
                    state = state.summary,
                    onSwitchAccount = { showAccountSwitcher = true },
                    onFetchDevices = onFetchDevices,
                    onAddDevice = onAddDevice,
                    onAssignDevice = onAssignDevice,
                    onHotWater = onHotWater,
                    onColdWater = onColdWater
                )
                DashboardTab.TASK -> TaskScreen(state = state.tasks, onRun = onRunTasks, modifier = Modifier.padding(0.dp))
                DashboardTab.MINE -> MineTab(
                    state = state.summary,
                    onLogin = onLogin,
                    onAccounts = onAccounts,
                    onScores = onScores,
                    themeMode = themeMode,
                    onOpenAppearanceSettings = { showAppearanceSettings = true },
                    onOpenSupport = { showSupportDialog = true }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        BottomTabs(selectedTab = selectedTab, onSelect = { selectedTab = it })
    }

    if (showAccountSwitcher) {
        AccountSwitcherDialog(
            accounts = state.accounts,
            onDismiss = { showAccountSwitcher = false },
            onSelect = { phone ->
                showAccountSwitcher = false
                onSelectAccount(phone)
            }
        )
    }
    if (showAppearanceSettings) {
        AppearanceSettingsDialog(
            mode = themeMode,
            onDismiss = { showAppearanceSettings = false },
            onSelect = { mode ->
                showAppearanceSettings = false
                onThemeModeChange(mode)
            }
        )
    }
    if (showSupportDialog) {
        SupportDeveloperDialog(onDismiss = { showSupportDialog = false })
    }
}

@Composable
private fun HomeTab(
    state: DashboardSummaryUiState,
    onSwitchAccount: () -> Unit,
    onFetchDevices: () -> Unit,
    onAddDevice: (String) -> Unit,
    onAssignDevice: (String, String) -> Unit,
    onHotWater: () -> Unit,
    onColdWater: () -> Unit
) {
    Text("饮水控制中心", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Text("一键出水，设备与积分状态一目了然", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    HeroCard(state = state, onSwitchAccount = onSwitchAccount)
    TodayOverviewCard(usage = state.usage)
    Text("快捷出水", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    QuickActions(
        onHotWater = onHotWater,
        onColdWater = onColdWater,
        hotEnabled = state.hasAccount && state.hasAppToken && state.hasDevices,
        coldEnabled = state.hasAccount && state.hasAppToken && state.hasDevices
    )
    DeviceCard(
        state = state,
        onFetchDevices = onFetchDevices,
        onAddDevice = onAddDevice,
        onAssignDevice = onAssignDevice
    )
    HelpCard()
}

@Composable
private fun HeroCard(state: DashboardSummaryUiState, onSwitchAccount: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.78f))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(colors.onPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("水", color = colors.onPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("当前服务账号", color = colors.onPrimary.copy(alpha = 0.78f), fontSize = 12.sp)
                    Text(state.accountTitle, color = colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Surface(
                    onClick = onSwitchAccount,
                    shape = RoundedCornerShape(14.dp),
                    color = colors.onPrimary.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("切换", color = colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "切换账号", tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.onPrimary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("可用积分", color = colors.onPrimary.copy(alpha = 0.76f), fontSize = 12.sp)
                        Text(state.scoreTitle, color = colors.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(state.scoreSubtitle, color = colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(state.accountSubtitle, color = colors.onPrimary.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayOverviewCard(usage: WaterUsageUiState) {
    InfoCard(title = "今天概览", subtitle = "") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            UsageMetric(
                label = "今日花费",
                value = usage.todayCostText,
                accent = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            UsageMetric(
                label = "今日水量",
                value = usage.todayWaterText,
                accent = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UsageMetric(
    label: String,
    value: String,
    accent: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = accent) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = content.copy(alpha = 0.78f), fontSize = 12.sp)
            Text(value, color = content, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickActions(onHotWater: () -> Unit, onColdWater: () -> Unit, hotEnabled: Boolean, coldEnabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        DispenseButton(
            label = "热水",
            caption = if (hotEnabled) "立即出水" else "等待设备就绪",
            marker = "热",
            onClick = onHotWater,
            enabled = hotEnabled,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        DispenseButton(
            label = "冷水",
            caption = if (coldEnabled) "立即出水" else "等待设备就绪",
            marker = "冷",
            onClick = onColdWater,
            enabled = coldEnabled,
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DispenseButton(
    label: String,
    caption: String,
    marker: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(126.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(contentColor.copy(alpha = if (enabled) 0.12f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(marker, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(caption, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DeviceCard(
    state: DashboardSummaryUiState,
    onFetchDevices: () -> Unit,
    onAddDevice: (String) -> Unit,
    onAssignDevice: (String, String) -> Unit
) {
    var selectedRole by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = state.hasAccount && state.hasAppToken && state.hasDevices
    InfoCard(
        title = "设备状态",
        subtitle = if (ready) "点击热水或冷水卡片即可切换设备" else "完成配置后即可开启一键出水"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DeviceStatusItem(
                label = "热水",
                value = state.hotDevice,
                ready = state.hasDevices,
                onClick = { selectedRole = "hot" },
                modifier = Modifier.weight(1f)
            )
            DeviceStatusItem(
                label = "冷水",
                value = state.coldDevice,
                ready = state.hasDevices,
                onClick = { selectedRole = "cold" },
                modifier = Modifier.weight(1f)
            )
        }
        if (!ready) {
            Spacer(Modifier.height(14.dp))
            ConfigurationNotice(state = state)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onFetchDevices, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Text("同步设备")
            }
            Button(onClick = { onAddDevice("both") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("添加设备", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
    selectedRole?.let { role ->
        DevicePickerDialog(
            role = role,
            devices = state.recentDevices,
            onDismiss = { selectedRole = null },
            onSelect = { deviceId ->
                selectedRole = null
                onAssignDevice(role, deviceId)
            },
            onAddNew = {
                selectedRole = null
                onAddDevice(role)
            }
        )
    }
}

@Composable
private fun DevicePickerDialog(
    role: String,
    devices: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAddNew: () -> Unit
) {
    val roleName = if (role == "hot") "热水" else "冷水"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择${roleName}设备", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (devices.isEmpty()) "还没有可用设备。添加后会显示在这里，方便下次切换。" else "选择一台用过的设备，立即设为${roleName}设备。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                devices.forEach { deviceId ->
                    Surface(
                        onClick = { onSelect(deviceId) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(deviceId, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                Text("设为${roleName}设备", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ArrowForwardIos, contentDescription = "选择设备", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAddNew) { Text("添加新设备") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DeviceStatusItem(label: String, value: String, ready: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (ready) colors.secondaryContainer else colors.surfaceVariant
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = colors.onSurfaceVariant, fontSize = 12.sp)
            Text(value, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (ready) "已连接" else "未配置", color = if (ready) colors.onSecondaryContainer else colors.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConfigurationNotice(state: DashboardSummaryUiState) {
    val (title, detail) = when {
        !state.hasAccount -> "尚未登录账号" to "登录账号后可同步设备并使用出水控制。"
        !state.hasAppToken -> "设备控制尚未开通" to "请在账户管理中补充设备控制登录信息后再试。"
        else -> "未配置出水设备" to "同步收藏设备，或手动添加设备编号后再试。"
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(detail, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MineTab(
    state: DashboardSummaryUiState,
    onLogin: () -> Unit,
    onAccounts: () -> Unit,
    onScores: () -> Unit,
    themeMode: AppThemeMode,
    onOpenAppearanceSettings: () -> Unit,
    onOpenSupport: () -> Unit
) {
    TabHeader("我的", "账户、用水与应用设置")
    PersonalUsageSummary(usage = state.usage)
    InfoCard(title = state.accountTitle, subtitle = state.accountSubtitle) {
        StatusRow("账号数量", "${state.accountCount}")
        StatusRow("当前积分", state.scoreTitle)
        StatusRow("设备控制", if (state.hasAppToken) "已开通" else "待开通")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onLogin, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("登录") }
            OutlinedButton(onClick = onAccounts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("管理账号") }
        }
        OutlinedButton(onClick = onScores, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("查看积分与流水") }
    }
    SettingsRow(
        icon = Icons.Default.DarkMode,
        title = "外观设置",
        subtitle = "${themeMode.label} · 夜间模式与显示偏好",
        onClick = onOpenAppearanceSettings
    )
    SettingsRow(
        icon = Icons.Default.Favorite,
        title = "支持开发者",
        subtitle = "喜欢这个应用？欢迎请我喝杯水",
        onClick = onOpenSupport
    )
    Text(
        text = "WaterWidget  v${BuildConfig.VERSION_NAME}",
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
}

@Composable
private fun PersonalUsageSummary(usage: WaterUsageUiState) {
    InfoCard(title = "消费统计", subtitle = "") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            UsageSummaryItem("今日", usage.todayCostText, usage.todayWaterText, Modifier.weight(1f))
            UsageSummaryItem("本月", usage.monthCostText, usage.monthWaterText, Modifier.weight(1f))
            UsageSummaryItem("本年", usage.yearCostText, usage.yearWaterText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun UsageSummaryItem(label: String, cost: String, water: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), fontSize = 11.sp)
            Text(cost, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(water, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun AccountSwitcherDialog(accounts: List<DashboardAccountUiState>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换账号", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择用于设备控制和积分查询的账号", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                accounts.forEach { account ->
                    Surface(
                        onClick = { onSelect(account.phone) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (account.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                Text(account.title.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(account.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(account.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                            }
                            if (account.isCurrent) Icon(Icons.Default.Check, contentDescription = "当前账号", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { Text("取消", modifier = Modifier.padding(8.dp).clickable(onClick = onDismiss), color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun AppearanceSettingsDialog(mode: AppThemeMode, onDismiss: () -> Unit, onSelect: (AppThemeMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("外观设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择应用的显示模式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                AppThemeMode.entries.forEach { item ->
                    Surface(
                        onClick = { onSelect(item) },
                        shape = RoundedCornerShape(15.dp),
                        color = if (item == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, modifier = Modifier.weight(1f), fontWeight = if (item == mode) FontWeight.SemiBold else FontWeight.Normal)
                            if (item == mode) Icon(Icons.Default.Check, contentDescription = "已选中", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { Text("完成", modifier = Modifier.padding(8.dp).clickable(onClick = onDismiss), color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun SupportDeveloperDialog(onDismiss: () -> Unit) {
    var selectedMethod by rememberSaveable { mutableStateOf(SupportMethod.WECHAT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("支持开发者", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("如果这个应用对你有帮助，欢迎请我喝杯水。感谢你的支持！", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SupportMethod.entries.forEach { method ->
                        val selected = method == selectedMethod
                        if (selected) {
                            Button(onClick = { selectedMethod = method }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Text(method.label)
                            }
                        } else {
                            OutlinedButton(onClick = { selectedMethod = method }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Text(method.label)
                            }
                        }
                    }
                }
                SupportQrCode(label = selectedMethod.label, imageRes = selectedMethod.imageRes)
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

private enum class SupportMethod(val label: String, val imageRes: Int) {
    WECHAT("微信赞赏", R.drawable.reward_wechat),
    ALIPAY("支付宝赞赏", R.drawable.reward_alipay)
}

@Composable
private fun SupportQrCode(label: String, imageRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(228.dp).clip(RoundedCornerShape(10.dp))
            )
            Text("使用${label}扫码", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TabHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BottomTabs(selectedTab: DashboardTab, onSelect: (DashboardTab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(selected = selectedTab == DashboardTab.CONTROL, onClick = { onSelect(DashboardTab.CONTROL) }, icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text(DashboardTab.CONTROL.label) })
        NavigationBarItem(selected = selectedTab == DashboardTab.TASK, onClick = { onSelect(DashboardTab.TASK) }, icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }, label = { Text(DashboardTab.TASK.label) })
        NavigationBarItem(selected = selectedTab == DashboardTab.MINE, onClick = { onSelect(DashboardTab.MINE) }, icon = { Icon(Icons.Default.Person, contentDescription = null) }, label = { Text(DashboardTab.MINE.label) })
    }
}

@Composable
private fun HelpCard() {
    InfoCard(title = "使用指引", subtitle = "首次配置可按以下顺序完成") {
        Text("登录账户并开通设备控制\n同步收藏设备并测试出水\n按需添加桌面小部件或快捷磁贴\n每日运行积分任务", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    WaterTheme {
        DashboardScreen(
            state = DashboardUiState(summary = DashboardSummaryUiState("测试账户", "2 个账户 · 设备控制已开通", "2380", "≈2.38元可用", true, true, true, "device-001", "共用热水设备", 2, listOf("device-001", "device-002"))),
            onLogin = {}, onAccounts = {}, themeMode = AppThemeMode.SYSTEM, onThemeModeChange = {}, onRunTasks = {}, onScores = {}, onSelectAccount = {}, onFetchDevices = {}, onAddDevice = {}, onAssignDevice = { _, _ -> }, onHotWater = {}, onColdWater = {}
        )
    }
}
