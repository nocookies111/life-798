package com.water.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.water.widget.ui.DashboardScreen
import com.water.widget.ui.DashboardViewModel
import com.water.widget.ui.WaterTheme
import kotlinx.coroutines.launch

/**
 * Compose 版主页入口。
 * 继续复用原有账户、设备、Widget 与磁贴逻辑，只替换主界面的呈现层。
 */
class ConfigActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_WATER_RECOVERY = "com.water.widget.EXTRA_OPEN_WATER_RECOVERY"
    }

    private val viewModel: DashboardViewModel by viewModels()
    private val ui = Handler(Looper.getMainLooper())
    private var scoreGeneration = 0
    private var destroyed = false
    private var notificationRequestInFlight = false
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationRequestInFlight = false
        if (!granted) {
            toast("未开启通知权限，功能仍会运行，但通知栏不会显示进度")
        }
    }
    private val addDevice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.reloadAccounts()
            updateWidgets()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
        AppNotifications.ensureChannels(this)
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = state,
                    onLogin = { startActivity(Intent(this, LoginActivity::class.java)) },
                    onAccounts = { startActivity(Intent(this, AccountsActivity::class.java)) },
                    themeMode = ThemeSettings.mode(this),
                    onThemeModeChange = { mode ->
                        ThemeSettings.setMode(this, mode)
                        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
                        recreate()
                    },
                    onRunTasks = { runWithNotificationPermission(::runTasksInHome) },
                    onScores = { startActivity(Intent(this, ScoreActivity::class.java)) },
                    onSelectAccount = { phone -> switchAccount(phone) },
                    onFetchDevices = { fetchDevices(showFeedback = true) },
                    onAddDevice = { openAddDevice() },
                    onEditDevice = { deviceId, alias -> editDevice(deviceId, alias) },
                    onRemoveDevice = { deviceId -> removeDevice(deviceId) },
                    onSelectDevice = { deviceId -> selectControlCenterDevice(deviceId) },
                    onStartDevice = { deviceId ->
                        runWithNotificationPermission { startDevice(deviceId) }
                    }
                )
            }
        }
        observeTaskCompletion()
        consumeRecoveryIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRecoveryIntent(intent)
    }

    private fun consumeRecoveryIntent(intent: Intent) {
        val openedForRecovery = intent.getBooleanExtra(EXTRA_OPEN_WATER_RECOVERY, false)
        if (openedForRecovery) {
            intent.removeExtra(EXTRA_OPEN_WATER_RECOVERY)
            ui.post { toast("控制中心出水失败，请检查设备控制登录信息、设备与签约状态") }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadAccounts(resetScore = true)
        refreshCurrentScore()
        updateWidgets()
        fetchDevices(showFeedback = false)
    }

    override fun onDestroy() {
        destroyed = true
        scoreGeneration++
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** 页面只发送启动请求，真正的任务生命周期由 TaskForegroundService 持有。 */
    private fun runTasksInHome(): Boolean =
        when (TaskForegroundService.start(this)) {
            TaskForegroundService.StartResult.STARTED -> true
            TaskForegroundService.StartResult.ALREADY_RUNNING -> {
                toast("积分任务正在运行，请勿重复启动")
                true
            }
            TaskForegroundService.StartResult.NO_ACCOUNTS -> {
                toast("没有可运行账号，请先登录或导入账户信息")
                false
            }
            TaskForegroundService.StartResult.FAILED -> {
                toast("积分任务启动失败，请保持应用在前台后重试")
                false
            }
        }

    /**
     * 先启动用户请求，再申请通知权限；即使权限弹窗期间页面重建，任务也不会丢失。
     */
    private fun runWithNotificationPermission(action: () -> Boolean) {
        if (!action()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !notificationRequestInFlight
        ) {
            notificationRequestInFlight = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun observeTaskCompletion() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasRunning = TaskRunRepository.state.value.running
                TaskRunRepository.state.collect { state ->
                    if (
                        wasRunning &&
                        !state.running &&
                        (
                            state.result == TaskRunResult.COMPLETED ||
                                state.result == TaskRunResult.PARTIAL
                        )
                    ) {
                        refreshCurrentScore()
                    }
                    wasRunning = state.running
                }
            }
        }
    }

    private fun switchAccount(phone: String) {
        val account = viewModel.selectAccount(phone)
        if (account == null) {
            toast("账号不存在")
            return
        }
        toast("已切换到 $account")
        refreshCurrentScore()
        updateWidgets()
    }

    private fun refreshCurrentScore() {
        val generation = ++scoreGeneration
        val account = AccountStore.getCurrent(this)
        val accountPhone = account?.phone
        val token = account?.token?.takeIf { it.isNotBlank() } ?: account?.appToken?.takeIf { it.isNotBlank() }
        if (account == null || token.isNullOrBlank()) {
            viewModel.setCurrentScoreData(null, null)
            return
        }
        IlifeApi.missionLstWithToken(token) { missionJson, _ ->
            IlifeApi.scoreLstWithToken(token) { scoreJson, _ ->
                runOnUiThread {
                    if (destroyed || generation != scoreGeneration) return@runOnUiThread
                    if (AccountStore.getCurrent(this)?.phone != accountPhone) return@runOnUiThread
                    val score = missionJson?.optJSONObject("data")?.optJSONObject("accScoreRsp")?.optInt("validScore")
                    viewModel.setCurrentScoreData(score, scoreJson)
                }
            }
        }
    }

    private fun openAddDevice() {
        addDevice.launch(Intent(this, DeviceAddActivity::class.java))
    }

    private fun editDevice(deviceId: String, alias: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null || deviceId.isBlank()) return
        account.setDeviceAlias(deviceId, alias)
        AccountStore.updateCurrent(this, account)
        viewModel.reloadAccounts()
        toast("设备名称已保存")
    }

    private fun removeDevice(deviceId: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null || deviceId.isBlank()) return
        IlifeApi.deviceFavorite(this, deviceId, true) { _, err ->
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                if (err != null && err != "TOKEN_EXPIRED") {
                    toast("移除失败：$err")
                    return@runOnUiThread
                }
                account.forgetDevice(deviceId)
                AccountStore.updateCurrent(this, account)
                viewModel.reloadAccounts()
                updateWidgets()
                toast(if (err == "TOKEN_EXPIRED") "已从本地移除；登录过期，服务器收藏可能仍保留" else "设备已移除")
            }
        }
    }

    private fun selectControlCenterDevice(deviceId: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null || deviceId.isBlank()) return
        if (account.selectedDeviceId() == deviceId) {
            toast("已是控制中心默认设备")
            return
        }
        account.selectDevice(deviceId)
        AccountStore.updateCurrent(this, account)
        viewModel.reloadAccounts()
        updateWidgets()
        toast("已设为控制中心默认设备")
    }

    /** 拉取主页收藏设备列表；首次同步时自动选择第一台设备。 */
    private fun fetchDevices(showFeedback: Boolean) {
        val account = AccountStore.getCurrent(this)
        if (account == null || (!account.hasToken() && !account.hasAppToken())) {
            if (showFeedback) toast("请先登录")
            return
        }

        if (showFeedback) toast("正在同步设备…")
        IlifeApi.master(this, object : IlifeApi.JsonCallback {
            override fun onResult(json: org.json.JSONObject?, err: String?) {
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    if (json == null) {
                        if (showFeedback) toast("同步失败：${err ?: "未知错误"}")
                        return@runOnUiThread
                    }

                    val code = json.optInt("code", -999)
                    when {
                        code == -99 -> {
                            if (showFeedback) toast("登录已过期，请重新登录")
                            return@runOnUiThread
                        }
                        code != 0 -> {
                            if (showFeedback) toast("同步失败：code=$code")
                            return@runOnUiThread
                        }
                    }

                    val favos = json.optJSONObject("data")?.optJSONArray("favos")
                    if (favos == null || favos.length() == 0) {
                        if (showFeedback) toast("暂无已收藏设备")
                        return@runOnUiThread
                    }

                    val devices = mutableListOf<Pair<String, String>>()
                    for (index in 0 until favos.length()) {
                        favos.optJSONObject(index)?.let { device ->
                            device.optString("id", "").takeIf(String::isNotBlank)?.let { id ->
                                devices += id to device.optString("name", "")
                            }
                        }
                    }
                    devices.asReversed().forEach { (id, name) -> account.rememberDevice(id, name) }
                    if (account.selectedDeviceId().isBlank()) {
                        devices.firstOrNull()?.first?.let(account::selectDevice)
                    }

                    AccountStore.updateCurrent(this@ConfigActivity, account)
                    if (showFeedback) toast("已同步 ${devices.size} 台设备")
                    viewModel.reloadAccounts()
                    updateWidgets()
                }
            }
        })
    }

    private fun startDevice(deviceId: String): Boolean {
        val account = AccountStore.getCurrent(this)
        if (account == null) {
            toast("请先登录")
            return false
        }
        if (!account.hasAppToken()) {
            toast("启动设备需要设备控制登录信息，请在账户管理中补充")
            return false
        }

        if (deviceId.isBlank()) {
            toast("请先选择设备")
            return false
        }

        return when (
            WaterService.start(this, deviceId, AppWidgetManager.INVALID_APPWIDGET_ID)
        ) {
            WaterService.StartResult.STARTED -> {
                toast("设备启动中…")
                true
            }
            WaterService.StartResult.ALREADY_RUNNING -> {
                toast("已有接水会话正在监测，请勿重复启动")
                true
            }
            WaterService.StartResult.FAILED -> {
                toast("设备启动失败，请稍后重试")
                false
            }
        }
    }

    private fun updateWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, WaterWidgetProvider::class.java))
        ids.forEach { id -> manager.updateAppWidget(id, WaterWidgetProvider.buildViews(this, id, null)) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
