package com.water.widget

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 进程内任务批次的独占协调器。
 *
 * 同一批次会占有账户 phone、uid 和所有非空 Token；任一标识重合时，后续入口不能重复调度。
 * Token 限速器同样在进程内共享，避免不同 Activity 对同一个凭据各自计时。
 */
object TaskExecutionCoordinator {
    // IlifeApi 的连接超时和读取超时各为 15 秒，窗口需覆盖最坏的串行超时。
    private const val IN_FLIGHT_NETWORK_GRACE_MILLIS = 31_000L
    private val releaseExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "water-task-lease-release").apply { isDaemon = true }
    }

    class Lease internal constructor(
        internal val ownerId: Long,
        internal val keys: Set<String>
    )

    private val ownerByKey = mutableMapOf<String, Long>()
    private val delayedReleases = mutableMapOf<Long, ScheduledFuture<*>>()
    private var nextOwnerId = 0L

    /** 所有任务入口共用，确保跨 Activity 的同 Token 请求仍保持间隔。 */
    val tokenRateLimiter = TokenTaskRateLimiter()

    @Synchronized
    fun tryAcquire(accounts: List<Account>): Lease? {
        val keys = accounts.flatMap(::accountKeys).toSet()
        if (keys.any(ownerByKey::containsKey)) return null

        val ownerId = ++nextOwnerId
        keys.forEach { key -> ownerByKey[key] = ownerId }
        return Lease(ownerId, keys)
    }

    /** 正常完成时立即释放批次所有权。重复释放安全。 */
    @Synchronized
    fun release(lease: Lease) {
        delayedReleases.remove(lease.ownerId)?.cancel(false)
        lease.keys.forEach { key ->
            if (ownerByKey[key] == lease.ownerId) ownerByKey.remove(key)
        }
    }

    /**
     * 页面销毁时无法取消已经送出的 HttpURLConnection，因此保留一个读超时窗口。
     * 未发出的 Handler 任务会由 Activity 清除，随后才允许相同凭据的新批次获取 lease。
     */
    @Synchronized
    fun releaseAfterInFlightNetworkGrace(lease: Lease) {
        if (delayedReleases.containsKey(lease.ownerId)) return
        val future = releaseExecutor.schedule(
            { release(lease) },
            IN_FLIGHT_NETWORK_GRACE_MILLIS,
            TimeUnit.MILLISECONDS
        )
        delayedReleases[lease.ownerId] = future
    }

    private fun accountKeys(account: Account): List<String> = buildList {
        account.phone?.trim()?.takeIf(String::isNotEmpty)?.let { add("phone:$it") }
        account.uid?.trim()?.takeIf(String::isNotEmpty)?.let { add("uid:$it") }
        account.token?.trim()?.takeIf(String::isNotEmpty)?.let { add("token:$it") }
        account.appToken?.trim()?.takeIf(String::isNotEmpty)?.let { add("token:$it") }
    }
}
