package org.konaframework.firefly.testing

import org.konaframework.firefly.PanelPage
import org.konaframework.firefly.Resource
import org.konaframework.firefly.widgets.Page

/** Assertions for a rendered Firefly page, form, table, or dialog. */
class FireflyAssert(private val html: String) {
    fun assertSee(text: String): FireflyAssert = apply {
        if (!html.contains(text)) throw AssertionError("Page did not contain [$text]")
    }

    fun assertDontSee(text: String): FireflyAssert = apply {
        if (html.contains(text)) throw AssertionError("Page unexpectedly contained [$text]")
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

    fun html(): String = html
}

/**
 * Firefly test helpers: render asserts ([test]) and Livewire-style resource page drivers ([livewire]).
 *
 * Prefer the package-qualified name when [org.konaframework.firefly.Firefly] is also imported.
 */
object Firefly {
    @JvmStatic
    fun test(page: Page): FireflyAssert = FireflyAssert(page.render())

    @JvmStatic
    fun test(html: String): FireflyAssert = FireflyAssert(html)

    /**
     * Drive [resource]'s page of type [page] (Filament `livewire(EditUser::class, ['record' => …])`).
     * [record] is a model, a primary key, or null for create / list.
     */
    @JvmStatic
    fun livewire(resource: Resource, page: Class<out PanelPage>): LivewireTestable =
        livewire(resource, page, null)

    @JvmStatic
    fun livewire(resource: Resource, page: Class<out PanelPage>, record: Any?): LivewireTestable {
        // Resolve pages so onShow hooks bind form / table schemas.
        val match = resource.pages().firstOrNull { page.isInstance(it) }
            ?: throw IllegalArgumentException(
                "Resource [${resource.slug()}] has no page of type ${page.name}. Pages: " +
                    resource.pages().joinToString { it.javaClass.simpleName },
            )
        return LivewireTestable(resource, match, record)
    }

    /** Drive [resource]'s page matching [page]'s class (uses the resource-bound instance). */
    @JvmStatic
    fun livewire(resource: Resource, page: PanelPage): LivewireTestable =
        livewire(resource, page.javaClass, null)

    @JvmStatic
    fun livewire(resource: Resource, page: PanelPage, record: Any?): LivewireTestable =
        livewire(resource, page.javaClass, record)
}
