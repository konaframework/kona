package org.konaframework.brew.console

import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind brew:stubs` — publish customizable component stubs (`livewire:stubs`).
 * After publishing, `make:brew` prefers files under `stubs/brew/` when present.
 */
class BrewStubsCommand : ConsoleCommand() {
    override val signature: String =
        "brew:stubs {--base= : Project root} {--force : Overwrite existing stubs}"

    override val description: String = "Publish Brew component stubs for make:brew customization"

    override fun handle(): Int {
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val force = optionBool("force")
        val root = base.resolve("stubs/brew")
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
                error("Stub already exists: stubs/brew/$name (use --force)")
                return FAILURE
            }
            Files.createDirectories(dest.parent)
            Files.writeString(dest, content)
            success("Published stubs/brew/$name")
        }
        return SUCCESS
    }

    companion object {
        /** Resolve a stub from the project [base], falling back to the built-in default. */
        fun load(base: Path, name: String, fallback: String): String {
            val custom = base.resolve("stubs/brew/$name")
            if (Files.isRegularFile(custom)) return Files.readString(custom)
            return fallback
        }
    }
}

private val STUB_KT = """
    package {{package}}

    import org.konaframework.brew.Component

    class {{class}} : Component() {
        var count = 0

        fun increment() {
            count++
        }

        override fun template() = ""${'"'}
            <div>
                <span>{{ count }}</span>
                <button wire:click="increment">+</button>
            </div>
        ""${'"'}
    }
""".trimIndent() + "\n"

private val STUB_JAVA = """
    package {{package}};

    import org.konaframework.brew.Component;

    public class {{class}} extends Component {
        public int count = 0;

        public void increment() {
            count++;
        }

        @Override
        public String template() {
            return ""${'"'}
                <div>
                    <span>{{ count }}</span>
                    <button wire:click="increment">+</button>
                </div>
                ""${'"'};
        }
    }
""".trimIndent() + "\n"

private val STUB_VIEW = """
    <div>
        <span>{{ count }}</span>
        <button wire:click="increment">+</button>
    </div>
""".trimIndent() + "\n"

private val STUB_TEST_KT = """
    package {{package}}

    import org.konaframework.brew.Brew
    import org.junit.jupiter.api.Test

    class {{class}}Test {
        @Test
        fun renders() {
            Brew.test("{{name}}").assertSet("count", 0).call("increment").assertSet("count", 1)
        }
    }
""".trimIndent() + "\n"

private val STUB_TEST_JAVA = """
    package {{package}};

    import org.konaframework.brew.Brew;
    import org.junit.jupiter.api.Test;

    class {{class}}Test {
        @Test
        void renders() {
            Brew.test({{class}}.class).assertSet("count", 0).call("increment").assertSet("count", 1);
        }
    }
""".trimIndent() + "\n"
