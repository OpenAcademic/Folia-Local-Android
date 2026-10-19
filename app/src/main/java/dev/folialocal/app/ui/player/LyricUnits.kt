package dev.folialocal.app.ui.player

import dev.folialocal.app.data.lyrics.LyricLine

data class TimedChar(
    val text: String,
    var startMs: Long,
    var endMs: Long,
)

/**
 * 一个排版单元：CJK 单字或一个拉丁词。
 * 拉丁词内部仍保留每个字符的时间，逐个弹出，但换行时不会把单词拆开。
 */
data class LyricUnit(
    val chars: MutableList<TimedChar>,
    val isSpace: Boolean,
    val isCjk: Boolean,
) {
    val text: String get() = chars.joinToString("") { it.text }
    val startMs: Long get() = chars.firstOrNull()?.startMs ?: 0L
    val endMs: Long get() = chars.lastOrNull()?.endMs ?: 0L
}

/**
 * 把一行歌词拆成逐字/逐词单元。
 *
 * 优先使用增强型 LRC 的逐字时间轴；如果它与显示文本无法对齐，就按字数和
 * 整行时长均匀分配，保证每个字仍然按顺序弹出。
 */
fun buildLyricUnits(line: LyricLine): List<LyricUnit> {
    val chars = codePoints(line.text)
    if (chars.isEmpty()) return emptyList()
    val aligned = alignSegmentChars(line)
    val units = tokenize(chars, aligned)
    if (units.isEmpty()) return emptyList()
    if (aligned == null) {
        assignFallbackTiming(units, line)
    }
    return units
}

private fun tokenize(chars: List<String>, timed: List<TimedChar>?): List<LyricUnit> {
    val units = mutableListOf<LyricUnit>()
    var timedIndex = 0
    var index = 0
    fun timingAt(position: Int): TimedChar? {
        val value = timed?.getOrNull(position)
        return value
    }

    while (index < chars.size) {
        val char = chars[index]
        val codePoint = char.codePointAt(0)
        if (Character.isWhitespace(codePoint)) {
            val time = timingAt(timedIndex)?.startMs ?: 0L
            units += LyricUnit(
                chars = mutableListOf(TimedChar(" ", time, time)),
                isSpace = true,
                isCjk = false,
            )
            index += 1
            continue
        }

        if (isCjk(codePoint)) {
            val timedChar = timingAt(timedIndex)
            val unitChars = mutableListOf(
                TimedChar(char, timedChar?.startMs ?: -1L, timedChar?.endMs ?: -1L),
            )
            timedIndex += 1
            index += 1
            // 中文标点跟随前一个字，不单独弹一次。
            while (index < chars.size) {
                val next = chars[index]
                val nextCodePoint = next.codePointAt(0)
                if (!isCjkPunctuation(nextCodePoint)) break
                val nextTimed = timingAt(timedIndex)
                unitChars += TimedChar(next, nextTimed?.startMs ?: -1L, nextTimed?.endMs ?: -1L)
                timedIndex += 1
                index += 1
            }
            units += LyricUnit(unitChars, isSpace = false, isCjk = true)
            continue
        }

        val wordChars = mutableListOf<TimedChar>()
        while (index < chars.size) {
            val next = chars[index]
            val nextCodePoint = next.codePointAt(0)
            if (Character.isWhitespace(nextCodePoint) || isCjk(nextCodePoint)) break
            val nextTimed = timingAt(timedIndex)
            wordChars += TimedChar(next, nextTimed?.startMs ?: -1L, nextTimed?.endMs ?: -1L)
            timedIndex += 1
            index += 1
        }
        units += LyricUnit(wordChars, isSpace = false, isCjk = false)
    }
    return units
}

private fun assignFallbackTiming(units: List<LyricUnit>, line: LyricLine) {
    val totalChars = units.filterNot { it.isSpace }.sumOf { it.chars.size }
    if (totalChars <= 0) return
    val duration = (line.endMs - line.startMs).coerceAtLeast(totalChars * 90L)
    var elapsed = 0L
    var cursor = 0L
    units.forEach { unit ->
        if (unit.isSpace) {
            // 空格没有时长，占住当前时间点。
            val space = unit.chars.firstOrNull() ?: return@forEach
            space.startMs = cursor
            space.endMs = cursor
            return@forEach
        }
        val updated = unit.chars.map { char ->
            val start = line.startMs + elapsed
            val charDuration = duration / totalChars
            elapsed += charDuration
            cursor = start + charDuration
            TimedChar(char.text, start, start + charDuration)
        }
        unit.chars.clear()
        unit.chars.addAll(updated)
    }
}

private fun alignSegmentChars(line: LyricLine): List<TimedChar>? {
    if (line.segments.isEmpty()) return null
    val timed = mutableListOf<TimedChar>()
    line.segments.forEach { segment ->
        val chars = codePoints(segment.text)
        if (chars.isEmpty()) return@forEach
        val duration = (segment.endMs - segment.startMs).coerceAtLeast(0L)
        val step = duration / chars.size
        chars.forEachIndexed { index, char ->
            val start = segment.startMs + step * index
            val end = if (index == chars.lastIndex) segment.endMs else segment.startMs + step * (index + 1)
            timed += TimedChar(char, start, end)
        }
    }
    if (timed.isEmpty()) return null

    val result = mutableListOf<TimedChar>()
    var pointer = 0
    for (char in codePoints(line.text)) {
        val codePoint = char.codePointAt(0)
        if (Character.isWhitespace(codePoint)) {
            val start = timed.getOrNull(pointer)?.startMs ?: (timed.lastOrNull()?.endMs ?: 0L)
            result += TimedChar(" ", start, start)
            continue
        }
        if (pointer < timed.size && timed[pointer].text == char) {
            result += timed[pointer]
            pointer += 1
            continue
        }
        var matched = false
        for (skip in 1..2) {
            val index = pointer + skip
            if (index < timed.size && timed[index].text == char) {
                pointer = index + 1
                result += timed[index]
                matched = true
                break
            }
        }
        if (!matched) return null
    }
    return result
}

private fun codePoints(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val result = ArrayList<String>(text.length)
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val count = Character.charCount(codePoint)
        result += text.substring(index, index + count)
        index += count
    }
    return result
}

private fun isCjk(codePoint: Int): Boolean = when (codePoint) {
    in 0x4E00..0x9FFF,
    in 0x3400..0x4DBF,
    in 0x3040..0x30FF,
    in 0xAC00..0xD7AF,
    in 0xF900..0xFAFF,
    -> true
    else -> false
}

private fun isCjkPunctuation(codePoint: Int): Boolean = isCjkPunctuationRange(codePoint)

private fun isCjkPunctuationRange(codePoint: Int): Boolean = when (codePoint) {
    in 0x3000..0x303F,
    in 0xFE10..0xFE1F,
    in 0xFF01..0xFF0F,
    in 0xFF1A..0xFF20,
    in 0xFF3B..0xFF40,
    in 0xFF5B..0xFF65,
    -> true
    else -> false
}
