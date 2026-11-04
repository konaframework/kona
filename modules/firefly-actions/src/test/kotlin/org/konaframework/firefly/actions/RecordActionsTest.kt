package org.konaframework.firefly.actions

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException

/** Server handlers on record actions: record / data callbacks, record visibility, titles, bound POST. */
class RecordActionsTest {
    @AfterEach
    fun clear() {
        Action.gate(null)
    }

    private class Entry(val state: String)

    private fun recordSchema(record: Any?): Schema = Schema.make().record(record)

    @Test
    fun recordHandlerReceivesTheSchemaRecord() {
        var seen: Any? = null
        val post = Action.make("post").action { record -> seen = record }
        val entry = Entry("draft")
        val result = post.call(recordSchema(entry))
        assertSame(entry, seen)
        assertEquals(ActionResult.SUCCESS, result.status)
        assertNull(result.notificationTitle)
    }

    @Test
    fun recordHandlerNeedsARecord() {
        val post = Action.make("post").action { _ -> }
        val missing = assertThrows(FireflyException::class.java) { post.call(recordSchema(null)) }
        assertTrue(missing.message!!.contains("needs a record"))
        val withData = Action.make("post").actionWithData { _, _ -> }
        assertThrows(FireflyException::class.java) { withData.call(recordSchema(null), mapOf("a" to 1)) }
    }

    @Test
    fun dataHandlerReceivesRecordAndData() {
        var seenRecord: Any? = null
        var seenData: Map<String, Any?> = emptyMap()
        val action = Action.make("password").actionWithData { record, data ->
            seenRecord = record
            seenData = data
        }
        val entry = Entry("x")
        action.call(recordSchema(entry), mapOf("password" to "secret"))
        assertSame(entry, seenRecord)
        assertEquals(mapOf("password" to "secret"), seenData)
        // Without data the handler still runs with an empty map.
        action.call(recordSchema(entry))
        assertEquals(emptyMap<String, Any?>(), seenData)
    }

    @Test
    fun lastHandlerWins() {
        val order = ArrayList<String>()
        val action = Action.make("run")
            .action { _, _ -> order.add("page") }
            .action { _ -> order.add("record") }
        action.call(recordSchema(Entry("a")))
        action.actionWithData { _, _ -> order.add("data") }.call(recordSchema(Entry("a")))
        action.action { _, _ -> order.add("page-again") }.call(recordSchema(Entry("a")))
        action.action { _ -> order.add("record-again") }.call(recordSchema(Entry("a")))
        assertEquals(listOf("record", "data", "page-again", "record-again"), order)
    }

    @Test
    fun hasHandlerAndIsLink() {
        assertFalse(Action.make("a").hasHandler())
        assertTrue(Action.make("a").action { _, _ -> }.hasHandler())
        assertTrue(Action.make("a").action { _ -> }.hasHandler())
        assertTrue(Action.make("a").actionWithData { _, _ -> }.hasHandler())
        assertFalse(Action.make("a").isLink())
        assertTrue(Action.make("a").url("/x").isLink())
    }

    @Test
    fun beforeAndAfterWrapRecordHandlers() {
        val order = ArrayList<String>()
        val action = Action.make("run")
            .before { _, _ -> order.add("before") }
            .action { _ -> order.add("handler") }
            .after { _, _ -> order.add("after") }
        action.call(recordSchema(Entry("a")))
        assertEquals(listOf("before", "handler", "after"), order)
    }

    @Test
    fun dialogComponentsExposeTheSchema() {
        val field = org.konaframework.firefly.schemas.Text.make("hello")
        val action = Action.make("with").schema(field)
        assertEquals(listOf<Component>(field), action.dialogComponents())
        assertTrue(Action.make("none").dialogComponents().isEmpty())
    }

    @Test
    fun visibleForRecordHidesWithoutAMatchingRecord() {
        val post = Action.make("post").visibleForRecord { record -> (record as Entry).state == "draft" }
        assertTrue(post.isHidden(recordSchema(null)))
        assertTrue(post.isHidden(recordSchema(Entry("posted"))))
        assertFalse(post.isHidden(recordSchema(Entry("draft"))))
        assertThrows(FireflyException::class.java) { post.call(recordSchema(Entry("posted"))) }
        // The usual component hiding still applies once the record predicate passes.
        val hidden = Action.make("hidden").visibleForRecord { true }.hidden(true)
        assertTrue(hidden.isHidden(recordSchema(Entry("draft"))))
        val html = Schema.make().record(Entry("draft")).components(post.action { _ -> }).render()
        assertTrue(html.contains("data-ff-action=\"post\""))
        val concealed = Schema.make().record(Entry("posted")).components(post).render()
        assertTrue(concealed.contains("ff-hidden"))
        assertFalse(concealed.contains("data-ff-action"))
    }

    @Test
    fun authorizeAbilityResolvesAgainstTheRecord() {
        var seen: Any? = null
        Action.gate { ability, _, schema ->
            seen = schema.record()
            ability == "post"
        }
        val entry = Entry("draft")
        val allowed = Action.make("post").authorize("post")
        assertFalse(allowed.isHidden(recordSchema(entry)))
        assertSame(entry, seen)
        val denied = Action.make("cancel").authorize("cancel")
        assertTrue(denied.isHidden(recordSchema(entry)))
    }

    @Test
    fun notificationTitlesTravelOnTheResult() {
        val ok = Action.make("post").successNotificationTitle("Posted").action { _ -> }
        assertEquals("Posted", ok.call(recordSchema(Entry("a"))).notificationTitle)
        val dynamic = Action.make("post").successNotificationTitle { get -> "Posted " + get.get("/n") }.action { _ -> }
        val schema = recordSchema(Entry("a")).fill(mapOf("n" to "7"))
        assertEquals("Posted 7", dynamic.call(schema).notificationTitle)
        val failing = Action.make("post")
            .failureNotificationTitle("Could not post")
            .failureRedirectUrl("/back")
            .action { _ -> throw ActionFailure("Already posted") }
        val failed = failing.call(recordSchema(Entry("a")))
        assertEquals(ActionResult.FAILED, failed.status)
        assertEquals("Could not post", failed.notificationTitle)
        assertEquals("Already posted", failed.message)
        assertEquals("/back", failed.redirectUrl)
        val dynamicFailure = Action.make("post")
            .failureNotificationTitle { _ -> "Nope" }
            .action { action, _ -> action.failure("bad") }
        assertEquals("Nope", dynamicFailure.call(recordSchema(Entry("a"))).notificationTitle)
        val plain = Action.make("post").action { _ -> throw ActionFailure("bad") }
        assertNull(plain.call(recordSchema(Entry("a"))).notificationTitle)
        val halted = Action.make("post").successNotificationTitle("Posted").action { _ -> throw ActionHalt() }
        val haltResult = halted.call(recordSchema(Entry("a")))
        assertEquals(ActionResult.HALTED, haltResult.status)
        assertNull(haltResult.notificationTitle)
    }

    @Test
    fun actionResultKeepsItsOriginalConstructor() {
        val legacy = ActionResult(ActionResult.SUCCESS, null, "/next", null)
        assertNull(legacy.notificationTitle)
        assertEquals("/next", legacy.redirectUrl)
    }

    @Test
    fun plainActionPostsAsAOneClickFormButton() {
        val post = Action.make("post").label("Post").color("primary")
        val html = post.renderWithPost(recordSchema(Entry("a")), "/admin/entry/actions/post", """<input type="hidden" name="id" value="7" />""")
        assertTrue(html.contains("""<form method="post" action="/admin/entry/actions/post""""))
        assertTrue(html.contains("""<input type="hidden" name="id" value="7" />"""))
        assertTrue(html.contains("""<button type="submit" class="ff-action-trigger"""))
        assertTrue(html.contains("""data-ff-action="post""""))
        assertFalse(html.contains("wire:click"))
        assertFalse(html.contains("data-ff-dialog-open"))
        // The binding is scoped to the call: the same action renders unbound afterwards.
        val unbound = Schema.make().components(post).render()
        assertTrue(unbound.contains("wire:click=\"post\""))
        assertFalse(unbound.contains("<form"))
        val lockedAction = Action.make("lock")
        lockedAction.disabled(true)
        val locked = lockedAction.renderWithPost(recordSchema(Entry("a")), "/x")
        assertTrue(locked.contains(" disabled"))
        val open = Action.make("open").renderWithPost(recordSchema(Entry("a")), "/x")
        assertFalse(open.contains(" disabled"))
    }

    @Test
    fun confirmedActionPostsFromItsDialog() {
        val post = Action.make("post").requiresConfirmation().modalHeading("Post entry")
        val html = post.renderWithPost(recordSchema(Entry("a")), "/x/actions/post", "<i id=\"extras\"></i>")
        assertTrue(html.contains("data-ff-dialog-open"))
        assertTrue(html.contains("""<form method="post" action="/x/actions/post" class="ff-action-post""""))
        assertTrue(html.contains("<i id=\"extras\"></i>"))
        assertTrue(html.contains("""class="ff-action-submit""""))
        assertFalse(html.contains("wire:click=\"post\""))
    }

    @Test
    fun dialogSchemaPostsItsFieldsToTheBoundUrl() {
        val field = org.konaframework.firefly.schemas.Text.make("Enter a password")
        val action = Action.make("password").schema(field)
        val html = action.renderWithPost(recordSchema(Entry("a")), "/x/actions/password", "<i id=\"extras\"></i>")
        assertTrue(html.contains("""<form method="post" action="/x/actions/password" class="ff-action-form"><i id="extras"></i>"""))
        assertTrue(html.contains("Enter a password"))
        assertTrue(html.contains("""<button type="submit" class="ff-action-submit""""))
    }

    @Test
    fun linkActionStaysALinkWhenBound() {
        val link = Action.make("view").url("/view")
        val html = link.renderWithPost(recordSchema(Entry("a")), "/x/actions/view")
        assertTrue(html.contains("""href="/view""""))
        assertFalse(html.contains("<form"))
    }

    @Test
    fun blankBoundUrlIsRejected() {
        assertThrows(FireflyException::class.java) { Action.make("post").renderWithPost(recordSchema(Entry("a")), " ") }
    }

    @Test
    fun bindingOnlyAppliesToTheActionItWasMadeFor() {
        val inner = Action.make("inner")
        val outer = Action.make("outer").requiresConfirmation().modalSubmitAction(inner)
        val html = outer.renderWithPost(recordSchema(Entry("a")), "/x/actions/outer")
        // The nested footer action keeps its own (unbound) wire:click trigger.
        assertTrue(html.contains("""wire:click="inner""""))
        assertFalse(html.contains("""action="/x/actions/inner""""))
    }

    @Test
    fun nestedBindingRestoresTheOuterOne() {
        val inner = Action.make("inner")
        val schema = recordSchema(Entry("a"))
        val outer = Action.make("outer").requiresConfirmation().schema(Nester("nest", inner, schema))
        val html = outer.renderWithPost(schema, "/x/actions/outer")
        assertTrue(html.contains("""action="/x/actions/inner""""))
        assertTrue(html.contains("""action="/x/actions/outer""""))
        // After both calls the thread holds no binding.
        assertFalse(Schema.make().components(outer).render().contains("""action="/x/actions/outer""""))
    }

    @Test
    fun bindingIsReleasedWhenRenderingFails() {
        val broken = Action.make("broken").requiresConfirmation().modalWidth("huge")
        assertThrows(FireflyException::class.java) { broken.renderWithPost(recordSchema(Entry("a")), "/x/actions/broken") }
        val plain = Action.make("plain")
        assertTrue(Schema.make().components(plain).render().contains("wire:click=\"plain\""))
        // A failing nested render hands the outer binding back before the error propagates.
        val badInner = Action.make("inner").requiresConfirmation().modalWidth("huge")
        val schema = recordSchema(Entry("a"))
        val outer = Action.make("outer").requiresConfirmation().schema(Nester("nest", badInner, schema))
        assertThrows(FireflyException::class.java) { outer.renderWithPost(schema, "/x/actions/outer") }
        assertTrue(Schema.make().components(plain).render().contains("wire:click=\"plain\""))
    }

    private class Nester(name: String, private val inner: Action, private val schema: Schema) : Component(name) {
        override fun renderBody(schema: Schema): String = inner.renderWithPost(this.schema, "/x/actions/inner")
    }

    @Test
    fun isDisabledFollowsTheDisabledSetting() {
        val schema = Schema.make()
        val open = Action.make("open")
        assertFalse(open.isDisabled(schema))
        val locked = Action.make("locked")
        locked.disabled(true)
        assertTrue(locked.isDisabled(schema))
    }
}
