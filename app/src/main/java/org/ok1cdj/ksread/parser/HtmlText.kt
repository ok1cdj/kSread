package org.ok1cdj.ksread.parser

/** Shared HTML helpers used by the EPUB and MOBI parsers. */
object HtmlText {

    private val NUMERIC_HEX = Regex("&#[xX]([0-9a-fA-F]+);")
    private val NUMERIC_DEC = Regex("&#(\\d+);")

    // Common named entities. `&amp;` is applied last so decoded text isn't
    // re-interpreted (e.g. "&amp;nbsp;" must not turn into a space).
    private val NAMED = listOf(
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ", "shy" to "",
        "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "laquo" to "«", "raquo" to "»",
        "ldquo" to "“", "rdquo" to "”",
        "lsquo" to "‘", "rsquo" to "’",
        "bdquo" to "„", "sbquo" to "‚",
        "copy" to "©", "reg" to "®", "trade" to "™",
        "deg" to "°", "middot" to "·",
        "euro" to "€", "pound" to "£",
    )

    /** Decode named and numeric HTML entities into their characters. */
    fun decodeEntities(input: String): String {
        var s = input
        if (s.indexOf('&') < 0) return s
        s = NUMERIC_HEX.replace(s) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrNull() } ?: m.value
        }
        s = NUMERIC_DEC.replace(s) { m ->
            m.groupValues[1].toIntOrNull()?.let { cp -> runCatching { String(Character.toChars(cp)) }.getOrNull() } ?: m.value
        }
        for ((name, repl) in NAMED) s = s.replace("&$name;", repl)
        return s.replace("&amp;", "&")
    }

    /** Strip scripts/styles/tags and decode entities, collapsing whitespace. */
    fun toPlainText(html: String): String = html
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?</style>"), " ")
        .replace(Regex("(?is)<head.*?</head>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .let { decodeEntities(it) }
}
