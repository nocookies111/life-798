package com.water.widget.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateFactoryTest {
    @Test
    fun `积分服务登录展示精简文案`() {
        val state = LoginUiStateFactory.from(LoginPlatform.ALIPAY, false, false, "13800000000", "", "")

        assertEquals("积分服务", state.platformTitle)
        assertEquals("保存积分服务登录", state.actionLabel)
        assertTrue(state.canLoadCaptcha)
        assertFalse(state.canSendSms)
        assertFalse(state.canLogin)
    }

    @Test
    fun `设备控制登录使用对应保存按钮`() {
        val state = LoginUiStateFactory.from(LoginPlatform.APP, true, true, "13800000000", "1234", "1234")

        assertEquals("设备控制", state.platformTitle)
        assertEquals("保存设备控制登录", state.actionLabel)
        assertTrue(state.canSendSms)
        assertTrue(state.canLogin)
    }

    @Test
    fun `手机号无效时不能刷新图形验证码`() {
        val state = LoginUiStateFactory.from(
            platform = LoginPlatform.ALIPAY,
            captchaLoaded = false,
            smsSent = false,
            phone = "123",
            graphCode = "",
            smsCode = ""
        )

        assertFalse(state.canLoadCaptcha)
        assertFalse(state.canSendSms)
        assertFalse(state.canLogin)
    }

    @Test
    fun `验证码为空时不能发送短信或登录`() {
        val noGraph = LoginUiStateFactory.from(LoginPlatform.ALIPAY, true, false, "13800000000", "", "")
        val noSms = LoginUiStateFactory.from(LoginPlatform.ALIPAY, true, true, "13800000000", "1234", "")
        val ready = LoginUiStateFactory.from(LoginPlatform.ALIPAY, true, true, "13800000000", "1234", "5678")

        assertFalse(noGraph.canSendSms)
        assertFalse(noSms.canLogin)
        assertTrue(ready.canLogin)
    }
}
