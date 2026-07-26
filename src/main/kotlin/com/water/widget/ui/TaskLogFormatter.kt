package com.water.widget.ui

/**
 * 将执行层的诊断信息转换成适合直接展示给用户的运行记录。
 * 调度、并发和 Token 复用策略仍由执行层负责，但不占用界面日志空间。
 */
object TaskLogFormatter {
    private val accountHeader = Regex("""^-+\s*\[(.+?)]\s+通道.+?-+$""")
    private val accountResult = Regex("""^\[(.+?)]\s+本账号预计获得\s+(\d+)\s+分$""")
    private val allDone = Regex("""^=+\s*全部完成，本次预计获得\s+(\d+)\s+分\s*=+$""")
    private val taskPlan = Regex("""^\[\d+/\d+]\s+\[(?:APP|支付宝)]""")

    fun format(raw: String): String? {
        val line = raw.trim()
        if (line.isBlank()) return null

        accountHeader.matchEntire(line)?.let {
            return "正在处理：${it.groupValues[1]}"
        }
        accountResult.matchEntire(line)?.let {
            return "${it.groupValues[1]}：完成 · 获得 ${it.groupValues[2]} 分"
        }
        allDone.matchEntire(line)?.let {
            return "全部完成 · 本次获得 ${it.groupValues[1]} 分"
        }
        if (line.contains("首页任务中心：一键运行全部账号")) {
            return "开始运行全部账号"
        }

        if (line.contains("安全通道并发执行") ||
            line.contains("共享账号标识") ||
            line.contains("当前积分:") ||
            line.contains("官方 App 任务数:") ||
            line.startsWith("待执行 ") ||
            line.startsWith("每日签到: 未签到") ||
            line.startsWith("每日签到: 无签到数据") ||
            line.startsWith("✅ 已提交") ||
            taskPlan.containsMatchIn(line)
        ) {
            return null
        }

        return line
            .replace("❌ ", "")
            .replace("⏳ ", "")
            .replace(Regex("""\s+"""), " ")
    }
}
