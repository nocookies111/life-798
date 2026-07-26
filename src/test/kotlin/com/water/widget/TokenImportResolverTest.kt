package com.water.widget

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenImportResolverTest {
    @Test
    fun inspectUsesExplicitPlatformMarker() {
        val probe = JSONObject(
            """
            {
              "viewMain":{"code":0,"data":{"id":"u1","applicationType":"1,1"}},
              "viewApp":{"code":0,"data":{"id":"u1"}},
              "missionMain":{"code":0,"data":{"missions":[]}},
              "masterApp":{"code":0,"data":{"favos":[]}}
            }
            """.trimIndent()
        )

        val result = TokenImportResolver.inspect("app-token", probe, null)

        assertTrue(result.valid)
        assertEquals(ImportedTokenPlatform.APP, result.platform)
        assertEquals("u1", result.uid)
    }

    @Test
    fun resolveCorrectsReversedTokens() {
        val resolution = TokenImportResolver.resolve(
            candidates = listOf(
                candidate("app-token", ImportedTokenPlatform.APP, ImportedTokenPlatform.MAIN),
                candidate("main-token", ImportedTokenPlatform.MAIN, ImportedTokenPlatform.APP)
            ),
            expectedPhone = "13800000000"
        )

        assertTrue(resolution.success)
        assertEquals("main-token", resolution.mainToken)
        assertEquals("app-token", resolution.appToken)
        assertTrue(resolution.notices.contains("登录信息位置已自动纠正"))
    }

    @Test
    fun resolveMergesTwoAppTokensAndPrefersAppField() {
        val resolution = TokenImportResolver.resolve(
            candidates = listOf(
                candidate("older-app-token", ImportedTokenPlatform.APP, ImportedTokenPlatform.MAIN),
                candidate("newer-app-token", ImportedTokenPlatform.APP, ImportedTokenPlatform.APP)
            ),
            expectedPhone = "13800000000"
        )

        assertTrue(resolution.success)
        assertEquals("", resolution.mainToken)
        assertEquals("newer-app-token", resolution.appToken)
        assertTrue(resolution.notices.contains("检测到多条设备控制登录，已合并"))
    }

    @Test
    fun resolveDeduplicatesSameTokenInBothFields() {
        val resolution = TokenImportResolver.resolve(
            candidates = listOf(
                TokenImportCandidate(
                    inspection = InspectedToken(
                        token = "same-token",
                        valid = true,
                        platform = ImportedTokenPlatform.APP,
                        uid = "u1"
                    ),
                    requestedPlatforms = setOf(
                        ImportedTokenPlatform.MAIN,
                        ImportedTokenPlatform.APP
                    )
                )
            ),
            expectedPhone = "13800000000"
        )

        assertTrue(resolution.success)
        assertEquals("", resolution.mainToken)
        assertEquals("same-token", resolution.appToken)
        assertTrue(resolution.notices.contains("重复登录信息已合并"))
    }

    @Test
    fun resolveRejectsDifferentAccounts() {
        val resolution = TokenImportResolver.resolve(
            candidates = listOf(
                candidate("main-token", ImportedTokenPlatform.MAIN, ImportedTokenPlatform.MAIN, "u1"),
                candidate("app-token", ImportedTokenPlatform.APP, ImportedTokenPlatform.APP, "u2")
            ),
            expectedPhone = "13800000000"
        )

        assertFalse(resolution.success)
        assertTrue(resolution.error.contains("不同账户"))
    }

    private fun candidate(
        token: String,
        detected: ImportedTokenPlatform,
        requested: ImportedTokenPlatform,
        uid: String = "u1"
    ) = TokenImportCandidate(
        inspection = InspectedToken(token, true, detected, uid = uid),
        requestedPlatforms = setOf(requested)
    )
}
