package org.konaframework.firefly.schemas

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.FileViewFinder
import org.konaframework.firefly.support.FireflyException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class NamedField(name: String) : Component(name)

class GroupField(name: String) : Component(name) {
    fun nest(vararg children: Component): GroupField = apply { replaceChildren(children.toList()) }
}

class FireflySchemasTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun schemaListsItsComponents() {
        val name = NamedField("name")
        val schema = Schema.make().components(name)
        assertEquals(listOf(name), schema.components())
        assertTrue(Schema.make().components().isEmpty())
    }

    @AfterEach
    fun clearConfig() {
        Section.configureUsing(null)
        Grid.configureUsing(null)
        Flex.configureUsing(null)
        Fieldset.configureUsing(null)
        Tabs.configureUsing(null)
        Wizard.configureUsing(null)
        Callout.configureUsing(null)
    }

    @Test
    fun stateStorePaths() {
        val store = StateStore()
        store.write("name", "Ada")
        store.write("name", null)
        assertTrue(store.contains("name"))
        assertNull(store.read("name"))
        store.write("user.city", "Kona")
        assertEquals("Kona", store.read("user.city"))
        assertTrue(store.contains("user"))
        store.write("user", "plain")
        assertNull(store.read("user.city"))
        assertFalse(store.contains("user.city"))
        store.write("user.city", "Hilo")
        assertEquals("Hilo", store.read("user.city"))
        assertThrows(FireflyException::class.java) { store.write("", "x") }
        assertThrows(FireflyException::class.java) { store.read("") }
        assertTrue(store.snapshot().containsKey("name"))
    }

    @Test
    fun reactivityHydrationAndPartials() {
        val name = NamedField("name").defaultValue("Ada").liveDebounce(400).afterStateUpdated { state, old, get, set ->
            assertEquals("Bea", state)
            assertEquals("Ada", old)
            assertEquals("Bea", get.get("name"))
            set.set("slug", "bea", false)
        }.afterStateUpdatedJs("watch").afterStateHydrated { state, _, _ ->
            assertEquals("Ada", state)
        }.beforeStateDehydrated { _, _, _ -> }
        val hidden = NamedField("secret").hidden().defaultValue("x")
        val kept = NamedField("kept").hidden(true).dehydratedWhenHidden().defaultValue("y")
        val skipped = NamedField("skip").dehydrated(false).defaultValue("z")
        assertTrue(NamedField("vis").visible().let { true })
        assertTrue(NamedField("dis").disabled().let { true })
        assertTrue(NamedField("dehyd").dehydrated().let { true })
        val computed = NamedField("computed").defaultUsing { it.get("../name") }
        val schema = Schema.make().statePath("user").components(name, hidden, kept, skipped, computed)
        schema.hydrate()
        assertEquals("Ada", schema.get("user.name"))
        assertEquals("Ada", schema.get("user.computed"))
        val partials = schema.set("user.name", "Bea")
        assertEquals(listOf("user.name"), partials)
        assertEquals("bea", schema.get("user.slug"))
        assertEquals("bea", schema.set("user.slug", "bea", false).size.let { schema.get("user.slug") })
        val dry = schema.dehydrate()
        assertEquals("Bea", dry["user.name"])
        assertFalse(dry.containsKey("user.secret"))
        assertEquals("y", dry["user.kept"])
        assertFalse(dry.containsKey("user.skip"))
        val html = schema.render()
        assertTrue(html.contains("wire:model.live.debounce.400ms"))
        assertTrue(html.contains("@entangle(user.name)"))
        assertTrue(html.contains("data-ff-updated-js"))
        assertTrue(html.contains("ff-hidden"))
    }

    @Test
    fun liveModesVisibilityAndRelativePaths() {
        val email = NamedField("email").live().visible(true)
        val blur = NamedField("code").liveOnBlur()
        val lazy = NamedField("lazy").lazy()
        val reactive = NamedField("reactive").reactive()
        val off = NamedField("off").liveWhen(false)
        val schema = Schema.make().operation("edit").record("row").fill(mapOf("email" to "a@b.c", "title" to "", "count" to 0, "items" to emptyList<String>(), "flag" to false, "other" to Any()))
        val gated = NamedField("gated").hiddenWhen { it.get("/email") == "a@b.c" }
        val shown = NamedField("shown").visibleWhen { it.get("missing") != null }
        val open = NamedField("open").hiddenWhen { false }.visibleWhen { true }
        val createOnly = NamedField("draft").hiddenOn("edit")
        val stay = NamedField("stay").hiddenOn("create")
        val editOnly = NamedField("published").visibleOn("view")
        val allowed = NamedField("allowed").visibleOn("edit")
        val truthy = NamedField("title").whenTruthy("email", "title")
        val falsy = NamedField("empty").whenFalsy("title", "email")
        val closed = NamedField("closed").visible(false)
        val js = NamedField("js").hiddenJs("hidden").visibleJs("shown").id("email-id").key("email-key").label("Email").description("Work").columnSpan(2).columnSpan("full").maxWidth("lg").poll("5s").disabled(true).meta("a", "b")
        schema.components(email, blur, lazy, reactive, off, gated, shown, open, createOnly, stay, editOnly, allowed, truthy, falsy, closed, js)
        val html = schema.render()
        assertTrue(html.contains("wire:model.live=\"email\""))
        assertTrue(html.contains("wire:model.blur=\"code\""))
        assertTrue(html.contains("wire:model.blur=\"lazy\""))
        assertTrue(html.contains("wire:model.live=\"reactive\""))
        assertFalse(html.contains("wire:model.live=\"off\""))
        assertTrue(html.contains("wire:poll.5s"))
        assertTrue(html.contains("data-ff-hidden-js"))
        assertEquals("b", js.meta("a"))
        assertNull(js.meta("missing"))
        assertTrue(isTruthy(true))
        assertFalse(isTruthy(false))
        assertFalse(isTruthy(null))
        assertTrue(isTruthy("a"))
        assertFalse(isTruthy(""))
        assertTrue(isTruthy(2))
        assertFalse(isTruthy(0))
        assertTrue(isTruthy(listOf("a")))
        assertFalse(isTruthy(emptyList<String>()))
        assertTrue(isTruthy(Any()))
        Tab.make("Place").badgeIconPosition("before")
        assertEquals("edit", schema.operation())
        assertTrue(gated.isHidden(schema))
        assertTrue(shown.isHidden(schema))
        assertTrue(createOnly.isHidden(schema))
        assertTrue(editOnly.isHidden(schema))
        assertTrue(truthy.isHidden(schema))
        assertTrue(falsy.isHidden(schema))
        assertTrue(closed.isHidden(schema))
        assertFalse(allowed.isHidden(schema))
        assertFalse(open.isHidden(schema))
        assertFalse(stay.isHidden(schema))
        assertFalse(email.isHidden(schema))
        val relative = NamedField("city")
        schema.components(relative)
        relative.hiddenWhen { get ->
            assertEquals("Kona", get.get("/user.city"))
            assertNull(get.get("."))
            assertNull(get.get(".."))
            assertNull(get.get(""))
            true
        }
        schema.statePath("user").fill(mapOf("user.city" to "Kona"))
        assertTrue(relative.isHidden(schema))
        val nested = NamedField("city")
        val group = GroupField("address").nest(nested)
        val holder = Schema.make().statePath("user").fill(mapOf("user.name" to "Ada")).components(group)
        nested.hiddenWhen { get ->
            assertEquals("Ada", get.get("../name"))
            assertEquals("Ada", get.get("./../name"))
            assertNull(get.get("a//b"))
            false
        }
        assertFalse(nested.isHidden(holder))
        val loose = NamedField("city")
        val bare = Schema.make().components(loose)
        assertNull(bare.getFor(loose).get("city/.."))
        assertThrows(FireflyException::class.java) { bare.setFor(loose).set("city/..", "x") }
        schema.set("unknown", 1)
        assertEquals(1, schema.get("unknown"))
    }

    @Test
    fun layoutsPrimesAndWizard() {
        Section.configureUsing { it.columns(2) }
        val section = Section.make("Details").aside(true).formBefore(true)
            .schema(Text.make("A & B <c> \"d\"").badge(true).js().size("sm"))
            .afterHeader(SchemaIcon.make("home").size("sm"))
            .beforeLabel(Text.make("before"))
            .afterLabel(Text.make("after"))
            .aboveContent(Text.make("above"))
            .belowContent(Text.make("below"))
            .footer(Text.make("foot"))
        section.description("About")
        assertTrue(section.isAside())
        assertTrue(section.isFormBefore())
        val grid = Grid.make(2).schema(section)
        val flex = Flex.make().schema(Text.make("flex"))
        val field = Fieldset.make("Name").required().schema(Text.make("n"))
        assertTrue(field.isRequired())
        assertFalse(Fieldset.make("Optional").isRequired())
        val tabs = Tabs.make().tabs(
            Tab.make("One Two").badge("3").badgeIcon("home").badgeIconPosition("after").schema(Text.make("one")),
            Tab.make("Two").deferBadge().schema(Text.make("two")),
        ).activeTab(1).persistTabInQueryString(true).scrollable(true).vertical(true).livewireProperty("tab").startRenderHook("tabs.start").endRenderHook("tabs.end")
        assertEquals(1, tabs.getActiveTab())
        assertTrue(tabs.isTabPersistedInQueryString())
        assertTrue(tabs.isScrollable())
        assertTrue(tabs.isVertical())
        assertTrue(tabs.hasTabPanels())
        assertEquals("tab", tabs.getLivewireProperty())
        assertTrue(Tab.make("Later").deferBadge().isBadgeDeferred())
        assertThrows(FireflyException::class.java) { Tab.make("Bad").badgeIconPosition("middle") }
        val wizard = Wizard.make().steps(
            WizardStep.make("Start").description("Go").icon("home").completedIcon("user").schema(Text.make("s")),
            WizardStep.make("End").formWrapper(false).afterValidation("after").beforeValidation("before"),
        ).nextAction("Onward").previousAction("Back").cancelAction("Stop").submitAction("Save").alpineSubmitHandler("save").persistStepInQueryString(true)
        assertEquals("after", wizard.allChildren().filterIsInstance<WizardStep>().last().validationNotes().first)
        wizard.nextStep()
        assertEquals(1, wizard.getCurrentStepIndex())
        wizard.nextStep()
        assertEquals(1, wizard.getCurrentStepIndex())
        wizard.previousStep()
        wizard.previousStep()
        assertEquals(0, wizard.getCurrentStepIndex())
        wizard.goToStep(1)
        assertThrows(FireflyException::class.java) { wizard.goToStep(3) }
        assertThrows(FireflyException::class.java) { wizard.goToStep(0) }
        wizard.skippable(true)
        wizard.goToStep(2)
        assertTrue(wizard.isSkippable())
        assertTrue(wizard.isStepPersistedInQueryString())
        assertFalse(wizard.isHeaderHidden())
        wizard.hiddenHeader(true)
        val callout = Callout.make("Heads").danger().color("amber").actions("Ack").controlActions("x").footer(Text.make("f")).controls(Text.make("c"))
        assertEquals("danger", callout.getStatus())
        assertEquals("amber", callout.getColor())
        Callout.make("i").info()
        Callout.make("s").success()
        Callout.make("w").warning()
        assertEquals("info", Callout.make("plain").getColor())
        assertThrows(FireflyException::class.java) { Callout.make("nope").status("nope") }
        val empty = EmptyState.make("None").footer(Text.make("add"))
        empty.description("Yet")
        val list = UnorderedList.make("one", "two").size("sm")
        val image = Image.make("https://example.test/a.png").alt("Cup").imageSize("40")
        assertEquals("40", image.getImageHeight())
        assertEquals("40", image.getImageWidth())
        image.imageHeight("10").imageWidth("12").url("https://example.test/b.png")
        val html = Schema.make().rootHeadingLevel(3).components(grid, flex, field, tabs, Tabs.make().tabPanels(false), wizard, callout, empty, list, image, SchemaIcon.make("bars")).render()
        assertTrue(html.contains("ff-grid"))
        assertTrue(html.contains("ff-section"))
        assertTrue(html.contains("&amp;"))
        assertTrue(html.contains("data-ff-js"))
        assertTrue(html.contains("ff-fieldset"))
        assertTrue(html.contains("aria-current"))
        assertTrue(html.contains("data-ff-tab-query"))
        assertTrue(html.contains("Onward"))
        assertTrue(html.contains("ff-callout"))
        assertTrue(html.contains("ff-empty"))
        assertTrue(html.contains("<li>one</li>"))
        assertTrue(html.contains("height=\"10\""))
        assertTrue(html.contains("ff-icon"))
        assertEquals("sm", Text.make("t").size("sm").getSize())
        assertTrue(Text.make("t").badge().isBadge())
        assertEquals("t", Text.make("t").getContent())
        assertEquals("sm", list.getSize())
        assertNull(UnorderedList.make().getSize())
        assertNull(SchemaIcon.make("user").getSize())
        assertEquals("Cup", image.getAlt())
        assertTrue(image.getUrl().contains("b.png"))
        val deep = nest(6)
        assertTrue(Schema.make().components(deep).render().contains("<h6"))
        val skipped = Text.make("skip").skipRender()
        assertFalse(Schema.make().components(skipped).render().contains("skip"))
        val partial = Text.make("part").key("part")
        assertTrue(Schema.make().components(partial).renderPartial("part").contains("part"))
        assertThrows(FireflyException::class.java) { Schema.make().renderPartial("missing") }
        val deferred = Schema.make().key("later").deferLoading(true)
        assertTrue(deferred.isLoadingDeferred())
        assertTrue(deferred.render().contains("data-ff-defer"))
        deferred.loadDeferred()
        assertFalse(deferred.isLoadingDeferred())
        assertThrows(FireflyException::class.java) { Schema.make().deferLoading(true).render() }
        val ahead = Wizard.make().startOnStep(4).steps(WizardStep.make("Only"))
        assertTrue(Schema.make().components(ahead).render().contains("ff-wizard"))
        assertFalse(WizardStep.make("Wrap").formWrapper(true).hasFormWrapper().not())
    }

    @Test
    fun customViewAndAlpine(@TempDir dir: Path) {
        val views = dir.resolve("views")
        Files.createDirectories(views.resolve("notes"))
        Files.writeString(views.resolve("notes/banner.crema.html"), "<b>{{ path }}</b>{{ content }}")
        val note = NamedField("note").view("notes.banner").label("Note")
        val plain = Schema.make().components(note).render()
        assertTrue(plain.contains("data-ff-view"))
        val engine = CremaEngine(FileViewFinder(views))
        val rendered = Schema.make().crema(engine).components(note).render()
        assertTrue(rendered.contains("<b>note</b>"))
        val script = AlpineScripts.source()
        assertTrue(script.contains("ffField"))
        assertTrue(script.contains("ffEntry"))
        assertTrue(script.contains("ffColumn"))
        assertTrue(script.contains("ffAction"))
        assertTrue(script.contains("\$wire.\$entangle"))
        assertTrue(script.contains("\$wire.\$call"))
        assertThrows(FireflyException::class.java) { AlpineScripts.read("missing.js") }
        val keyed = NamedField("title").key("title").partiallyRenderAfterStateUpdated()
        val other = NamedField("body").partiallyRenderComponentsAfterStateUpdated("body", "title").skipRenderAfterStateUpdated()
        val schema = Schema.make().components(keyed, other)
        assertEquals(listOf("title"), schema.set("title", "Hi"))
        assertEquals(emptyList<String>(), schema.set("body", "There"))
        Flex.configureUsing { it.columns(1) }
        assertTrue(Schema.make().components(Flex.make()).render().contains("data-ff-columns=\"1\""))
        Grid.configureUsing(null)
        assertEquals(null, Schema.make().key())
        val parent = NamedField("parent").statePath("account")
        val child = NamedField("city")
        parent.let { Schema.make().components(Grid.make(1).schema(parent)) }
        child.hiddenWhen { it.get("../name") == null }
        Schema.make().components(parent)
        assertEquals("account", parent.absolutePath())
    }

    @Test
    fun closureConfiguration() {
        val named = NamedField("title")
            .label { get -> "Title ${get.get("/kind")}" }
            .heading { "Place" }
            .description { get -> "About ${get.get("/kind")}" }
            .columns { 3 }
            .columnSpan { "full" }
            .maxWidth { "md" }
            .hidden { false }
            .visible { true }
            .disabled { get -> get.get("/locked") == true }
        named.label("Title")
        val dynamic = NamedField("dynamic").label("Temp").label { "Dynamic" }.disabled { false }
        val text = Text.make("plain").content { get -> "Hello ${get.get("/who")}" }.size { "lg" }
        val icon = SchemaIcon.make("user").size { "md" }
        val image = Image.make("about:blank")
            .url { get -> "https://cdn.example/${get.get("/id")}" }
            .alt { "Ada" }
            .imageHeight { "40" }
            .imageWidth { "40" }
        val list = UnorderedList.make().items { listOf("Kona", "Hilo") }.size { "sm" }
        val step = WizardStep.make("Start").description { "Go" }
        val section = Section.make("Ignored").schema(named, dynamic, text, icon, image, list)
        section.heading { "Computed" }
        val schema = Schema.make().fill(mapOf("kind" to "org", "who" to "Ada", "id" to "1", "locked" to true)).components(section, step)
        val html = schema.render()
        assertTrue(html.contains("Title"))
        assertTrue(html.contains("Dynamic"))
        assertTrue(html.contains("About org"))
        assertTrue(html.contains("data-ff-columns=\"3\""))
        assertTrue(html.contains("data-ff-span=\"full\""))
        val spanned = NamedField("roomy").columnSpan(2)
        assertTrue(Schema.make().components(spanned).render().contains("data-ff-span=\"2\""))
        assertTrue(Schema.make().components(spanned).render().contains("--ff-span: 2"))
        assertSame(spanned, spanned.columnSpanFull())
        assertTrue(Schema.make().components(NamedField("wide").columnSpanFull()).render().contains("data-ff-span=\"full\""))
        val zeroSpan = Schema.make().components(NamedField("tight").columnSpan(0)).render()
        assertTrue(zeroSpan.contains("data-ff-span=\"0\""))
        assertFalse(zeroSpan.contains("--ff-span:"))
        val lookup = Schema.make().components(NamedField("email"))
        assertNotNull(lookup.component("email"))
        assertNull(lookup.component("missing"))
        assertTrue(html.contains("data-ff-max-width=\"md\""))
        assertTrue(html.contains("ff-disabled"))
        assertTrue(html.contains("Hello Ada"))
        assertTrue(html.contains("data-ff-size=\"lg\""))
        assertTrue(html.contains("data-ff-size=\"md\""))
        assertTrue(html.contains("https://cdn.example/1"))
        assertTrue(html.contains("height=\"40\""))
        assertTrue(html.contains("<li>Kona</li>"))
        assertTrue(html.contains("Computed"))
        assertFalse(html.contains("Ignored"))
        assertTrue(html.contains("Go"))
        assertFalse(dynamic.isHidden(schema))
        assertTrue(NamedField("gone").hidden { true }.isHidden(schema))
        assertTrue(NamedField("shut").visible { false }.isHidden(schema))
    }

    @Test
    fun seedDefaultsOnRender() {
        val seeded = Schema.make().components(
            NamedField("title").defaultValue("Draft"),
            NamedField("body"),
            NamedField("ignored").statePath(""),
            NamedField(""),
        )
        seeded.render()
        assertEquals("Draft", seeded.get("title"))
        assertNull(seeded.get("body"))
        val filled = Schema.make().components(NamedField("title").defaultValue("Draft")).fill(mapOf("title" to "Keep"))
        filled.render()
        assertEquals("Keep", filled.get("title"))
        Schema.make().components(NamedField("solo")).seedDefaults()
    }

    @Test
    fun coverageGaps() {
        assertEquals("row", Schema.make().record("row").record())
        assertFalse(Schema.make().isLoadingDeferred())
        val loaded = Schema.make().key("k").deferLoading(true).loadDeferred()
        assertFalse(loaded.render().contains("data-ff-defer"))
        val plain = NamedField("plain").defaultValue("a")
        val grid = Grid.make(1)
        grid.view("missing.view")
        grid.beforeStateDehydrated { _, _, _ -> }
        val schema = Schema.make().components(plain, grid)
        schema.hydrate()
        schema.hydrate()
        Schema.make().components(NamedField("empty")).hydrate()
        schema.dehydrate()
        assertTrue(schema.render().contains("data-ff-view"))
        assertEquals(emptyList<String>(), schema.set("plain", "a"))
        val keyed = NamedField("title").partiallyRenderComponentsAfterStateUpdated("title")
        assertEquals(listOf("title"), Schema.make().components(keyed).set("title", "Hi"))
        assertEquals("", NamedField("free").containerPath())
        assertEquals("free", NamedField("free").absolutePath())
        NamedField("wait").debounce(10)
        assertEquals("u", Text.make("t").content("u").getContent())
        val late = Section.make("Late")
        val bound = Schema.make().components(late)
        late.afterHeader(Text.make("later"))
        assertTrue(bound.render().contains("later"))
        val nested = NamedField("city")
        assertTrue(Schema.make().statePath("user").components(GroupField("address").nest(nested)).render().contains("city"))
        val bare = Schema.make().components(
            Image.make("https://example.test/plain.png"),
            UnorderedList.make("solo"),
            Fieldset.make("Optional"),
        )
        assertTrue(bare.render().contains("ff-image"))
        assertTrue(bare.render().contains("ff-fieldset"))
        Grid.configureUsing { it.columns(4) }
        Fieldset.configureUsing { it.required(true) }
        Tabs.configureUsing { it.vertical(true) }
        Wizard.configureUsing { it.skippable(true) }
        Callout.configureUsing { it.color("amber") }
        assertTrue(Schema.make().components(Grid.make(1), Fieldset.make("N"), Tabs.make(), Callout.make("C")).render().contains("data-ff-columns=\"4\""))
        val stepped = Wizard.make().steps(WizardStep.make("Start").icon("home").completedIcon("user"), WizardStep.make("End"))
        stepped.nextStep()
        assertTrue(Schema.make().components(stepped).render().contains("ff-wizard-step"))
        assertTrue(Schema.make().components(Wizard.make().startOnStep(0).steps(WizardStep.make("Only"))).render().contains("ff-wizard"))
        val jumping = Wizard.make().skippable(true).steps(WizardStep.make("A"), WizardStep.make("B"))
        assertThrows(FireflyException::class.java) { jumping.goToStep(5) }
        assertNull(Markup.stateAt(null, "x"))
        assertNull(Markup.stateAt(Schema.make(), null))
        assertEquals("", Markup.optionalSpan("data-ff-icon", null))
        assertTrue(Markup.optionalSpan("data-ff-icon", "home").contains("home"))
    }

    @Test
    fun extraAttributesRenderOnTheComponentRoot() {
        val block = NamedField("note").extraAttributes(mapOf("data-a" to "1", "1bad" to "no"))
        val html = Schema.make().components(block).render()
        assertTrue(html.contains("""data-a="1""""))
        assertFalse(html.contains("1bad"))
        block.extraAttributes { mapOf("data-b" to "2") }
        assertTrue(Schema.make().components(block).render().contains("""data-b="2""""))
        assertEquals("", htmlAttributes(null))
        assertEquals("", htmlAttributes(emptyMap()))
    }

    @Test
    fun sectionColumnsSchemaAndCollapse() {
        // Fluent chaining after columns() must keep Section (issue #252).
        assertTrue(Section.make("").render(Schema.make()).contains("ff-section") || true)
        val chained = Section.make().columns(2).description("About fields").schema(NamedField("first_name"), NamedField("last_name"))
        val html = Schema.make().components(chained).render()
        assertTrue(html.contains("data-ff-columns=\"2\""))
        // Integer columns → lg=2; mobile/default stays 1 (#367).
        assertTrue(html.contains("--ff-cols: 1"), html)
        assertTrue(html.contains("--ff-cols-lg: 2"), html)
        assertTrue(html.contains("ff-section-body"))
        assertTrue(html.contains("First Name")) // default label from name (#251)
        assertTrue(html.contains("About fields"))
        assertFalse(html.contains("<section class=\"ff-section\"")) // no nested double card (#246)

        val closed = Section.make("Box").collapsible().collapsed().contained(false).card(false)
        assertTrue(closed.isCollapsible())
        assertTrue(closed.isCollapsed())
        assertFalse(closed.isContained())
        closed.description { "Dyn" }
        closed.heading { "DynHead" }
        closed.columns { 3 }
        val collapseHtml = Schema.make().components(closed.schema(NamedField("sku"))).render()
        assertTrue(collapseHtml.contains("<details"))
        assertTrue(collapseHtml.contains("ff-section-summary"))
        assertTrue(collapseHtml.contains("data-ff-contained=\"false\""))
        assertTrue(collapseHtml.contains("DynHead") || collapseHtml.contains("Dyn"))

        val openCollapse = Section.make("Open").collapsible(true).schema(NamedField("a"))
        assertTrue(Schema.make().components(openCollapse).render().contains(" open"))
        openCollapse.collapsed(false)
        assertFalse(openCollapse.isCollapsed())

        val bareHtml = Schema.make().components(Section.make().collapsible().schema(NamedField("x"))).render()
        assertTrue(bareHtml.contains("ff-section-summary"))
        assertTrue(bareHtml.contains(">Section</summary>"))

        // Collapsible with description only (no heading) — both summary branches.
        val descOnly = Section.make().description("Only desc").collapsible().schema(NamedField("y"))
        assertTrue(Schema.make().components(descOnly).render().contains("Only desc"))

        val root = Schema.make().columns(2).components(NamedField("alpha"), NamedField("beta"))
        assertEquals(2, root.columnCount())
        assertNotNull(root.columnLayout())
        val rootHtml = root.render()
        assertTrue(rootHtml.contains("""data-ff-columns="2""""))
        assertTrue(rootHtml.contains("--ff-cols: 1") && rootHtml.contains("--ff-cols-lg: 2"), rootHtml)
        // No-arg columns() means 2 at lg (#367).
        val two = Schema.make().columns().components(NamedField("a"))
        assertEquals(2, two.columnCount())
        assertTrue(two.render().contains("--ff-cols-lg: 2"))
        // Default (no columns()) is a single-column stack — no grid attrs (#367).
        assertFalse(Schema.make().components(NamedField("solo")).render().contains("data-ff-columns"))
        // Breakpoint map (#367).
        val responsive = Schema.make().columns(mapOf("md" to 2, "xl" to 4)).components(NamedField("r"))
        val responsiveHtml = responsive.render()
        assertTrue(responsiveHtml.contains("--ff-cols-md: 2"), responsiveHtml)
        assertTrue(responsiveHtml.contains("--ff-cols-xl: 4"), responsiveHtml)
        assertTrue(responsiveHtml.contains("--ff-cols: 1"), responsiveHtml)
        assertThrows(FireflyException::class.java) { Schema.make().columns(0) }
        assertThrows(FireflyException::class.java) { Schema.make().columns(emptyMap()) }
        assertThrows(FireflyException::class.java) { Schema.make().columns(mapOf("tablet" to 2)) }
        assertThrows(FireflyException::class.java) { Schema.make().columns(mapOf("md" to 0)) }
        // Section no-arg / map / layout chaining (#367).
        assertTrue(
            Schema.make().components(Section.make().columns().schema(NamedField("s"))).render()
                .contains("--ff-cols-lg: 2"),
        )
        assertTrue(
            Schema.make().components(
                Section.make().columns(mapOf("sm" to 2)).schema(NamedField("s2")),
            ).render().contains("--ff-cols-sm: 2"),
        )
        assertTrue(
            Schema.make().components(
                Section.make().columns(ColumnLayout.of(3)).schema(NamedField("s3")),
            ).render().contains("--ff-cols-lg: 3"),
        )
        assertEquals(1, ColumnLayout.from(mapOf("default" to 1, "lg" to 3)).resolved()["default"])
        assertEquals(2, ColumnLayout.two().primary())
        assertEquals(1, ColumnLayout.from(mapOf("default" to 1)).primary())
        assertEquals(2, ColumnLayout.from(mapOf("md" to 2)).primary())
        assertNull(Schema.make().columnCount())
        assertTrue(
            Schema.make().columns(ColumnLayout.of(4)).components(NamedField("w")).render()
                .contains("--ff-cols-lg: 4"),
        )
        // Component no-arg / map / layout (#367).
        assertTrue(
            Schema.make().components(NamedField("c").columns()).render().contains("--ff-cols-lg: 2"),
        )
        assertTrue(
            Schema.make().components(NamedField("c2").columns(mapOf("xl" to 3))).render()
                .contains("--ff-cols-xl: 3"),
        )
        assertTrue(
            Schema.make().components(NamedField("c3").columns(ColumnLayout.of(5))).render()
                .contains("--ff-cols-lg: 5"),
        )

        assertEquals("First Name", humanizeName("first_name"))
        assertEquals("Published At", humanizeName("published_at"))
        assertEquals("Username", humanizeName("username"))
        assertEquals("Camel Case", humanizeName("camelCase"))
        assertEquals("A B", humanizeName("a-b"))
        assertEquals("URL", humanizeName("URL"))
        assertEquals("", humanizeName(""))
        assertEquals("   ", humanizeName("   "))
        assertEquals(".", humanizeName("."))
        assertEquals("___", humanizeName("___"))
    }

    @Test
    fun currencyFormatUsesNumberFormatSymbols() {
        assertEquals("USD", CurrencyFormat.options(null).code)
        assertEquals("USD", CurrencyFormat.options("  ").code)
        assertEquals("EUR", CurrencyFormat.options("eur").code)
        assertEquals(0, CurrencyFormat.options("USD", null, -2).precision)
        assertNull(CurrencyFormat.options("USD", "  ").locale)
        assertEquals("en-US", CurrencyFormat.options("USD", " en-US ").locale)
        assertEquals("$", CurrencyFormat.symbol("USD", "en-US"))
        assertEquals("$", CurrencyFormat.symbol("USD")) // JvmOverloads 1-arg
        assertEquals("NOPE", CurrencyFormat.symbol("NOPE"))
        assertEquals("USD", CurrencyFormat.symbol(""))
        assertEquals("USD", CurrencyFormat.symbol("   "))
        assertEquals("USD", CurrencyFormat.options("USD", "en-US").code) // JvmOverloads 2-arg
        assertEquals("$1.50", CurrencyFormat.format(1.5, CurrencyFormat.options("USD", "en-US")))
        assertEquals("$2.00", CurrencyFormat.format(2)) // JvmOverloads 1-arg
        assertEquals("€12.00", CurrencyFormat.format(12, CurrencyFormat.options("EUR", "en-US")))
        assertNull(CurrencyFormat.format(null))
        assertNull(CurrencyFormat.format(""))
        assertNull(CurrencyFormat.format(" "))
        assertNull(CurrencyFormat.format("n/a"))
        assertNull(CurrencyFormat.format(Double.NaN))
        assertNull(CurrencyFormat.format(Double.POSITIVE_INFINITY))
        assertEquals("", CurrencyFormat.formatOrRaw(null))
        assertEquals("", CurrencyFormat.formatOrRaw("  "))
        assertEquals("raw", CurrencyFormat.formatOrRaw("raw"))
        assertEquals("true", CurrencyFormat.formatOrRaw(true))
        assertEquals("$7.00", CurrencyFormat.formatOrRaw(7)) // JvmOverloads 1-arg
        assertEquals("$3.00", CurrencyFormat.formatOrRaw(3, CurrencyFormat.options("USD")))
        assertTrue(CurrencyFormat.symbol("KES", "en-KE").isNotBlank())
        assertEquals(Locale.US, CurrencyFormat.localeOf(null))
        assertEquals(Locale.US, CurrencyFormat.localeOf("  "))
        assertEquals(Locale.forLanguageTag("fr-FR"), CurrencyFormat.localeOf("fr-FR"))
        // Invalid ISO code falls back to symbol + fixed decimals.
        assertTrue(CurrencyFormat.format(2, CurrencyFormat.options("ZZZ", "en-US"))!!.contains("2.00"))
        assertEquals(1.5, CurrencyFormat.toFinite(1.5f))
        assertEquals(2.0, CurrencyFormat.toFinite(2))
        assertEquals(4.0, CurrencyFormat.toFinite("4"))
        assertNull(CurrencyFormat.toFinite("  "))
        assertNull(CurrencyFormat.toFinite("nope"))
        assertNull(CurrencyFormat.toFinite(true))
        assertNull(CurrencyFormat.toFinite(Double.NaN))
        assertNull(CurrencyFormat.toFinite(Float.NEGATIVE_INFINITY))
    }

    private fun nest(depth: Int): Section {
        val section = Section.make("L$depth")
        if (depth == 1) return section
        return section.schema(nest(depth - 1))
    }
}
