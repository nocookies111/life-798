package com.water.widget.ui

enum class LoginPlatform(val title: String, val description: String, val actionLabel: String) {
    ALIPAY("积分服务", "积分、签到与日常任务", "保存积分服务登录"),
    APP("设备控制", "设备启动与应用任务", "保存设备控制登录")
}

data class LoginUiState(
    val platform: LoginPlatform,
    val platformTitle: String,
    val platformDescription: String,
    val actionLabel: String,
    val nextHint: String,
    val canLoadCaptcha: Boolean,
    val canSendSms: Boolean,
    val canLogin: Boolean
)

object LoginUiStateFactory {
    fun from(
        platform: LoginPlatform,
        captchaLoaded: Boolean,
        smsSent: Boolean,
        phone: String,
        graphCode: String,
        smsCode: String
    ): LoginUiState {
        val nextHint = when {
            !captchaLoaded -> "输入手机号并刷新图形验证码"
            !smsSent -> "填写图形验证码并获取短信"
            else -> "填写短信验证码"
        }

        return LoginUiState(
            platform = platform,
            platformTitle = platform.title,
            platformDescription = platform.description,
            actionLabel = platform.actionLabel,
            nextHint = nextHint,
            canLoadCaptcha = phone.matches(Regex("^1\\d{10}$")),
            canSendSms = captchaLoaded && graphCode.isNotBlank(),
            canLogin = smsSent && smsCode.isNotBlank()
        )
    }
}
