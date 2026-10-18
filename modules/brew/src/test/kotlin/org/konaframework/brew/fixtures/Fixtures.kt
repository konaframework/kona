package org.konaframework.brew.fixtures

import org.konaframework.brew.BrewView
import org.konaframework.brew.Component
import org.konaframework.brew.Computed
import org.konaframework.brew.Locked
import org.konaframework.brew.Renderless
import org.konaframework.brew.Wireable
import org.konaframework.brew.lifecycle.StopPropagation
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class Counter : Component() {
    var count = 0

    fun increment() {
        count++
    }

    fun add(by: Int) {
        count += by
    }

    fun add(by: Int, times: Int) {
        count += by * times
    }

    fun double(): Int = count * 2

    @Renderless
    fun quiet() {
        count++
    }

    fun skip() {
        count++
        skipRender()
    }

    fun skipWith() {
        skipRender("<div>skipped</div>")
    }

    override fun template() = """<div><span>Count: {{ count }}</span></div>"""
}

class Greeter : Component() {
    var name: String = "World"
    var title: String? = null

    fun mount(name: String = "World") {
        this.name = name.uppercase()
    }

    override fun render(): Any = inline("<p>Hello {{ name }}{{ suffix }}</p>", mapOf("suffix" to "!"))
}

enum class Status { DRAFT, PUBLISHED }

data class Address(val street: String = "", val city: String = "")

class Money(val cents: Long) : Wireable {
    override fun toBrew(): Any = cents

    companion object {
        @JvmStatic
        fun fromBrew(value: Any?): Money = Money((value as Number).toLong())
    }
}

class Kitchen : Component() {
    var text = "a"
    var number = 1
    var decimal = 1.5
    var flag = false
    var nullable: Int? = null
    var tags = mutableListOf("x", "y")
    var matrix = listOf(listOf(1, 2), listOf(3))
    var lookup = linkedMapOf("a" to 1)
    var unique = linkedSetOf("u")
    var status = Status.DRAFT
    var date: LocalDate = LocalDate.of(2026, 1, 2)
    var price = BigDecimal("9.99")
    var big = 9_007_199_254_740_993L
    var id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    var address = Address("Main", "Kona")
    var addresses = mutableListOf(Address("A", "B"))
    var money = Money(250)
    var array = arrayOf("p", "q")

    @Locked
    var secret = "s"

    val readOnly = "r"

    override fun template() = "<div>{{ text }}</div>"
}

class Calculator : Component() {
    var a = 2
    var b = 3
    var calls = 0

    @Computed
    fun sum(): Int {
        calls++
        return a + b
    }

    @Computed(persist = true)
    fun persisted(): Int {
        calls++
        return a * 10
    }

    @Computed(cache = true, key = "calc-shared", seconds = 60)
    fun shared(): Int {
        calls++
        return 42
    }

    @Computed(cache = true)
    fun sharedDefaultKey(): String = "dk"

    fun forget() {
        forgetComputed("sum")
    }

    fun readSum(): Int = computed<Int>("sum") + computed<Int>("sum")

    override fun template() = "<div>{{ sum }}-{{ \$this.a }}</div>"
}

interface HasAudit

class Lifecycle : Component(), HasAudit {
    var log = mutableListOf<String>()
    var name = ""
    var items = mutableListOf(mutableMapOf("title" to "t"))
    var swallow = false

    fun boot() {
        log += "boot"
    }

    fun bootHasAudit() {
        log += "bootHasAudit"
    }

    fun initializeHasAudit() {
        log += "initializeHasAudit"
    }

    fun mount() {
        log += "mount"
    }

    fun mountHasAudit() {
        log += "mountHasAudit"
    }

    fun booted() {
        log += "booted"
    }

    fun bootedHasAudit() {
        log += "bootedHasAudit"
    }

    fun hydrate() {
        log += "hydrate"
    }

    fun hydrateHasAudit() {
        log += "hydrateHasAudit"
    }

    fun hydrateName(value: String) {
        log += "hydrateName:$value"
    }

    fun dehydrate() {
        log += "dehydrate"
    }

    fun dehydrateHasAudit() {
        log += "dehydrateHasAudit"
    }

    fun dehydrateName(value: String) {
        log += "dehydrateName:$value"
    }

    fun updating(path: String, value: Any?) {
        log += "updating:$path=$value"
    }

    fun updatingHasAudit(path: String) {
        log += "updatingHasAudit:$path"
    }

    fun updatingName(value: String) {
        log += "updatingName:$value"
    }

    fun updated(path: String) {
        log += "updated:$path"
    }

    fun updatedHasAudit(path: String) {
        log += "updatedHasAudit:$path"
    }

    fun updatedName(value: String) {
        log += "updatedName:$value"
    }

    fun updatingItems(value: Any?, key: String?) {
        log += "updatingItems:$key"
    }

    fun updatedItems(value: Any?, key: String?) {
        log += "updatedItems:$key"
    }

    fun updatedItems0Title(value: Any?, key: String?) {
        log += "updatedItems0Title:$key"
    }

    fun rendering(view: BrewView, data: Map<String, Any?>) {
        log += "rendering"
    }

    fun renderingHasAudit() {
        log += "renderingHasAudit"
    }

    fun rendered(view: BrewView, html: String) {
        log += "rendered"
    }

    fun renderedHasAudit() {
        log += "renderedHasAudit"
    }

    fun exception(e: Throwable, stop: StopPropagation) {
        log += "exception:${e.message}"
        if (swallow) stop()
    }

    fun exceptionHasAudit(e: Throwable) {
        log += "exceptionHasAudit"
    }

    fun fail() {
        throw IllegalStateException("boom")
    }

    override fun template() = "<div>{{ name }}</div>"
}

class Clock {
    fun now() = "tick"
}

class Injected : Component() {
    var result = ""

    fun run(label: String, clock: Clock) {
        result = label + clock.now()
    }

    override fun template() = "<div>{{ result }}</div>"
}

class Conventional : Component()

class TwoRoots : Component() {
    override fun template() = "<div>a</div><div>b</div>"
}

class Utilities : Component() {
    var a = 1
    var b = "two"
    var c = mutableListOf(1)

    fun mutate() {
        a = 9
        b = "changed"
        c.add(2)
    }

    fun resetAll() = reset()

    fun resetA() = reset("a")

    fun resetExceptA() = resetExcept("a")

    fun pullB(): Any? = pull("b")

    fun pullAll(): Map<String, Any?> = pull()

    fun pullMany(): Map<String, Any?> = pull("a", "b")

    fun onlyA(): Map<String, Any?> = only("a")

    fun fillMap() = fill(mapOf("a" to 5, "b" to "filled", "missing" to 1))

    override fun template() = "<div>{{ a }}</div>"
}
