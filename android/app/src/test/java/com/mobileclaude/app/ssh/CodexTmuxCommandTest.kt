package com.mobileclaude.app.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexTmuxCommandTest {
    @Test
    fun plainWindowPassesResolvedCodexPathIntoTmuxShellCommand() {
        val launch = buildCodexLaunchCommand(null, null)
        val startup = buildCodexTmuxStartupCommand("/home/tester", "claude-link-codex-123", launch, 1_200)

        assertTrue(startup.contains("CODEX_LAUNCH='exec \"\$CODEX_BIN\"'"))
        assertTrue(startup.contains("\"CODEX_BIN=\$CODEX_BIN; export CODEX_BIN; \$CODEX_LAUNCH\""))
        assertTrue(startup.contains("Codex 进程启动后立即退出"))
        assertFalse(startup.contains("-c '/home/tester' 'exec \"\$CODEX_BIN\"'"))
    }

    @Test
    fun resumeWindowKeepsSessionIdAndPromptShellQuoted() {
        val sessionId = "01a0d173-76a1-73a1-a4f7-a4d6ea2f3517"
        val launch = buildCodexLaunchCommand(sessionId, "继续 user's 任务；不要重做")
        val startup = buildCodexTmuxStartupCommand("/home/tester/project", "claude-link-codex-456", launch, 1_200)

        assertTrue(launch.contains("resume '$sessionId'"))
        assertTrue(launch.contains("'继续 user'\"'\"'s 任务；不要重做'"))
        assertTrue(startup.contains("CODEX_LAUNCH="))
        assertTrue(startup.contains("export CODEX_BIN; \$CODEX_LAUNCH"))
    }

    @Test
    fun promptOnlyWindowUsesNewCodexSession() {
        val launch = buildCodexLaunchCommand(null, "继续扫码接力")

        assertTrue(launch.startsWith("exec \"\$CODEX_BIN\""))
        assertFalse(launch.contains(" resume "))
        assertTrue(launch.endsWith("'继续扫码接力'"))
    }
}
