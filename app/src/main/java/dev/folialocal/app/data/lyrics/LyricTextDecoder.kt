package dev.folialocal.app.data.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 歌词文件编码探测：优先 UTF-8（含 BOM），失败后按 GB18030 读取，
 * 兼容国内常见的 GBK/GB2312 歌词文件。
 */
object LyricTextDecoder {

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val (charsetFromBom, offset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                Charset.forName("UTF-8") to 3
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                Charset.forName("UTF-16LE") to 2
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                Charset.forName("UTF-16BE") to 2
            else -> null to 0
        }
        val payload = if (offset > 0) bytes.copyOfRange(offset, bytes.size) else bytes
        if (charsetFromBom != null) {
            return String(payload, charsetFromBom)
        }
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(payload)).toString()
        } catch (_: CharacterCodingException) {
            String(payload, Charset.forName("GB18030"))
        }
    }
}
