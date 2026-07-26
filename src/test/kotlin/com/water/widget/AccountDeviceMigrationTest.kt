package com.water.widget

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeviceMigrationTest {
    @Test
    fun `旧版冷热设备迁移为当前设备并保留设备列表`() {
        val account = Account.fromJson(
            JSONObject()
                .put("phone", "13800000000")
                .put("hotDid", "device-hot")
                .put("coldDid", "device-cold")
        )

        assertEquals("device-hot", account.selectedDeviceId())
        assertEquals(listOf("device-hot", "device-cold"), account.rememberedDevices())
        assertTrue(account.hasDevices())
        assertFalse(account.toJson().has("hotDid"))
        assertFalse(account.toJson().has("coldDid"))
    }

    @Test
    fun `选择设备后将其置顶并可完整保存`() {
        val account = Account("13800000000").apply {
            selectDevice("device-001")
            selectDevice("device-002")
        }

        val restored = Account.fromJson(account.toJson())

        assertEquals("device-002", restored.selectedDeviceId())
        assertEquals(listOf("device-002", "device-001"), restored.rememberedDevices())
        assertEquals("device-002", restored.toJson().getString("deviceId"))
    }

    @Test
    fun `设备名称别名与移除操作可持久化`() {
        val account = Account("13800000000").apply {
            rememberDevice("device-001", "官方名称")
            setDeviceAlias("device-001", "宿舍饮水机")
        }

        val restored = Account.fromJson(account.toJson())
        assertEquals("宿舍饮水机", restored.deviceDisplayName("device-001"))

        restored.forgetDevice("device-001")
        assertTrue(restored.rememberedDevices().isEmpty())
    }

    @Test
    fun `设备列表不再限制十二台`() {
        val account = Account("13800000000")
        repeat(30) { account.rememberDevice("device-$it", "设备 $it") }

        assertEquals(30, account.rememberedDevices().size)
    }
}
