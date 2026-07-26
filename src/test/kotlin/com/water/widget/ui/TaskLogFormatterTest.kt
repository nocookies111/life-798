package com.water.widget.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskLogFormatterTest {
    @Test
    fun hidesInternalSchedulingDetails() {
        assertNull(
            TaskLogFormatter.format(
                "共 2 个账号，分为 1 个安全通道并发执行；共享账号标识、uid 或 Token 的账号将串行执行。"
            )
        )
    }

    @Test
    fun convertsAccountHeaderToReadableProgress() {
        assertEquals(
            "正在处理：测试账号",
            TaskLogFormatter.format(
                "---------- [测试账号] 通道 1/2，账号 1/2 ----------"
            )
        )
    }

    @Test
    fun keepsOnlyFinalTaskSummary() {
        assertNull(TaskLogFormatter.format("[1/8] [APP] 浏览任务 +10分"))
        assertNull(TaskLogFormatter.format("✅ 已提交 +10分"))
        assertEquals(
            "测试账号：完成 · 获得 80 分",
            TaskLogFormatter.format("[测试账号] 本账号预计获得 80 分")
        )
        assertEquals(
            "全部完成 · 本次获得 80 分",
            TaskLogFormatter.format("===== 全部完成，本次预计获得 80 分 =====")
        )
    }
}
