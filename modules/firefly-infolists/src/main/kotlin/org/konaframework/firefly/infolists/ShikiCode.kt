package org.konaframework.firefly.infolists

import org.konaframework.firefly.support.FireflyException

object ShikiCode {
    private const val DEFAULT = "/org/konaframework/firefly/infolists/firefly-shiki.js"

    @JvmStatic
    fun source(): String = read(DEFAULT)

    @JvmStatic
    fun manifest(): String =
        """{"highlighter":"shiki","editor":"codemirror","method":"createHighlighter"}"""

    internal fun read(path: String): String {
        val stream = ShikiCode::class.java.getResourceAsStream(path)
        if (stream == null) throw FireflyException("Missing Firefly Shiki script")
        val text = stream.readAllBytes().toString(Charsets.UTF_8)
        stream.close()
        return text
    }
}
