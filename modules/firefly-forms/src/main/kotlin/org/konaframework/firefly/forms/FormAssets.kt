package org.konaframework.firefly.forms

import org.konaframework.firefly.support.FireflyException

object FormAssets {
    @JvmStatic
    fun script(): String = read("firefly-forms.js")

    @JvmStatic
    fun stylesheet(): String = read("firefly-forms.css")

    @JvmStatic
    fun languages(): List<String> = read("shiki-languages.txt").lines().filter { it.isNotBlank() }

    internal fun read(name: String): String {
        val stream = FormAssets::class.java.getResourceAsStream("/org/konaframework/firefly/forms/$name")
        if (stream == null) throw FireflyException("Missing Firefly forms asset [$name]")
        return stream.use { it.readAllBytes().toString(Charsets.UTF_8) }
    }
}
