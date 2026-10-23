package org.konaframework.wire.console

import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind wire:stubs` — publish customizable component stubs (`livewire:stubs`).
 * After publishing, `make:wire` prefers files under `stubs/wire/` when present.
 */
open class WireStubsCommand : ConsoleCommand() {
    protected open val commandToken: String = "wire:stubs"
    protected open val deprecationHint: String? = null

    override val signature: String
        get() = "$commandToken {--base= : Project root} {--force : Overwrite existing stubs}"

    override val description: String = "Publish Wire component stubs for make:wire customization"

    override fun handle(): Int {
        deprecationHint?.let { warn(it) }
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val force = optionBool("force")
        val root = base.resolve("stubs/wire")
        val files = linkedMapOf(
            "component.kt.stub" to STUB_KT,
            "component.java.stub" to STUB_JAVA,
            "view.crema.html.stub" to STUB_VIEW,
            "test.kt.stub" to STUB_TEST_KT,
            "test.java.stub" to STUB_TEST_JAVA,
        )
        for ((name, content) in files) {
            val dest = root.resolve(name)
            if (!force && Files.exists(dest)) {
                error("Stub already exists: stubs/wire/$name (use --force)")
                return FAILURE
            }
            Files.createDirectories(dest.parent)
            Files.writeString(dest, content)
            success("Published stubs/wire/$name")
        }
        return SUCCESS
    }

    companion object {
        /** Resolve a stub from the project [base], falling back to the built-in default. */
        fun load(base: Path, name: String, fallback: String): String {
            val custom = base.resolve("stubs/wire/$name")
            if (Files.isRegularFile(custom)) return Files.readString(custom)
            return fallback
        }
    }
}

private val STUB_KT = """
    package {{package}}

    import org.konaframework.wire.Component

    class {{class}} : Component() {
        override fun render() = view("{{view}}")
    }
""".trimIndent() + "\n"

private val STUB_JAVA = """
    package {{package}};

    import org.konaframework.wire.Component;

    public class {{class}} extends Component {
        @Override
        public Object render() {
            return view("{{view}}");
        }
    }
""".trimIndent() + "\n"

private val STUB_VIEW = """
    <p>{{quote}}</p>
""".trimIndent() + "\n"

private val STUB_TEST_KT = """
    package {{package}}

    import org.konaframework.wire.Wire
    import org.junit.jupiter.api.Test

    class {{class}}Test {
        @Test
        fun renders() {
            Wire.test("{{name}}").assertSee("<p>")
        }
    }
""".trimIndent() + "\n"

private val STUB_TEST_JAVA = """
    package {{package}};

    import org.konaframework.wire.Wire;
    import org.junit.jupiter.api.Test;

    class {{class}}Test {
        @Test
        void renders() {
            Wire.test({{class}}.class).assertSee("<p>");
        }
    }
""".trimIndent() + "\n"
