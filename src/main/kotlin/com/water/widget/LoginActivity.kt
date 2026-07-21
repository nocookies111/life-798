package com.water.widget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.water.widget.ui.LoginPlatform
import com.water.widget.ui.LoginPurpose
import com.water.widget.ui.LoginScreen
import com.water.widget.ui.LoginUiStateFactory
import com.water.widget.ui.WaterTheme
import org.json.JSONObject

/**
 * Compose 版登录页：引导用户选择账户统计或设备控制所需的登录方式。
 */
class LoginActivity : ComponentActivity() {
    private var purpose = LoginPurpose.ALL_IN_ONE
    private var platform = LoginPlatform.ALIPAY
    private var phone = ""
    private var graphCode = ""
    private var smsCode = ""
    private var captchaKey = ""
    private var captchaBitmap: Bitmap? = null
    private var smsSent = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
        render()
    }

    private fun render() {
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                LoginScreen(
                    state = LoginUiStateFactory.from(purpose, platform, captchaBitmap != null, smsSent, phone, graphCode, smsCode),
                    phone = phone,
                    graphCode = graphCode,
                    smsCode = smsCode,
                    captchaBitmap = captchaBitmap,
                    loading = loading,
                    onPurposeChange = { changePurpose(it) },
                    onPlatformChange = { platform = it; smsSent = false; render() },
                    onPhoneChange = { phone = it.trim(); smsSent = false; render() },
                    onGraphCodeChange = { graphCode = it.trim(); render() },
                    onSmsCodeChange = { smsCode = it.trim(); render() },
                    onLoadCaptcha = { loadCaptcha() },
                    onSendSms = { sendSms() },
                    onLogin = { doLogin() }
                )
            }
        }
    }

    private fun changePurpose(newPurpose: LoginPurpose) {
        purpose = newPurpose
        platform = when (newPurpose) {
            LoginPurpose.SCORE_ONLY -> LoginPlatform.ALIPAY
            LoginPurpose.WATER_CONTROL -> LoginPlatform.APP
            LoginPurpose.ALL_IN_ONE -> platform
        }
        smsSent = false
        render()
    }

    private fun loadCaptcha() {
        if (!isValidPhone(phone)) {
            toast("请输入正确的手机号")
            return
        }
        setLoading(true)
        captchaKey = IlifeApi.newCaptchaKey()
        IlifeApi.captcha(captchaKey) { bytes, err ->
            runOnUiThread {
                setLoading(false)
                if (bytes == null) {
                    toast("获取图形码失败: ${err ?: "未知错误"}")
                    return@runOnUiThread
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp == null) {
                    toast("图形码解析失败")
                    return@runOnUiThread
                }
                captchaBitmap = bmp
                smsSent = false
                toast("图形码已加载，请填写后发送短信")
                render()
            }
        }
    }

    private fun sendSms() {
        if (!isValidPhone(phone)) {
            toast("请输入正确的手机号")
            return
        }
        if (graphCode.isBlank()) {
            toast("请输入图形验证码")
            return
        }
        setLoading(true)
        IlifeApi.sendSms(phone, graphCode, captchaKey) { json, err ->
            runOnUiThread {
                setLoading(false)
                if (json == null) {
                    toast("发送出错: ${err ?: "未知错误"}")
                    return@runOnUiThread
                }
                val code = json.optInt("code", -999)
                if (code == 0) {
                    smsSent = true
                    toast("短信已发送，请查收")
                } else {
                    smsSent = false
                    toast("发送失败: ${json.optString("msg", "code=$code")}")
                }
                render()
            }
        }
    }

    private fun doLogin() {
        if (smsCode.isBlank()) {
            toast("请输入短信验证码")
            return
        }
        setLoading(true)
        IlifeApi.login(phone, smsCode, platform == LoginPlatform.APP) { json, err ->
            runOnUiThread {
                setLoading(false)
                if (json == null) {
                    toast("登录出错: ${err ?: "未知错误"}")
                    return@runOnUiThread
                }
                handleLoginResult(json)
            }
        }
    }

    private fun handleLoginResult(json: JSONObject) {
        val code = json.optInt("code", -999)
        if (code != 0) {
            toast("登录失败: ${json.optString("msg", "code=$code")}")
            return
        }
        val al = json.optJSONObject("data")?.optJSONObject("al")
        val newToken = al?.optString("token") ?: ""
        if (newToken.isBlank()) {
            toast("登录返回异常：无 token")
            return
        }
        val loginData = al ?: return

        val existing = AccountStore.get(this, phone)
        val account = existing ?: Account(phone)
        if (platform == LoginPlatform.APP) account.appToken = newToken else account.token = newToken
        if (TextUtils.isEmpty(account.uid)) account.uid = loginData.optString("uid")
        if (TextUtils.isEmpty(account.eid)) account.eid = loginData.optString("eid")
        AccountStore.addOrUpdate(this, account)
        fetchUidAsync(phone, newToken)
        toast(if (platform == LoginPlatform.APP) "设备控制登录信息已保存" else "日常登录信息已保存")
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun fetchUidAsync(targetPhone: String, token: String) {
        IlifeApi.viewInfoWithToken(token) { json, _ ->
            if (json != null && json.optInt("code", -999) == 0) {
                val data = json.optJSONObject("data")
                val id = data?.optString("id") ?: ""
                if (id.isNotBlank()) {
                    val target = AccountStore.get(this, targetPhone)
                    if (target != null) {
                        target.uid = id
                        AccountStore.addOrUpdateKeepingCurrent(this, target)
                    }
                }
            }
        }
    }

    private fun setLoading(value: Boolean) {
        loading = value
        render()
    }

    private fun isValidPhone(value: String): Boolean = value.matches(Regex("^1\\d{10}$"))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
