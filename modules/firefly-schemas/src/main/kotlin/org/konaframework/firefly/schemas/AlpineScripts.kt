package org.konaframework.firefly.schemas

import org.konaframework.firefly.support.FireflyException

object AlpineScripts {
    private const val DEFAULT = "/org/konaframework/firefly/schemas/firefly-alpine.js"

    @JvmStatic
    fun source(): String = read(DEFAULT)

    internal fun read(path: String): String {
        val stream = AlpineScripts::class.java.getResourceAsStream(path)
        if (stream == null) throw FireflyException("Missing Firefly Alpine script")
        val text = stream.readAllBytes().toString(Charsets.UTF_8)
        stream.close()
        return text
    }
}
