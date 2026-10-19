package dev.folialocal.app.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcMetadataTest {

    @Test
    fun `parses existing header tags`() {
        val metadata = LrcMetadata.parse(
            """
            [ti:夜航]
            [ar:Folia Test]
            [al:Demo]
            [by:Someone]
            [offset:250]
            [00:01.00]歌词
            """.trimIndent(),
        )
        assertEquals("夜航", metadata.title)
        assertEquals("Folia Test", metadata.artist)
        assertEquals("Demo", metadata.album)
        assertEquals("Someone", metadata.lyricist)
        assertEquals(250L, metadata.offsetMs)
    }

    @Test
    fun `rewrites header while preserving lyrics and unknown tags`() {
        val raw = """
            [ti:Old]
            [ar:Old Artist]
            [re:Some Editor]
            [00:01.00]第一行
            [00:05.00]第二行
        """.trimIndent()

        val result = LrcMetadata.apply(
            raw,
            LyricMetadata(
                title = "New",
                artist = "New Artist",
                album = "Album",
                lyricist = "Lyricist",
                offsetMs = -120,
            ),
        )

        assertTrue(result.startsWith("[ti:New]\n[ar:New Artist]\n[al:Album]\n[by:Lyricist]\n[offset:-120]\n"))
        assertTrue(result.contains("[re:Some Editor]"))
        assertTrue(result.contains("[00:01.00]第一行"))
        assertTrue(result.contains("[00:05.00]第二行"))
        assertTrue(result.endsWith("\n"))
    }

    @Test
    fun `clears tags that are emptied`() {
        val result = LrcMetadata.apply(
            "[ti:Old]\n[ar:Old]\n[00:01.00]词",
            LyricMetadata(title = "", artist = "Artist", offsetMs = 0),
        )
        assertTrue(!result.contains("[ti:"))
        assertTrue(result.contains("[ar:Artist]"))
        assertTrue(!result.contains("[offset:"))
    }

    @Test
    fun `square brackets in values cannot break the tag`() {
        val result = LrcMetadata.apply("", LyricMetadata(title = "A]B"))
        assertTrue(result.startsWith("[ti:AB]"))
    }
}
