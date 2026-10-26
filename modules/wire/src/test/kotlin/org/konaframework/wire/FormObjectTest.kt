package org.konaframework.wire

import org.konaframework.wire.fixtures.AbstractFormHost
import org.konaframework.wire.fixtures.Address
import org.konaframework.wire.fixtures.JavaFormHost
import org.konaframework.wire.fixtures.JavaProfileForm
import org.konaframework.wire.fixtures.NeedsArgForm
import org.konaframework.wire.fixtures.NeedsArgHost
import org.konaframework.wire.fixtures.ProfileForm
import org.konaframework.wire.fixtures.ReadOnlyFormHost
import org.konaframework.wire.fixtures.TitleHost
import org.konaframework.wire.fixtures.ValidatedCounter
import org.konaframework.wire.hydration.FormSynth
import org.konaframework.wire.hydration.Synthesizers
import org.konaframework.wire.lifecycle.ComponentContext
import org.konaframework.http.ValidationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.function.Consumer

class FormObjectTest {
    private val manager = brew().component("validated-counter", ValidatedCounter::class.java)

    @AfterEach
    fun reset() {
        Wire.reset()
    }

    private fun facade(): WireManager {
        Wire.setManager(manager)
        return manager
    }

    @Test
    fun `component validate includes form rules and helpers`() {
        facade()
        val c = Wire.test(ValidatedCounter::class.java).instance() as ValidatedCounter
        assertEquals("profile", c.profile.getPropertyName())
        assertSame(c, c.profile.getComponent())
        c.email = "ada@example.com"
        assertThrows<ValidationException> { c.validate() }
        assertTrue(c.errorBag().has("profile.name"))
        c.profile.fill(mapOf("name" to "Ada", "nickname" to "n"))
        assertEquals("ada@example.com", c.validate()["email"])
        c.profile.fill(mapOf("name" to "A"))
        assertThrows<ValidationException> { c.validateOnly("profile.name") }
        assertTrue(c.errorBag().has("profile.name"))
        c.resetErrorBag()

        c.profile.addError("name", "bad")
        c.profile.addError("profile.name", "again")
        assertEquals(listOf("bad", "again"), c.profile.getErrorBag().get("profile.name"))
        c.profile.resetValidation("name")
        assertFalse(c.errorBag().has("profile.name"))
        c.profile.addError("name", "bad")
        c.errorBag().add("profile", "whole")
        c.errorBag().add("email", "nope")
        c.profile.resetErrorBag()
        assertFalse(c.errorBag().has("profile.name"))
        assertFalse(c.errorBag().has("profile"))
        assertTrue(c.errorBag().has("email"))

        c.profile.fill(mapOf("name" to "Ada", "nickname" to "nick"))
        assertEquals("Ada", c.profile.pull()["name"])
        assertEquals("", c.profile.all()["name"])
        c.profile.fill(mapOf("name" to "Ada", "nickname" to "nick"))
        assertEquals("Ada", c.profile.pull("name", "nickname")["name"])
        assertEquals("", c.profile.all()["nickname"])
        c.profile.fill(mapOf("name" to "Ada", "nickname" to "nick"))
        c.profile.reset(listOf("nickname"))
        assertEquals("Ada", c.profile.all()["name"])
        assertEquals("", c.profile.all()["nickname"])
        c.profile.reset(emptyList())
        assertEquals("", c.profile.all()["name"])
        c.profile.fill(mapOf("name" to "Ada", "nickname" to "nick", "code" to "zz"))
        c.profile.resetExcept("name", "nickname", "code")
        assertEquals("Ada", c.profile.all()["name"])
        c.profile.resetExcept("name")
        assertEquals("Ada", c.profile.all()["name"])
        assertEquals("", c.profile.all()["nickname"])
        c.profile.resetExcept()
        assertEquals("", c.profile.all()["name"])

        val loose = ProfileForm()
        assertEquals("", loose.getPropertyName())
        loose.attach(c, "")
        loose.addError("name", "local")
        assertTrue(c.errorBag().has("name"))
        loose.resetErrorBag()
        assertFalse(c.errorBag().has("name"))
        assertThrows<WireException> { ProfileForm().addError("name", "x") }
    }

    @Test
    fun `nested writes keep the form and skip realtime without Validate`() {
        facade()
        val t = Wire.test(ValidatedCounter::class.java)
        val before = (t.instance() as ValidatedCounter).profile
        val finish = Wire.manager().updateProperty(t.instance(), "profile.name", "Ada", ComponentContext(t.instance(), false))
        finish()
        val after = t.instance() as ValidatedCounter
        assertSame(before, after.profile)
        assertEquals("Ada", after.profile.all()["name"])
        assertEquals("profile", after.profile.getPropertyName())

        val code = Wire.test(ValidatedCounter::class.java).set("profile.code", "TOOLONG")
        assertEquals("TOOLONG", (code.instance() as ValidatedCounter).profile.all()["code"])
        assertFalse(code.instance().errorBag().has("profile.code"))
        Wire.test(ValidatedCounter::class.java).set("note", "hello there")
        assertThrows<PublicPropertyNotFoundException> {
            Wire.test(ValidatedCounter::class.java).set("missing", "x")
        }

        @Suppress("UNCHECKED_CAST")
        val data = Wire.test(ValidatedCounter::class.java).snapshot()["data"] as Map<String, Any?>
        val tuple = data["profile"] as List<*>
        assertEquals("frm", (tuple[1] as Map<*, *>)["s"])
    }

    @Test
    fun `null form properties are constructed`() {
        facade()
        val meta = org.konaframework.wire.meta.ComponentMeta.of(TitleHost::class.java)
        assertEquals(setOf("form"), meta.properties.keys)
        val prop = meta.properties.getValue("form")
        assertFalse(prop.readOnly)
        assertEquals(org.konaframework.wire.fixtures.TitleForm::class.java, prop.type)
        val host = Wire.test(TitleHost::class.java)
        val form = (host.instance() as TitleHost).form
        assertNotNull(form)
        assertEquals("form", form!!.getPropertyName())
        assertThrows<ValidationException> { (host.instance() as TitleHost).save() }
        assertTrue(host.instance().errorBag().has("form.title"))
        val updated = host.set("form.title", "Hi").set("form.code", "TOOL")
        assertEquals("TOOL", (updated.instance() as TitleHost).form!!.all()["code"])
        assertFalse(updated.instance().errorBag().has("form.code"))

        assertNull((Wire.test(AbstractFormHost::class.java).instance() as AbstractFormHost).form)
        assertNull((Wire.test(ReadOnlyFormHost::class.java).instance() as ReadOnlyFormHost).form)
        assertThrows<WireException> { Wire.test(NeedsArgHost::class.java) }

        val java = Wire.test(JavaFormHost::class.java)
        assertNotNull((java.instance() as JavaFormHost).form)
        assertNull((java.instance() as JavaFormHost).label)
        val cleared = java.set("form", null)
        assertNull((cleared.instance() as JavaFormHost).form)
        val restored = cleared.refresh()
        val again = (restored.instance() as JavaFormHost).form
        assertNotNull(again)
        assertEquals("form", again!!.getPropertyName())
    }

    @Test
    fun `form synth round trip and guards`() {
        facade()
        val synths = Synthesizers()
        val ctx = synths.context("profile")
        val form = ProfileForm()
        form.fill(mapOf("name" to "Ada"))
        assertTrue(FormSynth.match(form))
        assertFalse(FormSynth.match("plain"))
        val (data, meta) = FormSynth.dehydrate(form, ctx)
        assertEquals("Ada", (data as Map<*, *>)["name"])
        val hydrated = FormSynth.hydrate(data, meta, ctx, null) as ProfileForm
        assertEquals("Ada", hydrated.all()["name"])
        val blank = FormSynth.hydrate("nope", meta, ctx, null) as ProfileForm
        assertEquals("", blank.all()["name"])
        val skipped = FormSynth.hydrate(mapOf("name" to "Bea", "nope" to 1), meta, ctx, null) as ProfileForm
        assertEquals("Bea", skipped.all()["name"])
        assertNull(FormSynth.get(form, "missing"))
        assertEquals("Ada", FormSynth.get(form, "name"))
        assertSame(form, FormSynth.set(form, "missing", "z"))
        assertSame(form, FormSynth.set(form, "name", "Cara"))
        assertEquals("Cara", form.all()["name"])
        assertSame(form, FormSynth.unset(form, "missing"))
        assertSame(form, FormSynth.unset(form, "name"))
        assertEquals("", form.all()["name"])
        assertNull(FormSynth.childType(null, null, "name"))
        assertNull(FormSynth.childType(null, form, "missing"))
        assertEquals(String::class.java, FormSynth.childType(null, form, "name"))
        assertThrows<CorruptComponentPayloadException> {
            FormSynth.hydrate(emptyMap<String, Any?>(), mapOf("class" to 1), ctx, null)
        }
        assertThrows<CorruptComponentPayloadException> {
            FormSynth.hydrate(emptyMap<String, Any?>(), mapOf("class" to "java.lang.String"), ctx, null)
        }
        assertThrows<CorruptComponentPayloadException> {
            FormSynth.hydrate(emptyMap<String, Any?>(), mapOf("class" to Address::class.java.name), ctx, null)
        }
        assertThrows<WireException> {
            FormSynth.hydrate(emptyMap<String, Any?>(), mapOf("class" to NeedsArgForm::class.java.name), ctx, null)
        }
        val seen = booleanArrayOf(false)
        val java = JavaProfileForm()
        java.withValidator(Consumer { seen[0] = true })
        java.attach(Wire.test(ValidatedCounter::class.java).also { facade() }.instance(), "form")
        java.name = "Ada"
        java.nickname = "ok"
        java.validate()
        assertTrue(seen[0])
    }
}
