package org.konaframework.firefly.infolists

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Grid
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Section
import org.konaframework.firefly.schemas.Tab
import org.konaframework.firefly.schemas.Tabs
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.LinkedHashMap

class BareEntry(name: String) : Entry<BareEntry>(name)

class FireflyInfolistsTest {
    @AfterEach
    fun reset() {
        EntryClock.frozen = null
        TextEntry.configureUsing(null)
        IconEntry.configureUsing(null)
        ImageEntry.configureUsing(null)
        ColorEntry.configureUsing(null)
        KeyValueEntry.configureUsing(null)
        CodeEntry.configureUsing(null)
        RepeatableEntry.configureUsing(null)
    }

    @Test
    fun textLiteralsAndLists() {
        val plain = TextEntry.make("title").state("Ada <Dev>")
        assertTrue(html(plain).contains("Ada &lt;Dev&gt;"))
        assertTrue(html(plain).contains("x-data=\"ffEntry\""))
        val empty = TextEntry.make("title").placeholder("None <x>")
        assertTrue(html(empty).contains("None &lt;x&gt;"))
        val oneBadge = TextEntry.make("title").badge().state("Hot")
        assertSame(oneBadge, oneBadge.badge())
        assertTrue(html(oneBadge).contains("data-ff-badge=\"true\""))
        val bullets = TextEntry.make("title").bulleted(true).state(listOf("a", "b"))
        assertTrue(html(bullets).contains("<ul"))
        val breaks = TextEntry.make("title").listWithLineBreaks(true).state(listOf("a", "b"))
        assertTrue(html(breaks).contains("ff-text-item"))
        val joined = TextEntry.make("title").state(listOf("a<b", "c"))
        assertTrue(html(joined).contains("a&lt;b, c"))
        val limited = TextEntry.make("title").limitList(1).state(listOf("a", "b", "c"))
        assertTrue(html(limited).contains("ff-more\">+2"))
        val expandable = TextEntry.make("title").bulleted(true).limitList(1).expandableLimitedList(true).state(listOf("a", "b"))
        val expanded = html(expandable)
        assertTrue(expanded.contains("data-ff-extra=\"true\""))
        assertFalse(expanded.contains("ff-more"))
        val held = TextEntry.make("title").limitList(5).expandableLimitedList(true).state(listOf("a", "b"))
        assertFalse(html(held).contains("data-ff-extra"))
        val words = TextEntry.make("title").words(2).limit(4).state("alpha beta gamma")
        assertTrue(html(words).contains("…"))
        val short = TextEntry.make("title").words(5).limit(50).state("alpha")
        assertTrue(html(short).contains("alpha"))
        assertFalse(html(short).contains("…"))
        val htmlState = TextEntry.make("title").html(true).state("<b>ok</b>")
        assertTrue(html(htmlState).contains("<b>ok</b>"))
        val htmlChain = TextEntry.make("title").html().state("<em>rich</em>")
        assertTrue(html(htmlChain).contains("<em>rich</em>"))
        assertFalse(html(TextEntry.make("title").state("<b>x</b>")).contains("<b>x</b>"))
        val md = TextEntry.make("title").markdown(true).prose(true).state("**bold** and *em* and `c` and [a](https://kona.test)")
        val mdHtml = html(md)
        assertTrue(mdHtml.contains("<strong>bold</strong>"))
        assertTrue(mdHtml.contains("<em>em</em>"))
        assertTrue(mdHtml.contains("data-ff-prose=\"true\""))
        val dated = TextEntry.make("title").date().dateTooltip().timezone("UTC").state(LocalDate.of(2024, 6, 15))
        assertTrue(html(dated).contains("2024-06-15"))
        assertTrue(html(dated).contains("data-ff-date-tooltip"))
        val clock = TextEntry.make("title").dateTime("yyyy").timezone { "UTC" }.state(Instant.parse("2024-06-15T12:00:00Z"))
        assertTrue(html(clock).contains("2024"))
        val time = TextEntry.make("title").time().state(LocalDateTime.of(2024, 6, 15, 8, 30))
        assertTrue(html(time).contains("08:30"))
        EntryClock.frozen = Instant.parse("2024-06-15T12:00:00Z")
        val ago = TextEntry.make("title").since().state(Instant.parse("2024-06-13T12:00:00Z"))
        assertTrue(html(ago).contains("2 days ago"))
        val cash = TextEntry.make("title").money("USD").state(12)
        assertTrue(html(cash).contains("USD 12.00"))
        val num = TextEntry.make("title").numeric(1).state(1.26)
        assertTrue(html(num).contains("1.3"))
        assertTrue(html(TextEntry.make("title").date("yyyy-MM-dd").state(LocalDate.of(2024, 6, 15))).contains("2024-06-15"))
        assertTrue(html(TextEntry.make("title").dateTime().state(LocalDateTime.of(2024, 6, 15, 8, 30))).contains("2024-06-15 08:30"))
        assertTrue(html(TextEntry.make("title").time("HH:mm").state(LocalDateTime.of(2024, 6, 15, 8, 30))).contains("08:30"))
        assertTrue(html(TextEntry.make("title").money().state(3)).contains("USD 3.00"))
        val whole = TextEntry.make("title").numeric().state(1.6)
        assertTrue(html(whole).contains("2"))
        val custom = TextEntry.make("title").formatStateUsing { "Custom" }.state("ignored")
        assertTrue(html(custom).contains("Custom"))
        val blankName = TextEntry.make("")
        assertTrue(html(blankName).contains("ff-placeholder"))
        val forced = TextEntry.make("title").state(null)
        val schema = Schema.make().components(forced).fill(mapOf("title" to "Ada"))
        assertTrue(schema.render().contains("ff-placeholder"))
        val fromStore = TextEntry.make("title")
        val filled = Schema.make().components(fromStore).fill(mapOf("title" to "Ada"))
        assertTrue(filled.render().contains("Ada"))
    }

    @Test
    fun textClosuresAffixesAndChrome() {
        val entry = TextEntry.make("title")
        entry.label("Name")
        entry.label { "Closure" }
        entry.heading("H")
        entry.heading { "H2" }
        entry.description("D")
        entry.description { "D2" }
        entry.placeholder { "Missing" }
        entry.color("primary")
        entry.color { "accent" }
        entry.icon("user")
        entry.icon { "x" }
        entry.iconColor("danger")
        entry.iconColor { "success" }
        entry.iconPosition("before")
        entry.iconPosition { "after" }
        entry.tooltip("tip")
        entry.tooltip { "tip2" }
        entry.url("https://kona.test")
        entry.url { "https://kona.test/x" }
        entry.openUrlInNewTab()
        entry.openUrlInNewTab(true)
        entry.openUrlInNewTab { true }
        entry.copyable()
        entry.copyable(true)
        entry.copyable { true }
        entry.copyMessage("Copied")
        entry.copyMessage { "Copied" }
        entry.copyMessageDuration(1000)
        entry.copyMessageDuration { 500 }
        entry.copyableState("Ada")
        entry.copyableState { "Ada" }
        entry.extraAttributes(mapOf("data-a" to "1\"2", "1bad" to "no"))
        entry.extraAttributes { mapOf("data-b" to "ok") }
        entry.helperText("help")
        entry.helperText { "help2" }
        entry.hint("hint")
        entry.hint { "hint2" }
        entry.hintColor("primary")
        entry.hintColor { "accent" }
        entry.hintIcon("user")
        entry.hintIcon { "x" }
        entry.prefix("P")
        entry.prefix { "P2" }
        entry.suffix("S")
        entry.suffix { "S2" }
        entry.alignment("left")
        entry.alignment { "right" }
        entry.alignStart()
        entry.alignCenter()
        entry.alignEnd()
        entry.alignJustify()
        entry.wrap()
        entry.wrap(true)
        entry.wrap { true }
        entry.inlineLabel()
        entry.inlineLabel(true)
        entry.inlineLabel { false }
        entry.badge { true }
        entry.bulleted { false }
        entry.listWithLineBreaks { true }
        entry.limitList { 1 }
        entry.expandableLimitedList { false }
        entry.prose { true }
        entry.size("large")
        entry.size { "small" }
        entry.weight("bold")
        entry.weight { "light" }
        entry.fontFamily("mono")
        entry.fontFamily { "serif" }
        entry.lineClamp(2)
        entry.lineClamp { 3 }
        entry.limit { 20 }
        entry.words { 4 }
        entry.html { false }
        entry.markdown { false }
        entry.date { "yyyy" }
        entry.money { "EUR" }
        entry.aboveContent(Text.make("Above"))
        entry.belowContent(Text.make("Below"))
        entry.state(java.util.function.Function<org.konaframework.firefly.schemas.Get, Any?> { "Ada" })
        assertTrue(html(TextEntry.make("when").date { "yyyy" }.state(LocalDate.of(2024, 1, 2))).contains("2024"))
        assertTrue(html(TextEntry.make("when").dateTime { "yyyy" }.state(LocalDateTime.of(2024, 1, 2, 3, 4))).contains("2024"))
        assertTrue(html(TextEntry.make("when").time { "HH" }.state(LocalDateTime.of(2024, 1, 2, 3, 4))).contains("03"))
        assertTrue(html(TextEntry.make("when").dateTooltip { "yyyy" }.state(LocalDate.of(2024, 1, 2))).contains("data-ff-date-tooltip=\"2024\""))
        assertTrue(html(TextEntry.make("when").alignment { "left" }.state("A")).contains("data-ff-align=\"start\""))
        assertTrue(html(TextEntry.make("when").numeric { 1 }.state(1.26)).contains("1.3"))
        val rendered = html(entry)
        assertTrue(rendered.contains("Closure"))
        assertTrue(rendered.contains("Above"))
        assertTrue(rendered.contains("Below"))
        assertTrue(rendered.contains("data-b=\"ok\""))
        assertTrue(rendered.contains("target=\"_blank\""))
        assertTrue(rendered.contains("ff-hint"))
        assertTrue(rendered.contains("ff-helper"))
        assertTrue(rendered.contains("ff-icon-after"))
        val sameTab = TextEntry.make("title").url("https://kona.test").state("Ada")
        assertFalse(html(sameTab).contains("target="))
        val blankUrl = TextEntry.make("title").url("").state("Ada")
        assertFalse(html(blankUrl).contains("<a "))
        val noHint = TextEntry.make("title").state("Ada")
        assertFalse(html(noHint).contains("ff-hint"))
        assertThrows(FireflyException::class.java) { TextEntry.make("title").alignment("nope") }
        assertThrows(FireflyException::class.java) { TextEntry.make("title").size("huge") }
        assertThrows(FireflyException::class.java) { TextEntry.make("title").weight("nope") }
        assertThrows(FireflyException::class.java) { TextEntry.make("title").fontFamily("comic") }
        assertThrows(FireflyException::class.java) { TextEntry.make("title").iconPosition("side") }
        assertThrows(FireflyException::class.java) {
            html(TextEntry.make("title").size { "huge" }.state("Ada"))
        }
        assertThrows(FireflyException::class.java) {
            html(TextEntry.make("title").timezone("Nope/Zone").date().state(Instant.parse("2024-06-15T00:00:00Z")))
        }
        assertThrows(FireflyException::class.java) {
            html(TextEntry.make("title").date("'").state(LocalDate.of(2024, 1, 1)))
        }
        assertThrows(FireflyException::class.java) {
            html(TextEntry.make("title").numeric(-1).state(1))
        }
    }

    @Test
    fun temporalMoneyAndSinceBranches() {
        val zone = ZoneId.of("UTC")
        EntryClock.frozen = Instant.parse("2024-06-15T12:00:00Z")
        assertEquals("2024-06-15", temporalText(LocalDate.of(2024, 6, 15), "yyyy-MM-dd", zone))
        assertEquals("08:30", temporalText(LocalDateTime.of(2024, 6, 15, 8, 30), "HH:mm", zone))
        assertTrue(temporalText(Instant.parse("2024-06-15T12:00:00Z"), "HH:mm", zone).isNotEmpty())
        assertTrue(temporalText(OffsetDateTime.parse("2024-06-15T12:00:00+02:00"), "HH:mm", zone).isNotEmpty())
        assertTrue(temporalText(ZonedDateTime.parse("2024-06-15T12:00:00+02:00[Europe/Paris]"), "yyyy", zone).contains("2024"))
        assertTrue(temporalText(Date.from(Instant.parse("2024-06-15T12:00:00Z")), "yyyy", zone).contains("2024"))
        assertEquals("2024-06-15T12:00:00Z", temporalText("2024-06-15T12:00:00Z", "yyyy-MM-dd'T'HH:mm:ssX", zone))
        assertEquals("nope", temporalText("nope", "yyyy", zone))
        assertEquals("true", temporalText(true, "yyyy", zone))
        assertEquals("just now", sinceText(Instant.parse("2024-06-15T11:59:40Z"), zone))
        assertEquals("1 minute ago", sinceText(Instant.parse("2024-06-15T11:59:10Z"), zone))
        assertEquals("1 minute ago", sinceText(Instant.parse("2024-06-15T11:59:00Z"), zone))
        assertEquals("2 minutes ago", sinceText(Instant.parse("2024-06-15T11:58:00Z"), zone))
        assertEquals("in 1 minute", sinceText(Instant.parse("2024-06-15T12:01:00Z"), zone))
        assertEquals("1 hour ago", sinceText(Instant.parse("2024-06-15T10:30:00Z"), zone))
        assertEquals("2 hours ago", sinceText(Instant.parse("2024-06-15T10:00:00Z"), zone))
        assertEquals("1 day ago", sinceText(Instant.parse("2024-06-14T00:00:00Z"), zone))
        assertEquals("2 days ago", sinceText(Instant.parse("2024-06-13T12:00:00Z"), zone))
        assertEquals("nope", sinceText("nope", zone))
        assertEquals("", sinceText(null, zone))
        assertNull(toInstant(null, zone))
        assertEquals(Instant.parse("2024-06-15T12:00:00Z"), toInstant(Instant.parse("2024-06-15T12:00:00Z"), zone))
        assertNotNullInstant(toInstant(OffsetDateTime.parse("2024-06-15T12:00:00Z"), zone))
        assertNotNullInstant(toInstant(ZonedDateTime.parse("2024-06-15T12:00:00Z[UTC]"), zone))
        assertNotNullInstant(toInstant(LocalDateTime.of(2024, 6, 15, 12, 0), zone))
        assertNotNullInstant(toInstant(LocalDate.of(2024, 6, 15), zone))
        assertNotNullInstant(toInstant(Date.from(Instant.parse("2024-06-15T12:00:00Z")), zone))
        assertNotNullInstant(toInstant("2024-06-15T12:00:00Z", zone))
        assertNotNullInstant(parseInstant("2024-06-15T12:00:00+02:00", zone))
        assertNotNullInstant(parseInstant("2024-06-15T12:00:00+02:00[Europe/Paris]", zone))
        assertNotNullInstant(parseInstant("2024-06-15T12:00:00", zone))
        assertNotNullInstant(parseInstant("2024-06-15", zone))
        assertNull(parseInstant("nope", zone))
        assertNull(toInstant(true, zone))
        assertEquals("USD 1.50", moneyText(1.5, "USD"))
        assertEquals("USD 2.00", moneyText(2, "USD"))
        assertEquals("nope", moneyText("nope", "USD"))
        assertEquals("NaN", moneyText(Double.NaN, "USD"))
        assertEquals("Infinity", moneyText(Double.POSITIVE_INFINITY, "USD"))
        assertEquals("1.5", numericText(1.5f, 1))
        assertEquals("2", numericText("2", 0))
        assertEquals("true", numericText(true, 0))
        assertNull(toNumber(true))
        assertEquals(3.0, toNumber(3))
        assertEquals(1.5, toNumber("1.5"))
        assertNull(toNumber("x"))
        EntryClock.frozen = null
        assertTrue(sinceText(LocalDate.of(2020, 1, 1), zone).contains("ago"))
        assertThrows(FireflyException::class.java) { zoneOf("Nope/Zone") }
        assertEquals(ZoneId.systemDefault(), zoneOf(null))
        assertThrows(FireflyException::class.java) { numericText(1, -1) }
        assertEquals("ab…", limitText("abcd", 2, null))
        assertEquals("ab", limitText("ab", 5, null))
        assertEquals("a b…", limitText("a  b c", null, 2))
        assertEquals("a", limitText("a", null, 3))
        assertEquals("a", limitText("a", null, null))
    }

    @Test
    fun markdownJsonColorsAndIcons() {
        assertEquals("", markdown(""))
        assertEquals("", markdown("\n\n"))
        assertTrue(markdown("Hello\nworld").contains("<p>Hello world</p>"))
        assertTrue(markdown("```kt\nval a = 1\n```").contains("<pre><code>"))
        assertTrue(markdown("```code```").contains("code"))
        assertTrue(markdown("```kt\nopen").contains("open"))
        assertTrue(inlineMarkdown("**").contains("**"))
        assertTrue(inlineMarkdown("`").contains("`"))
        assertTrue(inlineMarkdown("[a](").contains("["))
        assertTrue(inlineMarkdown("[").contains("["))
        assertEquals("plain", fenceBody("plain"))
        assertTrue(inlineMarkdown("*").contains("*"))
        assertTrue(inlineMarkdown("a & b").contains("&amp;"))
        assertEquals("code", fenceBody("```code```"))
        assertEquals("&amp;&lt;&gt;&quot;", escapeHtml("&<>\""))
        assertEquals("a", escapeHtml("a"))
        assertNull(cssColor(null))
        assertNull(cssColor("  "))
        assertEquals("#abc", cssColor("#abc"))
        assertEquals("#abcd", cssColor("#abcd"))
        assertEquals("#DF8723", cssColor("#DF8723"))
        assertEquals("#DF8723ff", cssColor("#DF8723ff"))
        assertEquals("rgb(1, 2, 3)", cssColor("rgb(1, 2, 3)"))
        assertEquals("rgba(1, 2, 3, 0.5)", cssColor("rgba(1, 2, 3, 0.5)"))
        assertEquals("hsl(10, 20%, 30%)", cssColor("hsl(10, 20%, 30%)"))
        assertEquals("hsla(10, 20%, 30%, 0.5)", cssColor("hsla(10, 20%, 30%, 0.5)"))
        assertEquals("red", cssColor(" red "))
        assertNull(cssColor("red;"))
        assertEquals("", iconHtml(null))
        assertEquals("", iconHtml(""))
        assertTrue(iconHtml("user").contains("<svg"))
        assertTrue(iconHtml("envelope").contains("data-ff-icon=\"envelope\""))
        assertEquals("start", alignmentOf("left"))
        assertEquals("end", alignmentOf("right"))
        assertEquals("center", alignmentOf("center"))
        assertEquals("sm", sizeOf("small"))
        assertEquals("md", sizeOf("medium"))
        assertEquals("md", sizeOf("base"))
        assertEquals("lg", sizeOf("large"))
        assertEquals("bold", weightOf("bold"))
        assertEquals("mono", fontOf("mono"))
        assertEquals("before", iconPositionOf("before"))
        assertThrows(FireflyException::class.java) { alignmentOf("nope") }
        assertEquals("", extraAttrs(null))
        assertEquals("", extraAttrs(emptyMap()))
        assertEquals("", extraAttrs(mapOf("1bad" to "x")))
        assertTrue(extraAttrs(mapOf("data-a" to "a\"b")).contains("data-a=\"a&quot;b\""))
        assertEquals("", attr("data-a", null))
        assertEquals(" data-a=\"b\"", attr("data-a", "b"))
        assertEquals("", flag("data-a", false))
        assertEquals(" data-a=\"true\"", flag("data-a", true))
        assertEquals("", wrapSpan("ff-prefix", ""))
        assertEquals("<span class=\"ff-prefix\">x</span>", wrapSpan("ff-prefix", "x"))
        assertTrue(isBlank(null))
        assertTrue(isBlank(""))
        assertTrue(isBlank(emptyList<String>()))
        assertFalse(isBlank(0))
        assertFalse(isBlank("a"))
        assertFalse(truthy(null))
        assertTrue(truthy(true))
        assertFalse(truthy(false))
        assertFalse(truthy(0))
        assertTrue(truthy(2))
        assertTrue(truthy("1"))
        assertTrue(truthy("TRUE"))
        assertTrue(truthy("yes"))
        assertFalse(truthy("no"))
        assertFalse(truthy("maybe"))
        assertTrue(truthy(Any()))
        assertEquals("null", toJson(null, 0))
        assertEquals("true", toJson(true, 0))
        assertEquals("false", toJson(false, 0))
        assertEquals("3", toJson(3, 0))
        assertEquals("1.5", toJson(1.5, 0))
        assertEquals("1.5", toJson(1.5f, 0))
        assertEquals("null", toJson(Float.NaN, 0))
        assertEquals("null", toJson(Double.POSITIVE_INFINITY, 0))
        assertEquals("{}", toJson(emptyMap<String, Any?>(), 0))
        assertEquals("[]", toJson(emptyList<Any>(), 0))
        assertEquals("""{"a":1,"b":2}""", toJson(linkedMapOf("a" to 1, "b" to 2), 0))
        assertTrue(toJson(linkedMapOf("a" to 1, "b" to 2), 128).contains("\n"))
        assertTrue(toJson(listOf(1, 2), 128).contains("\n"))
        assertEquals("[1,2]", toJson(listOf(1, 2), 0))
        assertEquals("[1]", toJson(setOf(1), 0))
        val special = toJson("a/b<c\"d\\e\n\r\t\u0001", 0)
        assertTrue(special.contains("\\/"))
        assertTrue(special.contains("<"))
        assertTrue(toJson("a<b/c", 1).contains("\\u003C"))
        assertFalse(toJson("a/b", 64).contains("\\/"))
        assertTrue(toJson(object : Any() { override fun toString() = "obj" }, 0).contains("obj"))
        assertTrue(ShikiCode.source().contains("createHighlighter"))
        assertTrue(ShikiCode.source().contains("shiki"))
        assertFalse(ShikiCode.source().lowercase().contains("prism"))
        assertTrue(ShikiCode.manifest().contains("codemirror"))
        assertThrows(FireflyException::class.java) { ShikiCode.read("/missing-shiki.js") }
    }

    @Test
    fun iconImageColorKeyAndCode() {
        val yes = IconEntry.make("ok").boolean().state(true)
        assertTrue(html(yes).contains("success"))
        assertTrue(html(yes).contains("ff-sr\">true"))
        val no = IconEntry.make("ok").bool(true).state(false)
        assertTrue(html(no).contains("danger"))
        assertTrue(html(no).contains("ff-sr\">false"))
        val named = IconEntry.make("ok").whenTrue("user", "success").whenFalse("x", "danger").trueIcon("user").falseIcon("x").trueColor("success").falseColor("danger").state(true)
        assertTrue(html(named).contains("<svg"))
        val hiddenTrue = IconEntry.make("ok").hideTrueIcon().state(true)
        assertFalse(html(hiddenTrue).contains("<svg"))
        val hiddenFalse = IconEntry.make("ok").hideFalseIcon().state(false)
        assertFalse(html(hiddenFalse).contains("data-ff-icon"))
        val rows = IconEntry.make("ok").listWithLineBreaks(true).bool(true).state(listOf(true, false))
        assertTrue(html(rows).contains("ff-icon-row"))
        val closure = IconEntry.make("ok").bool { true }.size("lg").size { "sm" }.listWithLineBreaks { false }.trueIcon { "user" }.falseIcon { "x" }.trueColor { "success" }.falseColor { "danger" }.state(1)
        assertTrue(html(closure).contains("data-ff-size=\"sm\""))
        val byName = IconEntry.make("ok").icon("user").state(true)
        assertTrue(html(byName).contains("<svg"))
        val tint = IconEntry.make("ok").iconColor("primary").bool(true).state(false)
        assertTrue(html(tint).contains("primary"))
        val number = IconEntry.make("ok").state(1)
        assertTrue(html(number).contains("ff-icons"))
        val tipped = IconEntry.make("ok").tooltip("Ready").bool(true).state(true)
        assertTrue(html(tipped).contains("Ready"))
        val painted = IconEntry.make("ok").color("accent").iconColor("primary").bool(true).state(true)
        assertTrue(html(painted).contains("accent"))
        val placeholder = IconEntry.make("ok").placeholder("none").state(null)
        assertTrue(html(placeholder).contains("none"))
        val listNull = IconEntry.make("ok").bool(true).state(listOf(true, null))
        assertTrue(html(listNull).contains("ff-icon-item"))
        assertThrows(FireflyException::class.java) { IconEntry.make("ok").size("huge") }
        IconEntry.configureUsing { it.bool(true) }
        assertTrue(html(IconEntry.make("ok").state(true)).contains("success"))
        IconEntry.configureUsing(null)

        val photo = ImageEntry.make("photo")
        photo.disk("public")
        photo.disk { "local" }
        photo.visibility("public")
        photo.visibility { "private" }
        photo.imageHeight(12)
        photo.height(8)
        photo.height("9rem")
        photo.width("9rem")
        photo.imageHeight("2rem")
        photo.imageHeight { "3rem" }
        photo.imageWidth(12)
        photo.width(8)
        photo.imageWidth("2rem")
        photo.imageWidth { "4rem" }
        photo.imageSize(10)
        photo.size(6)
        photo.imageSize("1rem")
        photo.size("2rem")
        photo.imageSize { "5rem" }
        photo.circular(true)
        photo.circular { false }
        photo.square(true)
        photo.square { true }
        photo.alt("Cup")
        photo.alt { "Mug" }
        photo.defaultImageUrl("https://example.test/d.png")
        photo.defaultImageUrl { "https://example.test/e.png" }
        photo.extraImgAttributes(mapOf("data-img" to "1"))
        photo.extraImgAttributes { mapOf("data-img" to "2") }
        photo.stacked(true)
        photo.stacked { true }
        photo.overlap(2)
        photo.overlap { 3 }
        photo.ring(2)
        photo.ring("4")
        photo.ring { "1" }
        photo.limit(1)
        photo.limit { 1 }
        photo.limitedRemainingText(true)
        photo.limitedRemainingText { true }
        photo.limitedRemainingTextSeparate(true)
        photo.limitedRemainingTextSeparate { true }
        photo.limitedRemainingTextSize("sm")
        photo.limitedRemainingTextSize { "lg" }
        photo.checkFileExistence(false)
        photo.checkFileExistence { true }
        photo.url("https://kona.test")
        photo.openUrlInNewTab(false)
        photo.state(listOf("https://example.test/a.png", "https://example.test/b.png", null))
        val images = html(photo)
        assertTrue(images.contains("data-ff-stacked=\"true\""))
        assertTrue(images.contains("ff-more"))
        assertTrue(images.contains("data-img=\"2\""))
        val missing = ImageEntry.make("photo").placeholder("no photo")
        assertTrue(html(missing).contains("no photo"))
        val fallback = ImageEntry.make("photo").defaultImageUrl("https://example.test/d.png")
        assertTrue(html(fallback).contains("d.png"))
        val emptyDefault = ImageEntry.make("photo").defaultImageUrl("")
        assertTrue(html(emptyDefault).contains("ff-placeholder"))
        val spaces = ImageEntry.make("photo").checkFileExistence(true).state("   ")
        assertTrue(html(spaces).contains("ff-placeholder"))
        val kept = ImageEntry.make("photo").checkFileExistence(false).state("   ")
        assertTrue(html(kept).contains("src=\"   \""))
        val blankItem = ImageEntry.make("photo").defaultImageUrl("https://example.test/z.png").state(listOf(""))
        assertTrue(html(blankItem).contains("z.png"))
        val open = ImageEntry.make("photo").state(listOf("https://example.test/a.png"))
        assertFalse(html(open).contains("data-ff-limit"))
        val bareRepeat = RepeatableEntry.make("comments")
        assertTrue(html(bareRepeat).contains("No items"))
        val loose = TextEntry.make("title").bulleted(true).expandableLimitedList(true).state(listOf("a", "b"))
        assertTrue(html(loose).contains("<ul"))
        assertTrue(html(ImageEntry.make("photo").state(listOf(""))).contains("ff-placeholder"))
        assertTrue(html(ImageEntry.make("photo").defaultImageUrl("").state(listOf(""))).contains("ff-placeholder"))
        val noExtra = ImageEntry.make("photo").limit(5).limitedRemainingText(false).state(listOf("https://example.test/a.png"))
        assertFalse(html(noExtra).contains("ff-more"))
        ImageEntry.configureUsing { it.circular(true) }
        assertTrue(html(ImageEntry.make("photo").state("https://example.test/a.png")).contains("data-ff-circular"))
        ImageEntry.configureUsing(null)

        val swatch = ColorEntry.make("ink").state("#DF8723")
        assertTrue(html(swatch).contains("background-color: #DF8723"))
        val bad = ColorEntry.make("ink").state("red;")
        assertTrue(html(bad).contains("data-ff-invalid=\"true\""))
        val many = ColorEntry.make("ink").copyable(true).url("https://kona.test").state(listOf("red", "rgb(1, 2, 3)"))
        assertTrue(html(many).contains("ff-swatch"))
        val none = ColorEntry.make("ink").placeholder("no color")
        assertTrue(html(none).contains("no color"))
        ColorEntry.configureUsing { it.copyable(true) }
        assertTrue(html(ColorEntry.make("ink").state("blue")).contains("data-ff-copyable"))
        ColorEntry.configureUsing(null)

        val pairs = KeyValueEntry.make("meta").keyLabel("K").valueLabel("V").state(linkedMapOf("a" to "b", "c" to 1))
        assertTrue(html(pairs).contains(">K<"))
        assertTrue(html(pairs).contains(">b<"))
        val labeled = KeyValueEntry.make("meta").keyLabel { "Name" }.valueLabel { "Detail" }.emptyMessage("Empty").state(emptyMap<String, String>())
        assertTrue(html(labeled).contains("Empty"))
        val notMap = KeyValueEntry.make("meta").emptyMessage { "Nothing" }.state("nope")
        assertTrue(html(notMap).contains("Nothing"))
        val nullKey = LinkedHashMap<Any?, Any?>()
        nullKey[null] = "skip"
        nullKey["lane"] = "latte"
        assertTrue(html(KeyValueEntry.make("meta").state(nullKey)).contains("latte"))
        assertTrue(html(KeyValueEntry.make("meta")).contains("No values"))
        KeyValueEntry.configureUsing { it.keyLabel("Field") }
        assertTrue(html(KeyValueEntry.make("meta").state(mapOf("a" to "b"))).contains("Field"))
        KeyValueEntry.configureUsing(null)

        val code = CodeEntry.make("snippet").state("val a = 1")
        val codeHtml = html(code)
        assertTrue(codeHtml.contains("data-ff-grammar=\"html\""))
        assertTrue(codeHtml.contains("data-ff-shiki=\"shiki\""))
        assertTrue(codeHtml.contains("github-light"))
        assertTrue(codeHtml.contains("github-dark"))
        val json = CodeEntry.make("snippet").state(listOf(1, 2))
        assertTrue(html(json).contains("data-ff-grammar=\"json\""))
        val mapped = CodeEntry.make("snippet").grammar("kotlin").language("java").lightTheme("github-light").darkTheme("github-dark").jsonFlags(128).state(linkedMapOf("a" to 1))
        assertTrue(html(mapped).contains("data-ff-grammar=\"java\""))
        val themed = CodeEntry.make("snippet").grammar { "kotlin" }.language { "kotlin" }.lightTheme { "min-light" }.darkTheme { "min-dark" }.jsonFlags { 0 }.state("a = 1")
        val themedHtml = html(themed)
        assertTrue(themedHtml.contains("min-light"))
        assertTrue(themedHtml.contains("min-dark"))
        assertTrue(html(CodeEntry.make("snippet").placeholder("no code")).contains("no code"))
        CodeEntry.configureUsing { it.grammar("kotlin") }
        assertTrue(html(CodeEntry.make("snippet").state("a")).contains("data-ff-grammar=\"kotlin\""))
        CodeEntry.configureUsing(null)
    }

    @Test
    fun repeatableLayoutsAndBridge() {
        val comments = RepeatableEntry.make("comments")
            .schema(TextEntry.make("author.name").color { get ->
                val tone = get.get("tone")
                val banner = get.get("/banner")
                val missing = get.get("missing")
                if (tone == "warm" && banner == "Hi" && missing == null) "accent" else "danger"
            }, TextEntry.make("title"))
            .grid(2)
            .grid { 3 }
            .contained(false)
            .contained { true }
            .placeholder("None")
        val rows = listOf(
            mapOf("author" to mapOf("name" to "Jane"), "title" to "Wow", "tone" to "warm"),
            "skip",
            mapOf("title" to "Help"),
        )
        val schema = Schema.make().components(comments).fill(mapOf("banner" to "Hi", "comments" to rows))
        val rendered = schema.render()
        assertTrue(rendered.contains("Jane"))
        assertTrue(rendered.contains("Wow"))
        assertTrue(rendered.contains("data-ff-color=\"accent\""))
        assertTrue(rendered.contains("data-ff-grid=\"3\""))
        assertTrue(rendered.contains("data-ff-contained=\"true\""))
        val single = RepeatableEntry.make("comments").schema(TextEntry.make("title")).state(mapOf("title" to "Only"))
        assertTrue(html(single).contains("Only"))
        val junk = RepeatableEntry.make("comments").state("nope")
        assertTrue(html(junk).contains("No items"))
        val skipped = RepeatableEntry.make("comments").schema(TextEntry.make("title")).state(listOf("nope"))
        assertTrue(html(skipped).contains("No items"))
        val nullOnly = LinkedHashMap<Any?, Any?>()
        nullOnly[null] = "x"
        assertTrue(html(RepeatableEntry.make("comments").state(nullOnly)).contains("No items"))
        val table = RepeatableEntry.make("comments").table(
            TableColumn.make("Author"),
            TableColumn.make("Quiet").hiddenHeaderLabel(),
        ).schema(TextEntry.make("title"), Text.make("note")).state(listOf(mapOf("title" to "Ada")))
        val tableHtml = html(table)
        assertTrue(tableHtml.contains("ff-repeatable-table"))
        assertTrue(tableHtml.contains("ff-sr"))
        assertTrue(tableHtml.contains("Ada"))
        assertTrue(tableHtml.contains("note"))
        assertFalse(TableColumn.make("A").hiddenHeaderLabel(false).isHeaderHidden())
        assertEquals("A", TableColumn.make("A").label())
        RepeatableEntry.configureUsing { it.contained(false) }
        assertFalse(html(RepeatableEntry.make("comments").state(mapOf("title" to "A")).schema(TextEntry.make("title"))).contains("data-ff-contained"))
        RepeatableEntry.configureUsing(null)
        val layout = Infolist.make().statePath("user").operation("view").record("ada").components(
            Section.make("Profile").schema(
                Grid.make(2).schema(TextEntry.make("name").state("Ada")),
                Tabs.make().tabs(Tab.make("Code").schema(CodeEntry.make("snippet").state("a = 1"))),
            ),
        )
        val page = layout.render()
        assertTrue(page.contains("ff-section"))
        assertTrue(page.contains("ff-grid"))
        assertTrue(page.contains("ff-tab"))
        assertEquals("ada", layout.record())
        assertEquals("view", layout.operation())
        layout.fill(mapOf("user.name" to "Bea"))
        layout.hydrate()
        assertEquals("Bea", layout.get("user.name"))
        val live = TextEntry.make("title").state("Ada")
        live.live()
        assertTrue(html(live).contains("wire:model.live"))
        val hidden = TextEntry.make("title").state("Ada")
        hidden.hidden(true)
        assertTrue(html(hidden).contains("ff-hidden"))
        assertTrue(html(TextEntry.make("title").bulleted(true).state(listOf("only"))).contains("<ul"))
        assertTrue(html(TextEntry.make("title").listWithLineBreaks(true).state(listOf("only"))).contains("ff-text-item"))
        assertTrue(html(CodeEntry.make("snippet")).contains("ff-placeholder"))
        assertTrue(html(CodeEntry.make("snippet").state(mapOf("a" to 1))).contains("data-ff-grammar=\"json\""))
        assertTrue(html(ColorEntry.make("ink")).contains("ff-placeholder"))
        assertTrue(html(IconEntry.make("ok")).contains("ff-placeholder"))
        assertTrue(html(IconEntry.make("ok").state("user")).contains("ff-sr"))
        assertTrue(html(IconEntry.make("ok").bool(true).falseIcon("user").falseColor("primary").state(false)).contains("primary"))
        val bare = BareEntry("note")
        assertTrue(html(ImageEntry.make("photo").limit(1).limitedRemainingText(false).state(listOf("https://example.test/a.png", "https://example.test/b.png"))).contains("ff-image"))
        assertTrue(html(RepeatableEntry.make("comments").placeholder("None")).contains("None"))
        val emptyRow = LinkedHashMap<Any?, Any?>()
        emptyRow[null] = "x"
        assertTrue(html(RepeatableEntry.make("comments").state(listOf(emptyRow, mapOf("title" to "Kept"))).schema(TextEntry.make("title"))).contains("Kept"))
        assertTrue(html(bare).contains("ff-entry"))
        bare.view("firefly.entries.note")
        bare.meta("ff-view", "firefly.entries.note")
        assertTrue(html(bare).contains("data-ff-view=\"firefly.entries.note\""))
        TextEntry.configureUsing { it.badge(true) }
        assertTrue(html(TextEntry.make("title").state("A")).contains("data-ff-badge"))
        TextEntry.configureUsing(null)
        assertFalse(html(TextEntry.make("title").state("A")).contains("data-ff-badge"))

        val entry = TextEntry.make("title")
        val bound = Schema.make().statePath("user").components(entry)
        bound.fill(mapOf("user.title" to "Ada", "user.other" to "Bee", "banner" to "Hi"))
        val get = SchemaBridge.open(bound, entry)
        assertEquals("Ada", get.get("title"))
        val over = SchemaBridge.overlay(bound, entry) { path -> if (path == "tone") "warm" else MISS }
        assertEquals("Hi", over.get("/banner"))
        assertEquals("warm", over.get("tone"))
        assertEquals("Bee", over.get("other"))
        assertNull(digName(null, "a"))
        assertEquals("root", digName("root", ""))
        assertNull(digName("root", "a"))
        assertNull(digName(mapOf("a" to "b"), "missing"))
        assertEquals("Jane", digName(mapOf("author" to mapOf("name" to "Jane")), "author.name"))
        val item = mapOf("a" to mapOf("b" to 1), "s" to "text")
        assertEquals(MISS, itemLookup(item, ""))
        assertEquals(MISS, itemLookup(item, "."))
        assertEquals(MISS, itemLookup(item, ".."))
        assertEquals(MISS, itemLookup(item, "a/../b"))
        assertEquals(1, itemLookup(item, "a//b"))
        assertEquals(1, itemLookup(item, "a/./b"))
        assertEquals(MISS, itemLookup(item, "s/b"))
        assertEquals(MISS, itemLookup(item, "missing"))
        ItemFrame.use(mapOf("a" to 1)) {
            assertEquals(1, ItemFrame.current()!!["a"])
            ItemFrame.use(mapOf("b" to 2)) {
                assertEquals(2, ItemFrame.current()!!["b"])
            }
            assertEquals(1, ItemFrame.current()!!["a"])
        }
        assertNull(ItemFrame.current())
        assertThrows(IllegalStateException::class.java) {
            ItemFrame.use(mapOf("a" to 1)) { throw IllegalStateException("x") }
        }
        assertNull(ItemFrame.current())
    }

    private fun html(component: Component): String = Schema.make().components(component).render()

    private fun assertNotNullInstant(value: Instant?) {
        assertTrue(value != null)
    }
}
