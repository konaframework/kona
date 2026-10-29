package org.konaframework.firefly.forms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.MemoryQuery
import org.konaframework.firefly.tables.MemoryRecordStore
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import java.util.function.Consumer

class FireflyFormsHelperTest {
    @Test
    fun fieldChromeRendersActions() {
        val field = TextInput.make("title")
            .prefixAction(Action.make("prefix").label("Prefix"))
            .suffixAction(Action.make("suffix").label("Suffix"))
            .hintAction(Action.make("hint").label("Hint"))
        val html = Schema.make().components(field).render()
        assertTrue(html.contains("data-ff-action=\"prefix\""))
        assertTrue(html.contains("data-ff-action=\"suffix\""))
        assertTrue(html.contains("data-ff-action=\"hint\""))
    }

    @Test
    fun repeaterRendersItemActions() {
        val rows = Repeater.make("rows").schema(TextInput.make("name"))
            .addAction(Action.make("addRow").label("Add row"))
            .deleteAction(Action.make("deleteRow").label("Delete"))
            .cloneAction(Action.make("cloneRow").label("Clone"))
            .extraItemActions(Action.make("extraRow").label("Extra"))
        val schema = Schema.make().components(rows)
        schema.fill(mapOf("rows" to listOf(mapOf("name" to "Ada"))))
        val html = schema.render()
        assertTrue(html.contains("data-ff-action=\"addRow\""))
        assertTrue(html.contains("data-ff-action=\"deleteRow\""))
        assertTrue(html.contains("data-ff-action=\"cloneRow\""))
        assertTrue(html.contains("data-ff-action=\"extraRow\""))

        val plain = Repeater.make("plain").schema(TextInput.make("name"))
        val plainSchema = Schema.make().components(plain)
        plainSchema.fill(mapOf("plain" to listOf(mapOf("name" to "Bea"))))
        val plainHtml = plainSchema.render()
        assertTrue(plainHtml.contains("data-ff-repeater-add"))
        assertTrue(!plainHtml.contains("data-ff-action"))
    }

    @Test
    fun builderRendersBetweenAction() {
        val builder = Builder.make("page").blocks(
            BuilderBlock("hero", "Hero", listOf(TextInput.make("title"))),
            BuilderBlock("note", "Note", emptyList()),
        )
        val schema = Schema.make().components(builder)
        schema.fill(
            mapOf(
                "page" to listOf(
                    mapOf("type" to "hero", "data" to emptyMap<String, Any?>()),
                    mapOf("type" to "note", "data" to emptyMap<String, Any?>()),
                ),
            ),
        )
        val without = schema.render()
        assertTrue(!without.contains("data-ff-action=\"between\""))
        builder.addBetweenAction(Action.make("between").label("Between"))
        val with = schema.render()
        assertTrue(with.contains("data-ff-action=\"between\""))

        val single = Builder.make("one").blocks(BuilderBlock("hero", "Hero", emptyList()))
            .addBetweenAction(Action.make("betweenOne").label("Between"))
        val oneSchema = Schema.make().components(single)
        oneSchema.fill(mapOf("one" to listOf(mapOf("type" to "hero", "data" to emptyMap<String, Any?>()))))
        assertTrue(!oneSchema.render().contains("data-ff-action=\"betweenOne\""))
    }

    @Test
    fun richEditorWrapsBlockEditInAnAction() {
        val editor = RichEditor.make("body").customBlocks(
            EditorBlock("hero", "Hero", Schema.make()),
            EditorBlock("note", "Note", Schema.make()),
        ).editBlockAction("hero", Action.make("editHero").label("Edit hero"))
        val html = Schema.make().components(editor).render()
        assertTrue(html.contains("data-ff-action=\"editHero\""))
        assertTrue(html.contains("Hero"))
    }

    @Test
    fun selectCreateOptionActionAndRelationshipQuery() {
        val create = Select.make("kind").options(mapOf("a" to "A"))
            .createOptionAction(Action.make("createKind").label("Create"))
        val native = Select.make("nativeKind").options(mapOf("a" to "A")).native(true)
            .createOptionAction(Action.make("createNative").label("Create"))
        val html = Schema.make().components(create, native).render()
        assertTrue(html.contains("data-ff-action=\"createKind\""))
        assertTrue(html.contains("data-ff-action=\"createNative\""))

        val shelf = OptionShelf(
            listOf(
                mapOf("id" to "1", "name" to "Ada"),
                mapOf("id" to "2", "name" to "Grace"),
                mapOf("id" to "3"),
            ),
        )
        val empty = Select.make("emptyRel").relationship("author", "name")
        assertTrue(empty.resolvedOptions(Schema.make().components(empty).record(OptionShelf(emptyList()))).isEmpty())

        var seen: Any? = null
        val filtered = Select.make("authorId").relationship("author", "name") { query ->
            seen = query
            (query as OptionQuery).where("name", "Ada")
        }
        val schema = Schema.make().components(filtered).record(shelf)
        assertEquals(listOf("1"), filtered.resolvedOptions(schema).map { it.value })
        assertEquals("Ada", filtered.resolvedOptions(schema).first().label)
        assertTrue(seen is OptionQuery)

        val open = Select.make("everyone").relationship("author", "name")
        val labels = open.resolvedOptions(Schema.make().components(open).record(shelf)).associate { it.value to it.label }
        assertEquals("Ada", labels["1"])
        assertEquals("", labels["3"])

        val titled = Select.make("authorId").relationship("author", "name").getOptionLabelFromRecordUsing { record ->
            val name = (record as Map<*, *>)["name"]
            if (name == null) "blank" else "Dr $name"
        }
        val titledLabels = titled.resolvedOptions(Schema.make().components(titled).record(shelf)).associate { it.value to it.label }
        assertEquals("Dr Ada", titledLabels["1"])
        assertEquals("blank", titledLabels["3"])
        val limited = Select.make("authorId").relationship("author", "name").optionsLimit(1)
            .disableOptionWhen { it == "1" }
            .getOptionLabelUsing { it }
        val limitedOptions = limited.resolvedOptions(Schema.make().components(limited).record(shelf))
        assertEquals(1, limitedOptions.size)
        assertFalse(limitedOptions.first().enabled)
        // Preload off → no default cap on relationship options (#349).
        val uncapped = Select.make("authorId").relationship("author", "name").preload(false)
        assertTrue(uncapped.resolvedOptions(Schema.make().components(uncapped).record(shelf)).size >= 2)
        // Explicit limit larger than the related set leaves every row.
        val wide = Select.make("authorId").relationship("author", "name").optionsLimit(50)
        assertEquals(
            uncapped.resolvedOptions(Schema.make().components(uncapped).record(shelf)).size,
            wide.resolvedOptions(Schema.make().components(wide).record(shelf)).size,
        )
        val named = Select.make("authorId").relationship({ "author" }, { "name" }) { query ->
            (query as OptionQuery).where("name", "Grace")
        }
        assertEquals("2", named.resolvedOptions(Schema.make().components(named).record(shelf)).single().value)
        assertEquals("author", named.relation()!!.name)

        assertThrows(FireflyException::class.java) {
            Select.make("missing").modifyQueryUsing { }
        }
        filtered.modifyQueryUsing(Consumer { query -> (query as OptionQuery).where("name", "Grace") })
        assertEquals(listOf("2"), filtered.resolvedOptions(schema).map { it.value })
        assertFalse(Schema.make().components(Select.make("authorId").relationship("author", "name").native(false)).render().contains("<select"))
    }

    @Test
    fun modalTableSelectStoresKeys() {
        val bare = ModalTableSelect.make("bare")
        val bareSchema = Schema.make().components(bare)
        assertTrue(bareSchema.render().contains("data-ff-action=\"choose\""))
        assertTrue(!bareSchema.render().contains("ff-table"))
        assertThrows(FireflyException::class.java) { bare.choose(bareSchema, "1") }

        val table = Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name"))
        val pick = ModalTableSelect.make("pick").table(table)
        val schema = Schema.make().components(pick)
        assertTrue(schema.render().contains("ff-table"))
        assertEquals(listOf("1"), pick.choose(schema, "1"))
        assertEquals("1", schema.get("pick"))
        assertEquals(emptyList<String>(), pick.choose(schema, "1"))
        assertNull(schema.get("pick"))

        val many = ModalTableSelect.make("many").multiple(true).table(
            Table.memory(mapOf("id" to "9", "name" to "Lin")).columns(TextColumn.make("name")),
        )
        val manySchema = Schema.make().components(many)
        assertTrue(manySchema.render().contains("data-ff-multiple=\"true\""))
        assertEquals(listOf("9"), many.choose(manySchema, "9"))
        assertEquals(listOf("9"), manySchema.get("many"))
    }

    @Test
    fun morphToSelectLoadsTheChosenType() {
        val posts = MemoryRecordStore(
            listOf(
                mapOf("id" to "1", "name" to "Hello"),
                mapOf("id" to "2"),
            ),
        )
        val morph = MorphToSelect.make("link").titleAttribute("name").type("posts", posts)
        val schema = Schema.make().components(morph)
        assertThrows(FireflyException::class.java) { morph.optionsFor("pages") }
        assertThrows(FireflyException::class.java) { morph.selectType(schema, "pages", "1") }
        val labels = morph.optionsFor("posts")
        assertEquals("Hello", labels["1"])
        assertEquals("", labels["2"])
        morph.modifyQueryUsing(Consumer { query -> (query as MemoryQuery).where("name", "Hello") })
        assertEquals(mapOf("1" to "Hello"), morph.optionsFor("posts"))
        assertEquals(mapOf("type" to "posts", "id" to "1"), morph.selectType(schema, "posts", "1"))

        assertTrue(schema.render().contains("data-ff-value=\"1\""))
        schema.fill(mapOf("link" to "plain"))
        assertTrue(!schema.render().contains("data-ff-value"))
        schema.fill(mapOf("link" to mapOf("id" to "1")))
        assertTrue(!schema.render().contains("data-ff-value"))
        schema.fill(mapOf("link" to mapOf("type" to "")))
        assertTrue(!schema.render().contains("data-ff-value"))
        schema.fill(mapOf("link" to mapOf("type" to "missing")))
        assertTrue(!schema.render().contains("data-ff-value"))
    }

    @Test
    fun fileUploadBrowseListsAndChoosesPaths() {
        val disk = FakeDisk()
        disk.put("photos/a.png", "a")
        disk.put("photos/a.png.ffmeta", "meta")
        disk.put("../nope", "x")
        val upload = FileUpload.make("file").directory("photos")
            .browseAction(Action.make("browse").label("Browse"))
        assertEquals(listOf("photos/a.png"), upload.browse(disk))
        val html = Schema.make().components(upload).render()
        assertTrue(html.contains("data-ff-action=\"browse\""))

        val schema = Schema.make().components(upload)
        assertThrows(FireflyException::class.java) { upload.chooseBrowsed(schema, "../nope") }
        assertEquals("photos/a.png", upload.chooseBrowsed(schema, "photos/a.png"))

        val multi = FileUpload.make("files").directory("photos").multiple(true)
        val multiSchema = Schema.make().components(multi)
        assertEquals(listOf("photos/a.png"), multi.chooseBrowsed(multiSchema, "photos/a.png"))
        assertEquals(listOf("photos/a.png"), multi.chooseBrowsed(multiSchema, "photos/a.png"))
        assertEquals(listOf("photos/a.png", "photos/b.png"), multi.chooseBrowsed(multiSchema, "photos/b.png"))
    }
}

class OptionShelf(private val rows: List<Map<String, Any?>>) {
    fun author(): OptionQuery = OptionQuery(rows)
}

class OptionQuery(rows: List<Map<String, Any?>>) {
    private val rows = rows.map { LinkedHashMap(it) }
    private var column: String? = null
    private var expected: Any? = null

    fun where(column: String, value: Any?): OptionQuery = apply {
        this.column = column
        expected = value
    }

    fun get(): List<Map<String, Any?>> {
        val name = column ?: return rows
        return rows.filter { it[name] == expected }
    }
}
