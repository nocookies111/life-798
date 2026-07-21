package com.water.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AccountExecutionPlannerTest {
    @Test
    fun `不同 token 的账户分别进入并发通道`() {
        val first = account("100", "main-1", "app-1")
        val second = account("200", "main-2", "app-2")

        val lanes = AccountExecutionPlanner.plan(listOf(first, second))

        assertEquals(2, lanes.size)
        assertEquals(listOf(first), lanes[0].accounts)
        assertEquals(listOf(second), lanes[1].accounts)
    }

    @Test
    fun `共享任一 token 的账户保持同一串行通道`() {
        val first = account("100", "shared-main", "app-1")
        val second = account("200", "main-2", "shared-main")
        val third = account("300", "main-3", "app-3")

        val lanes = AccountExecutionPlanner.plan(listOf(first, second, third))

        assertEquals(2, lanes.size)
        assertEquals(listOf(first, second), lanes[0].accounts)
        assertSame(third, lanes[1].accounts.single())
    }

    @Test
    fun `经由共享 token 关联的账户合并为同一通道`() {
        val first = account("100", "token-a", "")
        val second = account("200", "token-a", "token-b")
        val third = account("300", "token-b", "")

        val lanes = AccountExecutionPlanner.plan(listOf(first, second, third))

        assertEquals(1, lanes.size)
        assertEquals(listOf(first, second, third), lanes.single().accounts)
    }

    @Test
    fun `相同账户标识即使 token 不同仍保持同一串行通道`() {
        val first = account("100", "main-1", "app-1")
        val second = account("100", "main-2", "app-2")

        val lanes = AccountExecutionPlanner.plan(listOf(first, second))

        assertEquals(1, lanes.size)
        assertEquals(listOf(first, second), lanes.single().accounts)
    }

    @Test
    fun `共享 uid 的账户保持同一串行通道`() {
        val first = account("100", "main-1", "app-1").apply { uid = "uid-a" }
        val second = account("200", "main-2", "app-2").apply { uid = "uid-a" }

        val lanes = AccountExecutionPlanner.plan(listOf(first, second))

        assertEquals(1, lanes.size)
        assertEquals(listOf(first, second), lanes.single().accounts)
    }

    private fun account(phone: String, token: String, appToken: String) = Account(phone).apply {
        this.token = token
        this.appToken = appToken
    }
}
