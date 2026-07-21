package com.water.widget

/**
 * 为提交积分和签到请求保留每个 Token 的最早可执行时间。
 * 调用方在发起网络请求前调用 reserveDelayMillis，再延迟对应时长执行请求。
 */
class TokenTaskRateLimiter(private val minimumIntervalMillis: Long = MINIMUM_INTERVAL_MILLIS) {
    private val nextAllowedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun reserveDelayMillis(token: String, nowMillis: Long): Long {
        val normalized = token.trim()
        require(normalized.isNotEmpty())
        val allowedAt = nextAllowedAt[normalized] ?: nowMillis
        val scheduledAt = maxOf(nowMillis, allowedAt)
        nextAllowedAt[normalized] = scheduledAt + minimumIntervalMillis
        return scheduledAt - nowMillis
    }

    @Synchronized
    fun clear() {
        nextAllowedAt.clear()
    }

    companion object {
        const val MINIMUM_INTERVAL_MILLIS = 30_000L
    }
}
