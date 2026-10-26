package org.konaframework.firefly.testing

import org.konaframework.firefly.widgets.Page

/** Assertions for a rendered Firefly page, form, table, or dialog. */
class FireflyAssert(private val html: String) {
    fun assertSee(text: String): FireflyAssert = apply {
        if (!html.contains(text)) throw AssertionError("Page did not contain [$text]")
    }

    fun assertFormField(name: String): FireflyAssert = apply {
        if (!html.contains("data-ff-path=\"$name\"")) throw AssertionError("Form field [$name] is missing")
    }

    fun assertTable(text: String): FireflyAssert = apply {
        if (!html.contains("ff-table") || !html.contains(text)) throw AssertionError("Table did not contain [$text]")
    }

    fun assertDialog(title: String): FireflyAssert = apply {
        if (!html.contains("ff-dialog") || !html.contains(title)) throw AssertionError("Dialog [$title] is missing")
    }
}

object Firefly {
    @JvmStatic
    fun test(page: Page): FireflyAssert = FireflyAssert(page.render())

    @JvmStatic
    fun test(html: String): FireflyAssert = FireflyAssert(html)
}
