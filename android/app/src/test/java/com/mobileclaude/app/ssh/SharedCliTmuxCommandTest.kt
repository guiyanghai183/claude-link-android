package com.mobileclaude.app.ssh

import org.junit.Assert.*
import org.junit.Test

class SharedCliTmuxCommandTest {
    @Test fun sharedConnectionNeverStartsOrResumesAnotherCli() {
        for (mode in listOf("codex", "qodercn")) {
            val target = "claude-link-$mode-" + "a".repeat(24)
            val command = buildSharedCliTmuxStartupCommand(target, 1_200)
            assertTrue(command.contains("has-session -t '=$target'"))
            assertTrue(command.contains("capture-pane -p -S -1200"))
            assertTrue(command.contains("attach-session -t '=$target'"))
            assertFalse(command.contains("new-session"))
            assertFalse(command.contains("--resume"))
            assertFalse(command.contains("reptyr"))
        }
    }

    @Test fun invalidTargetCannotRunShellCode() {
        for (target in listOf("; touch /tmp/test", "other-window", "claude-link-codex-" + "a".repeat(25))) {
            assertThrows(IllegalArgumentException::class.java) { buildSharedCliTmuxStartupCommand(target, 1_200) }
        }
    }

    @Test fun invalidHistorySizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            buildSharedCliTmuxStartupCommand("claude-link-codex-" + "b".repeat(24), -1)
        }
    }
}
