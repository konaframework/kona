package org.konaframework.firefly.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Schema
import java.util.function.Function

/** Header / footer actions rendered against a record host, posting to a bound URL. */
class PageRecordActionsTest {
    private class Entry(val state: String)

    private fun host(state: String): Schema = Schema.make().record(Entry(state)).fill(mapOf("state" to state))

    @Test
    fun actionsSeeTheHostRecord() {
        val post = Action.make("post").label("Post").visibleForRecord { (it as Entry).state == "draft" }.action { _ -> }
        val page = Page().headerActions(post).footerActions(post)
        val draft = page.actionHost(host("draft")).render()
        assertEquals(2, Regex("""data-ff-action="post"""").findAll(draft).count())
        val posted = page.actionHost(host("posted")).render()
        assertFalse(posted.contains("""data-ff-action="post""""))
        // Without a host the record predicate hides the action.
        assertFalse(Page().headerActions(post).render().contains("""data-ff-action="post""""))
        // Host state is readable through Get.
        val label = Action.make("state").label { get -> "State ${get.get("/state")}" }
        assertTrue(Page().headerActions(label).actionHost(host("draft")).render().contains("State draft"))
    }

    @Test
    fun handlerActionsPostWhileLinksAndSubmitsKeepTheirShape() {
        val post = Action.make("post").label("Post").action { _ -> }
        val confirm = Action.make("sign").label("Sign").requiresConfirmation().action { _ -> }
        val link = Action.make("back").label("Back").url("list")
        val linkWithHandler = Action.make("docs").label("Docs").url("docs").action { _ -> }
        val save = Action.make("save").label("Save").submitForm("ff-form")
        val page = Page()
            .headerActions(save, post, confirm, link, linkWithHandler)
            .actionHost(host("draft"))
            .actionPost(Function { "/admin/entries/actions/${it.name}" }, """<input type="hidden" name="id" value="7" />""")
        val html = page.render()
        assertTrue(html.contains("""<form method="post" action="/admin/entries/actions/post""""))
        assertTrue(html.contains("""<form method="post" action="/admin/entries/actions/sign""""))
        assertTrue(html.contains("""<input type="hidden" name="id" value="7" />"""))
        assertFalse(html.contains("/admin/entries/actions/back"))
        assertFalse(html.contains("/admin/entries/actions/save"))
        assertFalse(html.contains("/admin/entries/actions/docs"))
        assertTrue(html.contains("""href="docs""""))
        assertTrue(html.contains("""form="ff-form""""))
        assertTrue(html.contains("""href="list""""))
        assertTrue(html.contains("""<div class="ff-page-header-actions"><div class="ff-schema">"""))
    }

    @Test
    fun headerActionsStayOpenForSubclasses() {
        val page = object : Page() {
            override fun headerActions(vararg actions: Action): Page {
                super.headerActions(*actions)
                return this
            }
        }
        assertTrue(page.headerActions(Action.make("a").label("Alpha")).render().contains("Alpha"))
    }
}
