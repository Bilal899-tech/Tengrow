package com.safeguard.blocker

object KeywordNormalizer {

    private val LEET = mapOf(
        '0' to 'o', '1' to 'i', '2' to 'z', '3' to 'e', '4' to 'a',
        '5' to 's', '6' to 'g', '7' to 't', '8' to 'b', '9' to 'g',
        '@' to 'a', '$' to 's', '!' to 'i', '+' to 't', '*' to 'x',
        '#' to 'h', '%' to 'x', '|' to 'i', '`' to 'i', '\'' to 'i'
    )

    private val CYRILLIC = mapOf(
        '\u0430' to 'a', '\u0431' to 'b', '\u0432' to 'b', '\u0433' to 'g',
        '\u0434' to 'd', '\u0435' to 'e', '\u0451' to 'e', '\u0436' to 'j',
        '\u0437' to 'z', '\u0438' to 'i', '\u0439' to 'i', '\u043a' to 'k',
        '\u043b' to 'l', '\u043c' to 'm', '\u043d' to 'h', '\u043e' to 'o',
        '\u043f' to 'p', '\u0440' to 'r', '\u0441' to 'c', '\u0442' to 't',
        '\u0443' to 'y', '\u0444' to 'f', '\u0445' to 'x', '\u0446' to 'c',
        '\u0447' to 'c', '\u0448' to 'w', '\u0449' to 'w', '\u044a' to 'b',
        '\u044b' to 'b', '\u044c' to 'b', '\u044d' to 'e', '\u044e' to 'u',
        '\u044f' to 'a', '\u0456' to 'i', '\u0457' to 'i', '\u0454' to 'e'
    )

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (raw in input) {
            val c = raw.lowercaseChar()
            if (c.isDigit() || c in 'a'..'z') {
                sb.append(LEET[c] ?: c)
            } else if (c >= '\u0400' && c <= '\u04FF') {
                CYRILLIC[c]?.let { sb.append(it) }
            }
        }
        return sb.toString()
    }
}
