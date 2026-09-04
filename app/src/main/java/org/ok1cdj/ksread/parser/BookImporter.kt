package org.ok1cdj.ksread.parser

/**
 * Dispatch a raw book file to the right parser based on its filename, returning
 * plain reading text. Mirrors the extension handling in SwiftRead/index.tsx.
 */
object BookImporter {

    /** Extensions the library lists and can open. */
    val SUPPORTED = listOf(".txt", ".epub", ".mobi", ".mobi.zip", ".zip")

    fun isSupported(name: String): Boolean {
        val n = name.lowercase()
        return SUPPORTED.any { n.endsWith(it) }
    }

    fun extractText(fileName: String, bytes: ByteArray): String {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".epub") -> EpubParser.parseEpub(bytes)
            name.endsWith(".mobi") -> MobiParser.parseMobi(bytes)
            name.endsWith(".mobi.zip") || name.endsWith(".zip") -> MobiParser.parseMobiZip(bytes)
            else -> decodeBytes(bytes) // .txt and anything else: treat as plain text
        }
    }
}
