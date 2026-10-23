package org.konaframework.wire

import org.konaframework.wire.fixtures.EventBus
import org.konaframework.crema.CremaContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptedBus : Component() {
    var title: String = "t"

    override fun template() = """
        <div>
            <span x-data="{ title: @entangle('title') }"></span>
            <span>@this</span>
            @script
            console.log('hi')
            @endscript
            @assets
            <link rel="stylesheet" href="/a.css">
            @endassets
        </div>
    """.trimIndent()
}

class CremaDirectivesTest {
    private val manager = brew()
        .component("event-bus", EventBus::class.java)
        .component("scripted-bus", ScriptedBus::class.java)

    @AfterEach
    fun reset() {
        Wire.reset()
    }

    @Test
    fun `entangle this script and assets directives`() {
        Wire.setManager(manager)
        val engine = manager.views()

        assertEquals("\$wire.\$entangle('title')", engine.renderSource("@entangle('title')", CremaContext()))
        assertEquals("\$wire.\$entangle('name')", engine.renderSource("@entangle(\"name\")", CremaContext()))
        assertEquals("\$wire.\$entangle('')", engine.renderSource("@entangle()", CremaContext()))
        assertEquals("\$wire", engine.renderSource("@this", CremaContext()))

        val mounted = Wire.test(ScriptedBus::class.java)
        @Suppress("UNCHECKED_CAST")
        val scripts = mounted.effects()["scripts"] as? List<*>
        @Suppress("UNCHECKED_CAST")
        val assets = mounted.effects()["assets"] as? List<*>
        assertTrue(scripts != null && scripts.any { it.toString().contains("console.log") }, "$scripts")
        assertTrue(assets != null && assets.any { it.toString().contains("a.css") }, "$assets")

        // empty bodies / no current component...
        assertEquals("", engine.renderSource("@script\n\n@endscript", CremaContext()).trim())
        assertEquals("", engine.renderSource("@assets\n@endassets", CremaContext()).trim())
        assertEquals("", engine.renderSource("@script\nx\n@endscript", CremaContext()).trim())
    }
}
