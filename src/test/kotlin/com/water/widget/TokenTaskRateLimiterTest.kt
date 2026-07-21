package com.water.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenTaskRateLimiterTest {
    @Test
    fun `同一 token 的预留至少间隔三十秒`() {
        val limiter = TokenTaskRateLimiter()

        assertEquals(0L, limiter.reserveDelayMillis("token", 1_000L))
        assertEquals(30_000L, limiter.reserveDelayMillis("token", 1_000L))
        assertEquals(60_000L, limiter.reserveDelayMillis("token", 1_000L))
    }

    @Test
    fun `不同 token 可在相同时间执行`() {
        val limiter = TokenTaskRateLimiter()

        assertEquals(0L, limiter.reserveDelayMillis("token-a", 1_000L))
        assertEquals(0L, limiter.reserveDelayMillis("token-b", 1_000L))
    }

    @Test
    fun `时间推进后只等待剩余间隔`() {
        val limiter = TokenTaskRateLimiter()
        limiter.reserveDelayMillis("token", 1_000L)

        assertEquals(10_000L, limiter.reserveDelayMillis("token", 21_000L))
    }
}
