package com.mobileclaude.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexWindowRestoreTest {
    @Test
    fun coldStartRestoresPersistedWindow() {
        assertEquals(
            "window-2",
            chooseRestoredCodexWindowId(
                availableIds = listOf("window-1", "window-2"),
                currentId = null,
                persistedId = "window-2",
            ),
        )
    }

    @Test
    fun liveSelectionWinsOverOlderPersistedWindow() {
        assertEquals(
            "window-1",
            chooseRestoredCodexWindowId(
                availableIds = listOf("window-1", "window-2"),
                currentId = "window-1",
                persistedId = "window-2",
            ),
        )
    }

    @Test
    fun missingWindowFallsBackToFirstAvailableWindow() {
        assertEquals(
            "window-1",
            chooseRestoredCodexWindowId(
                availableIds = listOf("window-1", "window-2"),
                currentId = "deleted",
                persistedId = "also-deleted",
            ),
        )
    }

    @Test
    fun noWindowsClearsSelection() {
        assertNull(
            chooseRestoredCodexWindowId(
                availableIds = emptyList(),
                currentId = "deleted",
                persistedId = "deleted",
            ),
        )
    }
}
