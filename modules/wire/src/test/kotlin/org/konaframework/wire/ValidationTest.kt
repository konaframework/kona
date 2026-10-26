package org.konaframework.wire

import org.konaframework.wire.fixtures.EmptyRulesForm
import org.konaframework.wire.fixtures.JavaProfileForm
import org.konaframework.wire.fixtures.ProfileForm
import org.konaframework.wire.fixtures.RuleOnlyComponent
import org.konaframework.wire.fixtures.TransitionedCounter
import org.konaframework.wire.fixtures.UniqueEmailComponent
import org.konaframework.wire.fixtures.ValidatedCounter
import org.konaframework.http.ExistenceChecker
import org.konaframework.http.ValidationException
import org.konaframework.http.Validator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.function.Consumer

class ValidationTest {
    private val manager = brew()
        .component("validated-counter", ValidatedCounter::class.java)
        .component("rule-only", RuleOnlyComponent::class.java)
        .component("unique-email", UniqueEmailComponent::class.java)
        .component("transitioned", TransitionedCounter::class.java)

    @AfterEach
    fun reset() {
        Wire.reset()
    }

    private fun facade(): WireManager {
        Wire.setManager(manager)
        return manager
    }

    @Test
    fun `validate fails and fills error bag`() {
        facade()
        val t = Wire.test(ValidatedCounter::class.java)
        assertThrows<ValidationException> { t.instance().validate() }
        assertTrue(t.instance().errorBag().has("email"))
        assertTrue(t.instance().errorBag().first("email")!!.isNotBlank())
    }

    @Test
    fun `validateOnly and resetValidation`() {
        facade()
        val c = Wire.test(ValidatedCounter::class.java).instance() as ValidatedCounter
        c.email = "not-an-email"
        assertThrows<ValidationException> { c.validateOnly("email") }
        assertTrue(c.errorBag().has("email"))
        c.resetValidation("email")
        assertFalse(c.errorBag().has("email"))
        c.addError("name", "required")
        assertEquals("required", c.getErrorBag().first("name"))
        c.setErrorBag(ErrorBag().add("z", "zz"))
        assertTrue(c.errorBag().has("z"))
        c.resetErrorBag()
        assertTrue(c.errorBag().isEmpty())
        c.resetValidation()
        assertTrue(c.errorBag().isEmpty())
    }

    @Test
    fun `validation exception in action is swallowed into bag`() {
        facade()
        val t = Wire.test(ValidatedCounter::class.java).set("email", "bad").call("save")
        assertTrue(t.instance().errorBag().has("email"))
        @Suppress("UNCHECKED_CAST")
        val memo = t.snapshot()["memo"] as Map<String, Any?>
        assertTrue(memo["errors"] is Map<*, *>)
        assertTrue(t.effects().containsKey("errors") || t.instance().errorBag().isNotEmpty())
    }

    @Test
    fun `form object validate and helpers`() {
        facade()
        val c = Wire.test(ValidatedCounter::class.java).instance() as ValidatedCounter
        c.profile.fill(mapOf("name" to "Ada", "unknown" to 1))
        assertEquals("Ada", c.profile.all()["name"])
        assertEquals(mapOf("name" to "Ada"), c.profile.only("name"))
        assertTrue(c.profile.except("missing").containsKey("name"))
        c.profile.withValidator(Consumer { })
        c.profile.boot()
        c.profile.hydrate()
        c.profile.dehydrate()
        c.profile.updating("name", "x")
        c.profile.updated("name", "x")
        assertTrue(c.profile.shouldValidateOnUpdate("name"))
        c.profile.reset("nope")
        c.profile.reset()
        assertEquals("", c.profile.all()["name"])
        c.profile.fill(mapOf("name" to "Ada"))
        assertEquals("Ada", c.profile.pull("name"))
        assertEquals("", c.profile.all()["name"])
        c.profile.fill(mapOf("name" to "A"))
        assertThrows<ValidationException> { c.profile.validate() }
        assertTrue(c.errorBag().isNotEmpty())
        c.profile.fill(mapOf("name" to "Ada Lovelace"))
        c.errorBag().clear()
        assertEquals("Ada Lovelace", c.profile.validate()["name"])
        assertThrows<WireException> { ProfileForm().validate() }
        assertThrows<WireException> { ProfileForm().component() }
        val empty = EmptyRulesForm()
        assertTrue(empty.rules().isEmpty())
        assertTrue(empty.messages().isEmpty())
        assertTrue(empty.validationAttributes().isEmpty())
        assertThrows<WireException> { empty.validateOnly("value") }
        val javaForm = JavaProfileForm()
        javaForm.attach(Wire.test(ValidatedCounter::class.java).instance(), "javaProfile")
        javaForm.fill(mapOf("name" to "Ada", "nickname" to "ok"))
        assertEquals("Ada", javaForm.validate()["name"])
    }

    @Test
    fun `form nested wire model update`() {
        facade()
        val t = Wire.test(ValidatedCounter::class.java)
            .set("profile.name", "A")
        assertTrue(t.instance().errorBag().isNotEmpty() || (t.instance() as ValidatedCounter).profile.all()["name"] == "A")
        Wire.test(ValidatedCounter::class.java)
            .set("profile.name", "Valid Name")
            .assertSet("profile.name", "Valid Name")
        // Whole-form assignment hits Form update without a nested path.
        Wire.test(ValidatedCounter::class.java).set("profile", mapOf("name" to "Whole", "nickname" to "", "code" to ""))
    }

    @Test
    fun `realtime validate on update`() {
        facade()
        val t = Wire.test(ValidatedCounter::class.java).set("email", "nope")
        assertTrue(t.instance().errorBag().has("email"))
    }

    @Test
    fun `successful validate clears bag`() {
        facade()
        val c = Wire.test(ValidatedCounter::class.java).instance() as ValidatedCounter
        c.email = "ada@example.com"
        c.profile.fill(mapOf("name" to "Ada"))
        val validated = c.validate()
        assertEquals("ada@example.com", validated["email"])
        assertTrue(c.errorBag().isEmpty())
        c.validateOnly("email")
        assertTrue(c.errorBag().isEmpty())
    }

    @Test
    fun `rule annotation component`() {
        facade()
        val t = Wire.test(RuleOnlyComponent::class.java)
        assertThrows<ValidationException> { t.instance().validate() }
        t.set("title", "Hi").call("save")
        assertTrue(t.instance().errorBag().isEmpty() || t.instance().all()["title"] == "Hi")
    }

    @Test
    fun `error bag wire round trip`() {
        val bag = ErrorBag().add("a", "1").add("a", "2").add("b", "x")
        assertEquals(listOf("1", "2"), bag.get("a"))
        assertEquals("1", bag.first())
        assertTrue(bag.isNotEmpty())
        assertFalse(bag.isEmpty())
        val restored = ErrorBag.fromWire(bag.toWire())
        assertEquals(bag.all(), restored.all())
        assertTrue(ErrorBag.fromWire(null).isEmpty())
        assertTrue(ErrorBag.fromWire("x").isEmpty())
        ErrorBag.fromWire(mapOf("c" to "solo")).also { assertEquals(listOf("solo"), it.get("c")) }
        ErrorBag.fromWire(mapOf("d" to listOf("p", "q", null))).also { assertEquals(listOf("p", "q"), it.get("d")) }
        ErrorBag.fromWire(mapOf(null to "z")).also { assertTrue(it.isEmpty()) }
        ErrorBag.fromWire(mapOf("e" to null)).also { assertTrue(it.isEmpty()) }
        assertEquals(emptyList<String>(), ErrorBag().get("missing"))
        bag.clear("a")
        assertFalse(bag.has("a"))
        bag.clear()
        assertTrue(bag.isEmpty())
        assertEquals(null, bag.first())
        assertEquals(null, bag.first("missing"))
        assertEquals(emptyList<String>(), bag.get("missing"))
    }

    @Test
    fun `testable assertHasErrors`() {
        facade()
        Wire.test(ValidatedCounter::class.java)
            .call("save")
            .assertHasErrors("email")
            .assertHasNoErrors("missing")
        Wire.test(ValidatedCounter::class.java)
            .set("email", "ada@example.com")
            .set("profile.name", "Ada")
            .call("save")
            .assertHasNoErrors()
        assertThrows<AssertionError> {
            Wire.test(ValidatedCounter::class.java).call("save").assertHasNoErrors("email")
        }
        assertThrows<AssertionError> {
            Wire.test(ValidatedCounter::class.java)
                .set("email", "ada@example.com")
                .set("profile.name", "Ada")
                .call("save")
                .assertHasErrors()
        }
        assertThrows<AssertionError> {
            Wire.test(ValidatedCounter::class.java).call("save").assertHasErrors("nope")
        }
    }

    @Test
    fun `messages and validationAttributes defaults`() {
        facade()
        val c = Wire.test(ValidatedCounter::class.java).instance() as ValidatedCounter
        assertTrue(c.messages().isEmpty())
        assertTrue(c.validationAttributes().isEmpty())
        assertTrue(c.profile.messages().isEmpty())
        assertTrue(c.profile.validationAttributes().isEmpty())
        assertTrue(c.profile.rules().isEmpty())
        assertTrue(c.profile.shouldValidateOnUpdate("name"))
        assertFalse(c.profile.shouldValidateOnUpdate("code"))
        c.profile.fill(mapOf("name" to "Ada", "nickname" to "x".repeat(50)))
        assertThrows<ValidationException> { c.profile.validateOnly("profile.nickname") }
        assertThrows<ValidationException> { c.profile.validateOnly("nickname") }
        c.resetValidation()
        c.email = "ada@example.com"
        c.validateOnly("email.extra")
    }

    @Test
    fun `shouldValidateOnUpdate without cache`() {
        facade()
        val c = Wire.test(ValidatedCounter::class.java).instance()
        c.state().store.remove("brew.validateOnUpdate")
        assertTrue(ValidationRules.shouldValidateOnUpdate(c, "email"))
        assertFalse(ValidationRules.shouldValidateOnUpdate(c, "nope"))
    }

    @Test
    fun `jvmOverloads no-arg validate bridges`() {
        facade()
        val c = Wire.test(RuleOnlyComponent::class.java).instance() as RuleOnlyComponent
        c.title = "Hello"
        // Reflective no-arg validate() matches Java @JvmOverloads callers.
        @Suppress("UNCHECKED_CAST")
        val validated = c.javaClass.getMethod("validate").invoke(c) as Map<String, Any?>
        assertEquals("Hello", validated["title"])
        val form = JavaProfileForm()
        form.attach(c, "form")
        form.name = "Ada"
        form.nickname = "ok"
        @Suppress("UNCHECKED_CAST")
        val formValidated = form.javaClass.getMethod("validate").invoke(form) as Map<String, Any?>
        assertEquals("Ada", formValidated["name"])
        assertTrue(ErrorBag::class.java.getMethod("fromWire", Any::class.java).invoke(null, null).let { it as ErrorBag }.isEmpty())
    }

    @Test
    fun `Rules unique ignore and exists builders`() {
        assertEquals("unique:users", Rules.unique("users").toString())
        assertEquals("unique:users,email", Rules.unique("users", "email").toString())
        assertEquals("unique:users,email,7", Rules.unique("users", "email").ignore(7).toString())
        assertEquals("unique:users,,9", Rules.unique("users").ignore(9).toString())
        assertEquals("unique:t", Rules.unique("t").ignore(null).toString())
        assertEquals("exists:posts", Rules.exists("posts").toString())
        assertEquals("exists:posts,slug", Rules.exists("posts", "slug").toString())
        assertEquals("in:a,b,", Rules.inList("a", "b", null))
        assertEquals("not_in:1,2,", Rules.notIn(1, 2, null))
        // Java @JvmOverloads bridges for unique/exists(table only)...
        assertEquals("unique:users", Rules::class.java.getMethod("unique", String::class.java).invoke(null, "users").toString())
        assertEquals("exists:posts", Rules::class.java.getMethod("exists", String::class.java).invoke(null, "posts").toString())

        facade()
        Validator.setExistenceChecker(ExistenceChecker { _, _, _, exceptId -> exceptId != "7" })
        try {
            val t = Wire.test(UniqueEmailComponent::class.java).call("save")
            assertTrue(t.instance().errorBag().isEmpty())
            assertEquals("unique:users,email,7", (t.instance() as UniqueEmailComponent).rules()["email"]!!.last())
        } finally {
            Validator.setExistenceChecker(null)
        }
    }

    @Test
    fun `Transition annotation and helpers emit effects`() {
        facade()
        val next = Wire.test(TransitionedCounter::class.java).call("next")
        @Suppress("UNCHECKED_CAST")
        val transition = next.effects()["transition"] as Map<String, Any?>
        assertEquals("forward", transition["type"])

        val quiet = Wire.test(TransitionedCounter::class.java).call("quiet")
        @Suppress("UNCHECKED_CAST")
        val skipped = quiet.effects()["transition"] as Map<String, Any?>
        assertEquals(true, skipped["skip"])

        val manual = Wire.test(TransitionedCounter::class.java).call("manualForward")
        @Suppress("UNCHECKED_CAST")
        assertEquals("forward", (manual.effects()["transition"] as Map<String, Any?>)["type"])

        val skip = Wire.test(TransitionedCounter::class.java).call("manualSkip")
        @Suppress("UNCHECKED_CAST")
        assertEquals(true, (skip.effects()["transition"] as Map<String, Any?>)["skip"])

        val c = Wire.test(TransitionedCounter::class.java).instance() as TransitionedCounter
        c.transition()
        c.transition(null, true)
        c.transition("back", true)
        c.javaClass.getMethod("transition").invoke(c)
        c.javaClass.getMethod("transition", String::class.java).invoke(c, "sideways")
        c.javaClass.getMethod("transition", String::class.java, Boolean::class.javaPrimitiveType)
            .invoke(c, "both", true)
        assertEquals("both", c.state().store["transitionType"])
        assertEquals(true, c.state().store["transitionSkip"])
    }
}
