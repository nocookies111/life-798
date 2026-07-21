package com.water.widget.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateFactoryTest {
    @Test
    fun `积分任务模式推荐日常服务登录`() {
        val state = LoginUiStateFactory.from(LoginPurpose.SCORE_ONLY, LoginPlatform.ALIPAY, false, false)

        assertEquals("积分任务", state.purposeTitle)
        assertEquals("日常服务登录", state.platformTitle)
        assertTrue(state.canLoadCaptcha)
        assertFalse(state.canSendSms)
        assertFalse(state.canLogin)
    }

    @Test
    fun `设备控制模式推荐设备控制登录`() {
        val state = LoginUiStateFactory.from(LoginPurpose.WATER_CONTROL, LoginPlatform.APP, true, true)

        assertEquals("设备控制", state.purposeTitle)
        assertEquals("设备控制登录", state.platformTitle)
        assertTrue(state.canLoadCaptcha)
        assertTrue(state.canSendSms)
        assertTrue(state.canLogin)
    }

    @Test
    fun `手机号无效时不能加载图形验证码`() {
        val state = LoginUiStateFactory.from(
            purpose = LoginPurpose.ALL_IN_ONE,
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
    fun `验证码为空时不能发送短信 短信为空时不能登录`() {
        val noGraph = LoginUiStateFactory.from(LoginPurpose.ALL_IN_ONE, LoginPlatform.ALIPAY, true, false, "13800000000", "", "")
        val noSms = LoginUiStateFactory.from(LoginPurpose.ALL_IN_ONE, LoginPlatform.ALIPAY, true, true, "13800000000", "1234", "")
        val ready = LoginUiStateFactory.from(LoginPurpose.ALL_IN_ONE, LoginPlatform.ALIPAY, true, true, "13800000000", "1234", "5678")

        assertFalse(noGraph.canSendSms)
        assertFalse(noSms.canLogin)
        assertTrue(ready.canLogin)
    }
}
