package org.konaframework.firefly.actions

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.FileViewFinder
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Section
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.schemas.Wizard
import org.konaframework.firefly.schemas.WizardStep
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import java.nio.file.Files
import java.nio.file.Path

class FireflyActionsTest {
    @AfterEach
    fun clear() {
        Action.configureUsing(null)
        ActionGroup.configureUsing(null)
        Action.gate(null)
        Dialog.configureUsing(null)
    }

    @Test
    fun plainActionCallsThroughWireAndAlpine() {
        val save = Action.make("save_1").button()
        val html = page(save)
        assertTrue(html.contains("ff-action-button"))
        assertTrue(html.contains("ff-placement-schema"))
        assertTrue(html.contains("x-data=\"ffAction('save_1')\""))
        assertTrue(html.contains("wire:click=\"save_1\""))
        assertTrue(html.contains(">save_1</span>"))
        assertFalse(html.contains("ff-dialog"))
        assertEquals(ActionPlacement.SCHEMA, save.placement())
        var ran = false
        save.action { _, _ -> ran = true }
        val result = save.call(host(save))
        assertTrue(ran)
        assertEquals(ActionResult.SUCCESS, result.status)
        assertNull(result.url)
        assertNull(result.redirectUrl)
        assertNull(result.message)
    }

    @Test
    fun submitFormRendersNativeSubmitAssociatedWithFormId() {
        val save = Action.make("save").label("Create").submitForm("ff-form")
        val html = page(save)
        assertTrue(html.contains("""type="submit""""))
        assertTrue(html.contains("""form="ff-form""""))
        assertFalse(html.contains("wire:click"))
        val locked = Action.make("save").label("Create").submitForm().disabled(true)
        assertTrue(page(locked).contains(" disabled"))
        assertThrows(FireflyException::class.java) { Action.make("bad").submitForm(" ") }
    }

    @Test
    fun labelClosureAndLiteral() {
        val save = Action.make("save")
        save.label { get -> if (get.get("/kind") == "org") "Organization" else "Person" }
        assertTrue(page(save, mapOf("kind" to "org")).contains("Organization"))
        save.label("Save")
        val html = page(save, mapOf("kind" to "org"))
        assertTrue(html.contains("Save"))
        assertFalse(html.contains("Organization"))
        assertSame(save, save.label { _ -> "Again" }.icon("home"))
    }

    @Test
    fun relativeGetUsesSchema() {
        val save = Action.make("save").label { get ->
            if (get.get(".") != null) "No" else "Save ${get.get("../city")}"
        }
        val section = Section.make("Profile").schema(save)
        val html = Schema.make().statePath("user").fill(mapOf("user.city" to "Kona")).components(section).render()
        assertTrue(html.contains("Save Kona"))
    }

    @Test
    fun visibilityDisabledAndAuthorization() {
        val hidden = Action.make("save").hidden(true)
        assertTrue(page(hidden).contains("ff-hidden"))
        assertFalse(page(hidden).contains("wire:click"))
        val predicate = Action.make("save").hidden { get -> get.get("/draft") == true }
        assertFalse(page(predicate, mapOf("draft" to true)).contains("wire:click"))
        val shown = Action.make("save").visible { get -> get.get("/draft") == false }
        assertTrue(page(shown, mapOf("draft" to false)).contains("wire:click"))
        val invisible = Action.make("save").visible(false)
        assertFalse(page(invisible).contains("wire:click"))
        val locked = Action.make("save")
        locked.disabled(true)
        assertTrue(page(locked).contains("disabled"))
        assertThrows(FireflyException::class.java) { locked.call(host(locked)) }
        val lockedDialog = Action.make("save").requiresConfirmation()
        lockedDialog.disabled(true)
        val lockedDialogHtml = page(lockedDialog)
        assertTrue(lockedDialogHtml.contains("disabled"))
        assertTrue(lockedDialogHtml.contains("@click=\"open = true\""))
        val lockedLater = Action.make("save").disabled { get -> get.get("/lock") == true }
        assertTrue(page(lockedLater, mapOf("lock" to true)).contains("disabled"))
        val open = Action.make("save").disabled(false)
        assertFalse(page(open).contains("disabled"))
        val denied = Action.make("save").authorize(false)
        assertFalse(denied.isAuthorized(host(denied)))
        assertFalse(page(denied).contains("wire:click"))
        assertThrows(FireflyException::class.java) { denied.call(host(denied)) }
        val allowed = Action.make("save").authorize { get -> get.get("/ok") == true }
        assertTrue(page(allowed, mapOf("ok" to true)).contains("wire:click"))
        assertFalse(page(allowed, mapOf("ok" to false)).contains("wire:click"))
        val named = Action.make("save").authorize("edit")
        assertFalse(page(named).contains("wire:click"))
        Action.gate { _, _, _ -> true }
        assertTrue(page(named).contains("wire:click"))
        Action.gate { _, _, _ -> false }
        assertFalse(page(named).contains("wire:click"))
        val forced = Action.make("save").authorize("edit").authorize(true)
        Action.gate { _, _, _ -> true }
        assertTrue(forced.isAuthorized(host(forced)))
        val blocked = Action.make("save").authorize("edit").authorize(false)
        assertFalse(blocked.isAuthorized(host(blocked)))
        val both = Action.make("save").authorize { _ -> true }.authorize("edit")
        Action.gate { ability, component, _ -> ability == "edit" && component.name == "save" }
        assertTrue(both.isAuthorized(host(both)))
        assertThrows(FireflyException::class.java) { Action.make("save").authorize("") }
    }

    @Test
    fun iconColorTooltipBadgeSizeAndStyles() {
        val special = "A & B <tag> \"q\""
        val rich = Action.make("save")
            .icon { _ -> "home" }
            .color { _ -> "primary" }
            .tooltip { _ -> special }
            .badge { _ -> "3" }
            .badgeColor { _ -> "accent" }
            .size { _ -> "xs" }
            .iconPosition { _ -> "before" }
            .link()
        val html = page(rich)
        assertTrue(html.contains("ff-icon"))
        assertTrue(html.contains("ff-action-link"))
        assertTrue(html.contains("data-ff-color=\"primary\""))
        assertTrue(html.contains("color:var(--ff-primary)"))
        assertTrue(html.contains("font-size:0.75rem"))
        assertTrue(html.contains("title=\"A &amp; B &lt;tag&gt; &quot;q&quot;\""))
        assertTrue(html.contains("ff-action-badge"))
        assertTrue(html.contains("data-ff-color=\"accent\""))
        assertTrue(html.indexOf("ff-icon") < html.indexOf("ff-action-label"))
        val after = Action.make("save").icon("user").iconPosition("after").color("accent").size("sm")
        val afterHtml = page(after)
        assertTrue(afterHtml.indexOf("ff-action-label") < afterHtml.indexOf("ff-icon"))
        assertTrue(afterHtml.contains("color:var(--ff-accent)"))
        assertTrue(afterHtml.contains("font-size:0.875rem"))
        assertTrue(page(Action.make("save").color("heading").size("md")).contains("color:var(--ff-heading)"))
        assertTrue(page(Action.make("save").color("danger").size("lg")).contains("font-size:1.125rem"))
        assertTrue(page(Action.make("save").size("xl")).contains("font-size:1.25rem"))
        assertFalse(page(Action.make("save").color("danger")).contains("var(--ff-primary)"))
        assertTrue(page(Action.make("save").badge("1")).contains("ff-action-badge"))
        assertFalse(page(Action.make("save").badge("1")).contains("data-ff-color"))
        assertTrue(page(Action.make("save").iconButton().icon("bars")).contains("ff-action-icon-button"))
        assertFalse(page(Action.make("save").iconButton().icon("bars")).contains("ff-action-label"))
        assertTrue(page(Action.make("save").outlined()).contains("ff-action-outlined"))
        assertTrue(page(Action.make("save").grouped()).contains("ff-action-grouped"))
        assertTrue(page(Action.make("save").grouped().button().tooltip("Tip").badgeColor("info")).contains("ff-action-button"))
        assertTrue(page(Action.make("save").tooltip("Tip").badgeColor("info").badge("2")).contains("title=\"Tip\""))
        assertThrows(FireflyException::class.java) { page(Action.make("save").icon("nope")) }
        assertThrows(FireflyException::class.java) { page(Action.make("save").iconPosition("side")) }
        assertThrows(FireflyException::class.java) { page(Action.make("save").size("huge")) }
    }

    @Test
    fun urlAndNewTab() {
        val link = Action.make("docs").url { _ -> "https://example.test/a?b=1" }.openUrlInNewTab(true)
        val html = page(link)
        assertTrue(html.contains("href=\"https://example.test/a?b=1\""))
        assertTrue(html.contains("target=\"_blank\""))
        assertTrue(html.contains("rel=\"noopener noreferrer\""))
        assertTrue(html.contains("ffAction('docs')"))
        assertFalse(html.contains("wire:click"))
        val same = Action.make("docs").url("https://example.test").openUrlInNewTab { _ -> false }
        val sameHtml = page(same)
        assertTrue(sameHtml.contains("href=\"https://example.test\""))
        assertFalse(sameHtml.contains("target="))
        val disabled = Action.make("docs").url("https://example.test").openUrlInNewTab(true)
        disabled.disabled(true)
        val disabledHtml = page(disabled)
        assertTrue(disabledHtml.contains("aria-disabled=\"true\""))
        assertFalse(disabledHtml.contains("href="))
        var ran = false
        link.action { _, _ -> ran = true }
        val result = link.call(host(link))
        assertFalse(ran)
        assertEquals("https://example.test/a?b=1", result.url)
        assertEquals(result.url, result.redirectUrl)
        assertThrows(FireflyException::class.java) { Action.make("docs").url("").call(host(Action.make("docs").url(""))) }
    }

    @Test
    fun confirmationMountsDialog() {
        val save = Action.make("save").label("Save").requiresConfirmation()
        val html = page(save)
        assertTrue(html.contains("ff-dialog-modal"))
        assertTrue(html.contains("data-ff-align=\"top\""))
        assertTrue(html.contains("ff-dialog-lg"))
        assertTrue(html.contains("data-ff-drag"))
        assertTrue(html.contains("@click=\"open = true\""))
        assertTrue(html.contains("data-ff-dialog-open"))
        assertTrue(html.contains("data-ff-dialog-mount"))
        assertTrue(html.contains("data-ff-dialog-panel"))
        assertTrue(html.contains("@keydown.escape.window=\"open = false\""))
        assertTrue(html.contains("ff-dialog-overlay\" data-ff-dialog-close @click=\"open = false\""))
        assertTrue(html.contains("ff-action-dialog\" data-ff-dialog-panel x-show=\"open\" x-cloak style=\"display:none\" hidden"))
        assertTrue(html.contains("""aria-modal="true""""))
        assertTrue(html.contains("ff-action-close"))
        assertTrue(html.contains(">Confirm<"))
        assertTrue(html.contains(">Cancel<"))
        assertTrue(html.contains("autofocus"))
        assertTrue(html.contains("x-data=\"ffAction('save')\""))
        assertTrue(html.contains("wire:click=\"save\""))
        assertTrue(html.contains("Save"))
        val mounted = save.mountDialog(Schema.make().components(save))
        val mountedClosed = mounted.render()
        assertTrue(mountedClosed.contains("ff-dialog-modal"))
        assertTrue(mountedClosed.contains(" hidden"))
        assertTrue(mountedClosed.contains("""aria-hidden="true""""))
        assertFalse(mountedClosed.contains("ff-dialog-overlay\" data-ff-dialog-close @click=\"open = false\""))
        assertTrue(mounted.render(open = true).contains("""aria-modal="true""""))
        assertThrows(FireflyException::class.java) { Action.make("save").mountDialog(Schema.make()) }

        val delete = Action.make("delete").label("Delete").postForm("delete", """<input type="hidden" name="id" value="1" />""")
        val deleteHtml = page(delete)
        assertTrue(deleteHtml.contains("""action="delete""""))
        assertTrue(deleteHtml.contains("""name="id" value="1""""))
        assertTrue(deleteHtml.contains("ff-action-post"))
        assertTrue(deleteHtml.contains(">Delete<"))
        assertThrows(FireflyException::class.java) { Action.make("x").postForm("") }
    }

    @Test
    fun dialogModesWidthsAndCopy() {
        assertTrue(page(Action.make("edit").slideOver().modalHeading("Edit")).contains("data-ff-position=\"end\""))
        assertTrue(page(Action.make("edit").slideOverStart()).contains("data-ff-position=\"start\""))
        val full = page(Action.make("edit").slideOver().fullscreen().modalHeading("Edit"))
        assertTrue(full.contains("ff-dialog-fullscreen"))
        assertFalse(full.contains("data-ff-drag"))
        assertFalse(full.contains("ff-dialog-sidebar"))
        val wide = page(Action.make("edit").modal().modalWidth("xl").modalHeading("Edit"))
        assertTrue(wide.contains("ff-dialog-xl"))
        assertTrue(wide.contains("ff-dialog-modal"))
        assertTrue(page(Action.make("edit").modal(true).modalWidth("screen").modalHeading("Edit")).contains("ff-dialog-screen"))
        assertThrows(FireflyException::class.java) { page(Action.make("edit").modal().modalWidth("nope")) }
        val described = Action.make("edit")
            .modalDescription("A & B <b>")
            .modalSubmitActionLabel("Send")
            .modalCancelActionLabel("Back")
            .modalIcon("user")
            .modalIconColor("primary")
            .modalAutofocus(false)
            .modalCloseButton(false)
            .closeModalByClickingAway(false)
            .closeModalByEscaping(false)
            .modalDraggable(false)
        val describedHtml = page(described)
        assertTrue(describedHtml.contains("A &amp; B &lt;b&gt;"))
        assertTrue(describedHtml.contains(">Send<"))
        assertTrue(describedHtml.contains(">Back<"))
        assertTrue(describedHtml.contains("ff-action-modal-icon"))
        assertTrue(describedHtml.contains("data-ff-color=\"primary\""))
        assertFalse(describedHtml.contains("autofocus"))
        assertFalse(describedHtml.contains("ff-action-close"))
        assertFalse(describedHtml.contains("@keydown.escape"))
        assertFalse(describedHtml.contains("overlay\" @click"))
        assertFalse(describedHtml.contains("data-ff-drag"))
        val named = page(Action.make("edit").requiresConfirmation())
        assertTrue(named.contains(">edit</"))
        val fromLabel = page(Action.make("edit").label("Rename").modal())
        assertTrue(fromLabel.contains("Rename"))
        assertTrue(fromLabel.contains(">Submit<"))
        val closure = Action.make("edit")
            .modalHeading { _ -> "Heading" }
            .modalDescription { _ -> "About" }
            .modalSubmitActionLabel { _ -> "Go" }
            .modalCancelActionLabel { _ -> "Stop" }
            .modalIcon { _ -> "bars" }
            .modalIconColor { _ -> "accent" }
            .modalWidth { _ -> "sm" }
        val closureHtml = page(closure)
        assertTrue(closureHtml.contains("Heading"))
        assertTrue(closureHtml.contains("About"))
        assertTrue(closureHtml.contains(">Go<"))
        assertTrue(closureHtml.contains(">Stop<"))
        assertTrue(closureHtml.contains("ff-dialog-sm"))
        val iconOnly = page(Action.make("edit").modalIcon("home").modal())
        assertTrue(iconOnly.contains("ff-action-modal-icon"))
        assertFalse(iconOnly.contains("ff-action-modal-icon\" data-ff-color"))
        Dialog.configureUsing { it.width("sm") }
        assertTrue(page(Action.make("edit").requiresConfirmation()).contains("ff-dialog-sm"))
        val quiet = Action.make("edit").requiresConfirmation().modal(false)
        assertFalse(page(quiet).contains("ff-dialog"))
        assertTrue(page(quiet).contains("wire:click=\"edit\""))
        val hiddenModal = Action.make("edit").slideOver().modalHidden(true)
        assertFalse(page(hiddenModal).contains("ff-dialog"))
        val asked = Action.make("edit").requiresConfirmation { get -> get.get("/ask") == true }
        assertTrue(page(asked, mapOf("ask" to true)).contains("ff-dialog"))
        assertFalse(page(asked, mapOf("ask" to false)).contains("ff-dialog"))
        assertTrue(page(Action.make("edit").schema()).contains("wire:click"))
        assertFalse(page(Action.make("edit").schema()).contains("ff-dialog"))
    }

    @Test
    fun schemaContentFooterAndPlacement() {
        val extra = Action.make("extra").label("Extra")
        val save = Action.make("save")
            .placement(ActionPlacement.HEADER)
            .schema(Wizard.make().steps(WizardStep.make("One")), Text.make("Hello"))
            .extraModalFooterActions(extra)
        val html = page(save)
        assertTrue(html.contains("ff-placement-header"))
        assertTrue(html.contains("ff-wizard"))
        assertTrue(html.contains("Hello"))
        assertTrue(html.contains("wire:click=\"extra\""))
        val send = Action.make("send").label("Send")
        val back = Action.make("back").label("Back")
        val parent = Action.make("save").modalHeading("Save").modalSubmitAction(send).modalCancelAction(back)
        val parentHtml = page(parent)
        assertTrue(parentHtml.contains("wire:click=\"send\""))
        assertTrue(parentHtml.contains("wire:click=\"back\""))
        assertFalse(parentHtml.contains("ff-action-cancel"))
        val self = Action.make("save")
        assertThrows(FireflyException::class.java) { self.schema(self) }
        assertThrows(FireflyException::class.java) { self.modalSubmitAction(self) }
        assertThrows(FireflyException::class.java) { self.modalCancelAction(self) }
        assertThrows(FireflyException::class.java) { self.extraModalFooterActions(self) }
        val linked = Action.make("docs").url("https://example.test").openUrlInNewTab(true).requiresConfirmation()
        val linkedHtml = page(linked)
        assertTrue(linkedHtml.contains("ff-action-submit"))
        assertTrue(linkedHtml.contains("target=\"_blank\""))
        assertFalse(linkedHtml.contains("wire:click"))
        val stay = Action.make("docs").url("https://example.test").openUrlInNewTab(false).modalHeading("Docs")
        assertFalse(page(stay).contains("target="))
    }

    @Test
    fun lifecycleRedirectsAndAttributes() {
        val order = ArrayList<String>()
        val save = Action.make("save")
            .arguments(linkedMapOf("id" to 7))
            .before { action, _ ->
                order.add("before")
                assertEquals(7, action.argument("id"))
                assertNull(action.argument("missing"))
            }
            .action { _, _ -> order.add("run") }
            .after { _, _ -> order.add("after") }
            .successRedirectUrl("/done")
            .extraAttributes(mapOf("data-test" to "a & b"))
        val values = linkedMapOf<String, Any?>("id" to 7)
        save.arguments(values)
        values["id"] = 9
        val result = save.call(host(save))
        assertEquals(listOf("before", "run", "after"), order)
        assertEquals("/done", result.redirectUrl)
        assertEquals(7, save.argument("id"))
        assertTrue(page(save).contains("data-test=\"a &amp; b\""))
        assertTrue(page(save).contains("data-ff-success-url=\"/done\""))
        val closure = Action.make("save").successRedirectUrl { _ -> "/next" }.action { _, _ -> }
        assertEquals("/next", closure.call(host(closure)).redirectUrl)
        val halted = Action.make("save").before { action, _ -> action.halt() }.action { _, _ -> fail("ran") }.after { _, _ -> fail("after") }
        val haltedResult = halted.call(host(halted))
        assertEquals(ActionResult.HALTED, haltedResult.status)
        assertEquals("Action halted", haltedResult.message)
        assertNull(haltedResult.url)
        val failed = Action.make("save")
            .failureRedirectUrl("/nope")
            .action { action, _ -> action.failure("nope") }
            .after { _, _ -> fail("after") }
        val failedResult = failed.call(host(failed))
        assertEquals(ActionResult.FAILED, failedResult.status)
        assertEquals("nope", failedResult.message)
        assertEquals("/nope", failedResult.redirectUrl)
        val failedClosure = Action.make("save").failureRedirectUrl { _ -> "/later" }.action { action, _ -> action.failure("later") }
        assertEquals("/later", failedClosure.call(host(failedClosure)).redirectUrl)
        val boom = Action.make("save").action { _, _ -> throw IllegalStateException("boom") }.after { _, _ -> fail("after") }
        assertThrows(IllegalStateException::class.java) { boom.call(host(boom)) }
        assertThrows(FireflyException::class.java) { Action.make("save").call(host(Action.make("save"))) }
        assertThrows(ActionHalt::class.java) { Action.make("save").halt() }
        assertThrows(ActionFailure::class.java) { Action.make("save").failure("x") }
        assertThrows(FireflyException::class.java) { Action.make("bad-name") }
        assertThrows(FireflyException::class.java) { Action.make("save").extraAttributes(mapOf("bad name" to "x")) }
        assertThrows(FireflyException::class.java) { Action.make("save").extraAttributes(mapOf("wire:click" to "save")) }
        save.extraAttributes(emptyMap())
        assertFalse(page(save).contains("data-test"))
        val dynamic = Action.make("save").extraAttributes { mapOf("data-live" to "1") }.action { _, _ -> }
        assertTrue(page(dynamic).contains("data-live=\"1\""))
        assertThrows(FireflyException::class.java) { Action.make("save").extraAttributes { mapOf("bad name" to "x") }.render(host(Action.make("save"))) }
    }

    @Test
    fun configureUsingAndCustomView(@TempDir dir: Path) {
        Action.configureUsing { it.color("primary") }
        assertTrue(page(Action.make("save")).contains("data-ff-color=\"primary\""))
        Action.configureUsing(null)
        assertFalse(page(Action.make("save")).contains("data-ff-color"))
        val hidden = Noted("hiddenNote").hidden(true)
        assertFalse(page(hidden).contains("data-ff-view"))
        val plain = Noted("plain")
        assertTrue(page(plain).contains("data-ff-view=\"notes.action\""))
        val marked = Noted("marked").label("Marked")
        val views = dir.resolve("views")
        Files.createDirectories(views.resolve("notes"))
        Files.writeString(views.resolve("notes/action.crema.html"), "<i>{{ label }}</i>{{ name }}")
        val rendered = Schema.make().crema(CremaEngine(FileViewFinder(views))).components(marked).render()
        assertTrue(rendered.contains("<i>Marked</i>"))
        assertTrue(rendered.contains("marked"))
        val unlabeled = Schema.make().crema(CremaEngine(FileViewFinder(views))).components(Noted("plain")).render()
        assertTrue(unlabeled.contains("<i>plain</i>"))
    }

    @Test
    fun groups() {
        val save = Action.make("save")
        val group = ActionGroup.make(save)
            .label { _ -> "More" }
            .icon { _ -> "bars" }
            .color { _ -> "primary" }
            .tooltip { _ -> "Menu" }
            .placement(ActionPlacement.HEADER)
            .authorize { _ -> true }
            .dropdown()
        val html = page(group)
        assertTrue(html.contains("ff-action-group-dropdown"))
        assertTrue(html.contains("ff-placement-header"))
        assertTrue(html.contains("More"))
        assertTrue(html.contains("ff-icon"))
        assertTrue(html.contains("color:var(--ff-primary)"))
        assertTrue(html.contains("title=\"Menu\""))
        assertTrue(html.contains("open = !open"))
        assertTrue(html.contains("wire:click=\"save\""))
        assertTrue(group.isAuthorized(host(group)))
        val inline = ActionGroup.make(listOf(save)).button().placement(ActionPlacement.SCHEMA)
        val inlineHtml = page(inline)
        assertTrue(inlineHtml.contains("ff-action-group-inline"))
        assertFalse(inlineHtml.contains("ff-action-group-dropdown"))
        assertEquals(ActionPlacement.SCHEMA, inline.placement())
        val plain = ActionGroup.make(save)
        val plainHtml = page(plain)
        assertTrue(plainHtml.contains("ff-action-group-trigger\" @click=\"open = !open\"></button>"))
        assertFalse(plainHtml.contains("ff-icon"))
        assertFalse(plainHtml.contains("title="))
        assertFalse(plainHtml.contains("data-ff-color"))
        val denied = ActionGroup.make(save).authorize(false)
        assertFalse(page(denied).contains("wire:click"))
        assertFalse(denied.isAuthorized(host(denied)))
        val concealed = ActionGroup.make(save).hidden(true)
        assertTrue(page(concealed).contains("ff-hidden"))
        val locked = ActionGroup.make(save).disabled(true)
        assertTrue(page(locked).contains("disabled"))
        val inlineLocked = ActionGroup.make(save).button().disabled(true)
        assertTrue(page(inlineLocked).contains("aria-disabled=\"true\""))
        ActionGroup.configureUsing { it.icon("user") }
        assertTrue(page(ActionGroup.make(save)).contains("ff-icon"))
        ActionGroup.configureUsing(null)
        assertFalse(page(ActionGroup.make(save)).contains("ff-icon"))
        assertThrows(FireflyException::class.java) { page(ActionGroup.make(save).icon("nope")) }
        val gated = ActionGroup.make(save).authorize("view")
        Action.gate { ability, _, _ -> ability == "view" }
        assertTrue(gated.isAuthorized(host(gated)))
        assertSame(group, group.label("Row").icon("home").color("danger").tooltip("Tip"))
    }

    private fun page(component: org.konaframework.firefly.schemas.Component, values: Map<String, Any?> = emptyMap()): String {
        return Schema.make().fill(values).components(component).render()
    }

    private fun host(component: org.konaframework.firefly.schemas.Component): Schema {
        return Schema.make().components(component)
    }
}

private class Noted(name: String) : Action(name) {
    init {
        view("notes.action")
    }
}
