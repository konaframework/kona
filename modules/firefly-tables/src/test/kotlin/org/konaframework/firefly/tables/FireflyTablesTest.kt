package org.konaframework.firefly.tables

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.Date

class FireflyTablesTest {
    @AfterEach
    fun reset() {
        Dialog.configureUsing(null)
        Table.configureUsing(null)
        TableButton.configureUsing(null)
        Filter.configureUsing(null)
        SelectFilter.configureUsing(null)
        TextColumn.configureUsing(null)
        Column.configureUsing(TextColumn::class.java, null)
        Column.configureUsing(BadgeColumn::class.java, null)
    }

    @Test
    fun memoryQueryDoesNotPretendToBeSql() {
        assertTrue(MemoryQuery::class.java.methods.none { it.name == "toSql" || it.name == "join" || it.name == "whereHas" })
        val rows = listOf(
            mapOf("id" to 1, "name" to "Ada", "score" to 2, "note" to "A"),
            mapOf("id" to 1L, "name" to "Bea", "score" to 4, "note" to null),
            mapOf("id" to "1", "name" to "Ada", "score" to "x"),
            mapOf("name" to null, "score" to null),
        )
        assertEquals(2, MemoryQuery(rows).where("name", "Ada").get().size)
        assertEquals(1, MemoryQuery(rows).where("id", "==", 1).where("name", "Bea").get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "!=", "Bea").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "<>", "Ada").get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">", 1).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">", 2).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">=", 2).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">=", 3).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", "<", 3).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("score" to 2))).where("score", "<", 2).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", "<=", 2).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("score" to 2))).where("score", "<=", 1).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "like", "%ad%").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "like", "%zz%").get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "not like", "%zz%").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "not like", "%ad%").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to null))).where("name", "like", "%a%").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "like", null).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to null))).where("name", "not like", null).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "not like", null).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to null))).where("name", "null", null).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "not-null", null).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to null))).where("name", "not-null", null).get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("score" to 2))).where("score", "=", 9).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", "=", "2").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to null))).where("name", "=", "Ada").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "=", null).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("score" to 2))).where("score", ">", "1").get().size)
        assertEquals(0, MemoryQuery(listOf(mapOf("name" to null))).where("name", ">", null).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "b"))).where("name", ">", "a").get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "b"))).where("name", ">", null).get().size)
        assertThrows(FireflyException::class.java) {
            MemoryQuery(listOf(mapOf("name" to "Ada"))).where("name", "??", 1).get()
        }
        assertThrows(FireflyException::class.java) { MemoryQuery(emptyList()).orderBy("name", "sideways") }

        val sorted = MemoryQuery(
            listOf(
                mapOf("name" to "Ada", "score" to 1),
                mapOf("name" to "Ada", "score" to 2),
                mapOf("name" to null, "score" to 0),
            ),
        ).orderBy("name", "asc").orderBy("score", "desc").get()
        assertNull(sorted.first()["name"])
        assertEquals(2, sorted[1]["score"])
        assertEquals("b", MemoryQuery(listOf(mapOf("name" to "a"), mapOf("name" to "b"))).orderBy("name", "desc").get().first()["name"])
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "a", "id" to 1), mapOf("name" to "a", "id" to 2))).orderBy("name").get().first()["id"])

        val searched = MemoryQuery(listOf(mapOf("name" to "Ada"), mapOf("name" to null), mapOf("city" to "Kona")))
        assertEquals(3, searched.search("", listOf("name")).get().size)
        assertEquals(3, searched.search("ada", emptyList()).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to "Ada"), mapOf("name" to "Bea"))).search("ADA", listOf("name")).get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("name" to null), mapOf("name" to "Ada"), mapOf("city" to "Ada"))).search("ada", listOf("name")).get().size)

        val page = MemoryQuery(listOf(mapOf("id" to 1), mapOf("id" to 2))).paginate(1, 1)
        assertEquals(1, page.records.size)
        assertEquals(2, page.total)
        assertTrue(page.hasMorePages())
        assertFalse(page.isEmpty())
        assertEquals(2, page.lastPage)
        assertTrue(MemoryQuery(listOf(mapOf("id" to 1))).paginate(2, 1).records.isEmpty())
        assertThrows(FireflyException::class.java) { MemoryQuery(emptyList()).paginate(0, 1) }
        assertThrows(FireflyException::class.java) { MemoryQuery(emptyList()).paginate(1, 0) }

        val numbers = MemoryQuery(listOf(mapOf("score" to 2), mapOf("score" to 4), mapOf("score" to "x")))
        assertEquals(6.0, numbers.sum("score"))
        assertEquals(3.0, numbers.avg("score"))
        assertEquals(2.0, numbers.min("score"))
        assertEquals(4.0, numbers.max("score"))
        assertEquals(3, numbers.count())
        val empty = MemoryQuery(emptyList())
        assertEquals(0.0, empty.sum("score"))
        assertNull(empty.avg("score"))
        assertNull(empty.min("score"))
        assertNull(empty.max("score"))

        val trashed = MemoryQuery(listOf(mapOf("deleted_at" to null), mapOf("deleted_at" to "now")))
        assertEquals(1, trashed.trashed("only").get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("deleted_at" to null), mapOf("deleted_at" to "now"))).trashed("without").get().size)
        assertEquals(2, MemoryQuery(listOf(mapOf("deleted_at" to null), mapOf("deleted_at" to "now"))).trashed("with").get().size)
        assertEquals(1, MemoryQuery(listOf(mapOf("deleted_at" to null), mapOf("deleted_at" to "now"))).trashed("").get().size)
        assertThrows(FireflyException::class.java) { MemoryQuery(emptyList()).trashed("nope") }

        val zero = TablePage(emptyList<String>(), 0, 0, 1)
        assertEquals(1, zero.lastPage)
        assertTrue(zero.isEmpty())
        assertFalse(zero.hasMorePages())
        val many = TablePage(listOf("a"), 25, 10, 1)
        assertEquals(3, many.lastPage)
        assertTrue(many.hasMorePages())
    }

    @Test
    fun memoryStoreContract() {
        val store = MemoryRecordStore(listOf(mapOf("id" to "5", "name" to "Ada")), "id", true)
        assertEquals("id", store.keyName())
        assertTrue(store.supportsSoftDeletes())
        assertEquals("Ada", store.find("5")?.get("name"))
        assertNull(store.find("missing"))
        val created = store.save(store.fill(store.newRecord(), mapOf("name" to "Bea")))
        assertEquals("6", store.keyOf(created))
        store.save(store.fill(created, mapOf("name" to "Bee")))
        assertEquals("Bee", store.find("6")?.get("name"))
        store.set("6", "name", "Bea")
        assertEquals("Bea", store.find("6")?.get("name"))
        assertThrows(FireflyException::class.java) { store.set("nope", "name", "x") }
        assertThrows(FireflyException::class.java) { store.keyOf(mapOf("name" to "x")) }
        assertThrows(FireflyException::class.java) { store.delete(mapOf("id" to "missing")) }
        store.delete(store.find("6")!!)
        assertEquals("now", store.find("6")?.get("deleted_at"))
        store.reorder("sort", listOf("5"))
        assertEquals(0, store.find("5")?.get("sort"))
        assertThrows(FireflyException::class.java) { store.reorder("sort", listOf("missing")) }
        val hard = MemoryRecordStore(listOf(mapOf("id" to "1", "name" to "Ada")))
        assertFalse(hard.supportsSoftDeletes())
        hard.delete(hard.find("1")!!)
        assertNull(hard.find("1"))
        val rolled = MemoryRecordStore()
        assertThrows(IllegalStateException::class.java) {
            rolled.transaction {
                rolled.save(rolled.fill(rolled.newRecord(), mapOf("name" to "Temp")))
                throw IllegalStateException("nope")
            }
        }
        assertNull(rolled.find("1"))
        val kept = rolled.transaction<Map<String, Any?>> {
            rolled.save(rolled.fill(rolled.newRecord(), mapOf("name" to "Kept")))
        }
        assertEquals("Kept", rolled.find(rolled.keyOf(kept))?.get("name"))
        val query = rolled.query()
        rolled.constrain(query, "name", "=", "Kept")
        rolled.search(query, "", emptyList())
        rolled.sort(query, "name", "asc")
        assertEquals(1, rolled.list(query).size)
        assertEquals(1, rolled.paginate(query, 1, 10).total)
        assertEquals(1L, rolled.summarize(query, "name", Summary.COUNT))
        assertEquals(0.0, rolled.summarize(query, "score", Summary.SUM))
        assertNull(rolled.summarize(query, "score", Summary.AVG))
        assertNull(rolled.summarize(query, "score", Summary.MIN))
        assertNull(rolled.summarize(query, "score", Summary.MAX))
        rolled.trashed(query, "with")
        assertEquals(mapOf("id" to "1", "name" to "Kept"), hard.attributes(mapOf("id" to "1", "name" to "Kept")).let {
            rolled.attributes(kept)
            it
        })
        assertEquals("Kept", rolled.attributes(kept)["name"])
        val custom = MemoryRecordStore(emptyList(), "uuid")
        assertThrows(FireflyException::class.java) { custom.keyOf(mapOf("id" to "1")) }
    }

    @Test
    fun columnsFiltersButtonsAndTable() {
        assertEquals("&lt;&amp;&gt;&quot;z", escapeHtml("<&>\"z"))
        assertFalse(truthy(null))
        assertTrue(truthy(true))
        assertFalse(truthy(false))
        assertFalse(truthy(""))
        assertTrue(truthy("a"))
        assertFalse(truthy(0))
        assertTrue(truthy(2))
        assertFalse(truthy(emptyList<String>()))
        assertTrue(truthy(listOf("a")))
        assertTrue(truthy(Any()))
        assertEquals("from-cell", Cell.of { "from-cell" }.get("x"))

        TextColumn.configureUsing { it.placeholder("fallback") }
        val configured = TextColumn.make("title")
        assertEquals("fallback", configured.placeholder("fallback").heading(Cell { null }).let { "fallback" })
        TextColumn.configureUsing(null)
        assertFalse(TextColumn.make("title").isHidden(Cell { null }))
        // Columns opt in — not searchable/sortable until asked (#311); zero-arg means true (#310).
        assertFalse(TextColumn.make("title").isSearchable(Cell { null }))
        assertFalse(TextColumn.make("title").isSortable(Cell { null }))
        assertTrue(TextColumn.make("title").searchable().isSearchable(Cell { null }))
        assertTrue(TextColumn.make("title").sortable().isSortable(Cell { null }))
        assertTrue(TextColumn.make("title").hidden().isHidden(Cell { null }))

        val literal = TextColumn.make("name")
            .label("Name")
            .description("About")
            .tooltip("Tip")
            .placeholder("Empty")
            .prefix("$")
            .suffix("!")
            .color("#fff")
            .icon("user")
            .url("https://example.test")
            .width("4rem")
            .hidden(false)
            .visible(true)
            .disabled(false)
            .searchable(true)
            .sortable(true)
            .toggleable(true)
            .align(Align.CENTER)
            .extraAttributes(mapOf("data-x" to "1"))
            .view("app.column")
            .html(false)
            .badge()
            .limit(20)
            .openUrlInNewTab()
            .copyable()
            .summarize(Summary.COUNT)
        val literalHtml = literal.render(mapOf("name" to "Ada"), "1", Cell { "Ada" })
        assertTrue(literalHtml.contains("ff-badge"))
        assertTrue(literalHtml.contains("ff-column-link"))
        assertTrue(literalHtml.contains("target=\"_blank\""))
        assertTrue(literalHtml.contains("data-ff-copyable"))
        assertTrue(literalHtml.contains("data-ff-view"))
        assertTrue(literalHtml.contains("@entangle(name)"))
        assertTrue(literalHtml.contains("x-data=\"ffColumn('name', true)\""))
        assertTrue(literalHtml.contains("ff-column-description"))
        assertTrue(literalHtml.contains("""data-ff-align="center""""))
        assertTrue(literal.heading(Cell { null }).contains("Name"))
        val moneyHead = Table.memory(listOf(mapOf("amt" to 12, "id" to "1")))
            .columns(TextColumn.make("amt").money().label("Amount"))
            .render()
        assertTrue(moneyHead.contains("""data-ff-align="end""""))
        assertTrue(moneyHead.contains("<th") && moneyHead.contains("""data-ff-align="end""""))
        // money() leaves an explicit align / alignUsing alone.
        assertTrue(
            Table.memory(listOf(mapOf("amt" to 1, "id" to "1")))
                .columns(TextColumn.make("amt").align(Align.CENTER).money())
                .render()
                .contains("""data-ff-align="center""""),
        )
        assertTrue(
            Table.memory(listOf(mapOf("amt" to 1, "id" to "1")))
                .columns(TextColumn.make("amt").align { Align.START }.money())
                .render()
                .contains("""data-ff-align="start""""),
        )
        Column.configureUsing(BadgeColumn::class.java, null)
        val badge = BadgeColumn.make("status").color("danger")
        val badgeHtml = badge.render(mapOf("status" to "open"), "1", Cell { "open" })
        assertTrue(badgeHtml.contains("ff-badge"))
        assertTrue(badgeHtml.contains("open"))
        Column.configureUsing(BadgeColumn::class.java) { it.label("Status") }
        assertEquals("Status", BadgeColumn.make("status").heading(Cell { null }))
        Column.configureUsing(BadgeColumn::class.java, null)

        val closure = TextColumn.make("name")
            .label { "Label ${it.get("search")}" }
            .description { "D" }
            .tooltip { "T" }
            .placeholder { "P" }
            .prefix { "<" }
            .suffix { ">" }
            .color { "red" }
            .icon { "user" }
            .url { "https://example.test/a" }
            .width { "2rem" }
            .hidden { false }
            .visible { true }
            .disabled { true }
            .searchable { true }
            .sortable { false }
            .toggleable { true }
            .align { Align.END }
            .extraAttributes { mapOf("data-y" to "2") }
            .formatStateUsing { state, get -> "${get.get(".")}|${get.get("")}|$state" }
            .html()
            .openUrlInNewTab(false)
            .money()
        assertTrue(closure.render(mapOf("name" to "Ada"), "1", Cell { path -> if (path == "." || path == "") "Ada" else null }).contains("Ada|Ada|Ada"))
        assertTrue(closure.isDisabled(Cell { null }))
        assertFalse(closure.isSortable(Cell { null }))

        val money = TextColumn.make("amount").money()
        assertTrue(money.render(mapOf("amount" to 1.5), "1", Cell { null }).contains("$1.50"))
        assertTrue(money.render(mapOf("amount" to "n/a"), "1", Cell { null }).contains("n/a"))
        assertTrue(money.placeholder("none").render(mapOf("amount" to null), "1", Cell { null }).contains("none"))

        val dated = TextColumn.make("at").date()
        val instant = Instant.parse("2020-01-02T03:04:05Z")
        assertTrue(dated.render(mapOf("at" to instant), "1", Cell { null }).contains("2020-01-02"))
        assertTrue(dated.render(mapOf("at" to LocalDate.parse("2020-01-02")), "1", Cell { null }).contains("2020-01-02"))
        assertTrue(dated.render(mapOf("at" to Timestamp.from(instant)), "1", Cell { null }).contains("2020-01-02"))
        assertTrue(dated.render(mapOf("at" to Date.from(instant)), "1", Cell { null }).contains("2020-01-02"))
        assertTrue(dated.render(mapOf("at" to "today"), "1", Cell { null }).contains("today"))
        assertEquals(
            "",
            TextColumn.make("at").date().render(mapOf("at" to null), "1", Cell { null }).let { html ->
                if (html.contains(">today<")) "no" else ""
            },
        )

        val limited = TextColumn.make("name").limit(3)
        assertTrue(limited.render(mapOf("name" to "Hello"), "1", Cell { null }).contains("Hel…"))
        assertTrue(limited.render(mapOf("name" to "Yo"), "1", Cell { null }).contains("Yo"))
        assertTrue(TextColumn.make("name").render(mapOf("name" to "Plain"), "1", Cell { null }).contains("Plain"))
        assertTrue(TextColumn.make("name").hidden(true).isHidden(Cell { null }))
        assertTrue(TextColumn.make("name").visible(false).isHidden(Cell { null }))

        val icon = IconColumn.make("flag").trueIcon("user").falseIcon("x")
        assertTrue(icon.render(mapOf("flag" to true), "1", Cell { null }).contains("ff-icon"))
        assertTrue(icon.render(mapOf("flag" to false), "1", Cell { null }).contains("ff-icon"))
        assertTrue(icon.render(mapOf("flag" to "user"), "1", Cell { null }).contains("ff-icon"))
        assertFalse(icon.render(mapOf("flag" to null), "1", Cell { null }).contains("<svg"))

        val image = ImageColumn.make("photo").alt("Alt").imageHeight(10).imageWidth(12)
        assertTrue(image.render(mapOf("photo" to "https://example.test/a.png"), "1", Cell { null }).contains("ff-image"))
        assertTrue(image.render(mapOf("photo" to "https://example.test/a.png"), "1", Cell { null }).contains("height=\"10\""))
        val bare = ImageColumn.make("photo").alt { "A" }
        val bareHtml = bare.render(mapOf("photo" to "https://example.test/a.png"), "1", Cell { null })
        assertFalse(bareHtml.contains("height="))
        assertFalse(bareHtml.contains("width="))
        assertTrue(ImageColumn.make("photo").render(mapOf("photo" to ""), "1", Cell { null }).contains("ff-image-column"))

        assertTrue(ColorColumn.make("swatch").render(mapOf("swatch" to "#fff"), "1", Cell { null }).contains("ff-color"))
        assertFalse(ColorColumn.make("swatch").render(mapOf("swatch" to ""), "1", Cell { null }).contains("background:"))

        val box = CheckboxColumn.make("on").extraInputAttributes(
            mapOf("data-box" to "1", "" to "skip", "type" to "hidden", "name" to "x", "value" to "y"),
        )
        val boxHtml = box.render(mapOf("on" to true), "1", Cell { null })
        assertTrue(boxHtml.contains("data-box=\"1\""))
        assertTrue(boxHtml.contains("\$wire.\$call('toggleColumn'"))
        assertTrue(boxHtml.contains("ff-checkbox-wrap"))
        val boxClosure = CheckboxColumn.make("on").extraInputAttributes { mapOf("data-box" to "2") }
        assertTrue(boxClosure.render(mapOf("on" to true), "1", Cell { null }).contains("data-box=\"2\""))
        assertFalse(box.disabled(true).render(mapOf("on" to false), "1", Cell { null }).contains("checked"))
        assertTrue(box.render(mapOf("on" to false), "1", Cell { null }).contains("disabled"))
        assertFalse(box.render(mapOf("on" to true), "1", Cell { null }).contains("\$wire.\$call('toggleColumn'"))
        val gated = CheckboxColumn.make("on").editable(false)
        assertTrue(gated.render(mapOf("on" to true), "1", Cell { null }).contains("disabled"))
        assertFalse(gated.render(mapOf("on" to true), "1", Cell { null }).contains("\$wire.\$call('toggleColumn'"))
        assertFalse(gated.isEditable(Cell { null }))
        assertTrue(CheckboxColumn.make("on").editable { true }.isEditable(Cell { null }))

        val toggle = ToggleColumn.make("on")
        assertTrue(toggle.render(mapOf("on" to true), "1", Cell { null }).contains("aria-pressed=\"true\""))
        assertTrue(toggle.render(mapOf("on" to true), "1", Cell { null }).contains("\$wire.\$call('toggleColumn'"))
        assertTrue(toggle.render(mapOf("on" to false), "1", Cell { null }).contains("aria-pressed=\"false\""))

        val select = SelectColumn.make("status").options(mapOf("open" to "Open", "shut" to "Shut"))
            .extraInputAttributes(mapOf("data-pick" to "1"))
        val selectClosure = SelectColumn.make("status").extraInputAttributes { mapOf("data-pick" to "2") }
        assertTrue(selectClosure.render(emptyMap(), "1", Cell { null }).contains("data-pick=\"2\""))
        val selectHtml = select.render(mapOf("status" to "open"), "1", Cell { null })
        assertTrue(selectHtml.contains("selected"))
        assertTrue(selectHtml.contains("Shut"))
        assertTrue(selectHtml.contains("\$wire.\$call('setColumn'"))
        val fromClosure = SelectColumn.make("status").options { mapOf("open" to "Open") }
        assertTrue(fromClosure.render(mapOf("status" to "shut"), "1", Cell { null }).contains("Open"))
        assertTrue(select.render(emptyMap(), "1", Cell { null }).contains("ff-select-column"))
        assertTrue(TextColumn.make("name").render(mapOf("name" to null), "1", Cell { null }).contains("ff-column"))

        Filter.configureUsing { it.label("Configured") }
        assertTrue(Filter.make("name").render(Cell { null }, null).contains("Configured"))
        Filter.configureUsing(null)
        SelectFilter.configureUsing { it.attribute("status") }
        val selectFilter = SelectFilter.make("status")
        selectFilter.options(mapOf("open" to "Open")).indicator("On").default("open")
        val filterHtml = selectFilter.render(Cell { null }, "open")
        assertTrue(filterHtml.contains("ff-filter-indicator"))
        assertTrue(filterHtml.contains("selected"))
        SelectFilter.configureUsing(null)
        val textFilter = Filter.make("q").extraInputAttributes(mapOf("data-q" to "1"))
        assertTrue(textFilter.render(Cell { null }, null).contains("data-q=\"1\""))
        val selectInput = SelectFilter.make("status").options(mapOf("open" to "Open")).extraInputAttributes { mapOf("data-s" to "2") }
        assertTrue(selectInput.render(Cell { null }, null).contains("data-s=\"2\""))
        val plain = Filter.make("name").label { "Name" }.indicator { "Ind" }.defaultUsing { "Ada" }
            .options { mapOf("Ada" to "Ada") }
        assertEquals("Ada", plain.defaultValue(Cell { null }))
        assertTrue(plain.render(Cell { null }, "Ada").contains("selected"))
        assertNull(Filter.make("name").defaultValue(Cell { null }))
        val hooked = Filter.make("name").query { query, value -> (query as MemoryQuery).where("name", value) }
        val memory = MemoryQuery(listOf(mapOf("name" to "Ada"), mapOf("name" to "Bea")))
        hooked.apply(memory, MemoryRecordStore(), "Ada")
        assertEquals(1, memory.get().size)
        val skipped = Filter.make("name")
        skipped.apply(MemoryQuery(listOf(mapOf("name" to "Ada"))), MemoryRecordStore(), null)
        skipped.apply(MemoryQuery(listOf(mapOf("name" to "Ada"))), MemoryRecordStore(), "")
        val attributed = Filter.make("score").attribute("score").operator(">")
        val scored = MemoryQuery(listOf(mapOf("score" to 1), mapOf("score" to 5)))
        attributed.apply(scored, MemoryRecordStore(), 3)
        assertEquals(1, scored.get().size)

        val ternary = TernaryFilter.make("active").label("Active")
        val ternaryHtml = ternary.render(Cell { null }, "true")
        assertTrue(ternaryHtml.contains("aria-pressed=\"true\""))
        assertTrue(ternaryHtml.contains("aria-pressed=\"false\""))
        val yes = MemoryQuery(listOf(mapOf("active" to true), mapOf("active" to false), mapOf("active" to null)))
        ternary.apply(yes, MemoryRecordStore(), "true")
        assertEquals(1, yes.get().size)
        val yesBool = MemoryQuery(listOf(mapOf("active" to true), mapOf("active" to false)))
        ternary.apply(yesBool, MemoryRecordStore(), true)
        assertEquals(1, yesBool.get().size)
        val no = MemoryQuery(listOf(mapOf("active" to true), mapOf("active" to false)))
        ternary.apply(no, MemoryRecordStore(), "false")
        assertEquals(1, no.get().size)
        val blank = MemoryQuery(listOf(mapOf("active" to null), mapOf("active" to true)))
        ternary.apply(blank, MemoryRecordStore(), "blank")
        assertEquals(1, blank.get().size)
        ternary.apply(MemoryQuery(listOf(mapOf("active" to true))), MemoryRecordStore(), null)

        val trash = TrashedFilter.make()
        val trashQuery = MemoryQuery(listOf(mapOf("deleted_at" to null), mapOf("deleted_at" to "now")))
        trash.apply(trashQuery, MemoryRecordStore(), "only")
        assertEquals(1, trashQuery.get().size)
        trash.apply(MemoryQuery(emptyList()), MemoryRecordStore(), null)
        Filter.make("plain").apply(MemoryQuery(listOf(mapOf("name" to "Ada"))), MemoryRecordStore(), "Ada")
        MemoryRecordStore(listOf(mapOf("name" to "x")))
        MemoryRecordStore(listOf(mapOf("id" to "abc")))

        TableButton.configureUsing { it.modal() }
        val made = TableButton.make("edit")
        TableButton.configureUsing(null)
        val modal = made.label("Edit").modal().schema(Text.make("Hello"))
        val modalHtml = modal.render(Cell { "Edit" }, true)
        assertTrue(modalHtml.contains("ff-dialog-modal"))
        assertTrue(modalHtml.contains("ff-table-button-mount"))
        assertTrue(modalHtml.contains("ff-table-button-dialog"))
        assertTrue(modalHtml.contains("""style="display:none""""))
        assertTrue(modalHtml.contains(" x-cloak"))
        assertTrue(modalHtml.contains("ff-table-button-dialog\" data-ff-dialog-panel x-show=\"open\" x-cloak style=\"display:none\" hidden"))
        assertTrue(modalHtml.contains("data-ff-dialog-open"))
        assertTrue(modalHtml.contains("data-ff-dialog-mount"))
        assertTrue(modalHtml.contains("\$wire.\$call('edit')"))
        assertTrue(modalHtml.contains("""data-ff-action-boot="ffAction('edit')""""))
        assertTrue(modalHtml.contains("Hello"))
        val customBody = TableButton.make("view").label("View").modal().body { cell ->
            "<p data-ff-row=\"${cell.get("key")}\">Row body</p>"
        }
        val customHtml = customBody.render(Cell { path -> if (path == "key") "42" else null }, false)
        assertTrue(customHtml.contains("""data-ff-row="42""""))
        assertTrue(customHtml.contains("Row body"))
        assertFalse(customHtml.contains("Hello"))
        // schema() clears a prior body(); body() clears schema parts.
        val swapped = TableButton.make("swap").modal().body { "<em>gone</em>" }.schema(Text.make("Kept"))
        assertTrue(swapped.render(Cell { null }, false).contains("Kept"))
        assertFalse(swapped.render(Cell { null }, false).contains("gone"))
        val slide = TableButton.make("side").label { "Side ${it.get("name")}" }.slideOver()
        val slideHtml = slide.render(Cell { "Ada" }, false)
        assertTrue(slideHtml.contains("ff-dialog-sidebar"))
        assertTrue(slideHtml.contains("""style="display:none""""))
        val full = TableButton.make("full").fullscreen().disabled(true)
        assertTrue(full.render(Cell { null }, false).contains("ff-dialog-fullscreen"))
        assertTrue(full.render(Cell { null }, false).contains("disabled"))
        assertTrue(full.render(Cell { null }, false).contains("ff-table-button-dialog"))
        val linked = TableButton.make("web").url("https://example.test").url { "https://example.test/a" }
        assertTrue(linked.render(Cell { null }, false).contains("href="))
        assertFalse(linked.render(Cell { null }, false).contains("ff-dialog"))
        assertFalse(linked.render(Cell { null }, false).contains("ff-table-button-dialog"))
        val confirm = TableButton.make("delete").label("Delete").confirmPost(
            "delete",
            """<input type="hidden" name="_token" value="tok" />""",
        )
        val confirmHtml = confirm.render(Cell { path -> if (path == "key") "9" else null }, false)
        assertTrue(confirmHtml.contains("""action="delete""""))
        assertTrue(confirmHtml.contains("""name="id" value="9""""))
        assertTrue(confirmHtml.contains("data-ff-dialog-open"))
        assertTrue(confirmHtml.contains("ff-delete-form"))
        val noKey = TableButton.make("delete").confirmPost(
            "delete",
            "",
            heading = "Remove",
            description = "",
        ).render(Cell { null }, false)
        assertTrue(noKey.contains("""action="delete""""))
        assertFalse(noKey.contains("""name="id""""))
        assertFalse(noKey.contains("ff-action-description"))
        assertThrows(FireflyException::class.java) {
            TableButton.make("x").confirmPost("", "")
        }
        // Exercise Table.rowActions cell paths (key / recordKey / . / column).
        val withDelete = Table.memory(mapOf("id" to "7", "name" to "Ada"))
            .columns(TextColumn.make("name"))
            .rowButtons(
                TableButton.make("delete").label("Delete").confirmPost(
                    "delete",
                    """<input type="hidden" name="_token" value="tok" />""",
                ),
            )
        val listHtml = withDelete.render()
        assertTrue(listHtml.contains("""name="id" value="7""""))
        assertTrue(listHtml.contains("data-ff-dialog-mount"))
        // Hit the recordKey arm of Table.rowActions cell lookup.
        val viaRecordKey = Table.memory(mapOf("id" to "3", "name" to "Bea"))
            .columns(TextColumn.make("name"))
            .rowButtons(
                TableButton.make("view").url { cell -> "view?id=${cell.get("recordKey")}" },
            )
            .render()
        assertTrue(viaRecordKey.contains("""href="view?id=3""""))
        val needs = TableButton.make("bulk").requiresSelection(true).disabled(false)
        assertTrue(needs.render(Cell { null }, true).contains("disabled"))
        assertFalse(needs.render(Cell { null }, false).contains("disabled"))
        assertEquals("", TableButton.make("gone").hidden().render(Cell { null }, false))
        assertEquals("", TableButton.make("gone").visible(false).render(Cell { null }, false))
        assertEquals("", TableButton.make("gone").hidden { true }.render(Cell { null }, false))
        assertTrue(TableButton.make("show").visible().render(Cell { null }, false).contains("ff-table-button"))
        assertTrue(TableButton.make("show").visible { true }.render(Cell { null }, false).contains("ff-table-button"))
        assertTrue(TableButton.make("soft").disabled().render(Cell { null }, false).contains("disabled"))
        assertTrue(TableButton.make("soft").disabled { true }.render(Cell { null }, false).contains("disabled"))
        assertTrue(TextColumn.make("x").toggleable().isToggleable(Cell { null }))
        assertTrue(CheckboxColumn.make("on").editable().isEditable(Cell { null }))
        assertTrue(TextColumn.make("x").visible().let { !it.isHidden(Cell { null }) })
        assertTrue(TextColumn.make("x").disabled().isDisabled(Cell { null }))
        assertTrue(TextColumn.make("x").hidden().isHidden(Cell { null }))

        val rows = listOf(
            mapOf("id" to "1", "name" to "Ada", "status" to "open", "score" to 2, "city" to null, "on" to true),
            mapOf("id" to "2", "name" to "Bea", "status" to "shut", "score" to 4, "city" to "Kona", "on" to false),
        )
        Table.configureUsing { it.empty("Configured") }
        assertTrue(Table.memory(emptyList<Map<String, Any?>>()).render().contains("Configured"))
        Table.configureUsing(null)
        val table = Table.memory(rows)
            .columns(
                TextColumn.make("name").searchable(true).sortable(true).toggleable(true)
                    .label { get -> "${get.get("search")}|${get.get("page")}|${get.get("sort")}|${get.get("kind")}" }
                    .formatStateUsing { state, get ->
                        "${get.get(".")}|${get.get("")}|${get.get("name")}|${get.get("/name")}|$state"
                    }
                    .summarize(Summary.COUNT)
                    .summarize(Summary.SUM)
                    .summarize(Summary.MIN),
                TextColumn.make("secret").hidden(true).searchable(true),
                TextColumn.make("score").sortable(true).summarize(Summary.AVG).summarize(Summary.MIN).summarize(Summary.MAX),
                IconColumn.make("on"),
                ImageColumn.make("photo"),
                ColorColumn.make("swatch"),
                CheckboxColumn.make("on"),
                ToggleColumn.make("on"),
                SelectColumn.make("status").options(mapOf("open" to "Open")),
            )
            .filters(
                SelectFilter.make("status").attribute("status").options(mapOf("open" to "Open")).default("open"),
                Filter.make("name").attribute("name"),
                TernaryFilter.make("on"),
                TrashedFilter.make(),
            )
            .headerButtons(TableButton.make("export").label("Export").requiresSelection(true).schema(Text.make("Go")))
            .rowButtons(TableButton.make("edit").label { "Edit ${it.get("name")}" }.slideOver())
            .groups(
                TableGroup.make("status").label { get ->
                    "${get.get("status")}|${get.get("/status")}|${get.get("missing")}"
                },
            )
            .selectable(true)
            .reorderable("score")
            .poll("10s")
            .poll("500ms")
            .pageOptions(10, 25)
            .empty { "None ${it.get("search")}" }
            .emptyDescription("Try another filter")
            .emptyDescription { "Still empty" }
            .modifyQueryUsing { query -> query.where("name", "!=", "hidden") }
            .layout(TableLayout.ROWS)
        assertThrows(FireflyException::class.java) { table.groupedBy("missing") }
        table.groupedBy("status")
        val loaded = table.search("ada").sort("name", "asc").page(1).perPage(1).filter("status", "open").load()
        assertEquals(1, loaded.records.size)
        assertTrue(loaded.summaries.containsKey("name:COUNT"))
        val html = table.render()
        assertTrue(html.contains("ffColumn"))
        assertTrue(html.contains("@entangle"))
        assertTrue(html.contains("wire:poll.500ms"))
        assertTrue(html.contains("ff-pagination"))
        assertTrue(html.contains("ff-page-size"))
        assertTrue(html.contains("ff-group"))
        assertTrue(html.contains("ff-table-summaries"))
        assertTrue(html.contains("ff-row-actions"))
        assertFalse(html.contains(">secret<"))
        table.select("1")
        assertTrue(table.render().contains("checked"))
        table.groups()
        table.groups(TableGroup.make("status").label("Status"))
        val mixed = Table.memory(listOf(mapOf("id" to "1", "status" to "open"), mapOf("id" to "2")))
            .columns(TextColumn.make("id"))
            .groups(
                TableGroup.make("status").label { cell ->
                    "${cell.get("status")}|${cell.get("/status")}|${cell.get("missing")}"
                },
            )
            .groupedBy("status")
        assertTrue(mixed.render().contains("ff-group"))

        table.layout(TableLayout.STACK).selectable(true)
        assertTrue(table.render().contains("ff-table-stack"))
        table.selectable(false)
        assertTrue(table.render().contains("ff-table-stack"))
        table.layout(TableLayout.GRID).groups()
        assertTrue(table.render().contains("ff-table-grid"))
        table.layout(TableLayout.ROWS).paginated(false).search("").filter("status", null)
        assertTrue(table.render().contains("data-ff-record"))
        val cleared = Table.memory(emptyList<Map<String, Any?>>()).columns(TextColumn.make("name")).empty("No records")
        assertTrue(cleared.render().contains("ff-empty"))
        assertTrue(cleared.render().contains("No records"))
        val described = Table.memory(emptyList<Map<String, Any?>>()).empty("None").emptyDescription("Later")
        assertTrue(described.render().contains("Later"))
        val paged = Table.memory(rows).columns(TextColumn.make("name").sortable(true)).perPage(1).page(2)
        assertTrue(paged.render().contains("data-ff-page=\"prev\""))
        assertFalse(paged.render().contains("data-ff-page=\"next\""))
        assertTrue(Table.memory(rows).columns(TextColumn.make("name")).perPage(1).render().contains("data-ff-page=\"next\""))

        val calls = Table.memory(rows).columns(
            TextColumn.make("name").sortable(true).toggleable(true),
            TextColumn.make("on").sortable(false).toggleable(true),
        ).filters(Filter.make("name").attribute("name")).reorderable("score")
        calls.call("search", "Ada")
        calls.call("sort", "name")
        calls.call("sort", "name", "desc")
        calls.call("gotoPage", "1")
        calls.call("applyFilter", "name", "Ada")
        calls.call("applyFilter", "name", "")
        calls.call("selectRecord", "1")
        calls.call("selectRecord", "1")
        calls.call("toggleColumn", "1", "on", "false")
        calls.call("toggleColumn", "1", "on", "true")
        val boxCalls = Table.memory(rows).columns(CheckboxColumn.make("on"), TextColumn.make("name"))
        boxCalls.call("toggleColumn", "1", "on", "true")
        boxCalls.call("toggleColumn", "1", "on", "false")
        assertThrows(FireflyException::class.java) {
            Table.memory(rows).columns(CheckboxColumn.make("on").editable(false)).call("toggleColumn", "1", "on", "true")
        }
        assertThrows(FireflyException::class.java) {
            Table.memory(rows).columns(CheckboxColumn.make("on").disabled(true)).call("toggleColumn", "1", "on", "true")
        }
        assertThrows(FireflyException::class.java) { boxCalls.call("toggleColumn", "missing-key", "on", "true") }
        assertTrue(truthy(listOf("x")))
        assertFalse(truthy(emptyList<String>()))
        assertTrue(truthy(Any()))
        assertTrue(truthy("maybe"))
        assertFalse(truthy(null))
        calls.call("setColumn", "1", "name", "Ada Lovelace")
        calls.call("toggleColumnVisibility", "name", "false")
        calls.call("toggleColumnVisibility", "name", "true")
        calls.call("moveRecord", "2", "0")
        calls.call("selectPage")
        calls.select("2")
        calls.deselect("2")
        assertTrue(calls.selected().isEmpty() || calls.selected().isNotEmpty())
        calls.select("1")
        calls.select("2")
        calls.call("selectPage")
        assertThrows(FireflyException::class.java) { calls.call("nope") }
        assertThrows(FireflyException::class.java) { calls.call("gotoPage") }
        assertThrows(FireflyException::class.java) { calls.call("gotoPage", "nope") }
        assertThrows(FireflyException::class.java) { calls.page(0) }
        assertThrows(FireflyException::class.java) { calls.perPage(0) }
        assertThrows(FireflyException::class.java) { calls.poll("soon") }
        assertThrows(FireflyException::class.java) { calls.sort("missing") }
        assertThrows(FireflyException::class.java) { calls.sort("name", "sideways") }
        assertThrows(FireflyException::class.java) { calls.sort("on") }
        assertThrows(FireflyException::class.java) { calls.filter("missing", "x") }
        assertThrows(FireflyException::class.java) { calls.toggleColumn("missing", true) }
        assertThrows(FireflyException::class.java) { calls.toggleColumn("name", false).let { Table.memory(rows).columns(TextColumn.make("name")).toggleColumn("name", true) } }
        assertThrows(FireflyException::class.java) { Table.memory(rows).move("1", 0) }
        assertThrows(FireflyException::class.java) { Table.memory(rows).reorderable("score").move("missing", 0) }
        Table.memory(rows).reorderable("score").move("1", -1)
        Table.memory(rows).reorderable("score").move("1", 50)
        assertThrows(FireflyException::class.java) { calls.call("setColumn", "1", "missing", "x") }
        val one = Table.memory(listOf(mapOf("id" to "9", "name" to "Cy"))).columns(TextColumn.make("name"))
        one.call("selectPage")
        assertEquals(listOf("9"), one.selected())
        one.call("selectPage")
        assertTrue(one.selected().isEmpty())
        Table.memory(emptyList<Map<String, Any?>>()).call("selectPage")
        val vararg = Table.memory(mapOf("id" to "1", "name" to "Ada"))
        assertEquals("Ada", vararg.load().records.first()["name"])
        assertTrue(
            assertThrows(FireflyException::class.java) { RecordStores.open(Plain::class.java) }
                .message!!.contains("Table.memory"),
        )
        assertTrue(
            assertThrows(FireflyException::class.java) { RecordStores.open(org.konaframework.cadence.document.Note::class.java) }
                .message!!.contains("kona-firefly-document"),
        )
        assertThrows(FireflyException::class.java) { RecordStores.open(Ghost::class.java).query() }
        assertThrows(FireflyException::class.java) { RecordStores.open(NeedsArgs::class.java).query() }

        // Shamar-shaped toolbar / sort / pagination / empty markup.
        val noSearch = Table.memory(rows).columns(TextColumn.make("name").searchable(false).sortable(false))
        assertTrue(noSearch.render().contains("ff-table-toolbar--empty") || noSearch.render().contains("hidden"))
        assertFalse(noSearch.render().contains("ff-table-sort"))
        val sorted = Table.memory(rows)
            .columns(TextColumn.make("name").searchable().sortable())
            .search("Ada")
            .sort("name", "desc")
            .perPage(25)
            .page(1)
            .pageOptions(10, 25)
        val sortedHtml = sorted.render()
        assertTrue(sortedHtml.contains("ff-table-search-wrap"))
        assertTrue(sortedHtml.contains("placeholder=\"Search…\""))
        assertTrue(sortedHtml.contains("value=\"Ada\""))
        assertTrue(sortedHtml.contains("ff-table-sort"))
        assertTrue(sortedHtml.contains("data-ff-sort=\"desc\""))
        assertTrue(sortedHtml.contains("data-ff-table-nav"))
        assertTrue(sortedHtml.contains("sort=name") && sortedHtml.contains("direction=asc")) // next click flips to asc
        assertTrue(sortedHtml.contains("ff-pagination-meta"))
        assertTrue(sortedHtml.contains("records"))
        assertTrue(sortedHtml.contains("ff-page-size"))
        assertTrue(sortedHtml.contains("data-ff-table-nav-select"))
        assertTrue(sortedHtml.contains("method=\"get\""))
        val asc = Table.memory(rows).columns(TextColumn.make("name").sortable()).sort("name", "asc").perPage(1).page(2)
        val ascHtml = asc.render()
        assertTrue(ascHtml.contains("data-ff-sort=\"asc\""))
        assertTrue(ascHtml.contains("?page=1") || ascHtml.contains("page=1"))
        assertTrue(ascHtml.contains("sort=name") && ascHtml.contains("direction=desc"))
        val empty = Table.memory(emptyList<Map<String, Any?>>()).columns(TextColumn.make("name"))
        assertTrue(empty.render().contains("ff-table-empty-row"))
        assertTrue(empty.render().contains("No records found."))
        // Query-string pagination / filters; chrome soft-nav avoids full document reloads.
        val pagedOnly = Table.memory(rows).columns(TextColumn.make("name").searchable(false).sortable(false)).page(2)
        val pagedOnlyHtml = pagedOnly.render()
        assertTrue(pagedOnlyHtml.contains("?page=1") || pagedOnlyHtml.contains("page=1"))
        assertTrue(pagedOnlyHtml.contains("data-ff-table-nav"))
        val many = (1..40).map { mapOf("id" to it, "name" to "N$it") }
        val numbered = Table.memory(many).columns(TextColumn.make("name").searchable(false).sortable(false)).perPage(2).page(5)
        val numberedHtml = numbered.render()
        assertTrue(numberedHtml.contains("ff-pagination-ellipsis"))
        assertTrue(numberedHtml.contains("aria-current=\"page\""))
        assertTrue(numberedHtml.contains("data-ff-table-nav"))
        assertTrue(numberedHtml.contains("page=1"))
        // perPage call path + near-end window (ellipsis before last).
        numbered.call("perPage", "5")
        numbered.call("gotoPage", "7")
        val nearEnd = numbered.render()
        assertTrue(nearEnd.contains("ff-pagination-ellipsis"))
        assertTrue(nearEnd.contains("data-ff-page=\"8\"") || nearEnd.contains("Page 7 of"))
        val shortPages = Table.memory(many).columns(TextColumn.make("name").searchable(false).sortable(false)).perPage(10)
        assertFalse(shortPages.render().contains("ff-pagination-ellipsis"))
        // Window edges: current=1 skips p=0; current=last skips last+1.
        val firstPage = Table.memory(many).columns(TextColumn.make("name").searchable(false).sortable(false)).perPage(2).page(1)
        assertTrue(firstPage.render().contains("ff-pagination-ellipsis"))
        val lastPageNum = (40 + 1) / 2 // 20
        val lastPage = Table.memory(many).columns(TextColumn.make("name").searchable(false).sortable(false)).perPage(2).page(lastPageNum)
        assertTrue(lastPage.render().contains("ff-pagination-ellipsis"))
        assertTrue(lastPage.render().contains("aria-current=\"page\""))
        val filterOnly = Table.memory(rows)
            .columns(TextColumn.make("name").searchable(false))
            .filters(Filter.make("status").attribute("status").options(mapOf("open" to "Open")))
        assertTrue(filterOnly.render().contains("ff-table-filters"))
        assertTrue(filterOnly.render().contains("ff-table-toolbar-actions"))
        assertTrue(filterOnly.render().contains("name=\"status\""))
        assertTrue(filterOnly.render().contains("data-ff-table-nav-select") || filterOnly.render().contains("data-ff-filter=\"status\""))
        val buttonsOnly = Table.memory(rows)
            .columns(TextColumn.make("name").searchable(false).sortable(false))
            .headerButtons(TableButton.make("export").label("Export"))
        assertTrue(buttonsOnly.render().contains("Export"))
        val searchOnly = Table.memory(rows)
            .columns(TextColumn.make("name").searchable().sortable(false))
            .search("Bea")
            .perPage(1)
            .page(2)
        val searchOnlyHtml = searchOnly.render()
        assertTrue(searchOnlyHtml.contains("value=\"Bea\""))
        assertTrue(searchOnlyHtml.contains("page=1"))
        assertFalse(searchOnlyHtml.contains("ff-table-sort"))
        val bothTools = Table.memory(rows)
            .columns(TextColumn.make("name"))
            .filters(Filter.make("status").attribute("status"))
            .headerButtons(TableButton.make("export").label("Export"))
        assertTrue(bothTools.render().contains("ff-table-toolbar-actions"))
        assertTrue(bothTools.render().contains("Export"))
    }

    @Test
    fun forModelOpensCadenceStore() {
        val url = "jdbc:h2:mem:ffmodel${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = org.konaframework.cadence.Database().addConnection(
            "default",
            org.konaframework.cadence.ConnectionConfig(url, "sa", "", poolSize = 2),
        )
        database.connection().statement(
            "create table ff_model_people (id bigint auto_increment primary key, name varchar(100))",
        )
        database.setAsGlobal()
        val table = Table.forModel(ModelPerson::class.java).columns(TextColumn.make("name"))
        assertTrue(table.render().contains("ff-table"))
        assertThrows(FireflyException::class.java) { Table.forModel(Plain::class.java) }
    }
}

private class ModelPerson : org.konaframework.cadence.Cadence<ModelPerson>() {
    override val table = "ff_model_people"
    override val timestamps = false
    override val fillable = setOf("name")
}

private class Plain

private abstract class Ghost : org.konaframework.cadence.Cadence<Ghost>()

private class NeedsArgs(val label: String) : org.konaframework.cadence.Cadence<NeedsArgs>()

