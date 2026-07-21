package com.water.widget.ui

enum class LoginPurpose(val title: String, val description: String) {
    SCORE_ONLY("积分任务", "登录后可完成每日签到、日常任务并查看积分明细"),
    WATER_CONTROL("设备控制", "登录后可使用热水、冷水等设备控制，也能补充官方应用专属任务"),
    ALL_IN_ONE("都需要", "完成两种登录后，可使用积分和设备控制的完整服务")
}

enum class LoginPlatform(val title: String, val description: String) {
    ALIPAY("日常服务登录", "用于签到、积分任务和积分看板；登录信息可能会定期失效"),
    APP("设备控制登录", "用于热水、冷水等设备控制，也可获取官方应用专属任务")
}

data class LoginUiState(
    val purpose: LoginPurpose,
    val platform: LoginPlatform,
    val purposeTitle: String,
    val purposeDescription: String,
    val platformTitle: String,
    val platformDescription: String,
    val nextHint: String,
    val canLoadCaptcha: Boolean,
    val canSendSms: Boolean,
    val canLogin: Boolean
)

object LoginUiStateFactory {
    fun from(
        purpose: LoginPurpose,
        platform: LoginPlatform,
        captchaLoaded: Boolean,
        smsSent: Boolean,
        phone: String = "13800000000",
        graphCode: String = "1234",
        smsCode: String = "1234"
    ): LoginUiState {
        val nextHint = when {
            !captchaLoaded -> "第 1 步：输入手机号并加载图形验证码"
            !smsSent -> "第 2 步：填写图形验证码并发送短信"
            else -> "第 3 步：输入短信验证码完成登录"
        }

        return LoginUiState(
            purpose = purpose,
            platform = platform,
            purposeTitle = purpose.title,
            purposeDescription = purpose.description,
            platformTitle = platform.title,
            platformDescription = platform.description,
            nextHint = nextHint,
            canLoadCaptcha = phone.matches(Regex("^1\\d{10}$")),
            canSendSms = captchaLoaded && graphCode.isNotBlank(),
            canLogin = smsSent && smsCode.isNotBlank()
        )
    }
}
