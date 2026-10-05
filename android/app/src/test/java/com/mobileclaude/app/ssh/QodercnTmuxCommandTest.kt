package com.mobileclaude.app.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QodercnTmuxCommandTest {
    private val sessionId = "493c4d1d-b506-4ca3-96b6-21ce1237c614"

    @Test
    fun newWindowUsesStableSessionIdAndReconnectCanResumePersistedHistory() {
        val launch = buildQodercnLaunchCommand(sessionId)
        assertTrue(launch.contains("--session-id '$sessionId'"))
        assertTrue(launch.contains("--resume '$sessionId'"))
        assertTrue(launch.contains("-maxdepth 2 -type f -name '$sessionId.jsonl'"))
        assertFalse(launch.contains("--continue"))
    }

    @Test
    fun existingTmuxWindowIsAttachedBeforeLookingForCliExecutable() {
        val startup = buildQodercnTmuxStartupCommand("/home/tester", "claude-link-qodercn-123", sessionId, 1_200)
        assertTrue(startup.indexOf("has-session") < startup.indexOf("type -P qodercn"))
        assertTrue(startup.contains("capture-pane -p -S -1200 -t 'claude-link-qodercn-123'"))
        assertTrue(startup.contains("attach-session -t 'claude-link-qodercn-123'"))
        assertFalse(startup.contains("kill-session"))
    }

    @Test
    fun startupResolvesCnDispatcherAndExplicitlyPassesExecutableToTmux() {
        val startup = buildQodercnTmuxStartupCommand("/home/tester's project", "claude-link-qodercn-456", sessionId, 1_200)
        assertTrue(startup.contains("type -P qodercn || type -P qoderclicn"))
        assertTrue(startup.contains("\$HOME/.qoder-cn/entry/qodercn"))
        assertTrue(startup.contains("\$HOME/.local/bin/qoderclicn"))
        assertTrue(startup.contains("QODERCN_BIN=\$QODERCN_BIN; export QODERCN_BIN; \$QODERCN_LAUNCH"))
        assertTrue(startup.contains("'/home/tester'\"'\"'s project'"))
        assertFalse(startup.contains("type -P codex"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSessionIdCannotBecomeShellInput() {
        buildQodercnLaunchCommand("x'; touch /tmp/injected; '")
    }

    @Test
    fun codexHistoryRestoresByExactSessionIdWithoutSendingAnInitialPrompt() {
        val launch = buildCodexLaunchCommand(sessionId, null)
        assertTrue(launch == "exec \"\$CODEX_BIN\" resume '$sessionId'")
    }
}
