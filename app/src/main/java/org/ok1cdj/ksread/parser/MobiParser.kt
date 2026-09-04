package org.ok1cdj.ksread.parser

import java.util.zip.ZipInputStream

/**
 * MOBI / MOBI.ZIP parsing. Direct port of `parseMobi`, `parseMobiZip` and
 * `decompressPalmDoc` from SwiftRead/parsers.ts.
 *
 * The PalmDB header holds a record-offset table; record 0 is the PalmDOC
 * header (compression type + text record count), records 1..n are the (often
 * PalmDOC-compressed) text.
 */
object MobiParser {

    private fun u16(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)

    private fun u32(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xFF) shl 24) or
            ((data[off + 1].toInt() and 0xFF) shl 16) or
            ((data[off + 2].toInt() and 0xFF) shl 8) or
            (data[off + 3].toInt() and 0xFF)

    /** PalmDOC (LZ77-ish) decompression. */
    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val output = ArrayList<Byte>(data.size * 2)
        var i = 0
        while (i < data.size) {
            val c = data[i++].toInt() and 0xFF
            when {
                c == 0 -> output.add(0)
                c in 1..8 -> {
                    var j = 0
                    while (j < c && i < data.size) {
                        output.add(data[i++]); j++
                    }
                }
                c <= 0x7f -> output.add(c.toByte())
                c >= 0xc0 -> {
                    output.add(32) // space
                    output.add((c xor 0x80).toByte())
                }
                else -> { // 0x80..0xbf: back-reference
                    if (i >= data.size) break
                    val next = data[i++].toInt() and 0xFF
                    val distance = (((c and 0x3f) shl 8) or next) shr 3
                    val length = (next and 0x07) + 3
                    val startPos = output.size - distance
                    if (startPos >= 0) {
                        for (j in 0 until length) {
                            output.add(output[startPos + j])
                        }
                    }
                }
            }
        }
        val arr = ByteArray(output.size)
        for (k in output.indices) arr[k] = output[k]
        return arr
    }

    fun parseMobi(data: ByteArray): String {
        val recordCount = u16(data, 76)
        val offsets = IntArray(recordCount) { u32(data, 78 + it * 8) }
        if (offsets.isEmpty()) return ""

        val offset0 = offsets[0]
        val compression = u16(data, offset0)
        val textRecordCount = u16(data, offset0 + 8)

        val sb = StringBuilder()
        var i = 1
        while (i <= textRecordCount) {
            if (i >= offsets.size) break
            val start = offsets[i]
            val end = if (i + 1 < offsets.size) offsets[i + 1] else data.size
            if (start !in 0..data.size || end < start) { i++; continue }
            val record = data.copyOfRange(start, minOf(end, data.size))
            val decompressed = when (compression) {
                1 -> record
                2 -> decompressPalmDoc(record)
                else -> { i++; continue }
            }
            sb.append(String(decompressed, Charsets.UTF_8))
            i++
        }
        return HtmlText.decodeEntities(sb.toString().replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Extract the first .mobi inside a ZIP and parse it. */
    fun parseMobiZip(data: ByteArray): String {
        ZipInputStream(data.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".mobi")) {
                    return parseMobi(zis.readBytes())
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalArgumentException("No MOBI file found inside the ZIP archive.")
    }
}
