package com.water.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.water.widget.ui.DashboardScreen
import com.water.widget.ui.DashboardViewModel
import com.water.widget.ui.WaterTheme
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

/**
 * Compose 版主页入口。
 * 继续复用原有账户、设备、Widget 与磁贴逻辑，只替换主界面的呈现层。
 */
class ConfigActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AUTO_RUN_TASKS = "com.water.widget.EXTRA_AUTO_RUN_TASKS"
        const val EXTRA_OPEN_WATER_RECOVERY = "com.water.widget.EXTRA_OPEN_WATER_RECOVERY"
    }

    private val viewModel: DashboardViewModel by viewModels()
    private val ui = Handler(Looper.getMainLooper())
    private val taskRateLimiter = TaskExecutionCoordinator.tokenRateLimiter
    private var taskGeneration = 0
    private var taskLease: TaskExecutionCoordinator.Lease? = null
    private var scoreGeneration = 0
    private var destroyed = false
    private val addDevice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.reloadAccounts()
            updateWidgets()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
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
                    onRunTasks = { runTasksInHome() },
                    onScores = { startActivity(Intent(this, ScoreActivity::class.java)) },
                    onSelectAccount = { phone -> switchAccount(phone) },
                    onFetchDevices = { fetchDevices() },
                    onAddDevice = { role -> openAddDevice(role) },
                    onAssignDevice = { role, deviceId -> assignDevice(role, deviceId) },
                    onHotWater = { testWater("hot", "热水") },
                    onColdWater = { testWater("cold", "冷水") }
                )
            }
        }
        consumeAutoRunIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAutoRunIntent(intent)
    }

    private fun consumeAutoRunIntent(intent: Intent) {
        val openedForRecovery = intent.getBooleanExtra(EXTRA_OPEN_WATER_RECOVERY, false)
        if (openedForRecovery) {
            intent.removeExtra(EXTRA_OPEN_WATER_RECOVERY)
            ui.post { toast("控制中心出水失败，请检查设备控制登录信息、设备与签约状态") }
        }
        if (intent.getBooleanExtra(EXTRA_AUTO_RUN_TASKS, false)) {
            intent.removeExtra(EXTRA_AUTO_RUN_TASKS)
            ui.post { runTasksInHome() }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadAccounts(resetScore = true)
        refreshCurrentScore()
        updateWidgets()
    }

    override fun onDestroy() {
        destroyed = true
        taskGeneration++
        taskLease?.let(TaskExecutionCoordinator::releaseAfterInFlightNetworkGrace)
        taskLease = null
        scoreGeneration++
        ui.removeCallbacksAndMessages(null)
        viewModel.cancelTasks()
        super.onDestroy()
    }

    private fun runTasksInHome() {
        val accounts = viewModel.beginTasks() ?: return
        if (accounts.isEmpty()) {
            toast("没有可运行账号，请先登录或导入账户信息")
            return
        }
        val lease = TaskExecutionCoordinator.tryAcquire(accounts)
        if (lease == null) {
            viewModel.finishTasks()
            toast("任务已在其他页面运行，请勿重复启动")
            return
        }
        taskLease = lease
        appendTaskLog("===== 首页任务中心：一键运行全部账号 =====")
        taskRateLimiter.clear()
        val lanes = AccountExecutionPlanner.plan(accounts)
        appendTaskLog("共 ${accounts.size} 个账号，分为 ${lanes.size} 个安全通道并发执行；共享账号标识、uid 或 Token 的账号将串行执行。")
        val generation = ++taskGeneration
        val completed = AtomicInteger(0)
        lanes.forEachIndexed { laneIndex, lane ->
            runAccountLane(lane.accounts, 0, laneIndex + 1, lanes.size, generation) { gained ->
                if (!isTaskActive(generation)) return@runAccountLane
                viewModel.addTaskGained(gained)
                if (completed.incrementAndGet() == lanes.size && isTaskActive(generation)) {
                    appendTaskLog("===== 全部完成，本次预计获得 ${viewModel.totalTaskGained} 分 =====")
                    viewModel.finishTasks()
                    releaseTaskLease()
                    refreshCurrentScore()
                }
            }
        }
    }

    private fun runAccountLane(
        accounts: List<Account>,
        index: Int,
        laneIndex: Int,
        laneCount: Int,
        generation: Int,
        done: (Int) -> Unit
    ) {
        if (!isTaskActive(generation)) return
        if (index >= accounts.size) {
            done(0)
            return
        }
        val account = accounts[index]
        appendTaskLog("\n---------- [${account}] 通道 $laneIndex/$laneCount，账号 ${index + 1}/${accounts.size} ----------")
        loadAndRunAccountTasks(account, generation) { gained ->
            if (!isTaskActive(generation)) return@loadAndRunAccountTasks
            ui.postDelayed({
                if (!isTaskActive(generation)) return@postDelayed
                runAccountLane(accounts, index + 1, laneIndex, laneCount, generation) { nextGained ->
                    done(gained + nextGained)
                }
            }, 1200)
        }
    }

    private fun loadAndRunAccountTasks(account: Account, generation: Int, done: (Int) -> Unit) {
        IlifeApi.missionLstWithToken(account.token) { missionJson, missionErr ->
            runOnUiThread {
                if (!isTaskActive(generation)) return@runOnUiThread
                if (missionJson == null || missionJson.optInt("code", -999) != 0) {
                    appendTaskLog("  获取任务列表失败: ${missionErr ?: missionJson?.optString("msg", "未知错误")}")
                    done(0)
                    return@runOnUiThread
                }
                val data = missionJson.optJSONObject("data")
                val missions = data?.optJSONArray("missions")
                val score = data?.optJSONObject("accScoreRsp")?.optInt("validScore", 0) ?: 0
                appendTaskLog("  当前积分: $score，任务数: ${missions?.length() ?: 0}")
                runDailySignInIfNeeded(account, data, generation) { signGained ->
                    loadAppMissionsIfNeeded(account, missions, generation) { plannedMissions ->
                        IlifeApi.scoreLstWithToken(account.token) { scoreJson, _ ->
                            runOnUiThread {
                                if (!isTaskActive(generation)) return@runOnUiThread
                                val doneCount = parseTodayDoneCount(scoreJson)
                                val work = TaskMissionPlanner.buildWork(plannedMissions, doneCount)
                                if (work.isEmpty()) {
                                    appendTaskLog("  无可执行任务（已完成/无积分/已过滤）")
                                    done(signGained)
                                } else {
                                    appendTaskLog("  待执行 ${work.size} 次任务")
                                    runMissionItems(account, work, 0, signGained, generation, done)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun runDailySignInIfNeeded(
        account: Account,
        data: JSONObject?,
        generation: Int,
        done: (Int) -> Unit
    ) {
        if (!isTaskActive(generation)) return
        val dailyRsp = data?.optJSONObject("dailyRSP")
        val weekMask = data?.optJSONObject("accScoreRsp")?.optJSONObject("daily")?.optInt("week", 0) ?: 0
        val calendarDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val weekDay = if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
        val rules = dailyRsp?.optJSONArray("config")?.let { config ->
            buildList {
                for (index in 0 until config.length()) {
                    val rule = config.optJSONObject(index) ?: continue
                    add(
                        DailySignInRule(
                            weekMask = rule.optInt("rule", 0),
                            score = rule.optInt("score", 0),
                            description = rule.optString("msg", "")
                        )
                    )
                }
            }
        }.orEmpty()
        val plan = DailySignInPlanner.plan(
            weekMask = weekMask,
            weekDay = weekDay,
            adId = dailyRsp?.optString("adId", ""),
            score = dailyRsp?.optInt("score", 5) ?: 0,
            rules = rules
        )
        when {
            plan.adId.isBlank() -> {
                appendTaskLog("  每日签到: 无签到数据，跳过")
                done(0)
            }
            plan.alreadySigned -> {
                appendTaskLog("  每日签到: 今日已签到")
                done(0)
            }
            else -> {
                appendTaskLog("  每日签到: 未签到，优先执行")
                sendDailySignIn(account, plan, generation, retry = false, done = done)
            }
        }
    }

    private fun sendDailySignIn(
        account: Account,
        plan: DailySignInPlan,
        generation: Int,
        retry: Boolean,
        done: (Int) -> Unit
    ) {
        if (!isTaskActive(generation)) return
        val token = account.token.takeIf { it.isNotBlank() } ?: account.appToken.takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) {
            appendTaskLog("    签到失败: 无可用 Token，继续任务")
            done(0)
            return
        }
        val delay = taskRateLimiter.reserveDelayMillis(token, System.currentTimeMillis())
        ui.postDelayed({
            if (!isTaskActive(generation)) return@postDelayed
            IlifeApi.scoreSendSignIn(token, account.uid, plan.weekDay, plan.adId) { json, err ->
                runOnUiThread {
                    if (!isTaskActive(generation)) return@runOnUiThread
                    when {
                        json?.optInt("code", -999) == 0 -> {
                            plan.rewards.forEach { reward ->
                                val description = reward.description.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
                                appendTaskLog("    连签奖励 +${reward.score}分$description")
                            }
                            appendTaskLog("    签到成功 +${plan.totalScore}分${if (retry) "（重试）" else ""}")
                            done(plan.totalScore)
                        }
                        json?.optInt("code", -999) == -98 && !retry -> {
                            appendTaskLog("    签到频率限制，60秒后重试一次")
                            ui.postDelayed({ sendDailySignIn(account, plan, generation, retry = true, done = done) }, 60000)
                        }
                        else -> {
                            val code = json?.optInt("code", -999)
                            val reason = err ?: json?.optString("msg", "code=$code") ?: "未知错误"
                            appendTaskLog("    签到失败: $reason，继续任务")
                            done(0)
                        }
                    }
                }
            }
        }, delay)
    }

    private fun loadAppMissionsIfNeeded(
        account: Account,
        mainMissions: org.json.JSONArray?,
        generation: Int,
        callback: (List<PlannedMission>) -> Unit
    ) {
        if (!isTaskActive(generation)) return
        if (!account.hasAppToken()) {
            callback(TaskMissionPlanner.merge(mainMissions, null))
            return
        }
        IlifeApi.missionLstWithToken(account.appToken) { appJson, _ ->
            runOnUiThread {
                if (!isTaskActive(generation)) return@runOnUiThread
                var appMissions: org.json.JSONArray? = null
                if (appJson != null && appJson.optInt("code", -999) == 0) {
                    appMissions = appJson.optJSONObject("data")?.optJSONArray("missions")
                    appendTaskLog("  官方 App 任务数: ${appMissions?.length() ?: 0}，已合并去重")
                } else {
                    appendTaskLog("  官方 App 任务获取失败，继续执行支付宝任务")
                }
                callback(TaskMissionPlanner.merge(mainMissions, appMissions))
            }
        }
    }

    private fun runMissionItems(
        account: Account,
        work: List<MissionWorkItem>,
        index: Int,
        gained: Int,
        generation: Int,
        done: (Int) -> Unit
    ) {
        if (!isTaskActive(generation)) return
        if (index >= work.size) {
            appendTaskLog("  [${account}] 本账号预计获得 $gained 分")
            done(gained)
            return
        }
        val item = work[index]
        val round = if (item.total > 1) " (${item.round}/${item.total})" else ""
        val platformName = if (item.platform == TaskPlatform.APP) "[APP]" else "[支付宝]"
        val token = if (item.platform == TaskPlatform.APP && account.hasAppToken()) account.appToken else account.token
        appendTaskLog("  [${index + 1}/${work.size}] $platformName ${item.name} +${item.score}分$round")
        val delay = taskRateLimiter.reserveDelayMillis(token, System.currentTimeMillis())
        ui.postDelayed({
            if (!isTaskActive(generation)) return@postDelayed
            IlifeApi.scoreSendWithToken(token, account.uid, item.refId) { json, err ->
                runOnUiThread {
                    if (!isTaskActive(generation)) return@runOnUiThread
                    val nextGained = when {
                        json == null -> {
                            appendTaskLog("    ❌ 网络错误: ${err ?: "未知错误"}")
                            gained
                        }
                        json.optInt("code", -999) == 0 -> {
                            appendTaskLog("    ✅ 已提交 +${item.score}分")
                            gained + item.score
                        }
                        json.optInt("code", -999) == -98 -> {
                            appendTaskLog("    ⏳ 请求过于频繁，60秒后重试当前任务")
                            ui.postDelayed({ runMissionItems(account, work, index, gained, generation, done) }, 60000)
                            return@runOnUiThread
                        }
                        else -> {
                            appendTaskLog("    ❌ code=${json.optInt("code", -999)} ${json.optString("msg", "")}")
                            gained
                        }
                    }
                    ui.postDelayed({ runMissionItems(account, work, index + 1, nextGained, generation, done) }, 30000)
                }
            }
        }, delay)
    }

    private fun releaseTaskLease() {
        taskLease?.let(TaskExecutionCoordinator::release)
        taskLease = null
    }

    private fun isTaskActive(generation: Int): Boolean =
        !destroyed && generation == taskGeneration && viewModel.isTaskRunning

    private fun parseTodayDoneCount(scoreJson: JSONObject?): Map<String, Int> {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return emptyMap()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val out = mutableMapOf<String, Int>()
        val arr = scoreJson.optJSONArray("data") ?: return out
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optLong("ctime", 0L) < start) continue
            val adId = item.optJSONObject("data")?.optString("adId", "").orEmpty()
            if (adId.isNotBlank()) out[adId] = (out[adId] ?: 0) + 1
        }
        return out
    }

    private fun appendTaskLog(line: String) {
        viewModel.appendTaskLog(line)
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

    private fun openAddDevice(role: String) {
        val assignment = when (role) {
            "hot" -> DeviceAssignment.HOT.name
            "cold" -> DeviceAssignment.COLD.name
            else -> DeviceAssignment.BOTH.name
        }
        addDevice.launch(Intent(this, DeviceAddActivity::class.java).putExtra(DeviceAddActivity.EXTRA_ASSIGNMENT, assignment))
    }

    private fun assignDevice(role: String, deviceId: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null) {
            toast("请先登录")
            return
        }
        if (deviceId.isBlank()) return
        if (role == "hot") account.hotDid = deviceId else account.coldDid = deviceId
        account.rememberDevice(deviceId)
        AccountStore.updateCurrent(this, account)
        viewModel.reloadAccounts()
        updateWidgets()
        toast("已将 $deviceId 设为${if (role == "hot") "热水" else "冷水"}设备")
    }

    /** 拉取主页数据，把收藏设备列表写入当前账户，自动分配前两个为热/冷水。 */
    private fun fetchDevices() {
        val account = AccountStore.getCurrent(this)
        if (account == null || !account.hasToken()) {
            toast("请先登录")
            return
        }

        toast("拉取设备列表中...")
        IlifeApi.master(this, object : IlifeApi.JsonCallback {
            override fun onResult(json: org.json.JSONObject?, err: String?) {
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    if (json == null) {
                        toast("拉取失败: ${err ?: "未知错误"}")
                        return@runOnUiThread
                    }

                    val code = json.optInt("code", -999)
                    when {
                        code == -99 -> {
                            toast("登录已过期，请重新登录")
                            return@runOnUiThread
                        }
                        code != 0 -> {
                            toast("拉取失败: code=$code")
                            return@runOnUiThread
                        }
                    }

                    val favos = json.optJSONObject("data")?.optJSONArray("favos")
                    if (favos == null || favos.length() == 0) {
                        toast("服务器未返回设备列表，请手动在账户管理中填写")
                        return@runOnUiThread
                    }

                    account.hotDid = favos.optJSONObject(0)?.optString("id", "") ?: ""
                    account.coldDid = if (favos.length() >= 2) {
                        favos.optJSONObject(1)?.optString("id", "") ?: ""
                    } else {
                        account.hotDid
                    }
                    for (index in 0 until favos.length()) {
                        account.rememberDevice(favos.optJSONObject(index)?.optString("id", "").orEmpty())
                    }

                    AccountStore.updateCurrent(this@ConfigActivity, account)
                    toast(if (favos.length() == 1) "已分配 1 个设备（热/冷水共用）" else "已分配 ${favos.length()} 个设备")
                    viewModel.reloadAccounts()
                    updateWidgets()
                }
            }
        })
    }

    private fun testWater(which: String, name: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null) {
            toast("请先登录")
            return
        }
        if (!account.hasAppToken()) {
            toast("出水需要设备控制登录信息，请在账户管理中补充")
            return
        }

        val did = if (which == "hot") account.hotOrFallback() else account.coldOrFallback()
        if (did.isNullOrBlank()) {
            toast("未分配 $name 设备")
            return
        }

        toast("$name 请求中...")
        WaterApi.start(this, did, name) { status ->
            runOnUiThread {
                if (!destroyed) toast(status)
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
