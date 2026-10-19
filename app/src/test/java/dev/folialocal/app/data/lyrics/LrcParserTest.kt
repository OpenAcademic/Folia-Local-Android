package dev.folialocal.app.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses plain lrc lines in order`() {
        val lyrics = LrcParser.parse(
            """
            [ti:Test]
            [00:01.00]第一行
            [00:03.50]第二行
            [00:06.000]第三行
            """.trimIndent(),
            fallbackDurationMs = 10_000,
        )

        assertEquals(3, lyrics.lines.size)
        assertEquals(1_000, lyrics.lines[0].startMs)
        assertEquals(3_500, lyrics.lines[1].startMs)
        assertEquals(6_000, lyrics.lines[2].startMs)
        assertEquals(3_500, lyrics.lines[0].endMs)
        assertEquals(10_000, lyrics.lines[2].endMs)
        assertFalse(lyrics.hasWordTiming)
    }

    @Test
    fun `expands multiple timestamps on one line`() {
        val lyrics = LrcParser.parse("[00:01.00][00:05.00]副歌")
        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000, lyrics.lines[0].startMs)
        assertEquals(5_000, lyrics.lines[1].startMs)
        assertEquals("副歌", lyrics.lines[0].text)
    }

    @Test
    fun `parses enhanced word timings`() {
        val lyrics = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.50>world<00:11.00>")

        assertTrue(lyrics.hasWordTiming)
        val line = lyrics.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(2, line.segments.size)
        assertEquals("Hello", line.segments[0].text)
        assertEquals(10_000, line.segments[0].startMs)
        assertEquals(10_500, line.segments[1].startMs)
    }

    @Test
    fun `applies offset metadata`() {
        val lyrics = LrcParser.parse("[offset:500]\n[00:02.00]偏移")
        assertEquals(1_500, lyrics.lines.single().startMs)
    }

    @Test
    fun `supports centisecond and millisecond fractions`() {
        val lyrics = LrcParser.parse("[00:01.5]A\n[00:02.05]B\n[00:03.123]C")
        assertEquals(1_500, lyrics.lines[0].startMs)
        assertEquals(2_050, lyrics.lines[1].startMs)
        assertEquals(3_123, lyrics.lines[2].startMs)
    }

    @Test
    fun `finds the active line by position`() {
        val lyrics = LrcParser.parse("[00:01.00]A\n[00:05.00]B\n[00:09.00]C")
        assertEquals(0, lyrics.lineIndexAt(0))
        assertEquals(0, lyrics.lineIndexAt(1_000))
        assertEquals(0, lyrics.lineIndexAt(4_999))
        assertEquals(1, lyrics.lineIndexAt(5_000))
        assertEquals(2, lyrics.lineIndexAt(20_000))
    }

    @Test
    fun `decodes gb18030 fallback`() {
        val text = "[00:01.00]中文歌词"
        val bytes = text.toByteArray(charset("GB18030"))
        assertEquals(text, LyricTextDecoder.decode(bytes))
    }

    @Test
    fun `ignores lines without timestamps`() {
        val lyrics = LrcParser.parse("[ar:Someone]\n没有时间轴\n[00:01.00]有效")
        assertNotNull(lyrics.lines.singleOrNull())
        assertEquals("有效", lyrics.lines.single().text)
    }
}
