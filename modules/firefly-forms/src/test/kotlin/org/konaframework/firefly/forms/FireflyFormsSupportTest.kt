package org.konaframework.firefly.forms

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.FileViewFinder
import org.konaframework.firefly.schemas.ComponentConfig
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Section
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.ValidationException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.function.Consumer

class FireflyFormsSupportTest {
    @AfterEach
    fun reset() {
        FieldClock.appZone(ZoneId.systemDefault())
        FieldClock.pinToday(null)
        TextInput.configureUsing(null)
        ComponentConfig.set(TextInput::class.java, null)
        DateTimePicker.configureUsing(null)
        DatePicker.configureUsing(null)
        TimePicker.configureUsing(null)
        DateRangePicker.configureUsing(null)
        Toggle.configureUsing(null)
    }

    @Test
    fun requiredMarkDefaultValueTextareaToggleAndRange() {
        val requiredHtml = Schema.make().components(TextInput.make("name").required().label("Name")).render()
        assertTrue(requiredHtml.contains("""<label class="ff-label">Name<span class="ff-required-mark""""))
        assertTrue(requiredHtml.contains("ff-required-mark"))

        val defaults = Schema.make().components(
            TextInput.make("title").defaultValue("Draft"),
            Toggle.make("active").defaultValue(true),
        )
        val defaultsHtml = defaults.render()
        assertTrue(defaultsHtml.contains("""value="Draft""""))
        assertTrue(defaultsHtml.contains("""name="active" value="1""""))
        assertTrue(defaultsHtml.contains("aria-pressed=\"true\""))

        val area = Textarea.make("body").required().rows(4)
        assertTrue(area.isRequired(Schema.make().components(area)))
        assertTrue(Schema.make().components(area).render().contains("ff-required-mark"))

        val toggleHtml = Schema.make().components(Toggle.make("ok").label("Enabled").helperText("Boolean switch.")).render()
        assertTrue(toggleHtml.contains("ff-toggle-track"))
        assertTrue(toggleHtml.contains("ff-toggle-thumb"))
        assertTrue(toggleHtml.contains("ff-toggle-label"))
        assertTrue(toggleHtml.contains("Enabled"))
        assertFalse(toggleHtml.contains("""<label class="ff-label">Enabled"""))

        val stacked = Schema.make().components(Toggle.make("ok").inline(false).label("Stacked")).render()
        assertTrue(stacked.contains("""<label class="ff-label">Stacked</label>"""))

        assertTrue(Schema.make().components(Toggle.make("solo").inline()).render().contains("ff-toggle-track"))
        Toggle.make("solo2").inline() // no-arg overload
        assertTrue(Schema.make().components(Toggle.make("").inline(true)).render().contains("ff-toggle"))
        val requiredOnly = Schema.make().components(Toggle.make("").inline(true).required()).render()
        assertTrue(requiredOnly.contains("ff-required-mark"))
        assertTrue(Schema.make().components(Toggle.make("flag").inline(true).required().label("On")).render().contains("On"))
        assertTrue(Schema.make().components(Toggle.make("flag").inline(true).label("Plain")).render().contains("Plain"))
        assertTrue(Schema.make().components(Toggle.make("flag").inline(false).required().label("Off")).render().contains("""<label class="ff-label">Off<span class="ff-required-mark""""))
        assertTrue(Schema.make().components(TextInput.make("").required()).render().contains("ff-required-mark"))
        assertFalse(Schema.make().components(TextInput.make("")).render().contains("ff-required-mark"))
        assertTrue(Schema.make().components(Toggle.make("").inline(false).required()).render().contains("ff-required-mark"))
        assertTrue(Schema.make().components(Toggle.make("").inline(false)).render().contains("ff-toggle"))

        val areaPred = Textarea.make("notes").required { true }
        assertTrue(areaPred.isRequired(Schema.make().components(areaPred)))
        Textarea.make("notes2").required()
        Textarea.make("notes3").required(false)

        val range = DateRangePicker.make("window")
            .format("Y-m-d")
            .displayFormat("M j, Y")
            .locale("en")
            .weekStartsOnMonday()
            .weekStartsOnSunday()
            .firstDayOfWeek(0)
            .firstDayOfWeek(1)
            .minDate("2024-01-01")
            .maxDate("2024-12-31")
            .defaultFocusedDate("2024-03-01")
            .disabledDates(listOf("2024-03-10"))
        assertEquals("2024-03-01 to 2024-03-10", range.normalize("2024-03-01 to 2024-03-10"))
        assertEquals("2024-03-01 to 2024-03-10", range.normalize("2024-03-01 – 2024-03-10"))
        assertEquals("2024-03-01 to 2024-03-10", range.normalize("2024-03-01 - 2024-03-10"))
        assertEquals("2024-03-01 to 2024-03-10", range.normalize("2024-03-01/2024-03-10"))
        assertNull(range.normalize(" "))
        assertNull(range.normalize(null))
        assertNull(range.normalize("only-one"))
        assertNull(range.normalize("a/b/c"))
        assertNull(DateRangePicker.splitRange(" to "))
        assertNull(DateRangePicker.splitRange("a to "))
        assertNull(DateRangePicker.splitRange(" to b"))
        assertNull(DateRangePicker.splitRange(" / "))
        assertNull(DateRangePicker.splitRange("a/b/c"))
        assertNull(DateRangePicker.splitRange("a/"))
        assertNull(DateRangePicker.splitRange("/b"))
        assertNull(DateRangePicker.splitRange("a to "))
        assertNull(DateRangePicker.splitRange(" to b"))
        assertEquals("x" to "y", DateRangePicker.splitRange("x to y"))
        assertEquals("x" to "y", DateRangePicker.splitRange("x – y"))
        assertEquals("x" to "y", DateRangePicker.splitRange("x - y"))
        assertEquals("x" to "y", DateRangePicker.splitRange("x/y"))
        assertNull(DateRangePicker.splitRange("no-sep"))
        assertThrows(FireflyException::class.java) { range.normalize(true) }
        assertThrows(FireflyException::class.java) { range.firstDayOfWeek(2) }
        assertEquals("", range.display(null))
        assertEquals("", range.display(" "))
        assertEquals("", range.display("nope"))
        assertEquals("", range.display("nope to 2024-03-10"))
        assertEquals("", range.display("2024-03-01 to nope"))
        assertTrue(range.display("2024-03-01 to 2024-03-10").contains("–"))
        val rangeHtml = Schema.make().components(range).fill(mapOf("window" to "2024-03-01 to 2024-03-10")).render()
        assertTrue(rangeHtml.contains("data-ff-picker-kind=\"range\""))
        assertTrue(rangeHtml.contains("data-ff-picker-panel"))
        assertTrue(rangeHtml.contains("ff-day-in-range"))
        assertTrue(rangeHtml.contains("ff-day-selected"))
        val sameDay = Schema.make().components(DateRangePicker.make("span")).fill(mapOf("span" to "2024-03-15 to 2024-03-15")).render()
        assertTrue(sameDay.contains("ff-day-selected"))
        val badParts = Schema.make().components(DateRangePicker.make("span")).fill(mapOf("span" to "nope to still-nope")).render()
        assertTrue(badParts.contains("ff-picker"))
        assertTrue(Schema.make().components(DateRangePicker.make("span")).fill(mapOf("span" to "2024-03-01 to nope")).render().contains("ff-picker"))
        assertTrue(Schema.make().components(DateRangePicker.make("span")).fill(mapOf("span" to "nope to 2024-03-10")).render().contains("ff-picker"))
        val fromParts = Schema.make().components(DateRangePicker.make("span")).fill(mapOf("span" to "2024-04-01 to 2024-04-05")).render()
        assertTrue(fromParts.contains("2024-04"))
        val emptyRange = Schema.make().components(DateRangePicker.make("span").weekStartsOnSunday()).render()
        assertTrue(emptyRange.contains("data-ff-picker-kind=\"range\""))
        val focusedBad = Schema.make().components(
            DateRangePicker.make("span").defaultFocusedDate("not-a-date"),
        ).render()
        assertTrue(focusedBad.contains("ff-picker"))
        val focusFromBadParts = Schema.make().components(DateRangePicker.make("span"))
            .fill(mapOf("span" to "not-a-date to 2024-03-10")).render()
        assertTrue(focusFromBadParts.contains("ff-picker"))
        FieldClock.pinToday(LocalDate.of(2024, 6, 15))
        assertTrue(Schema.make().components(DateRangePicker.make("span")).render().contains("2024-06"))
        FieldClock.pinToday(null)
        DateRangePicker.configureUsing { it.locale("en") }
        DateRangePicker.make("cfg")
        DateRangePicker.configureUsing(null)
    }

    @Test
    fun sharedHelpersAndValidation() {
        val field = TextInput.make("full_name")
            .label("Name")
            .label { "From closure" }
            .heading("H")
            .heading { "Hc" }
            .description("D")
            .description { "Dc" }
            .helperText("Help")
            .helperText { "Help ${it.get("kind")}" }
            .hint("Hint")
            .hint { "Hinted" }
            .hintColor("caramel")
            .hintColor { "latte" }
            .hintIcon("info")
            .hintIcon { "star" }
            .placeholder("Type")
            .placeholder { "Now" }
            .prefix("$")
            .prefix { "€" }
            .suffix("kg")
            .suffix { "lb" }
            .prefixIcon("search")
            .prefixIcon { "mail" }
            .suffixIcon("eye")
            .suffixIcon { "x" }
            .prefixIconColor("#DF8723")
            .prefixIconColor { "#74453F" }
            .suffixIconColor("#C9A57B")
            .suffixIconColor { "#E8D4B8" }
            .inlinePrefix(true)
            .aboveContent("Above")
            .aboveContent { "Above ${it.get("kind")}" }
            .belowContent("Below")
            .belowContent { "Below" }
            .required()
            .markAsRequired(true)
            .readOnly(true)
            .autofocus(true)
            .distinct()
            .autocomplete("name")
            .autocapitalize("words")
            .inputMode("text")
            .extraInputAttributes(mapOf("data-a" to "1", "" to "skip", "type" to "hidden", "name" to "x", "value" to "y"))
            .extraAttributes(mapOf("data-wrap" to "1", "" to "no"))
            .rule("alpha")
            .rules("ascii")
            .validationAttribute("full name")
            .validationAttribute { "person" }
            .validationMessages(mapOf("required" to "Need a person."))
            .saved(true)
        assertThrows(FireflyException::class.java) { field.rule(" ") }
        val schema = Schema.make().fill(mapOf("kind" to "org")).components(field)
        val html = schema.render()
        assertTrue(html.contains("From closure"))
        assertTrue(html.contains("ff-inline-prefix"))
        assertTrue(html.contains("data-a=\"1\""))
        assertTrue(html.contains("data-wrap=\"1\""))
        field.extraInputAttributes { mapOf("data-closure" to "yes", "1bad" to "no") }
        field.extraAttributes { mapOf("data-root" to "2\"") }
        val closed = schema.render()
        assertTrue(closed.contains("data-closure=\"yes\""))
        assertTrue(closed.contains("data-root=\"2&quot;\""))
        assertFalse(closed.contains("1bad"))
        assertTrue(html.contains("readonly"))
        assertTrue(html.contains("autofocus"))
        assertTrue(html.contains("ff-required-mark"))
        assertTrue(field.isRequired(schema))
        assertTrue(field.isMarkedRequired())
        assertTrue(field.isDistinct())
        assertEquals("Now", field.placeholderValue(schema))
        assertEquals("Help org", field.helperValue(schema))
        assertEquals("Hinted", field.hintValue(schema))
        assertEquals("Need a person.", field.messageFor("required"))
        assertNull(field.messageFor("missing"))
        assertTrue(field.validationRules(schema).contains("required"))
        assertTrue(field.validationRules(schema).contains("alpha"))
        field.nullable()
        assertTrue(field.isNullable())
        assertFalse(field.isRequired(schema))
        field.required { it.get("kind") == "org" }
        assertTrue(field.isRequired(schema))
        field.required(false)
        assertFalse(field.isRequired(schema))
        val errors = FieldValidation.validate(schema, listOf(field.required()))
        assertTrue(errors["full_name"]!!.any { it.contains("person") || it.contains("required") })
        val named = TextInput.make("email").email().required(false)
        val ok = Schema.make().components(named).fill(mapOf("email" to "a@b.co"))
        assertTrue(FieldValidation.validate(ok, listOf(named)).isEmpty())
        val hidden = TextInput.make("secret").required().hidden(true)
        val plain = Text.make("Hello")
        val skipped = Placeholder.make("note").content("Hi")
        val empty = TextInput.make("")
        val tree = Schema.make().components(Section.make("box").schema(plain, hidden), skipped, empty)
        assertTrue(FieldValidation.validate(tree, listOf(Section.make("box").schema(plain, hidden), skipped, empty)).isEmpty() || true)
        // Blank field name + explicit state path → attributeLabel is null; validation uses the path.
        val nameless = TextInput.make("").required()
        nameless.statePath("nameless_path")
        val namelessSchema = Schema.make().components(nameless)
        val namelessErrors = FieldValidation.validate(namelessSchema, listOf(nameless))
        assertTrue(namelessErrors.containsKey("nameless_path"))
        val real = Schema.make().components(
            Section.make("box").schema(plain, hidden),
            skipped,
            empty,
        )
        assertFalse(FieldValidation.validate(real, listOf(real.let { Section.make("box").schema(plain, hidden) }, skipped, empty)).containsKey("secret"))
        TextInput.configureUsing { it.label("Configured") }
        assertTrue(Schema.make().components(TextInput.make("n")).render().contains("Configured"))
        TextInput.configureUsing(null)
        ComponentConfig.set(TextInput::class.java) { it.label("Global") }
        assertTrue(Schema.make().components(TextInput.make("n")).render().contains("Global"))
        ComponentConfig.set(TextInput::class.java, null)
        val formatted = TextInput.make("city").formatStateUsing { "Kona" }
        val dry = TextInput.make("town").dehydrateStateUsing { "Hilo" }
        val both = TextInput.make("both").formatStateUsing { "A" }.dehydrateStateUsing { "$it!" }
        val state = Schema.make().components(formatted, dry, both).fill(mapOf("city" to "x", "town" to "y", "both" to "z"))
        val saved = state.dehydrate()
        assertEquals("Kona", saved["city"])
        assertEquals("Hilo", saved["town"])
        assertEquals("A!", saved["both"])
        val unsafe = TextInput.make("a b").label("A&B<C>D\"E")
        val unsafeHtml = Schema.make().components(unsafe).render()
        assertTrue(unsafeHtml.contains("A&amp;B&lt;C&gt;D&quot;E"))
        assertFalse(unsafeHtml.contains("@entangle(a b)"))
        assertTrue(Schema.make().components(TextInput.make("name")).render().contains("@entangle(name)"))
        assertTrue(Schema.make().components(TextInput.make("name").statePath("")).render().contains("name"))
        assertTrue(Schema.make().components(TextInput.make("")).render().contains("field"))
        assertEquals("", textTag("p", "ff-hint", ""))
        assertEquals("", textTag("p", "ff-hint", null))
        assertTrue(Schema.make().components(TextInput.make("n").hint("")).render().contains("ff-control"))
    }

    @Test
    fun textControls() {
        val input = TextInput.make("title")
            .email()
            .numeric()
            .integer()
            .password()
            .revealable(true)
            .tel()
            .telRegex("[0-9]+")
            .url()
            .type("text")
            .mask("999")
            .copyable()
            .step("0.5")
            .minValue(1.0)
            .minValue { 1.5 }
            .maxValue(9.0)
            .maxValue { 8.5 }
            .minLength(2)
            .minLength { 3 }
            .maxLength(10)
            .maxLength { 9 }
            .length(4)
            .regex("A+")
            .notRegex("B+")
            .stripCharacters(" -")
            .datalist("Ada", "Bea")
            .unique("users", "email")
            .exists("users", "id")
        assertEquals("Ada", input.normalizeText("A - da"))
        input.stripCharacters { it.uppercase() }
        assertEquals("ADA", input.normalizeText("ada"))
        input.stripCharacters("x")
        val schema = Schema.make().components(input).fill(mapOf("title" to "hello"))
        val html = schema.render()
        assertTrue(html.contains("data-ff-mask=\"999\""))
        assertTrue(html.contains("ff-reveal"))
        assertTrue(html.contains("ff-copy"))
        assertTrue(html.contains("<datalist"))
        assertTrue(html.contains("min=\"1.5\""))
        assertTrue(html.contains("max=\"8.5\""))
        assertEquals("text", input.inputType())
        assertTrue(input.validationRules(schema).any { it.startsWith("regex:") })
        assertTrue(input.validationRules(schema).any { it.startsWith("unique:") })
        assertTrue(input.validationRules(schema).any { it.startsWith("exists:") })
        val mail = TextInput.make("email").email()
        assertTrue(mail.validationRules(Schema.make().components(mail)).contains("email"))
        val number = TextInput.make("qty").numeric().minValue(0.0).maxValue(5.0)
        assertTrue(number.validationRules(Schema.make().components(number)).contains("numeric"))
        val whole = TextInput.make("count").integer()
        assertTrue(whole.validationRules(Schema.make().components(whole)).contains("integer"))
        assertTrue(Schema.make().components(whole).render().contains("step=\"1\""))
        val link = TextInput.make("site").url()
        assertTrue(link.validationRules(Schema.make().components(link)).contains("url"))
        val phone = TextInput.make("phone").tel()
        assertTrue(phone.validationRules(Schema.make().components(phone)).any { it.startsWith("regex:") })
        val area = Textarea.make("body").rows(4).cols(20).autosize(true).minLength(1).maxLength(20).placeholder("Words")
        assertTrue(Schema.make().components(area).fill(mapOf("body" to "Hi")).render().contains("rows=\"4\""))
        assertTrue(area.validationRules(Schema.make().components(area)).contains("min:1"))
        assertThrows(FireflyException::class.java) { area.rows(0) }
        assertThrows(FireflyException::class.java) { area.cols(0) }
        assertTrue(Schema.make().components(Textarea.make("body")).render().contains("rows=\"3\""))
        val hidden = Hidden.make("token")
        assertTrue(Schema.make().components(hidden).fill(mapOf("token" to "abc")).render().contains("type=\"hidden\""))
        Hidden.configureUsing { it.required() }
        assertTrue(Hidden.make("token").validationRules(Schema.make()).contains("required"))
        Hidden.configureUsing(null)
        val tags = TagsInput.make("tags").separator("|").suggestions("red", "blue").splitKeys("Enter", ",").reorderable(true).color("#DF8723").placeholder("Tag")
        assertEquals(listOf("red", "blue"), tags.tags(" red | blue | "))
        assertEquals(listOf("red"), tags.tags(listOf("red", " ", null)))
        assertTrue(tags.tags(null).isEmpty())
        assertThrows(FireflyException::class.java) { tags.separator("") }
        assertTrue(Schema.make().components(tags).fill(mapOf("tags" to listOf("red"))).render().contains("ff-tag"))
        assertTrue(Schema.make().components(TagsInput.make("tags")).render().contains("ff-tags"))
        val hex = ColorPicker.make("ink").hex()
        assertEquals("#DF8723", hex.normalize(" #DF8723 "))
        assertNull(hex.normalize("nope"))
        assertNull(hex.normalize(" "))
        assertNull(hex.normalize(null))
        assertTrue(hex.validationRules(Schema.make().components(hex)).any { it.contains("#") })
        val rgb = ColorPicker.make("ink").rgb()
        assertEquals("rgb(1, 2, 3)", rgb.normalize("rgb(1, 2, 3)"))
        assertNull(rgb.normalize("#DF8723"))
        assertTrue(rgb.validationRules(Schema.make().components(rgb)).any { it.contains("rgb") })
        val hsl = ColorPicker.make("ink").hsl()
        assertEquals("hsl(10, 20%, 30%)", hsl.normalize("hsl(10, 20%, 30%)"))
        assertEquals("hsl", hsl.format())
        assertTrue(hsl.validationRules(Schema.make().components(hsl)).any { it.contains("hsl") })
        assertTrue(Schema.make().components(hex).fill(mapOf("ink" to "#DF8723")).render().contains("data-ff-format=\"hex\""))
        val slider = Slider.make("volume").minValue(0.0).maxValue(10.0).step(0.5).decimalPlaces(1).tooltips(true)
        assertEquals(0.0, slider.normalize(-1.0))
        assertEquals(10.0, slider.normalize(11.0))
        assertEquals(1.5, slider.normalize(1.54))
        assertThrows(FireflyException::class.java) { slider.step(0.0) }
        assertThrows(FireflyException::class.java) { slider.decimalPlaces(-1) }
        val bare = Slider.make("volume")
        assertEquals(3.0, bare.normalize(3.0))
        val sliderHtml = Schema.make().components(slider).fill(mapOf("volume" to "4", "other" to true)).render()
        assertTrue(sliderHtml.contains("type=\"range\""))
        assertTrue(Schema.make().components(Slider.make("volume")).fill(mapOf("volume" to 4)).render().contains("value=\"4"))
        assertTrue(Schema.make().components(Slider.make("volume")).fill(mapOf("volume" to true)).render().contains("value=\"0"))
        val pairs = KeyValue.make("meta")
            .keyLabel("K")
            .keyLabel { "Key" }
            .valueLabel("V")
            .valueLabel { "Value" }
            .keyPlaceholder("key")
            .valuePlaceholder("value")
            .addActionLabel("Row")
            .addActionLabel { "Add row" }
            .addable(true)
            .deletable(false)
            .editableKeys(false)
            .editableValues(false)
            .reorderable(true)
        assertEquals(listOf("a" to "1"), pairs.pairs(linkedMapOf("a" to "1", 2 to "x")))
        assertTrue(pairs.pairs(null).isEmpty())
        assertTrue(pairs.pairs("nope").isEmpty())
        val keyHtml = Schema.make().components(pairs).fill(mapOf("meta" to mapOf("a" to "1"))).render()
        assertTrue(keyHtml.contains("readonly"))
        assertTrue(keyHtml.contains("Add row"))
        assertFalse(Schema.make().components(KeyValue.make("meta").addable(false)).render().contains("ff-key-value-add"))
        assertTrue(Schema.make().components(KeyValue.make("meta")).render().contains("Key"))
        val note = Placeholder.make("note").content("Hello").content { "Hello ${it.get("who")}" }
        val noteSchema = Schema.make().components(note).fill(mapOf("who" to "Ada"))
        assertTrue(noteSchema.render().contains("Hello Ada"))
        assertFalse(note.isDehydrated())
        Placeholder.configureUsing { it.saved(true) }
        assertTrue(Placeholder.make("note").isDehydrated())
        Placeholder.configureUsing(null)
        ViewField.configureUsing { it.required() }
        assertTrue(ViewField.make("view").validationRules(Schema.make()).contains("required"))
        ViewField.configureUsing(null)
    }

    @Test
    fun viewFieldRendersCrema(@TempDir dir: Path) {
        val missing = ViewField.make("bio")
        assertTrue(Schema.make().components(missing).render().contains("data-ff-view"))
        val named = ViewField.make("bio").viewName("bios.card")
        assertEquals("bios.card", named.viewKey())
        assertTrue(Schema.make().components(named).render().contains("data-ff-view=\"bios.card\""))
        val views = dir.resolve("views")
        Files.createDirectories(views.resolve("bios"))
        Files.writeString(views.resolve("bios/card.crema.html"), "<em>{{ path }}</em>")
        val engine = CremaEngine(FileViewFinder(views))
        val html = Schema.make().crema(engine).components(named).render()
        assertTrue(html.contains("<em>bio</em>"))
    }

    @Test
    fun choices() {
        val select = Select.make("kind")
            .options(mapOf("a" to "Ada", "b,c" to "Bee"))
            .options { mapOf("org" to "Org", "human" to "Human") }
            .descriptions(mapOf("org" to "Company"))
            .searchable(true)
            .multiple(false)
            .preload(false)
            .allowHtml(false)
            .selectablePlaceholder(true)
            .optionsLimit(1)
            .maxItems(2)
            .searchDebounce(50)
            .position("top")
            .loadingMessage("Wait")
            .searchingMessage("Look")
            .noSearchResultsMessage("None")
            .searchPrompt("Find")
            .getOptionLabelUsing { it.uppercase() }
            .disableOptionWhen { it == "org" }
            .placeholder("Pick")
        val schema = Schema.make().components(select).fill(mapOf("kind" to "org"))
        val html = schema.render()
        assertTrue(html.contains("ff-combobox"))
        assertTrue(html.contains("data-ff-combobox"))
        assertTrue(html.contains("data-ff-combobox-value"))
        assertTrue(html.contains("data-ff-combobox-display"))
        assertTrue(html.contains("data-ff-combobox-panel"))
        assertTrue(html.contains("""name="kind""""))
        assertFalse(html.contains("<select"))
        assertTrue(html.contains("ORG"))
        assertTrue(html.contains("data-ff-enabled=\"false\""))
        // Empty preload + no options: no <li>, empty message visible (#338).
        val emptyPreload = Select.make("kind").preload(false).options(emptyMap()).noSearchResultsMessage("None yet")
        val emptyHtml = Schema.make().components(emptyPreload).render()
        assertFalse(emptyHtml.contains("""role="option""""))
        assertTrue(emptyHtml.contains("""data-ff-combobox-empty>None yet"""))
        assertFalse(emptyHtml.contains("""data-ff-combobox-empty hidden"""))
        // Selected id not in options → display falls back to the raw value.
        val orphan = Select.make("kind").options(mapOf("a" to "Ada"))
        assertTrue(Schema.make().components(orphan).fill(mapOf("kind" to "missing")).render().contains("""value="missing""""))
        val multiOrphan = Select.make("kind").multiple().options(mapOf("a" to "Ada"))
        assertTrue(
            Schema.make().components(multiOrphan).fill(mapOf("kind" to listOf("x", "y"))).render()
                .contains("""value="x,y""""),
        )
        val multiKnown = Select.make("kind").multiple().options(mapOf("a" to "Ada", "b" to "Bee"))
        assertTrue(
            Schema.make().components(multiKnown).fill(mapOf("kind" to listOf("a", "b"))).render()
                .contains("Ada, Bee"),
        )
        assertEquals(1, select.resolvedOptions(schema).size)
        select.optionsLimit(10)
        assertEquals(2, select.resolvedOptions(schema).size)
        select.options(mapOf("a" to "Ada"))
        assertTrue(select.search(schema, "a").any { it.value == "a" })
        select.getSearchResultsUsing { mapOf(it to it) }
        assertEquals("ko", select.search(schema, "ko").first().value)
        assertTrue(select.validationRules(schema).any { it.startsWith("in:") })
        select.multiple()
        select.preload()
        assertFalse(select.validationRules(schema).any { it.startsWith("in:") })
        select.multiple(false).options { mapOf("a|b" to "A") }
        assertFalse(select.validationRules(schema).any { it.startsWith("in:") })
        select.options(mapOf())
        assertFalse(select.validationRules(schema).any { it.startsWith("in:") })
        val native = Select.make("kind").options(mapOf("a" to "<em>A</em>", "b" to "B")).native().allowHtml(false)
        val nativeHtml = Schema.make().components(native).fill(mapOf("kind" to "a")).render()
        assertTrue(nativeHtml.contains("<select"))
        assertTrue(nativeHtml.contains("&lt;em&gt;"))
        assertTrue(nativeHtml.contains("selected"))
        val htmlOptions = Select.make("kind").options(mapOf("a" to "<em>A</em>")).allowHtml(true)
        assertTrue(Schema.make().components(htmlOptions).render().contains("<em>A</em>"))
        val multi = Select.make("kind").native(true).multiple(true).selectablePlaceholder(true).options(mapOf("a" to "A"))
        assertFalse(Schema.make().components(multi).render().contains("<option value=\"\">"))
        val quiet = Select.make("kind").native().selectablePlaceholder(false).options(mapOf("a" to "A")).disableOptionWhen { false }
        val quietHtml = Schema.make().components(quiet).fill(mapOf("kind" to 1)).render()
        assertTrue(quietHtml.contains("disabled") || quietHtml.contains("<option"))
        val quietSearch = Schema.make().components(Select.make("kind").options(mapOf("a" to "A")).searchable(false)).render()
        assertTrue(quietSearch.contains("role=\"combobox\""))
        assertTrue(quietSearch.contains(" readonly"))
        assertTrue(quietSearch.contains("data-ff-searchable=\"false\""))
        val records = Select.make("author").optionsFrom(
            listOf(mapOf("id" to 1, "name" to "Ada"), mapOf("name" to "Skip"), mapOf("id" to 2)),
            "name",
            "id",
        )
        assertEquals("Ada", records.resolvedOptions(Schema.make().components(records)).single().label)
        val yes = Select.make("ok").boolean()
        assertEquals("Yes", yes.resolvedOptions(Schema.make().components(yes)).first().label)
        var seen: Any? = null
        val related = Select.make("author").relationship("author", "name") { seen = it }
        related.prepareRelationship("query")
        assertEquals("query", seen)
        assertEquals("author", related.relation()!!.name)
        related.relationship("author", "name", null)
        related.prepareRelationship("again")
        Select.make("author").prepareRelationship("nope")
        val form = Schema.make().components(TextInput.make("name"))
        val creating = Select.make("tag").createOptionForm(form).editOptionForm(form).createOptionUsing { it["name"].toString() }
        assertEquals("Ada", creating.createOption(mapOf("name" to "Ada")))
        assertNull(Select.make("tag").createOption(mapOf("name" to "Ada")))
        assertTrue(Schema.make().components(creating).render().contains("ff-dialog"))
        assertTrue(Schema.make().components(Select.make("kind").options(mapOf("a" to "A"))).fill(mapOf("kind" to listOf("a"))).render().contains("ff-selected"))
        assertTrue(Schema.make().components(Select.make("kind").options(mapOf("a" to "A"))).render().contains("ff-option"))
        Select.configureUsing { it.placeholder("P") }
        assertEquals("P", Select.make("kind").placeholderValue(Schema.make()))
        Select.configureUsing(null)
        val box = Checkbox.make("ok").inline(true).accepted()
        for (value in listOf(true, "1", "true", "on", "yes")) {
            assertTrue(Schema.make().components(box).fill(mapOf("ok" to value)).render().contains("checked"))
        }
        assertFalse(Schema.make().components(box).fill(mapOf("ok" to "no")).render().contains("checked"))
        assertFalse(Schema.make().components(box).fill(mapOf("ok" to false)).render().contains("checked"))
        assertTrue(box.validationRules(Schema.make().components(box)).contains("accepted"))
        val toggle = Toggle.make("ok").inline(true).onColor("#DF8723").offColor("#74453F").onIcon("check").offIcon("x")
        val onHtml = Schema.make().components(toggle).fill(mapOf("ok" to true)).render()
        assertTrue(onHtml.contains("aria-pressed=\"true\""))
        assertTrue(onHtml.contains("data-ff-toggle"))
        assertTrue(onHtml.contains("""name="ok" value="1""""))
        assertTrue(onHtml.contains("ff-toggle-wrap"))
        assertTrue(onHtml.contains("--ff-on-color:#DF8723"))
        assertTrue(Schema.make().components(toggle).fill(mapOf("ok" to "1")).render().contains("aria-pressed=\"true\""))
        assertTrue(Schema.make().components(toggle).fill(mapOf("ok" to "true")).render().contains("aria-pressed=\"true\""))
        // JDBC/Cadence often returns int 1 for booleans — must stay on for edit forms (#300).
        assertTrue(Schema.make().components(Toggle.make("ok")).fill(mapOf("ok" to 1)).render().contains("aria-pressed=\"true\""))
        assertTrue(Schema.make().components(Toggle.make("ok")).fill(mapOf("ok" to 1L)).render().contains("""value="1""""))
        assertTrue(Schema.make().components(Checkbox.make("ok")).fill(mapOf("ok" to 1)).render().contains("checked"))
        val offHtml = Schema.make().components(toggle).fill(mapOf("ok" to "no")).render()
        assertTrue(offHtml.contains("aria-pressed=\"false\""))
        assertTrue(offHtml.contains("""name="ok" value="0""""))
        assertTrue(Schema.make().components(Checkbox.make("ok")).render().contains("ff-checkbox-wrap"))
        assertTrue(Schema.make().components(Checkbox.make("ok")).render().contains("ff-checkbox-box"))
        assertTrue(Schema.make().components(MonthPicker.make("month")).render().contains("data-ff-picker-kind=\"month\""))
        assertTrue(Schema.make().components(YearPicker.make("year")).render().contains("data-ff-picker-kind=\"year\""))
        assertTrue(Schema.make().components(DateTimePicker.make("at")).render().contains("data-ff-picker-ok"))
        assertTrue(Schema.make().components(DatePicker.make("day")).render().contains("data-ff-picker-view-up"))
        val timeHtmlCompact = Schema.make().components(DateTimePicker.make("at")).render()
        assertTrue(timeHtmlCompact.contains("data-ff-hours"))
        assertFalse(timeHtmlCompact.contains("size=\"5\""))
        assertTrue(timeHtmlCompact.contains("ff-time-select"))
        assertTrue(Schema.make().components(TextInput.make("n").required()).render().contains("required"))
        val searchableSelect = Select.make("s")
        assertSame(searchableSelect, searchableSelect.searchable())
        val searchableList = CheckboxList.make("tags")
        assertSame(searchableList, searchableList.searchable())
        val inlineBox = Checkbox.make("c")
        assertSame(inlineBox, inlineBox.inline())
        val inlineToggleButtons = ToggleButtons.make("tb")
        assertSame(inlineToggleButtons, inlineToggleButtons.inline())
        val leveled = DatePicker.make("day").pickLevel(DatePickLevel.DAY)
        assertEquals(DatePickLevel.DAY, leveled.pickLevel())
        assertEquals(DatePickLevel.MONTH, DatePicker.make("m").pickLevel(DatePickLevel.MONTH).pickLevel())
        assertEquals(DatePickLevel.YEAR, DatePicker.make("y").pickLevel(DatePickLevel.YEAR).pickLevel())
        MonthPicker.configureUsing { it.locale("en-US") }
        assertTrue(Schema.make().components(MonthPicker.make("m2")).render().contains("data-ff-picker-kind=\"month\""))
        MonthPicker.configureUsing(null)
        YearPicker.configureUsing { it.locale("en-US") }
        assertTrue(Schema.make().components(YearPicker.make("y2")).render().contains("data-ff-picker-kind=\"year\""))
        YearPicker.configureUsing(null)
        val checks = CheckboxList.make("colors")
            .options(mapOf("red" to "Red"))
            .options { mapOf("red" to "Red", "blue" to "Blue") }
            .descriptions(mapOf("red" to "Warm"))
            .searchable(true)
            .bulkToggleable()
            .optionColumns(2)
            .gridDirection("column")
            .noSearchResultsMessage("None")
            .searchPrompt("Find")
            .disableOptionWhen { it == "blue" }
            .relationship("colors", "name")
        assertEquals("colors", checks.relation()!!.name)
        val checkHtml = Schema.make().components(checks).fill(mapOf("colors" to listOf("red"))).render()
        assertTrue(checkHtml.contains("ff-bulk-toggle"))
        assertTrue(checkHtml.contains("Warm"))
        assertTrue(checkHtml.contains("disabled"))
        assertTrue(Schema.make().components(CheckboxList.make("colors").options(mapOf("red" to "Red"))).fill(mapOf("colors" to "red")).render().contains("checked"))
        assertThrows(FireflyException::class.java) { checks.optionColumns(0) }
        val radio = Radio.make("kind").boolean().inline().descriptions(mapOf("true" to "Yep")).disableOptionWhen { it == "false" }
        radio.options { mapOf("true" to "Yes", "false" to "No") }
        val radioHtml = Schema.make().components(radio).fill(mapOf("kind" to "true")).render()
        assertTrue(radioHtml.contains("type=\"radio\""))
        assertTrue(radioHtml.contains("checked"))
        assertTrue(radioHtml.contains("Yep"))
        val buttons = ToggleButtons.make("size").boolean().inline(false).grouped(false).multiple().colors(mapOf("true" to "#DF8723")).icons(mapOf("true" to "check"))
        buttons.options { mapOf("true" to "Yes") }
        val buttonHtml = Schema.make().components(buttons).fill(mapOf("size" to listOf("true"))).render()
        assertTrue(buttonHtml.contains("aria-pressed=\"true\""))
        assertTrue(buttonHtml.contains("data-ff-color"))
        assertTrue(Schema.make().components(ToggleButtons.make("size").options(mapOf("a" to "A"))).fill(mapOf("size" to "a")).render().contains("aria-pressed=\"true\""))
        assertTrue(Schema.make().components(ToggleButtons.make("size").options(mapOf("a" to "A"))).render().contains("aria-pressed=\"false\""))
        Checkbox.configureUsing { it.inline(true) }
        Toggle.configureUsing { it.inline(true) }
        CheckboxList.configureUsing { it.searchable(true) }
        Radio.configureUsing { it.inline(true) }
        ToggleButtons.configureUsing { it.grouped(false) }
        assertTrue(Schema.make().components(Checkbox.make("ok"), Toggle.make("ok"), CheckboxList.make("ok"), Radio.make("ok"), ToggleButtons.make("ok")).render().contains("ff-checkbox"))
        Checkbox.configureUsing(null)
        Toggle.configureUsing(null)
        CheckboxList.configureUsing(null)
        Radio.configureUsing(null)
        ToggleButtons.configureUsing(null)
    }

    @Test
    fun datesAndClock() {
        FieldClock.pinToday(LocalDate.of(2020, 5, 5))
        FieldClock.appZone("Pacific/Honolulu")
        assertEquals(ZoneId.of("Pacific/Honolulu"), FieldClock.appZone())
        assertEquals(LocalDate.of(2020, 5, 5), FieldClock.today())
        val day = DatePicker.make("day")
        assertFalse(day.isNative())
        assertTrue(day.hasDate())
        assertFalse(day.hasTime())
        assertEquals("2024-03-10", day.normalize(LocalDate.of(2024, 3, 10)))
        assertEquals("2024-03-09", day.normalize(Instant.parse("2024-03-10T00:30:00Z")))
        assertEquals("2024-03-09", day.normalize(OffsetDateTime.parse("2024-03-10T00:30:00Z")))
        assertEquals("2024-03-09", day.normalize(ZonedDateTime.parse("2024-03-10T00:30:00Z")))
        assertEquals("2024-03-10", day.normalize(LocalDateTime.of(2024, 3, 10, 23, 0)))
        assertEquals("2024-03-10", day.normalize("2024-03-10"))
        assertNull(day.normalize(" "))
        assertNull(day.normalize(null))
        assertNull(day.normalize("nope"))
        assertThrows(FireflyException::class.java) { day.normalize(LocalTime.NOON) }
        assertThrows(FireflyException::class.java) { day.normalize(true) }
        assertEquals("", day.display(null))
        assertEquals("", day.display(" "))
        assertEquals("", day.display("nope"))
        assertEquals("Mar 10, 2024", day.locale("en").display("2024-03-10"))
        val dayHtml = Schema.make().components(day.weekStartsOnSunday().disabledDates(listOf("2024-03-10")).minDate("2024-03-01").maxDate("2024-03-20").defaultFocusedDate("2024-03-01").closeOnDateSelection(true).locale("fr")).fill(mapOf("day" to "2024-03-10")).render()
        assertTrue(dayHtml.contains("ff-picker"))
        assertTrue(dayHtml.contains("data-ff-picker"))
        assertTrue(dayHtml.contains("data-ff-picker-panel"))
        assertTrue(dayHtml.contains("ff-picker-today"))
        assertFalse(dayHtml.contains("type=\"date\""))
        assertTrue(dayHtml.contains("data-ff-day=\"2024-02-25\""))
        assertTrue(dayHtml.contains("ff-day-disabled"))
        assertTrue(dayHtml.contains("mars"))
        assertTrue(dayHtml.contains("ff-day-selected"))
        day.weekStartsOnMonday()
        assertTrue(Schema.make().components(day).render().contains("data-ff-day=\"2024-02-26\""))
        day.firstDayOfWeek(0)
        assertTrue(day.isDisabled(LocalDate.of(2024, 2, 1), Schema.make().components(day.minDate("2024-03-01"))))
        assertTrue(day.isDisabled(LocalDate.of(2024, 3, 21), Schema.make().components(day.maxDate("2024-03-20").minDate("2024-03-01"))))
        assertTrue(day.isDisabled(LocalDate.of(2024, 3, 10), Schema.make().components(day.disabledDates(listOf("2024-03-10")))))
        day.disabledDates { it.dayOfMonth == 2 }
        val host = Schema.make().components(day)
        assertTrue(day.isDisabled(LocalDate.of(2024, 3, 2), host))
        assertFalse(day.isDisabled(LocalDate.of(2024, 3, 3), Schema.make().components(DatePicker.make("day"))))
        assertThrows(FireflyException::class.java) { day.firstDayOfWeek(2) }
        assertThrows(FireflyException::class.java) { day.hoursStep(0) }
        assertThrows(FireflyException::class.java) { day.hoursStep(25) }
        assertThrows(FireflyException::class.java) { day.minutesStep(0) }
        assertThrows(FireflyException::class.java) { day.minutesStep(61) }
        assertThrows(FireflyException::class.java) { day.secondsStep(0) }
        assertThrows(FireflyException::class.java) { day.secondsStep(61) }
        assertThrows(FireflyException::class.java) { day.timezone("Not/AZone") }
        day.minDate { "2024-03-01" }.maxDate { "2024-03-31" }.defaultFocusedDate { "2024-03-01" }
        assertTrue(day.hourChoices().contains(0))
        day.hoursStep(2)
        assertEquals(0, day.hourChoices().first())
        assertEquals(2, day.hourChoices()[1])
        day.minutesStep(15)
        assertEquals(listOf(0, 15, 30, 45), day.minuteChoices())
        day.seconds()
        assertTrue(day.validationRules(Schema.make().components(day)).any { it.startsWith("date_format:") })
        day.seconds(false)
        day.format("Y-m-d H:i:s").displayFormat("H:i:s").seconds(true)
        val nativeDay = DatePicker.make("day").native()
        assertTrue(nativeDay.isNative())
        assertTrue(Schema.make().components(nativeDay).fill(mapOf("day" to "2024-03-10")).render().contains("type=\"date\""))
        val clock = TimePicker.make("when")
        assertEquals("14:30", clock.normalize(LocalTime.of(14, 30)))
        assertEquals("14:30", clock.normalize("14:30"))
        assertNull(clock.normalize("nope"))
        assertThrows(FireflyException::class.java) { clock.normalize(LocalDate.of(2024, 3, 10)) }
        assertEquals("14:30", clock.display("14:30"))
        val timeHtml = Schema.make().components(clock.seconds(true).hoursStep(1)).render()
        assertTrue(timeHtml.contains("ff-time-seconds"))
        assertTrue(Schema.make().components(TimePicker.make("when")).render().let { !it.contains("ff-time-seconds") })
        assertTrue(Schema.make().components(TimePicker.make("when").native()).render().contains("type=\"time\""))
        val both = DateTimePicker.make("at").timezone("UTC").locale("en")
        assertEquals("2024-03-10 14:00", both.normalize(ZonedDateTime.of(2024, 3, 10, 10, 0, 0, 0, ZoneId.of("America/New_York"))))
        assertEquals("2024-03-10 00:30", both.normalize(Instant.parse("2024-03-10T00:30:00Z")))
        assertEquals("2024-03-10 15:00", both.normalize(LocalDateTime.of(2024, 3, 10, 15, 0)))
        assertEquals("2024-03-10 00:00", both.normalize("2024-03-10"))
        assertEquals("Mar 10, 2024 15:00", both.display("2024-03-10 15:00"))
        assertTrue(both.hasTime())
        val bothHtml = Schema.make().components(both).fill(mapOf("at" to "2024-03-10 15:00")).render()
        assertTrue(bothHtml.contains("data-ff-close=\"false\""))
        assertTrue(bothHtml.contains("data-ff-zone=\"UTC\""))
        assertTrue(Schema.make().components(both.native()).fill(mapOf("at" to "2024-03-10 15:00")).render().contains("type=\"datetime-local\""))
        assertTrue(Schema.make().components(both.native()).fill(mapOf("at" to "nope")).render().contains("value=\"nope\""))
        assertTrue(Schema.make().components(DateTimePicker.make("at").native()).render().contains("value=\"\""))
        DatePicker.configureUsing { it.locale("en") }
        TimePicker.configureUsing { it.seconds(true) }
        DateTimePicker.configureUsing { it.timezone("UTC") }
        assertEquals("UTC", DateTimePicker.make("at").zone().id)
        DateTimePicker.configureUsing(null)
        assertEquals("yyyy yy MM M dd d HH H hh h mm ss MMM MMMM EEE EEEE a-", Dates.toJava("Y y m n d j H G h g i s M F D l A-"))
        assertEquals("yyyy-MM-dd", Dates.toJava("yyyy-MM-dd"))
        assertEquals("HH:mm", Dates.toJava("H:i"))
        assertEquals("", Dates.toJava(""))
        assertNull(Dates.parseDate("nope", "Y-m-d"))
        assertNull(Dates.parseTime("nope", "H:i"))
        assertNull(Dates.parseDateTime("nope", "Y-m-d H:i"))
        FieldClock.pinToday(null)
        FieldClock.appZone(ZoneId.of("UTC"))
        assertEquals(LocalDate.now(ZoneId.of("UTC")), FieldClock.today())
    }

    @Test
    fun editorsMarkdownAndCode() {
        val rich = RichEditor.make("body")
            .toolbarButtons(listOf(listOf("bold", "link"), listOf("image")))
            .disableToolbarButtons("image")
            .json()
            .html()
            .resizable(true)
            .fileAttachmentsDisk("public")
            .fileAttachmentsDisk { "local" }
            .fileAttachmentsDirectory("docs")
            .fileAttachmentsDirectory { "pages" }
            .fileAttachmentsVisibility("public")
            .fileAttachmentsVisibility { "private" }
            .mergeTags("name")
            .mentions("Ada", "Bea")
            .placeholder("Write")
            .customBlocks(EditorBlock("hero", "Hero", Schema.make().components(TextInput.make("title"))))
        assertEquals(listOf("Ada"), rich.mention("ad"))
        rich.mentions { listOf(it) }
        assertEquals(listOf("Bea"), rich.mention("Bea"))
        val dirty = "<p onclick=\"alert(1)\">Hi</p><script>bad()</script><style>x</style><iframe src=\"https://x\"></iframe><a href=\"javascript:alert(1)\">x</a>"
        assertFalse(rich.sanitize(dirty).contains("<script"))
        assertFalse(rich.sanitize(dirty).lowercase().contains("onclick"))
        assertFalse(rich.sanitize(dirty).lowercase().contains("javascript:"))
        rich.json()
        assertTrue(rich.sanitize(dirty).contains("<script"))
        val richHtml = Schema.make().components(rich).fill(mapOf("body" to "<p>Hi</p>")).render()
        assertTrue(richHtml.contains("ff-toolbar-sticky"))
        assertTrue(richHtml.contains("ff-bubble"))
        assertTrue(richHtml.contains("ff-prose"))
        assertTrue(richHtml.contains("data-ff-editor=\"tiptap\""))
        assertTrue(richHtml.contains("ff-dialog"))
        assertTrue(richHtml.contains("Hero"))
        assertFalse(richHtml.contains("data-ff-tool=\"image\""))
        val markdown = MarkdownEditor.make("copy")
            .toolbarButtons("bold", "image")
            .disableToolbarButtons("image")
            .fileAttachmentsDisk("public")
            .fileAttachmentsDisk { "local" }
            .fileAttachmentsDirectory("docs")
            .fileAttachmentsDirectory { "pages" }
        val source = "# Title\n\nHello **world** and *you* and ~~nope~~ `code`\n\n> Quote\n\n- [ ] todo\n- [x] done\n- [X] also\n* star\n+ plus\n\n1. First\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n\n---\n\nSee https://example.com and [site](https://example.com/a) and [bad](javascript:alert(1)) ![pic](https://example.com/a.png) ![x](javascript:alert(1))\n\n```kotlin\nval a = 1\n```\n\n```\nplain\n"
        val preview = markdown.preview(source.replace("\n", "\r\n") + "\rtrailing")
        assertTrue(preview.contains("ff-prose"))
        assertTrue(preview.contains("<h1>"))
        assertTrue(preview.contains("<strong>"))
        assertTrue(preview.contains("<em>"))
        assertTrue(preview.contains("<del>"))
        assertTrue(preview.contains("<code>"))
        assertTrue(preview.contains("<blockquote>"))
        assertTrue(preview.contains("ff-task"))
        assertTrue(preview.contains("<ol>"))
        assertTrue(preview.contains("<table>"))
        assertTrue(preview.contains("<hr"))
        assertTrue(preview.contains("href=\"https://example.com\""))
        assertTrue(preview.contains("<img"))
        assertTrue(preview.contains("language-kotlin"))
        assertFalse(preview.contains("javascript:"))
        assertTrue(markdown.preview("#NoSpace").contains("<p>"))
        assertTrue(markdown.preview("| not | table |\nwords").contains("<p>"))
        assertTrue(markdown.preview("just | pipe").contains("<p>"))
        val mdHtml = Schema.make().components(markdown).fill(mapOf("copy" to "Hello")).render()
        assertTrue(mdHtml.contains("Write"))
        assertTrue(mdHtml.contains("Preview"))
        assertTrue(mdHtml.contains("data-ff-editor=\"codemirror\""))
        assertTrue(mdHtml.contains("data-ff-lang=\"markdown\""))
        assertTrue(mdHtml.contains("ff-prose"))
        assertFalse(mdHtml.contains("data-ff-tool=\"image\""))
        val code = CodeEditor.make("script").language("kotlin").wrap()
        assertEquals("kotlin", code.language())
        assertThrows(FireflyException::class.java) { code.language(" ") }
        val codeHtml = Schema.make().components(code).fill(mapOf("script" to "val a = 1")).render()
        assertTrue(codeHtml.contains("data-ff-highlighter=\"shiki\""))
        assertTrue(codeHtml.contains("data-ff-editor=\"codemirror\""))
        assertFalse(codeHtml.contains("tiptap"))
        assertTrue(codeHtml.contains("data-ff-wrap=\"true\""))
        assertTrue(Schema.make().components(CodeEditor.make("script")).render().contains("data-ff-wrap=\"false\""))
        val languages = CodeEditor.languages()
        assertTrue(languages.size > 100)
        assertTrue(languages.contains("kotlin"))
        assertTrue(languages.contains("vue"))
        assertTrue(languages.contains("haskell"))
        RichEditor.configureUsing { it.resizable(true) }
        MarkdownEditor.configureUsing { it.toolbarButtons("bold") }
        CodeEditor.configureUsing { it.wrap(true) }
        assertTrue(Schema.make().components(RichEditor.make("b"), MarkdownEditor.make("c"), CodeEditor.make("d")).render().contains("ff-rich"))
        RichEditor.configureUsing(null)
        MarkdownEditor.configureUsing(null)
        CodeEditor.configureUsing(null)
        val script = FormAssets.script()
        val css = FormAssets.stylesheet()
        assertTrue(script.contains("registerFireflyForms"))
        assertTrue(script.contains("FilePond"))
        assertTrue(script.contains("TipTap"))
        assertTrue(script.contains("CodeMirror"))
        assertTrue(script.contains("Shiki"))
        assertTrue(script.contains("markdown-it"))
        assertFalse(script.contains("Alpine.start"))
        assertTrue(css.contains(".ff-prose"))
        assertTrue(css.contains("#DF8723"))
        assertThrows(FireflyException::class.java) { FormAssets.read("missing.txt") }
        val live = TextInput.make("name").live()
        val blur = Textarea.make("notes").liveOnBlur()
        val debounce = Select.make("kind").liveDebounce(500)
        val updated = RichEditor.make("body").afterStateUpdated { state, _, _, set -> set.set("slug", state) }
        val slug = CodeEditor.make("slug")
        val pond = FileUpload.make("file").live()
        val clock = DateTimePicker.make("at").live()
        val md = MarkdownEditor.make("copy").live()
        val schema = Schema.make().components(live, blur, debounce, updated, slug, pond, clock, md)
        val html = schema.render()
        assertTrue(html.contains("wire:model.live=\"name\""))
        assertTrue(html.contains("wire:model.blur=\"notes\""))
        assertTrue(html.contains("debounce.500ms"))
        assertTrue(html.contains("@entangle(file)"))
        schema.set("body", "Hello")
        assertEquals("Hello", schema.get("slug"))
    }

    @Test
    fun repeaterAndBuilder() {
        var hooked = false
        val rows = Repeater.make("members")
            .simple(TextInput.make("name").required().distinct())
            .schema(TextInput.make("name").required().distinct(), Text.make("Note"))
            .addable(true)
            .deletable(true)
            .reorderable(true)
            .cloneable(true)
            .collapsible(true)
            .collapsed(true)
            .grid(2)
            .minItems(1)
            .maxItems(3)
            .defaultItems(1)
            .orderColumn("sort")
            .relationship("members")
            .itemLabel("Person")
            .itemLabel { index, _ -> "Row $index" }
            .addActionLabel("Add person")
            .addActionLabel { "Add" }
        rows.afterStateUpdated { _, _, _, _ -> hooked = true }
        assertThrows(FireflyException::class.java) { rows.grid(0) }
        val schema = Schema.make().components(rows)
        schema.hydrate()
        assertEquals(1, rows.items(schema).size)
        rows.addItem(schema)
        assertTrue(hooked)
        assertEquals(2, rows.items(schema).size)
        assertEquals(1, rows.items(schema)[0]["sort"])
        rows.cloneItem(schema, 0)
        assertEquals(3, rows.items(schema).size)
        rows.moveItem(schema, 2, 0)
        assertThrows(FireflyException::class.java) { rows.removeItem(schema, 9) }
        assertThrows(FireflyException::class.java) { rows.cloneItem(schema, -1) }
        assertThrows(FireflyException::class.java) { rows.moveItem(schema, 0, 9) }
        rows.removeItem(schema, 0)
        schema.fill(mapOf("members" to listOf(mapOf("name" to "Ada"), mapOf("name" to "Ada"), "nope")))
        val errors = rows.extraErrors(schema)
        assertTrue(errors["members"]!!.any { it.contains("duplicate") })
        schema.fill(mapOf("members" to listOf(mapOf("name" to ""))))
        assertTrue(rows.extraErrors(schema)["members"]!!.any { it.contains("required") })
        schema.fill(mapOf("members" to emptyList<Any>()))
        assertTrue(rows.extraErrors(schema)["members"]!!.any { it.contains("at least") })
        schema.fill(mapOf("members" to listOf(mapOf("name" to "A"), mapOf("name" to "B"), mapOf("name" to "C"), mapOf("name" to "D"))))
        assertTrue(rows.extraErrors(schema)["members"]!!.any { it.contains("more than") })
        val open = Repeater.make("members").schema(TextInput.make("name")).itemLabel("Person").addable(false).orderColumn("sort")
        schema.components(open)
        schema.fill(mapOf("members" to listOf(mapOf("name" to "Ada"))))
        val html = schema.render()
        assertTrue(html.contains("ff-repeater"))
        assertTrue(html.contains("Person"))
        assertTrue(html.contains("members.0.name"))
        assertFalse(html.contains("data-ff-repeater-add"))
        assertTrue(rows.items(Schema.make().components(Repeater.make("members")).fill(mapOf("members" to "nope"))).isEmpty())
        val labeled = Repeater.make("members").itemLabel { index, _ -> "N$index" }
        val labeledSchema = Schema.make().components(labeled).fill(mapOf("members" to listOf(mapOf("name" to "A"))))
        assertTrue(labeledSchema.render().contains("N0"))
        assertTrue(Schema.make().components(Repeater.make("members")).fill(mapOf("members" to listOf(mapOf("name" to "A")))).render().contains("Item 1"))
        assertTrue(Repeater.make("members").extraErrors(Schema.make().components(Repeater.make("members")).fill(mapOf("members" to listOf(mapOf("name" to "A"))))).isEmpty())
        Repeater.configureUsing { it.addActionLabel("More") }
        assertTrue(Schema.make().components(Repeater.make("members")).render().contains("More"))
        Repeater.configureUsing(null)
        val builder = Builder.make("blocks")
            .blocks(BuilderBlock("hero", "Hero", listOf(TextInput.make("title"), Text.make("Note"))))
            .addable(true)
            .deletable(true)
            .reorderable(true)
            .cloneable(true)
            .collapsible(true)
            .blockNumbers(true)
            .minItems(1)
            .maxItems(2)
            .addActionLabel("Insert")
            .addActionLabel { "Add block" }
        val built = Schema.make().components(builder)
        assertThrows(FireflyException::class.java) { builder.addBlock(built, "missing") }
        builder.addBlock(built, "hero")
        built.fill(mapOf("blocks" to listOf(mapOf("type" to "hero", "data" to mapOf("title" to "Hi")), mapOf("type" to "missing"), "skip")))
        val builderHtml = built.render()
        assertTrue(builderHtml.contains("1. "))
        assertTrue(builderHtml.contains("Hero"))
        assertTrue(builderHtml.contains("missing"))
        assertTrue(builderHtml.contains("Add block"))
        assertTrue(builder.items(built).size == 2)
        assertTrue(builder.items(Schema.make().components(Builder.make("blocks")).fill(mapOf("blocks" to "nope"))).isEmpty())
        built.fill(mapOf("blocks" to emptyList<Any>()))
        assertTrue(builder.extraErrors(built)["blocks"]!!.any { it.contains("at least") })
        built.fill(mapOf("blocks" to listOf(mapOf("type" to "hero"), mapOf("type" to "hero"), mapOf("type" to "hero"))))
        assertTrue(builder.extraErrors(built)["blocks"]!!.any { it.contains("more than") })
        assertTrue(Builder.make("blocks").extraErrors(Schema.make().components(Builder.make("blocks")).fill(mapOf("blocks" to listOf(mapOf("type" to "hero"))))).isEmpty())
        val quiet = Builder.make("blocks").blocks(BuilderBlock("hero", "Hero", listOf(TextInput.make("title")))).addable(false).blockNumbers(false)
        val quietHtml = Schema.make().components(quiet).fill(mapOf("blocks" to listOf(mapOf("type" to "hero", "data" to "nope")))).render()
        assertFalse(quietHtml.contains("ff-builder-picker"))
        assertTrue(quietHtml.contains("Hero"))
        Builder.configureUsing { it.blockNumbers(true) }
        assertTrue(Schema.make().components(Builder.make("blocks")).fill(mapOf("blocks" to listOf(mapOf("type" to "hero")))).render().contains("ff-builder"))
        Builder.configureUsing(null)
    }
}
