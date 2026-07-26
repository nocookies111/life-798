package com.water.widget

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import com.water.widget.ui.WaterTheme

class DeviceAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                DeviceAddScreen(
                    onSave = ::saveDevice,
                    onBack = { finish() }
                )
            }
        }
    }

    private fun saveDevice(deviceId: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null) {
            toast("请先登录账户后再添加设备")
            return
        }
        val normalized = DeviceIdParser.normalize(deviceId)
        if (normalized.isBlank()) {
            toast("请输入有效的设备编号")
            return
        }
        account.selectDevice(normalized)
        AccountStore.updateCurrent(this, account)
        toast("设备已保存")
        setResult(RESULT_OK)
        finish()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private fun scannerConfig(): ScannerConfig = ScannerConfig.build {
    // 仅启用 QR Code，减少无关格式分析，提升低对比度设备屏幕的识别速度。
    setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
    setOverlayStringRes(R.string.scan_device_qr_hint)
    setOverlayDrawableRes(R.drawable.ic_scan_device)
    setHapticSuccessFeedback(true)
    setShowTorchToggle(true)
    setShowCloseButton(true)
    setKeepScreenOn(true)
    setColors(
        buttonTint = AndroidColor.WHITE,
        buttonBackground = AndroidColor.rgb(23, 83, 104),
        frameHighlighted = AndroidColor.rgb(255, 195, 106),
        frame = AndroidColor.rgb(118, 216, 255),
        topIcon = AndroidColor.WHITE,
        topText = AndroidColor.WHITE
    )
}

@Composable
private fun DeviceAddScreen(
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var deviceId by rememberSaveable { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val rawValue = result.content.rawValue.orEmpty()
                val normalized = DeviceIdParser.normalize(rawValue)
                if (normalized.isNotBlank()) {
                    deviceId = normalized
                } else {
                    Toast.makeText(context, "二维码中未找到有效设备编号", Toast.LENGTH_LONG).show()
                }
            }

            is QRResult.QRError -> Toast.makeText(
                context,
                "扫码失败：${result.exception.localizedMessage ?: "请调整距离和屏幕亮度后重试"}",
                Toast.LENGTH_LONG
            ).show()

            QRResult.QRMissingPermission -> Toast.makeText(
                context,
                "需要相机权限才能扫码，可改为手动输入设备编号",
                Toast.LENGTH_LONG
            ).show()

            QRResult.QRUserCanceled -> Unit
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanner.launch(scannerConfig())
        } else {
            Toast.makeText(context, "需要相机权限才能扫码，可改为手动输入设备编号", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DeviceAddHero()

        DeviceAddCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("设备编号", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备编号 / 二维码内容") },
                    supportingText = { Text("扫码后会自动填入") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                )
                OutlinedButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            scanner.launch(scannerConfig())
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("扫码添加", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Button(onClick = { onSave(deviceId) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text("保存设备")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text("取消")
        }
    }
}

@Composable
private fun DeviceAddHero() {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("添加设备", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
            Text("扫描二维码或填写设备编号", color = colors.onPrimaryContainer.copy(alpha = 0.78f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DeviceAddCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}
