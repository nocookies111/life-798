package com.water.widget

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TaskExecutionCoordinatorTest {
    @Test
    fun `冲突批次在持有期间被拒绝且释放后可重试`() {
        val first = account(phone = "100", token = "token-a", appToken = "app-a", uid = "uid-a")
        val sharedToken = account(phone = "200", token = "token-a", appToken = "app-b", uid = "uid-b")

        val lease = TaskExecutionCoordinator.tryAcquire(listOf(first))
        assertNotNull(lease)
        assertNull(TaskExecutionCoordinator.tryAcquire(listOf(sharedToken)))

        TaskExecutionCoordinator.release(requireNotNull(lease))
        val retry = TaskExecutionCoordinator.tryAcquire(listOf(sharedToken))
        assertNotNull(retry)
        TaskExecutionCoordinator.release(requireNotNull(retry))
    }

    @Test
    fun `phone 或 uid 重合的批次不能并行`() {
        val held = TaskExecutionCoordinator.tryAcquire(listOf(account("100", "token-a", "app-a", "uid-a")))
        assertNotNull(held)
        assertNull(TaskExecutionCoordinator.tryAcquire(listOf(account("100", "token-b", "app-b", "uid-b"))))
        assertNull(TaskExecutionCoordinator.tryAcquire(listOf(account("200", "token-c", "app-c", "uid-a"))))

        TaskExecutionCoordinator.release(requireNotNull(held))
    }

    @Test
    fun `冲突批次不会部分占有未冲突标识`() {
        val held = TaskExecutionCoordinator.tryAcquire(listOf(account("100", "token-a", "app-a", "uid-a")))
        assertNotNull(held)

        assertNull(
            TaskExecutionCoordinator.tryAcquire(
                listOf(
                    account("200", "token-b", "app-b", "uid-b"),
                    account("300", "token-a", "app-c", "uid-c")
                )
            )
        )
        val independent = TaskExecutionCoordinator.tryAcquire(listOf(account("200", "token-b", "app-b", "uid-b")))
        assertNotNull(independent)

        TaskExecutionCoordinator.release(requireNotNull(held))
        TaskExecutionCoordinator.release(requireNotNull(independent))
    }

    @Test
    fun `不同账户与 token 的批次可同时取得所有权`() {
        val first = TaskExecutionCoordinator.tryAcquire(listOf(account("100", "token-a", "app-a", "uid-a")))
        val second = TaskExecutionCoordinator.tryAcquire(listOf(account("200", "token-b", "app-b", "uid-b")))

        assertNotNull(first)
        assertNotNull(second)
        TaskExecutionCoordinator.release(requireNotNull(first))
        TaskExecutionCoordinator.release(requireNotNull(second))
    }

    @Test
    fun `所有批次共享同一个 token 限速器`() {
        TaskExecutionCoordinator.tokenRateLimiter.clear()
        org.junit.Assert.assertEquals(0L, TaskExecutionCoordinator.tokenRateLimiter.reserveDelayMillis("shared", 1_000L))
        org.junit.Assert.assertEquals(30_000L, TaskExecutionCoordinator.tokenRateLimiter.reserveDelayMillis("shared", 1_000L))
        TaskExecutionCoordinator.tokenRateLimiter.clear()
    }

    private fun account(phone: String, token: String, appToken: String, uid: String) = Account(phone).apply {
        this.token = token
        this.appToken = appToken
        this.uid = uid
    }
}
