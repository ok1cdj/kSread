package org.ok1cdj.ksread.parser

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decode a byte array to text, trying UTF-8 first and falling back to
 * Windows-1250 (common for Czech .txt files). Port of `decodeBuffer` in
 * SwiftRead/parsers.ts.
 */
fun decodeBytes(bytes: ByteArray): String {
    val utf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        utf8.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
        Charset.forName("windows-1250").decode(ByteBuffer.wrap(bytes)).toString()
    }
}
