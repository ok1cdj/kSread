package org.ok1cdj.ksread.parser

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * EPUB parsing. Port of `parseEpub` from SwiftRead/parsers.ts.
 *
 * EPUB is a ZIP: META-INF/container.xml points to the OPF package file, whose
 * <manifest> maps ids to hrefs and whose <spine> lists reading order. We read
 * every spine document, strip HTML, and concatenate.
 */
object EpubParser {

    fun parseEpub(data: ByteArray): String {
        val files = unzip(data)

        val containerXml = files["META-INF/container.xml"]
            ?: throw IllegalArgumentException("Missing container.xml")
        val opfPath = findRootfilePath(containerXml)
            ?: throw IllegalArgumentException("Missing OPF path")

        val opfXml = files[opfPath] ?: throw IllegalArgumentException("Missing OPF file")
        val (manifest, spine) = parseOpf(opfXml)

        val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

        val sb = StringBuilder()
        for (idref in spine) {
            val href = manifest[idref] ?: continue
            val fullHref = normalize(opfDir + href)
            val htmlBytes = files[fullHref] ?: continue
            sb.append(' ').append(HtmlText.toPlainText(decodeBytes(htmlBytes)))
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun unzip(data: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    out[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser().apply { setInput(ByteArrayInputStream(bytes), null) }
    }

    private fun findRootfilePath(containerXml: ByteArray): String? {
        val parser = newParser(containerXml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser.name) == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            event = parser.next()
        }
        return null
    }

    /** Returns (manifest id->href, spine idref order). */
    private fun parseOpf(opfXml: ByteArray): Pair<Map<String, String>, List<String>> {
        val manifest = HashMap<String, String>()
        val spine = ArrayList<String>()
        val parser = newParser(opfXml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (localName(parser.name)) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> {
                        parser.getAttributeValue(null, "idref")?.let { spine.add(it) }
                    }
                }
            }
            event = parser.next()
        }
        return manifest to spine
    }

    private fun localName(name: String?): String =
        name?.substringAfterLast(':') ?: ""

    /** Collapse "a/b/../c" style hrefs so the ZIP key matches. */
    private fun normalize(path: String): String {
        val parts = ArrayList<String>()
        for (seg in path.split("/")) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }
}
