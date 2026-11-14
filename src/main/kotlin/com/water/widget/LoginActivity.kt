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
import com.water.widget.ui.LoginScreen
import com.water.widget.ui.LoginUiStateFactory
import com.water.widget.ui.WaterTheme
import org.json.JSONObject

/**
 * Compose 版登录页：先完成设备登录，再按需补充积分登录。
 *
 * 两个入口（用水 / 积分）都走 App 通道：`ApplicationType: 1,1` + `login?sign=`。
 * 支付宝小程序通道（1,5）的登录体必须有 `openCode`，它由官方小程序 `my.getAuthCode` 签发、
 * 绑定官方 appId，服务端要用它换 openid，第三方客户端生成不出来；
 * 而积分接口只校验 `Authorization`，App 通道的 token 同样能拿积分，所以这里不再尝试 1,5 通道。
 */
class LoginActivity : ComponentActivity() {
    private var platform = LoginPlatform.APP
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
        phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        platform = if (intent.getBooleanExtra(EXTRA_APP, true)) LoginPlatform.APP else LoginPlatform.ALIPAY
        render()
    }

    companion object {
        private const val EXTRA_PHONE = "login_phone"
        private const val EXTRA_APP = "login_app"

        fun reloginIntent(context: android.content.Context, phone: String, app: Boolean) =
            android.content.Intent(context, LoginActivity::class.java)
                .putExtra(EXTRA_PHONE, phone)
                .putExtra(EXTRA_APP, app)
    }

    private fun render() {
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                LoginScreen(
                    state = LoginUiStateFactory.from(platform, captchaBitmap != null, smsSent, phone, graphCode, smsCode),
                    phone = phone,
                    graphCode = graphCode,
                    smsCode = smsCode,
                    captchaBitmap = captchaBitmap,
                    loading = loading,
                    onPlatformChange = { platform = it; smsSent = false; render() },
                    onPhoneChange = { phone = it.trim(); smsSent = false; render() },
                    onGraphCodeChange = { graphCode = it.trim(); render() },
                    onSmsCodeChange = { smsCode = it.trim(); render() },
                    onLoadCaptcha = { loadCaptcha() },
                    onSendSms = { sendSms() },
                    onLogin = { doLogin() },
                    onBack = { finish() }
                )
            }
        }
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
        // 短信也走 App 通道（asApp = true），与下面的登录请求保持同一套协议头（1,1 / 3.1.9），
        // 避免"短信按小程序通道发、登录按 App 通道发"的混搭。
        IlifeApi.sendSms(phone, graphCode, captchaKey, true) { json, err ->
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
        val requestPhone = phone
        val requestPlatform = platform
        // 恒传 asApp = true：两个入口都走 App 通道登录，原因见类注释。
        IlifeApi.login(requestPhone, smsCode, true) { json, err ->
            runOnUiThread {
                setLoading(false)
                if (json == null) {
                    toast("登录出错: ${err ?: "未知错误"}")
                    return@runOnUiThread
                }
                handleLoginResult(json, requestPhone, requestPlatform)
            }
        }
    }

    private fun handleLoginResult(json: JSONObject, requestPhone: String, requestPlatform: LoginPlatform) {
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

        val existing = AccountStore.get(this, requestPhone)
        val account = existing ?: Account(requestPhone)
        // 这里拿到的永远是 App 通道 token，只能写进 appToken：
        // 写进 token 会让下游 useApp = !hasToken() 判成小程序通道，
        // score-send 就会用 MINI_SIGN_SALT 去签一个 App token，必然验签失败；
        // token 字段保留给"手动导入的真实小程序凭据"这条路径。
        account.appToken = newToken
        if (TextUtils.isEmpty(account.uid)) account.uid = loginData.optString("uid")
        if (TextUtils.isEmpty(account.eid)) account.eid = loginData.optString("eid")
        AccountStore.addOrUpdate(this, account)
        fetchUidAsync(requestPhone, newToken)
        toast(
            when {
                // 积分入口实际完成的是 App 通道登录，提示里说清楚，免得用户以为还要去支付宝授权。
                requestPlatform == LoginPlatform.ALIPAY -> "积分登录已完成（App 通道，无需支付宝小程序授权）"
                account.hasToken() -> "设备与积分登录均已完成"
                else -> "用水登录已完成；积分功能无需单独登录，可直接使用"
            }
        )
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun fetchUidAsync(targetPhone: String, token: String) {
        IlifeApi.viewInfoWithToken(token) { json, _ ->
            if (json != null && json.optInt("code", -999) == 0) {
                val data = json.optJSONObject("data")
                val id = data?.optString("id") ?: ""
                if (id.isNotBlank()) {
                    runOnUiThread { AccountStore.updateUid(this, targetPhone, token, id) }
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
