package org.konaframework.firefly.forms

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class FireflyFormsCoverageTest {
    @AfterEach
    fun reset() {
        FieldClock.appZone(java.time.ZoneId.systemDefault())
        FieldClock.pinToday(null)
        Textarea.configureUsing(null)
        TagsInput.configureUsing(null)
        ColorPicker.configureUsing(null)
        Slider.configureUsing(null)
        KeyValue.configureUsing(null)
        TextInput.configureUsing(null)
    }

    @Test
    fun fieldBaseAndMarkup() {
        val bare = BareField("bare")
        val schema = Schema.make().components(bare).fill(mapOf("bare" to "Ada"))
        assertEquals("field", bare.exposedKind())
        assertTrue(schema.render().contains("ff-control"))
        assertEquals("Ada", bare.read(schema))
        val named = TextInput.make("full_name").nullable()
        assertTrue(named.validationRules(Schema.make().components(named)).contains("nullable"))
        assertEquals("&amp;", esc("&"))
        assertEquals("&quot;", esc("\""))
        assertTrue(Schema.make().components(Placeholder.make("note")).render().contains("ff-placeholder"))
        Textarea.configureUsing { it.rows(2) }
        TagsInput.configureUsing { it.separator(",") }
        ColorPicker.configureUsing { it.hex() }
        Slider.configureUsing { it.step(1.0) }
        KeyValue.configureUsing { it.addable(true) }
        assertEquals(2, Textarea.make("body").let { Schema.make().components(it).render().contains("rows=\"2\"") }.let { if (it) 2 else 0 })
    }

    @Test
    fun textColorTagsAndKeys() {
        val whole = TextInput.make("qty").numeric().minValue(2.0).maxValue(1.5)
        assertTrue(Schema.make().components(whole).render().contains("min=\"2\""))
        assertTrue(Schema.make().components(whole).render().contains("max=\"1.5\""))
        val sized = TextInput.make("name").minLength { 4 }.maxLength { 8 }
        val rules = sized.validationRules(Schema.make().components(sized))
        assertTrue(rules.contains("min:4"))
        assertTrue(rules.contains("max:8"))
        val tel = TextInput.make("phone").telRegex("\\d+").tel()
        assertTrue(tel.validationRules(Schema.make().components(tel)).any { it.contains("\\d+") })
        assertTrue(TextInput.make("phone").tel().validationRules(Schema.make().components(TextInput.make("phone").tel())).any { it.startsWith("regex:") })
        assertTrue(Textarea.make("body").validationRules(Schema.make()).isEmpty())
        val area = Textarea.make("body").minLength(2).maxLength(9)
        val areaRules = area.validationRules(Schema.make().components(area))
        assertTrue(areaRules.contains("min:2"))
        assertTrue(areaRules.contains("max:9"))
        val tags = TagsInput.make("tags")
        assertEquals(listOf("a"), tags.tags(listOf(null, " a ", "")))
        assertTrue(Schema.make().components(ColorPicker.make("ink")).render().contains("value=\"\""))
        assertTrue(Schema.make().components(ColorPicker.make("ink")).fill(mapOf("ink" to "#112233")).render().contains("#112233"))
        val raw = linkedMapOf<Any, Any?>(1 to "x", "name" to "Ada", "note" to null)
        val open = KeyValue.make("meta")
        assertTrue(Schema.make().components(open).fill(mapOf("meta" to raw)).render().contains("value=\"Ada\""))
        val pairs = KeyValue.make("meta").editableKeys(false).editableValues(false)
        val html = Schema.make().components(pairs).fill(mapOf("meta" to raw)).render()
        assertTrue(html.contains("readonly"))
        assertTrue(html.contains("value=\"name\""))
    }

    @Test
    fun choicesAndValidation() {
        val select = Select.make("kind").options(mapOf("ab" to "Zed"))
        val schema = Schema.make().components(select)
        assertEquals("ab", select.search(schema, "ab").first().value)
        assertEquals("ab", select.search(schema, "zed").first().value)
        assertTrue(select.search(schema, "nope").isEmpty())
        assertFalse(select.isNative())
        assertThrows(FireflyException::class.java) { select.optionsLimit(0) }
        assertThrows(FireflyException::class.java) { select.maxItems(0) }
        select.relationship("orgs", "title").multiple(true).optionsLimit(5).maxItems(2)
        assertTrue(Schema.make().components(select).fill(mapOf("kind" to listOf(null, "ab"))).render().contains("data-ff-relationship=\"orgs\""))
        assertTrue(Schema.make().components(Select.make("kind").options(mapOf("a" to "A"))).render().contains("ff-combobox"))
        select.multiple(false).options(mapOf("a,b" to "A"))
        assertFalse(select.validationRules(schema).any { it.startsWith("in:") })
        select.options(mapOf("a|b" to "A"))
        assertFalse(select.validationRules(schema).any { it.startsWith("in:") })
        assertTrue(Checkbox.make("ok").validationRules(Schema.make()).isEmpty())
        val box = Checkbox.make("ok").accepted()
        assertTrue(box.validationRules(Schema.make().components(box)).contains("accepted"))
        val native = Select.make("kind").native().options(mapOf("a" to "A", "b" to "B")).disableOptionWhen { it == "a" }
        val nativeHtml = Schema.make().components(native).fill(mapOf("kind" to "b")).render()
        assertTrue(nativeHtml.contains("disabled"))
        assertTrue(nativeHtml.contains("selected"))
        val radio = Radio.make("kind").options(mapOf("a" to "A", "b" to "B"))
        assertTrue(Schema.make().components(radio).render().contains("type=\"radio\""))
        radio.disableOptionWhen { it == "a" }
        assertTrue(Schema.make().components(radio).render().contains("disabled"))
        val checks = CheckboxList.make("tags").options(mapOf("a" to "A"))
        assertTrue(Schema.make().components(checks).fill(mapOf("tags" to listOf(null, "a"))).render().contains("checked"))
        val buttons = ToggleButtons.make("mode").multiple(true).options(mapOf("a" to "A"))
        assertTrue(Schema.make().components(buttons).fill(mapOf("mode" to listOf(null, "a"))).render().contains("ff-toggle-button is-active"))
        val plain = TextInput.make("title")
        val required = TextInput.make("full_name").required().rule("email").validationMessages(mapOf("required" to "Need", "email" to "Bad"))
        val rows = Repeater.make("members").simple(TextInput.make("name")).minItems(1)
        val host = Schema.make().components(plain, required, rows)
        val errors = FieldValidation.validate(host, listOf(plain, required, rows))
        assertTrue(errors.containsKey("full_name"))
        assertTrue(errors.containsKey("members"))
        assertFalse(errors.containsKey("title"))
    }

    @Test
    fun datesMarkdownAndRepeaters() {
        FieldClock.appZone("UTC")
        val day = DatePicker.make("day")
        day.secondsStep(1)
        assertEquals("2024-03-10", day.normalize("2024-03-10T00:30:00Z"))
        assertEquals("2024-03-10", day.normalize("2024-03-10T00:30:00+00:00"))
        val us = DatePicker.make("us").format("m/d/Y")
        assertEquals("03/10/2024", us.normalize("2024-03-10"))
        val odd = DatePicker.make("odd").format("Y-m-d H:i")
        assertThrows(DateTimeException::class.java) { odd.normalize("2024-03-10 09:15") }
        val clock = TimePicker.make("when").timezone("UTC")
        assertEquals("00:30", clock.normalize(Instant.parse("2024-03-10T00:30:00Z")))
        assertEquals("00:30", clock.normalize("2024-03-10T00:30:00Z"))
        assertEquals("00:30", clock.normalize("2024-03-10T00:30:00+00:00"))
        assertEquals("14:30", clock.normalize("14:30:00"))
        val clockFormat = TimePicker.make("when").format("Y-m-d H:i")
        assertThrows(DateTimeException::class.java) { clockFormat.normalize("2024-03-10 14:30") }
        assertTrue(Schema.make().components(TimePicker.make("when").native()).fill(mapOf("when" to "14:30")).render().contains("type=\"time\""))
        val both = DateTimePicker.make("at").timezone("UTC")
        assertThrows(FireflyException::class.java) { both.normalize(LocalTime.NOON) }
        assertNull(both.normalize("nope"))
        val prose = MarkdownHtml.render(
            """
            > quote
            > more

            | a | b |
            | --- | --- |
            | c | d |

            - one
            * two

            1. first
            2. second

            # Title
            ##Title
            ###

            para
            ```
            code
            ```

            para
            - item

            para
            ---

            hi
            1x
            1.x
            12 
            ####### Title

            [mail](mailto:a@b.co)
            [root](/docs)
            [hash](#top)
            [web](http://example.com)
            [safe](https://example.com)
            [bad](javascript:alert(1))
            ![pic](https://example.com/a.png)
            """.trimIndent(),
        )
        assertTrue(prose.contains("<blockquote>"))
        assertTrue(prose.contains("mailto:a@b.co"))
        assertTrue(prose.contains("href=\"/docs\""))
        assertTrue(prose.contains("href=\"#top\""))
        assertTrue(prose.contains("http://example.com"))
        assertTrue(prose.contains("<h1>"))
        assertFalse(prose.contains("<h2>"))
        assertTrue(MarkdownHtml.render("> only").contains("<blockquote>"))
        assertTrue(MarkdownHtml.render("| a |\n| --- |\n| b |").contains("<table>"))
        assertTrue(MarkdownHtml.render("- only").contains("<ul>"))
        assertTrue(MarkdownHtml.render("- ").contains("<li>"))
        assertTrue(MarkdownHtml.render("# Title").contains("<h1>Title</h1>"))
        assertTrue(MarkdownHtml.render("##Title").contains("##Title"))
        assertTrue(MarkdownHtml.render("###").contains("###"))
        assertTrue(MarkdownHtml.render("####### Title").contains("####### Title"))
        assertTrue(MarkdownHtml.render("para").contains("<p>para</p>"))

        val open = Repeater.make("members")
        val openSchema = Schema.make().components(open)
        open.addItem(openSchema)
        assertEquals(1, open.items(openSchema).size)
        assertThrows(FireflyException::class.java) { open.removeItem(openSchema, -1) }
        assertThrows(FireflyException::class.java) { open.cloneItem(openSchema, 4) }
        assertThrows(FireflyException::class.java) { open.moveItem(openSchema, -1, 0) }
        assertThrows(FireflyException::class.java) { open.moveItem(openSchema, 0, -1) }
        assertThrows(FireflyException::class.java) { open.moveItem(openSchema, 4, 0) }
        val blank = Repeater.make("")
        val blankSchema = Schema.make().components(blank)
        assertTrue(blank.items(blankSchema).isEmpty())
        blank.addItem(blankSchema)
        assertEquals(1, blank.items(blankSchema).size)
        val flagged = Repeater.make("members").grid(2).orderColumn("sort").relationship("members").addable(false).deletable(false).reorderable(false).cloneable(false).collapsible(false).collapsed(false)
        assertTrue(Schema.make().components(flagged).render().contains("data-ff-grid=\"2\""))
        val mixed = Repeater.make("members").schema(TextInput.make("name").required().distinct(), TextInput.make("note"), Text.make("Note"))
        val mixedSchema = Schema.make().components(mixed).fill(
            mapOf("members" to listOf(linkedMapOf<Any, Any?>(1 to "x"), emptyMap<String, Any?>())),
        )
        assertTrue(mixedSchema.render().contains("ff-repeater"))
        assertTrue(mixed.extraErrors(mixedSchema)["members"]!!.any { it.contains("required") })
        assertTrue(Schema.make().components(Repeater.make("plain")).render().contains("ff-repeater"))

        val builder = Builder.make("blocks").blocks(BuilderBlock("hero", "Hero", listOf(TextInput.make("title")))).blockNumbers(true)
        val built = Schema.make().components(builder).fill(
            mapOf(
                "blocks" to listOf(
                    linkedMapOf<Any, Any?>("type" to "hero", "data" to linkedMapOf("title" to "Hi"), 1 to "x"),
                    mapOf("data" to "nope"),
                    mapOf("type" to "missing"),
                ),
            ),
        )
        val builderHtml = built.render()
        assertTrue(builderHtml.contains("1. Hero"))
        assertTrue(builderHtml.contains("missing"))
        val nameless = Builder.make("").blocks(BuilderBlock("hero", "Hero", emptyList()))
        val namelessSchema = Schema.make().components(nameless)
        assertTrue(nameless.items(namelessSchema).isEmpty())
        nameless.addBlock(namelessSchema, "hero")
        assertEquals(1, nameless.items(namelessSchema).size)
        assertTrue(nameless.extraErrors(namelessSchema).isEmpty())
    }
}

private class BareField(name: String) : Field<BareField>(name) {
    fun exposedKind(): String = kind()

    fun read(schema: Schema): Any? = stateValue(schema, name)
}
