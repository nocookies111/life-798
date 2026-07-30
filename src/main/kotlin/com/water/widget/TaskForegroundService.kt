package com.water.widget

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * 积分任务的唯一执行入口。
 *
 * Activity 仅负责发送启动请求；任务批次、限速等待和网络回调全部由此前台服务持有，
 * 因此旋转屏幕、离开页面或 Activity 被回收都不会中断当前批次。
 */
class TaskForegroundService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val taskRateLimiter = TaskExecutionCoordinator.tokenRateLimiter
    private var taskLease: TaskExecutionCoordinator.Lease? = null
    private var generation = 0
    private var running = false
    private var stoppingNormally = false
    private var completedLanes = 0
    private var hadFailures = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationScope.launch {
            TaskRunRepository.state.collect { state ->
                if (running || stoppingNormally) updateNotification(state)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_RUN_TASKS) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (running || TaskRunRepository.state.value.running) return START_NOT_STICKY

        val accounts = AccountStore.list(this).filter { it.hasToken() }
        if (accounts.isEmpty()) {
            startForegroundCompat(buildNotification(TaskRunState(running = true)))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val lease = TaskExecutionCoordinator.tryAcquire(accounts)
        if (lease == null) {
            val message = "上一次任务请求仍在收尾，请约半分钟后重试。"
            TaskRunRepository.fail(message)
            val failedState = TaskRunRepository.state.value
            startForegroundCompat(buildNotification(failedState))
            updateNotification(failedState)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val plannedLanes = AccountExecutionPlanner.plan(accounts)
        val laneStates = plannedLanes.mapIndexed { index, lane ->
            TaskLaneState(
                laneId = index + 1,
                laneCount = plannedLanes.size,
                accountCount = lane.accounts.size
            )
        }
        taskLease = lease
        taskRateLimiter.clear()
        completedLanes = 0
        hadFailures = false
        running = true
        stoppingNormally = false
        val runGeneration = ++generation

        TaskRunRepository.start(laneStates)
        appendTaskLog("===== 首页任务中心：一键运行全部账号 =====")
        appendTaskLog(
            "共 ${accounts.size} 个账号，分为 ${plannedLanes.size} 个安全通道并发执行；" +
                "共享账号标识、uid 或 Token 的账号将串行执行。"
        )
        startForegroundCompat(buildNotification(TaskRunRepository.state.value))

        plannedLanes.forEachIndexed { laneIndex, lane ->
            runAccountLane(
                accounts = lane.accounts,
                index = 0,
                laneId = laneIndex + 1,
                laneCount = plannedLanes.size,
                runGeneration = runGeneration
            ) {
                if (!isActive(runGeneration)) return@runAccountLane
                TaskRunRepository.completeLane(laneIndex + 1)
                completedLanes += 1
                if (completedLanes == plannedLanes.size) finishRun()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15 对 dataSync 前台服务触发时限回调时停止继续调度。
     * 已发出的 HttpURLConnection 无法可靠强制取消，所以 lease 保留一个网络超时窗口。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        softCancel("任务达到系统运行时限，已安全停止，可稍后重新运行。")
    }

    override fun onDestroy() {
        notificationScope.cancel()
        if (running && !stoppingNormally) {
            generation += 1
            running = false
            mainHandler.removeCallbacksAndMessages(null)
            taskLease?.let(TaskExecutionCoordinator::releaseAfterInFlightNetworkGrace)
            taskLease = null
            TaskRunRepository.cancel("任务服务被系统停止，可重新运行。")
        }
        super.onDestroy()
    }

    private fun runAccountLane(
        accounts: List<Account>,
        index: Int,
        laneId: Int,
        laneCount: Int,
        runGeneration: Int,
        done: () -> Unit
    ) {
        if (!isActive(runGeneration)) return
        if (index >= accounts.size) {
            done()
            return
        }

        val account = accounts[index]
        val accountLabel = account.toString().ifBlank { "账号 ${index + 1}" }
        TaskRunRepository.updateLane(
            laneId = laneId,
            accountIndex = index + 1,
            accountLabel = accountLabel,
            currentTask = "正在加载任务",
            phase = TaskLanePhase.LOADING
        )
        appendTaskLog(
            "\n---------- [$account] 通道 $laneId/$laneCount，" +
                "账号 ${index + 1}/${accounts.size} ----------"
        )
        loadAndRunAccountTasks(account, laneId, runGeneration) { gained ->
            if (!isActive(runGeneration)) return@loadAndRunAccountTasks
            appendTaskLog("  [$account] 本账号获得 $gained 分")
            TaskRunRepository.updateLane(
                laneId = laneId,
                currentTask = if (index + 1 < accounts.size) "准备下一个账号" else "正在收尾",
                phase = TaskLanePhase.WAITING
            )
            postDelayed(ACCOUNT_GAP_MILLIS, runGeneration) {
                runAccountLane(accounts, index + 1, laneId, laneCount, runGeneration, done)
            }
        }
    }

    private fun loadAndRunAccountTasks(
        account: Account,
        laneId: Int,
        runGeneration: Int,
        done: (Int) -> Unit
    ) {
        IlifeApi.missionLstWithToken(account.token) { missionJson, missionErr ->
            onMain {
                if (!isActive(runGeneration)) return@onMain
                if (missionJson == null || missionJson.optInt("code", -999) != 0) {
                    hadFailures = true
                    appendTaskLog(
                        "  [$account] 获取任务列表失败: " +
                            (missionErr ?: missionJson?.optString("msg", "未知错误"))
                    )
                    TaskRunRepository.updateLane(
                        laneId = laneId,
                        currentTask = "任务列表获取失败",
                        phase = TaskLanePhase.FAILED
                    )
                    done(0)
                    return@onMain
                }

                val data = missionJson.optJSONObject("data")
                val missions = data?.optJSONArray("missions")
                val score = data?.optJSONObject("accScoreRsp")?.optInt("validScore", 0) ?: 0
                appendTaskLog("  当前积分: $score，任务数: ${missions?.length() ?: 0}")
                TaskRunRepository.updateLane(
                    laneId = laneId,
                    currentTask = "检查每日签到",
                    phase = TaskLanePhase.SIGNING_IN
                )
                runDailySignInIfNeeded(account, data, laneId, runGeneration) { signGained ->
                    TaskRunRepository.updateLane(
                        laneId = laneId,
                        currentTask = "正在合并任务列表",
                        phase = TaskLanePhase.LOADING_MISSIONS
                    )
                    loadAppMissionsIfNeeded(account, missions, laneId, runGeneration) { plannedMissions ->
                        TaskRunRepository.updateLane(
                            laneId = laneId,
                            currentTask = "核对今日任务进度",
                            phase = TaskLanePhase.LOADING_MISSIONS
                        )
                        IlifeApi.scoreLstWithToken(account.token) { scoreJson, scoreErr ->
                            onMain {
                                if (!isActive(runGeneration)) return@onMain
                                if (scoreJson == null || scoreJson.optInt("code", -999) != 0) {
                                    hadFailures = true
                                    appendTaskLog(
                                        "  [$account] 获取今日任务进度失败: " +
                                            (scoreErr ?: scoreJson?.optString("msg", "未知错误"))
                                    )
                                }
                                val doneCount = parseTodayDoneCount(scoreJson)
                                val work = TaskMissionPlanner.buildWork(plannedMissions, doneCount)
                                if (work.isEmpty()) {
                                    appendTaskLog("  [$account] 无可执行任务（已完成/无积分/已过滤）")
                                    TaskRunRepository.updateLane(
                                        laneId = laneId,
                                        currentTask = "没有待执行任务",
                                        phase = TaskLanePhase.WAITING
                                    )
                                    done(signGained)
                                } else {
                                    appendTaskLog("  待执行 ${work.size} 次任务")
                                    runMissionItems(
                                        account = account,
                                        work = work,
                                        index = 0,
                                        gained = signGained,
                                        laneId = laneId,
                                        runGeneration = runGeneration,
                                        done = done
                                    )
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
        laneId: Int,
        runGeneration: Int,
        done: (Int) -> Unit
    ) {
        if (!isActive(runGeneration)) return
        val dailyRsp = data?.optJSONObject("dailyRSP")
        val weekMask = data?.optJSONObject("accScoreRsp")
            ?.optJSONObject("daily")
            ?.optInt("week", 0) ?: 0
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
                appendTaskLog("  [$account] 每日签到: 无签到数据，跳过")
                done(0)
            }
            plan.alreadySigned -> {
                appendTaskLog("  [$account] 每日签到: 今日已签到")
                done(0)
            }
            else -> {
                appendTaskLog("  [$account] 每日签到: 未签到，优先执行")
                sendDailySignIn(account, plan, laneId, runGeneration, retry = false, done = done)
            }
        }
    }

    private fun sendDailySignIn(
        account: Account,
        plan: DailySignInPlan,
        laneId: Int,
        runGeneration: Int,
        retry: Boolean,
        done: (Int) -> Unit
    ) {
        if (!isActive(runGeneration)) return
        val token = account.token.takeIf { it.isNotBlank() }
            ?: account.appToken.takeIf { it.isNotBlank() }
        if (token.isNullOrBlank()) {
            hadFailures = true
            appendTaskLog("    [$account] 签到失败: 无可用 Token，继续任务")
            done(0)
            return
        }

        TaskRunRepository.updateLane(
            laneId = laneId,
            currentTask = if (retry) "重试每日签到" else "每日签到",
            phase = TaskLanePhase.SIGNING_IN
        )
        val delay = taskRateLimiter.reserveDelayMillis(token, System.currentTimeMillis())
        postDelayed(delay, runGeneration) {
            IlifeApi.scoreSendSignIn(token, account.uid, plan.weekDay, plan.adId) { json, err ->
                onMain {
                    if (!isActive(runGeneration)) return@onMain
                    when {
                        json?.optInt("code", -999) == 0 -> {
                            appendTaskLog(
                                "    [$account] 签到成功 +${plan.baseScore}分" +
                                    if (retry) "（重试）" else ""
                            )
                            plan.rewards.forEach { reward ->
                                val description = reward.description
                                    .takeIf { it.isNotBlank() }
                                    ?.let { "（$it）" }
                                    .orEmpty()
                                appendTaskLog("    [$account] 连签奖励 +${reward.score}分$description")
                            }
                            TaskRunRepository.addPoints(laneId, plan.totalScore)
                            done(plan.totalScore)
                        }
                        json?.optInt("code", -999) == -98 && !retry -> {
                            appendTaskLog("    [$account] 签到频率限制，60秒后重试一次")
                            TaskRunRepository.updateLane(
                                laneId = laneId,
                                currentTask = "签到受限，等待重试",
                                phase = TaskLanePhase.WAITING
                            )
                            postDelayed(RETRY_DELAY_MILLIS, runGeneration) {
                                sendDailySignIn(
                                    account,
                                    plan,
                                    laneId,
                                    runGeneration,
                                    retry = true,
                                    done = done
                                )
                            }
                        }
                        else -> {
                            hadFailures = true
                            val code = json?.optInt("code", -999)
                            val reason = err ?: json?.optString("msg", "code=$code") ?: "未知错误"
                            appendTaskLog("    [$account] 签到失败: $reason，继续任务")
                            done(0)
                        }
                    }
                }
            }
        }
    }

    private fun loadAppMissionsIfNeeded(
        account: Account,
        mainMissions: JSONArray?,
        laneId: Int,
        runGeneration: Int,
        callback: (List<PlannedMission>) -> Unit
    ) {
        if (!isActive(runGeneration)) return
        if (!account.hasAppToken()) {
            callback(TaskMissionPlanner.merge(mainMissions, null))
            return
        }
        IlifeApi.missionLstWithToken(account.appToken) { appJson, _ ->
            onMain {
                if (!isActive(runGeneration)) return@onMain
                var appMissions: JSONArray? = null
                if (appJson != null && appJson.optInt("code", -999) == 0) {
                    appMissions = appJson.optJSONObject("data")?.optJSONArray("missions")
                    appendTaskLog("  官方 App 任务数: ${appMissions?.length() ?: 0}，已合并去重")
                } else {
                    hadFailures = true
                    appendTaskLog("  [$account] 官方 App 任务获取失败，继续执行支付宝任务")
                    TaskRunRepository.updateLane(
                        laneId = laneId,
                        currentTask = "App 任务获取失败，继续运行",
                        phase = TaskLanePhase.LOADING_MISSIONS
                    )
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
        laneId: Int,
        runGeneration: Int,
        done: (Int) -> Unit
    ) {
        if (!isActive(runGeneration)) return
        if (index >= work.size) {
            done(gained)
            return
        }

        val item = work[index]
        val round = if (item.total > 1) " (${item.round}/${item.total})" else ""
        val notificationRound = if (item.total > 1) "（${item.round}/${item.total}）" else ""
        val platformName = if (item.platform == TaskPlatform.APP) "[APP]" else "[支付宝]"
        val token = if (item.platform == TaskPlatform.APP && account.hasAppToken()) {
            account.appToken
        } else {
            account.token
        }
        TaskRunRepository.updateLane(
            laneId = laneId,
            currentTask = "$platformName ${item.name}$notificationRound",
            phase = TaskLanePhase.RUNNING_MISSION
        )
        appendTaskLog("  [${index + 1}/${work.size}] $platformName ${item.name} +${item.score}分$round")
        val delay = taskRateLimiter.reserveDelayMillis(token, System.currentTimeMillis())
        postDelayed(delay, runGeneration) {
            IlifeApi.scoreSendWithToken(token, account.uid, item.refId) { json, err ->
                onMain {
                    if (!isActive(runGeneration)) return@onMain
                    val nextGained = when {
                        json == null -> {
                            hadFailures = true
                            appendTaskLog(
                                "    [$account] $platformName ${item.name}$round：" +
                                    "网络错误 ${err ?: "未知错误"}"
                            )
                            gained
                        }
                        json.optInt("code", -999) == 0 -> {
                            appendTaskLog(
                                "    [$account] $platformName ${item.name}$round：" +
                                    "成功 +${item.score}分"
                            )
                            TaskRunRepository.addPoints(laneId, item.score)
                            gained + item.score
                        }
                        json.optInt("code", -999) == -98 -> {
                            appendTaskLog(
                                "    [$account] $platformName ${item.name}$round：" +
                                    "请求过于频繁，60秒后重试"
                            )
                            TaskRunRepository.updateLane(
                                laneId = laneId,
                                currentTask = "${item.name}受限，等待重试",
                                phase = TaskLanePhase.WAITING
                            )
                            postDelayed(RETRY_DELAY_MILLIS, runGeneration) {
                                runMissionItems(
                                    account,
                                    work,
                                    index,
                                    gained,
                                    laneId,
                                    runGeneration,
                                    done
                                )
                            }
                            return@onMain
                        }
                        else -> {
                            hadFailures = true
                            appendTaskLog(
                                "    [$account] $platformName ${item.name}$round：" +
                                    "失败 code=${json.optInt("code", -999)} ${json.optString("msg", "")}"
                            )
                            gained
                        }
                    }
                    TaskRunRepository.updateLane(
                        laneId = laneId,
                        currentTask = if (index + 1 < work.size) {
                            "等待下一项任务"
                        } else {
                            "正在收尾"
                        },
                        phase = TaskLanePhase.WAITING
                    )
                    postDelayed(MISSION_GAP_MILLIS, runGeneration) {
                        runMissionItems(
                            account,
                            work,
                            index + 1,
                            nextGained,
                            laneId,
                            runGeneration,
                            done
                        )
                    }
                }
            }
        }
    }

    private fun finishRun() {
        if (!running) return
        val gained = TaskRunRepository.state.value.totalGained
        appendTaskLog(
            if (hadFailures) {
                "===== 任务结束，部分未完成，本次获得 $gained 分 ====="
            } else {
                "===== 全部完成，本次获得 $gained 分 ====="
            }
        )
        TaskRunRepository.finish(partial = hadFailures)
        taskLease?.let(TaskExecutionCoordinator::release)
        taskLease = null
        running = false
        stoppingNormally = true
        updateNotification(TaskRunRepository.state.value)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun softCancel(message: String) {
        if (!running) return
        generation += 1
        running = false
        stoppingNormally = true
        mainHandler.removeCallbacksAndMessages(null)
        taskLease?.let(TaskExecutionCoordinator::releaseAfterInFlightNetworkGrace)
        taskLease = null
        TaskRunRepository.cancel(message)
        updateNotification(TaskRunRepository.state.value)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun isActive(runGeneration: Int): Boolean =
        running && generation == runGeneration && TaskRunRepository.state.value.running

    private fun appendTaskLog(line: String) {
        TaskRunRepository.appendLog(line)
        updateNotification(TaskRunRepository.state.value)
    }

    private fun postDelayed(delayMillis: Long, runGeneration: Int, action: () -> Unit) {
        if (!isActive(runGeneration)) return
        mainHandler.postDelayed(
            {
                if (isActive(runGeneration)) action()
            },
            delayMillis.coerceAtLeast(0L)
        )
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun parseTodayDoneCount(scoreJson: JSONObject?): Map<String, Int> {
        if (scoreJson == null || scoreJson.optInt("code", -999) != 0) return emptyMap()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val out = mutableMapOf<String, Int>()
        val items = scoreJson.optJSONArray("data") ?: return out
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optLong("ctime", 0L) < start) continue
            val adId = item.optJSONObject("data")?.optString("adId", "").orEmpty()
            if (adId.isNotBlank()) out[adId] = (out[adId] ?: 0) + 1
        }
        return out
    }

    private fun createNotificationChannel() {
        AppNotifications.ensureChannels(this)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppNotifications.TASK_PROGRESS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AppNotifications.TASK_PROGRESS_ID, notification)
        }
    }

    private fun updateNotification(state: TaskRunState) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(AppNotifications.TASK_PROGRESS_ID, buildNotification(state))
        } catch (_: SecurityException) {
            // Android 13+ 用户可关闭普通通知；前台服务本身仍由系统任务管理器展示。
        }
    }

    private fun buildNotification(state: TaskRunState): Notification {
        val content = TaskNotificationTextFormatter.format(state)
        val builder = Notification.Builder(this, AppNotifications.CHANNEL_TASK_PROGRESS)
            .setSmallIcon(com.water.widget.R.drawable.ic_water_drop)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(Notification.BigTextStyle().bigText(content.text))
            .setContentIntent(AppNotifications.openApp(this, false, AppNotifications.TASK_PROGRESS_ID))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(state.running)
            .setAutoCancel(!state.running)
            .setProgress(0, 0, state.running)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    enum class StartResult {
        STARTED,
        ALREADY_RUNNING,
        NO_ACCOUNTS,
        FAILED
    }

    companion object {
        const val ACTION_RUN_TASKS = "com.water.widget.action.RUN_TASKS"
        private const val ACCOUNT_GAP_MILLIS = 1_200L
        private const val MISSION_GAP_MILLIS = 30_000L
        private const val RETRY_DELAY_MILLIS = 60_000L

        fun start(context: Context): StartResult {
            if (TaskRunRepository.state.value.running) return StartResult.ALREADY_RUNNING
            if (AccountStore.list(context).none { it.hasToken() }) return StartResult.NO_ACCOUNTS
            return try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, TaskForegroundService::class.java).setAction(ACTION_RUN_TASKS)
                )
                StartResult.STARTED
            } catch (_: RuntimeException) {
                StartResult.FAILED
            }
        }
    }
}
