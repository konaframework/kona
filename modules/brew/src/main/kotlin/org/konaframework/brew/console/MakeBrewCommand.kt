package org.konaframework.brew.console

import org.konaframework.brew.Brew
import org.konaframework.brew.ComponentRegistry
import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind make:brew posts.create-post` — scaffolds a component (`make:livewire`).
 *
 * Default type comes from `brew.make.type` (`sfc` | `class` | `mfc`). Flags: `--class`, `--mfc`,
 * `--test`, `--emoji`, `--lang`, namespaces (`pages::`, `admin::`).
 *
 * Names that look like a todo list (`Todos`, `todo-list`, …) get a todos-shaped stub (class + Crema
 * view by default). Other names keep the counter-shaped stub unless `--class` / `--mfc` is set.
 */
class MakeBrewCommand : ConsoleCommand() {
    override val signature: String =
        "make:brew {name : Component name (posts.create-post, CreatePost, pages::dashboard)} " +
            "{--class : Separate Crema view file} {--mfc : Multi-file component assets} " +
            "{--test : Generate a test} {--emoji : Prefix the view name with ⚡} " +
            "{--lang= : kotlin or java} {--base= : Project root} {--force : Overwrite existing files}"

    override val description: String = "Create a new Brew component"

    override fun handle(): Int {
        val raw = argumentOrFail("name").replace('/', '.')
        if (!NAME.matches(raw)) {
            error("Invalid component name [$raw]: use names like counter, posts.create-post, brew/todos or CreatePost")
            return FAILURE
        }
        val name = normalize(raw)
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val lang = option("lang") ?: detectLang(base)
        if (lang != "kotlin" && lang != "java") {
            error("Unknown --lang [$lang]; expected kotlin or java")
            return FAILURE
        }
        val segments = name.substringAfter("::").split('.')
        val namespace = name.substringBefore("::", "").replace('-', '_')
        val packageParts = listOf("app", "brew") + listOfNotNull(namespace.ifEmpty { null }) +
            segments.dropLast(1).map { it.replace('-', '_') }
        val pkg = packageParts.joinToString(".")
        val className = ComponentRegistry.studly(segments.last())
        val shape = stubShape(className, name)
        // Todos stubs always use a separate Crema view (parity with the kit / samples).
        val type = if (shape == StubShape.TODOS && !optionBool("mfc")) "class" else resolveType()
        val separate = type == "class" || type == "mfc"
        val emoji = optionBool("emoji")
        val viewLeaf = name.replace("::", ".")
        val viewName = if (emoji) {
            val parts = viewLeaf.split('.')
            "brew." + (parts.dropLast(1) + ("⚡" + parts.last())).joinToString(".")
        } else {
            "brew.$viewLeaf"
        }
        val ext = if (lang == "java") "java" else "kt"
        val root = if (lang == "java") "src/main/java" else "src/main/kotlin"
        val classRel = "$root/${pkg.replace('.', '/')}/$className.$ext"
        val files = linkedMapOf(
            classRel to componentSource(base, lang, pkg, className, separate, viewName, shape),
        )
        if (separate) {
            val viewStub = if (shape == StubShape.TODOS) TODOS_VIEW else VIEW
            files["src/main/resources/views/${viewName.replace('.', '/')}.crema.html"] =
                BrewStubsCommand.load(base, if (shape == StubShape.TODOS) "todos.view.crema.html.stub" else "view.crema.html.stub", viewStub)
        }
        if (optionBool("test")) {
            val testRoot = if (lang == "java") "src/test/java" else "src/test/kotlin"
            val stub = if (lang == "java") "test.java.stub" else "test.kt.stub"
            val fallback = testSource(lang, pkg, className, name, shape)
            files["$testRoot/${pkg.replace('.', '/')}/${className}Test.$ext"] =
                BrewStubsCommand.load(base, stub, fallback)
                    .replace("{{package}}", pkg)
                    .replace("{{class}}", className)
                    .replace("{{name}}", name)
        }
        val force = optionBool("force")
        for ((relative, _) in files) {
            if (!force && Files.exists(base.resolve(relative))) {
                error("File already exists: $relative (use --force to overwrite)")
                return FAILURE
            }
        }
        for ((relative, content) in files) {
            val dest = base.resolve(relative)
            Files.createDirectories(dest.parent)
            Files.writeString(dest, content)
            success("Created $relative")
        }
        if (type == "mfc") {
            val mfcDir = base.resolve(classRel).parent.resolve(className.lowercase())
            Files.createDirectories(mfcDir)
            Files.writeString(mfcDir.resolve("$className.js"), "export default function (\$wire) {}\n")
            Files.writeString(mfcDir.resolve("$className.css"), "/* $className */\n")
            success("Created MFC assets under $mfcDir")
        }
        info("Render it with <brew:$name /> or @brew('$name')")
        return SUCCESS
    }

    private fun resolveType(): String {
        if (optionBool("mfc")) return "mfc"
        if (optionBool("class")) return "class"
        val configured = try {
            Brew.manager().config.makeType.lowercase()
        } catch (_: Exception) {
            "sfc"
        }
        return if (configured in setOf("sfc", "class", "mfc")) configured else "sfc"
    }

    private fun normalize(raw: String): String {
        if (raw.contains("::") || raw.contains('.') || raw.contains('-') || raw.first().isLowerCase()) return raw
        return ComponentRegistry.kebab(raw)
    }

    private fun detectLang(base: Path): String {
        val java = base.resolve("src/main/java")
        if (!Files.isDirectory(java)) return "kotlin"
        return Files.walk(java).use { s -> if (s.anyMatch { it.toString().endsWith(".java") }) "java" else "kotlin" }
    }

    private fun componentSource(
        base: Path,
        lang: String,
        pkg: String,
        name: String,
        separate: Boolean,
        view: String,
        shape: StubShape,
    ): String {
        val stubName = when {
            shape == StubShape.TODOS && lang == "java" -> "todos.component.java.stub"
            shape == StubShape.TODOS -> "todos.component.kt.stub"
            lang == "java" -> "component.java.stub"
            else -> "component.kt.stub"
        }
        val fallback = defaultComponent(lang, pkg, name, separate, view, shape)
        val loaded = BrewStubsCommand.load(base, stubName, fallback)
            .replace("{{package}}", pkg)
            .replace("{{class}}", name)
            .replace("{{view}}", view)
        // Prefer the stub when its shape matches the requested type; otherwise use the generated default.
        val stubOk = if (separate) "render()" in loaded || "Todo" in loaded else "template()" in loaded
        return if (stubOk) loaded else fallback
    }

    private fun defaultComponent(
        lang: String,
        pkg: String,
        name: String,
        separate: Boolean,
        view: String,
        shape: StubShape,
    ): String =
        when (shape) {
            StubShape.TODOS -> todosComponent(lang, pkg, name, separate, view)
            StubShape.COUNTER -> counterComponent(lang, pkg, name, separate, view)
        }

    private fun counterComponent(lang: String, pkg: String, name: String, separate: Boolean, view: String): String =
        if (lang == "java") {
            if (separate) {
                """
                package $pkg;

                import org.konaframework.brew.Component;

                public class $name extends Component {
                    public int count = 0;

                    public void increment() {
                        count++;
                    }

                    @Override
                    public Object render() {
                        return view("$view");
                    }
                }
                """.trimIndent() + "\n"
            } else {
                """
                package $pkg;

                import org.konaframework.brew.Component;

                public class $name extends Component {
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
            }
        } else if (separate) {
            """
            package $pkg

            import org.konaframework.brew.Component

            class $name : Component() {
                var count = 0

                fun increment() {
                    count++
                }

                override fun render() = view("$view")
            }
            """.trimIndent() + "\n"
        } else {
            """
            package $pkg

            import org.konaframework.brew.Component

            class $name : Component() {
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
        }

    private fun todosComponent(lang: String, pkg: String, name: String, @Suppress("UNUSED_PARAMETER") separate: Boolean, view: String): String =
        if (lang == "java") {
            """
            package $pkg;

            import org.konaframework.brew.Component;
            import org.konaframework.brew.Computed;
            import java.util.ArrayList;
            import java.util.List;

            public class $name extends Component {
                public static class Todo {
                    public String title = "";
                    public boolean done = false;

                    public Todo() {}

                    public Todo(String title, boolean done) {
                        this.title = title;
                        this.done = done;
                    }
                }

                public String draft = "";
                public List<Todo> items = new ArrayList<>(List.of(
                    new Todo("Grind the beans", false),
                    new Todo("Steam the milk", true)
                ));

                public void add() {
                    String title = draft.trim();
                    if (title.isEmpty()) {
                        return;
                    }
                    items.add(new Todo(title, false));
                    draft = "";
                }

                public void remove(int index) {
                    items.remove(index);
                }

                @Computed
                public long remaining() {
                    return items.stream().filter(todo -> !todo.done).count();
                }

                @Override
                public Object render() {
                    return view("$view");
                }
            }
            """.trimIndent() + "\n"
        } else {
            """
            package $pkg

            import org.konaframework.brew.Component
            import org.konaframework.brew.Computed

            data class Todo(var title: String = "", var done: Boolean = false)

            class $name : Component() {
                var draft = ""
                var items = mutableListOf(Todo("Grind the beans"), Todo("Steam the milk", done = true))

                fun add() {
                    val title = draft.trim()
                    if (title.isEmpty()) return
                    items.add(Todo(title))
                    draft = ""
                }

                fun remove(index: Int) {
                    items.removeAt(index)
                }

                @Computed
                fun remaining(): Int = items.count { !it.done }

                override fun render() = view("$view")
            }
            """.trimIndent() + "\n"
        }

    private fun testSource(lang: String, pkg: String, name: String, component: String, shape: StubShape): String =
        if (shape == StubShape.TODOS) {
            if (lang == "java") {
                """
                package $pkg;

                import org.konaframework.brew.Brew;
                import org.junit.jupiter.api.Test;

                class ${name}Test {
                    @Test
                    void renders() {
                        Brew.test($name.class)
                            .set("draft", "Pull espresso")
                            .call("add")
                            .assertSee("Pull espresso");
                    }
                }
                """.trimIndent() + "\n"
            } else {
                """
                package $pkg

                import org.konaframework.brew.Brew
                import org.junit.jupiter.api.Test

                class ${name}Test {
                    @Test
                    fun renders() {
                        Brew.test("$component")
                            .set("draft", "Pull espresso")
                            .call("add")
                            .assertSee("Pull espresso")
                    }
                }
                """.trimIndent() + "\n"
            }
        } else if (lang == "java") {
            """
            package $pkg;

            import org.konaframework.brew.Brew;
            import org.junit.jupiter.api.Test;

            class ${name}Test {
                @Test
                void renders() {
                    Brew.test($name.class).assertSet("count", 0).call("increment").assertSet("count", 1);
                }
            }
            """.trimIndent() + "\n"
        } else {
            """
            package $pkg

            import org.konaframework.brew.Brew
            import org.junit.jupiter.api.Test

            class ${name}Test {
                @Test
                fun renders() {
                    Brew.test("$component").assertSet("count", 0).call("increment").assertSet("count", 1)
                }
            }
            """.trimIndent() + "\n"
        }
}

private enum class StubShape { COUNTER, TODOS }

private fun stubShape(@Suppress("UNUSED_PARAMETER") className: String, normalizedName: String): StubShape {
    // Normalized leaf only — PascalCase names are kebab'd before this runs (`Todos` → `todos`).
    val leaf = normalizedName.substringAfterLast('.').substringAfterLast("::").lowercase().replace('-', '_')
    return if (leaf in TODOS_LEAVES) StubShape.TODOS else StubShape.COUNTER
}

private val TODOS_LEAVES = setOf("todos", "todo", "todo_list", "todolist")

private val NAME = Regex("^([a-z][a-z0-9-]*::)?[A-Za-z][A-Za-z0-9-]*(\\.[A-Za-z][A-Za-z0-9-]*)*$")

private val VIEW = """
    <div>
        <span>{{ count }}</span>
        <button wire:click="increment">+</button>
    </div>
""".trimIndent() + "\n"

private val TODOS_VIEW = """
    <section class="card">
        <h2>Todos <small>({{ remaining }} left)</small></h2>
        <form wire:submit="add">
            <input wire:model="draft" placeholder="What needs doing?">
            <button type="submit">Add</button>
        </form>
        <ul>
        @foreach(items as item)
            <li wire:key="todo-{{ loop.index }}">
                <label>
                    <input type="checkbox" wire:model.live="items.{{ loop.index }}.done">
                    {{ item.title }}
                </label>
                <button wire:click="remove({{ loop.index }})" aria-label="Remove">&times;</button>
            </li>
        @endforeach
        </ul>
    </section>
""".trimIndent() + "\n"
