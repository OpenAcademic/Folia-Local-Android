package dev.folialocal.app.ui.player

import dev.folialocal.app.data.lyrics.LyricLine
import dev.folialocal.app.data.lyrics.LyricSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricUnitsTest {

    @Test
    fun `cjk line pops one character at a time`() {
        val units = buildLyricUnits(
            LyricLine(startMs = 1_000, endMs = 5_000, text = "夜色落下"),
        )
        assertEquals(4, units.size)
        assertTrue(units.all { it.isCjk })
        assertEquals(listOf("夜", "色", "落", "下"), units.map { it.text })
        units.zipWithNext().forEach { (first, second) ->
            assertTrue("timing should move forward", second.startMs >= first.endMs)
        }
        assertEquals(1_000, units.first().startMs)
        assertEquals(5_000, units.last().endMs)
    }

    @Test
    fun `latin words stay together but each letter has a timing`() {
        val units = buildLyricUnits(
            LyricLine(startMs = 0, endMs = 4_000, text = "Hello world"),
        )
        val words = units.filterNot { it.isSpace }
        assertEquals(2, words.size)
        assertEquals("Hello", words[0].text)
        assertEquals("world", words[1].text)
        assertEquals(5, words[0].chars.size)
        assertEquals(5, words[1].chars.size)
        assertTrue(words[0].chars[1].startMs >= words[0].chars[0].startMs)
    }

    @Test
    fun `spaces keep their place between words`() {
        val units = buildLyricUnits(
            LyricLine(startMs = 0, endMs = 3_000, text = "ab cd"),
        )
        assertEquals("ab", units[0].text)
        assertTrue(units[1].isSpace)
        assertEquals("cd", units[2].text)
    }

    @Test
    fun `cjk punctuation follows the previous character`() {
        val units = buildLyricUnits(
            LyricLine(startMs = 0, endMs = 4_000, text = "你好，世界"),
        )
        assertEquals(4, units.size)
        assertEquals("好，", units[1].text)
        assertEquals(listOf("好", "，"), units[1].chars.map { it.text })
        assertEquals("世", units[2].text)
        assertEquals("界", units[3].text)
    }

    @Test
    fun `enhanced lrc word timings are preserved`() {
        val units = buildLyricUnits(
            LyricLine(
                startMs = 1_000,
                endMs = 3_000,
                text = "夜航",
                segments = listOf(
                    LyricSegment("夜", 1_000, 1_400),
                    LyricSegment("航", 1_900, 2_600),
                ),
            ),
        )
        assertEquals(2, units.size)
        assertEquals(1_000, units[0].startMs)
        assertEquals(1_400, units[0].endMs)
        assertEquals(1_900, units[1].startMs)
        assertEquals(2_600, units[1].endMs)
    }

    @Test
    fun `emoji and surrogate pairs are not split`() {
        val units = buildLyricUnits(
            LyricLine(startMs = 0, endMs = 3_000, text = "你😊好"),
        )
        val nonSpace = units.filterNot { it.isSpace }
        assertEquals(3, nonSpace.size)
        assertEquals("你", nonSpace[0].text)
        assertEquals("😊", nonSpace[1].text)
        assertEquals("好", nonSpace[2].text)
    }
}
