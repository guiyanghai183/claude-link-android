package com.mobileclaude.app.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.Deflater

class ClaudeLinkHandoffCodecTest {
    @Test
    fun decodesDeterministicFixtureGeneratedByPlugin() {
        val raw = "claudelink://handoff/v1?d=AW4Akf97InYiOjEsInMiOiJnIiwidCI6IuaPkuS7tuWIsOWuieWNk-WFvOWuueaApyIsImgiOiLnu6fnu63lvZPliY3ku7vliqHvvIzkuI3opoHku47lpLTph43lgZrjgIIiLCJhIjoxNzkwMjM2ODAwfQ"

        val decoded = ClaudeLinkHandoffCodec.decode(raw)

        assertEquals("ChatGPT", decoded.source)
        assertEquals("插件到安卓兼容性", decoded.title)
        assertEquals("继续当前任务，不要从头重做。", decoded.handoff)
    }

    @Test
    fun decodesPluginCompatibleContextHandoff() {
        val raw = encode(
            """{"v":1,"s":"g","t":"公式解释接力","h":"继续解释符号、维度、假设和直觉。","a":1790236800}""",
        )

        val decoded = ClaudeLinkHandoffCodec.decode(raw)

        assertEquals("ChatGPT", decoded.source)
        assertEquals("公式解释接力", decoded.title)
        assertEquals("继续解释符号、维度、假设和直觉。", decoded.handoff)
        assertNull(decoded.threadId)
        assertTrue(decoded.initialPrompt().contains("不要从头重做"))
    }

    @Test
    fun decodesRealCodexSessionId() {
        val id = "01a0d173-76a1-73a1-a4f7-a4d6ea2f3517"
        val raw = encode(
            """{"v":1,"s":"c","t":"继续实现","h":"从测试开始继续。","a":1790236800,"i":"$id"}""",
        )

        assertEquals(id, ClaudeLinkHandoffCodec.decode(raw).threadId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedQrCode() {
        ClaudeLinkHandoffCodec.decode("https://example.com/not-a-handoff")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSessionId() {
        ClaudeLinkHandoffCodec.decode(
            encode("""{"v":1,"s":"c","t":"继续实现","h":"继续。","a":1790236800,"i":"current"}"""),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTerminalControlCharacters() {
        ClaudeLinkHandoffCodec.decode(
            encode("""{"v":1,"s":"g","t":"继续","h":"清屏\u001b[2J后执行","a":1790236800}"""),
        )
    }

    private fun encode(json: String): String {
        val deflater = Deflater(9, true)
        deflater.setInput(json.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val output = ByteArray(json.toByteArray(Charsets.UTF_8).size + 128)
        val count = deflater.deflate(output)
        deflater.end()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(output.copyOf(count))
        return "claudelink://handoff/v1?d=$encoded"
    }
}
