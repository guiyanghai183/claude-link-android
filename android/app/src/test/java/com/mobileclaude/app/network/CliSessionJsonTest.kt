package com.mobileclaude.app.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CliSessionJsonTest {
    private val api = BridgeApi(0)
    private val sessionId = "01a10a99-59eb-7290-9d99-353e61278842"

    // Android's JSONObject can render its NULL sentinel as the literal "null";
    // the desktop org.json test dependency instead returns the empty fallback.
    private class AndroidNullJson : JSONObject() {
        override fun optString(name: String): String =
            if (has(name) && isNull(name)) "null" else super.optString(name)
    }

    private fun window() = AndroidNullJson().apply {
        put("id", "window-1")
        put("title", "Codex 1")
        put("projectPath", "/home/tester")
        put("mode", "codex")
    }

    private fun history() = AndroidNullJson().apply {
        put("id", sessionId)
        put("mode", "codex")
        put("title", "Existing conversation")
        put("projectPath", "/home/tester")
    }

    @Test fun newCodexWindowWithJsonNullDoesNotTryToResumeLiteralNull() {
        val json = window().put("cliSessionId", JSONObject.NULL)
        assertEquals("null", json.optString("cliSessionId"))
        assertNull(api.parseChat(json).cliSessionId)
    }

    @Test fun legacyWindowWithoutSessionFieldRemainsNewOrAttachable() {
        assertNull(api.parseChat(window()).cliSessionId)
        assertNull(api.parseChat(window().put("cliSessionId", "")).cliSessionId)
    }

    @Test fun realUuidSurvivesWindowParsingForResume() {
        assertEquals(sessionId, api.parseChat(window().put("cliSessionId", sessionId)).cliSessionId)
    }

    @Test fun activeOrdinaryTerminalWithJsonNullIsNotAnAttachableAppWindow() {
        val session = api.parseCliSession(history().put("windowId", JSONObject.NULL).put("running", true))
        assertTrue(session.running)
        assertNull(session.windowId)
    }

    @Test fun realWindowBindingIsPreservedForReconnect() {
        assertEquals("window-1", api.parseCliSession(history().put("windowId", "window-1")).windowId)
    }
}
