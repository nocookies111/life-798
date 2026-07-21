package com.water.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.water.widget.ui.AccountCardState
import com.water.widget.ui.AccountsScreen
import com.water.widget.ui.TextEntryDialog
import com.water.widget.ui.WaterTheme
import org.json.JSONObject

class AccountsActivity : ComponentActivity() {
    private var accounts by mutableStateOf<List<AccountCardState>>(emptyList())
    private var dialog by mutableStateOf<DialogState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
        refresh()
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (it.resultCode == RESULT_OK) toast("登录成功，已设为当前账户")
                    refresh()
                }
                AccountsScreen(
                    accounts = accounts,
                    onSmsLogin = { login.launch(Intent(this, LoginActivity::class.java)) },
                    onAddToken = { dialog = DialogState.AddToken },
                    onSetCurrent = { AccountStore.setCurrent(this, it); refresh(); toast("已切换当前账户") },
                    onSetAppToken = { phone -> dialog = DialogState.SetAppToken(phone) },
                    onRevealToken = { phone, app -> dialog = DialogState.Reveal(phone, app) },
                    onDelete = { phone -> dialog = DialogState.Delete(phone) }
                )
                RenderDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @androidx.compose.runtime.Composable
    private fun RenderDialog() {
        when (val state = dialog) {
            DialogState.AddToken -> TextEntryDialog(
                title = "手动添加登录信息",
                message = "登录信息属于敏感内容。请仅在可信环境中粘贴，保存前会在线核验账户。",
                fields = listOf("手机号" to "", "日常登录信息" to "", "设备控制登录信息（可选）" to ""),
                sensitive = true,
                onDismiss = { dialog = null },
                onConfirm = { dialog = null; addByToken(it) }
            )
            is DialogState.SetAppToken -> {
                val account = AccountStore.get(this, state.phone)
                TextEntryDialog(
                    title = "设置设备控制登录信息",
                    message = "这项登录信息用于启用设备控制和官方应用专属服务，请妥善保管。",
                    fields = listOf("设备控制登录信息" to account?.appToken.orEmpty()),
                    sensitive = true,
                    onDismiss = { dialog = null },
                    onConfirm = { values ->
                        dialog = null
                        val token = values.firstOrNull().orEmpty().trim()
                        if (account == null || token.isBlank()) toast("登录信息不能为空") else {
                            account.appToken = token
                            AccountStore.addOrUpdateKeepingCurrent(this, account)
                            refresh()
                            toast("设备控制已开通")
                        }
                    }
                )
            }
            is DialogState.Reveal -> {
                val account = AccountStore.get(this, state.phone)
                val token = if (state.app) account?.appToken.orEmpty() else account?.token.orEmpty()
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(if (state.app) "设备控制登录信息" else "日常登录信息") },
                    text = { Text("这是一段敏感登录信息，请勿发送给他人或粘贴到不可信应用。\n\n$token") },
                    confirmButton = { TextButton(onClick = { copySensitive(token); dialog = null }) { Text("复制登录信息") } },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
                )
            }
            is DialogState.Delete -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("删除账户") },
                text = { Text("确认删除 ${state.phone}？此操作会移除本机保存的 Token 和设备分配。") },
                confirmButton = { TextButton(onClick = { AccountStore.remove(this, state.phone); dialog = null; refresh(); toast("已删除") }) { Text("删除") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } }
            )
            null -> Unit
        }
    }

    private fun refresh() {
        val current = AccountStore.getCurrent(this)?.phone
        accounts = AccountStore.list(this).map { account ->
            AccountCardState(
                phone = account.phone.orEmpty(),
                title = account.toString(),
                current = account.phone == current,
                alipayToken = account.token.orEmpty(),
                appToken = account.appToken.orEmpty(),
                deviceSummary = buildList {
                    if (!account.hotDid.isNullOrBlank()) add("热水已分配")
                    if (!account.coldDid.isNullOrBlank()) add("冷水已分配")
                }.ifEmpty { listOf("未分配") }.joinToString(" · ")
            )
        }
    }

    private fun addByToken(values: List<String>) {
        val phone = values.getOrElse(0) { "" }.trim()
        val token = values.getOrElse(1) { "" }.trim()
        val appToken = values.getOrElse(2) { "" }.trim()
        if (phone.isBlank() || token.isBlank()) { toast("手机号和 Token 不能为空"); return }
        toast("正在验证 Token…")
        IlifeApi.viewInfoWithToken(token) { json, err ->
            runOnUiThread {
                if (json == null || json.optInt("code", -999) != 0) {
                    toast("验证失败: ${json?.optString("msg") ?: err ?: "未知错误"}")
                    return@runOnUiThread
                }
                val data = json.optJSONObject("data") ?: JSONObject()
                val account = AccountStore.get(this, phone) ?: Account(phone)
                account.token = token
                if (appToken.isNotBlank()) account.appToken = appToken
                account.uid = data.optString("id", account.uid.orEmpty())
                account.eid = data.optString("eid", account.eid.orEmpty())
                data.optString("name").takeIf { it.isNotBlank() }?.let { account.name = it }
                AccountStore.addOrUpdate(this, account)
                refresh()
                toast("账号添加成功")
            }
        }
    }

    private fun copySensitive(text: String) {
        val clip = ClipData.newPlainText("sensitive credential", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        toast("已复制敏感凭据")
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private sealed interface DialogState {
        data object AddToken : DialogState
        data class SetAppToken(val phone: String) : DialogState
        data class Reveal(val phone: String, val app: Boolean) : DialogState
        data class Delete(val phone: String) : DialogState
    }
}
