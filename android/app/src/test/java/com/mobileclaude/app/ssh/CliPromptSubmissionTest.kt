package com.mobileclaude.app.ssh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CliPromptSubmissionTest {
    @Test fun pasteAndEnterAreSeparateEventsWithTimeForCliToAcceptPaste() = runBlocking {
        val writes = mutableListOf<Pair<String, Long>>()
        submitCliPrompt("hello", { writes += it to System.nanoTime() })
        assertEquals(listOf("\u001b[200~hello\u001b[201~", "\r"), writes.map { it.first })
        assertTrue("Enter arrived before the paste could settle", (writes[1].second - writes[0].second) / 1_000_000 >= 180)
    }

    @Test fun changedOrDisconnectedWindowNeverReceivesEnter() = runBlocking {
        val writes = mutableListOf<String>()
        var current = true
        try {
            submitCliPrompt("keep my draft", { writes += it; current = false }, { current })
            fail("Submission should fail after the window changes")
        } catch (_: IOException) {
            assertEquals(listOf("\u001b[200~keep my draft\u001b[201~"), writes)
        }
    }

    @Test fun cancelledSubmissionDoesNotPressEnterAfterSwitchingTabs() = runBlocking {
        val writes = mutableListOf<String>()
        val pasted = CompletableDeferred<Unit>()
        val job = launch {
            submitCliPrompt("cancel this", { writes += it; pasted.complete(Unit) })
        }
        pasted.await()
        job.cancelAndJoin()
        assertEquals(listOf("\u001b[200~cancel this\u001b[201~"), writes)
    }

    @Test fun multilineChineseTextStaysInsideThePasteInsteadOfSubmittingLineByLine() = runBlocking {
        val writes = mutableListOf<String>()
        submitCliPrompt("中文第一行\n第二行\u001b", { writes += it })
        assertEquals(listOf("\u001b[200~中文第一行\n第二行\u001b[201~", "\r"), writes)
    }

    @Test fun emptyTextNeverWritesToTerminal() = runBlocking {
        val writes = mutableListOf<String>()
        try {
            submitCliPrompt(" \n", { writes += it })
            fail("Empty input should be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(writes.isEmpty())
        }
    }
}
