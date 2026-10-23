package org.konaframework.wire

import org.konaframework.wire.console.FUNNY_QUOTES
import org.konaframework.wire.console.MakeWireCommand
import org.konaframework.grind.CommandExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MakeWireCommandTest {
    @BeforeEach
    fun resetBrew() {
        Wire.reset()
    }

    private fun run(dir: Path, vararg args: String): Int =
        CommandExecutor(dir).run(MakeWireCommand(), args.toList() + "--base=$dir")

    @Test
    fun `kotlin class component with separate view by default`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "counter"))
        val file = dir.resolve("src/main/kotlin/app/wire/Counter.kt")
        val source = Files.readString(file)
        assertTrue(source.contains("package app.wire"))
        assertTrue(source.contains("override fun render() = view(\"wire.counter\")"), source)
        assertFalse(source.contains("var count"), source)
        assertFalse(source.contains("template()"), source)
        val view = Files.readString(dir.resolve("src/main/resources/views/wire/counter.crema.html"))
        assertTrue(view.startsWith("<p>"), view)
        assertTrue(view.trimEnd().endsWith("</p>"), view)
        assertTrue(FUNNY_QUOTES.any { it in view }, view)
        assertEquals(1, run(dir, "counter"))
        assertEquals(0, run(dir, "counter", "--force"))
    }

    @Test
    fun `kotlin inline single-file component`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "spark", "--inline"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/Spark.kt"))
        assertTrue(source.contains("override fun template() = \"\"\""), source)
        assertTrue(source.contains("<p>"), source)
        assertTrue(FUNNY_QUOTES.any { it in source }, source)
        assertFalse(Files.exists(dir.resolve("src/main/resources/views/wire/spark.crema.html")))
    }

    @Test
    fun `configured brew make type sfc matches inline`(@TempDir dir: Path) {
        Wire.setManager(brew(config = WireConfig(key = "t", makeType = "sfc")))
        assertEquals(0, run(dir, "ConfiguredSfc"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/ConfiguredSfc.kt"))
        assertTrue(source.contains("override fun template()"), source)
        assertFalse(Files.exists(dir.resolve("src/main/resources/views/wire/configured-sfc.crema.html")))
    }

    @Test
    fun `kotlin class component with view and test`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "posts.create-post", "--test"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/posts/CreatePost.kt"))
        assertTrue(source.contains("override fun render() = view(\"wire.posts.create-post\")"), source)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/posts/create-post.crema.html")))
        val test = Files.readString(dir.resolve("src/test/kotlin/app/wire/posts/CreatePostTest.kt"))
        assertTrue(test.contains("Wire.test(\"posts.create-post\")"))
        assertTrue(test.contains("assertSee(\"<p>\")"), test)
    }

    @Test
    fun `java components and namespaces`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "admin-area::UserList", "--lang=java", "--test"))
        val source = Files.readString(dir.resolve("src/main/java/app/wire/admin_area/UserList.java"))
        assertTrue(source.contains("public Object render()"), source)
        assertTrue(source.contains("return view(\"wire.admin-area.UserList\");"), source)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/admin-area/UserList.crema.html")))
        assertTrue(Files.exists(dir.resolve("src/test/java/app/wire/admin_area/UserListTest.java")))
        assertEquals(0, run(dir, "Dashboard", "--lang=java", "--inline"))
        val dash = Files.readString(dir.resolve("src/main/java/app/wire/Dashboard.java"))
        assertTrue(dash.contains("public String template()"), dash)
        assertTrue(dash.contains("<p>"), dash)
    }

    @Test
    fun `language detection and validation`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("src/main/java/app"))
        assertEquals(0, run(dir, "empty-java"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/wire/EmptyJava.kt")))
        Files.writeString(dir.resolve("src/main/java/app/App.java"), "class App {}")
        assertEquals(0, run(dir, "ShowPosts"))
        assertTrue(Files.exists(dir.resolve("src/main/java/app/wire/ShowPosts.java")))
        assertEquals(1, run(dir, "bad name!"))
        assertEquals(1, run(dir, "ok", "--lang=scala"))
        assertFalse(Files.exists(dir.resolve("src/main/scala")))
    }

    @Test
    fun `todos-shaped names get todos stubs`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "Todos", "--test"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/Todos.kt"))
        assertTrue(source.contains("var draft"), source)
        assertTrue(source.contains("fun remaining()"), source)
        assertTrue(source.contains("override fun render() = view(\"wire.todos\")"), source)
        assertFalse(source.contains("var count"), source)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/todos.crema.html")))
        val view = Files.readString(dir.resolve("src/main/resources/views/wire/todos.crema.html"))
        assertTrue(view.contains("wire:submit=\"add\""), view)
        val test = Files.readString(dir.resolve("src/test/kotlin/app/wire/TodosTest.kt"))
        assertTrue(test.contains("call(\"add\")"), test)

        assertEquals(0, run(dir, "todo-list", "--lang=java", "--force", "--test"))
        val java = Files.readString(dir.resolve("src/main/java/app/wire/TodoList.java"))
        assertTrue(java.contains("class Todo"), java)
        assertTrue(java.contains("remaining()"), java)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/todo-list.crema.html")))
        assertTrue(Files.exists(dir.resolve("src/test/java/app/wire/TodoListTest.java")))

        assertEquals(0, run(dir, "todo", "--lang=kotlin", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/kotlin/app/wire/Todo.kt")).contains("var draft"))
        assertEquals(0, run(dir, "todolist", "--lang=kotlin", "--force"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/todolist.crema.html")))

        // --mfc keeps MFC type even for todos-shaped names (skips the forced class path).
        assertEquals(0, run(dir, "Todos", "--lang=kotlin", "--mfc", "--force"))
        assertTrue(Files.isDirectory(dir.resolve("src/main/kotlin/app/wire/todos")))
    }

    @Test
    fun `slash path names normalize like dotted`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "posts/create-post"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/wire/posts/CreatePost.kt")))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/posts/create-post.crema.html")))
    }

    @Test
    fun `mismatched custom stub falls back to generated default`(@TempDir dir: Path) {
        val stubs = dir.resolve("stubs/wire")
        Files.createDirectories(stubs)
        Files.writeString(stubs.resolve("component.kt.stub"), "package {{package}}\n\nclass {{class}}\n")
        assertEquals(0, run(dir, "counter"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/Counter.kt"))
        assertTrue(source.contains("override fun render()"), source)
        assertFalse(source.contains("var count"), source)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/wire/counter.crema.html")))
    }

    @Test
    fun `inline with mismatched stub falls back to template`(@TempDir dir: Path) {
        val stubs = dir.resolve("stubs/wire")
        Files.createDirectories(stubs)
        Files.writeString(
            stubs.resolve("component.kt.stub"),
            "package {{package}}\n\nclass {{class}} : org.konaframework.wire.Component() { override fun render() = view(\"{{view}}\") }\n",
        )
        assertEquals(0, run(dir, "inline-me", "--inline"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/InlineMe.kt"))
        assertTrue(source.contains("override fun template()"), source)
        assertTrue(source.contains("<p>"), source)
    }

    @Test
    fun `todos custom stub without render still accepted via Todo marker`(@TempDir dir: Path) {
        val stubs = dir.resolve("stubs/wire")
        Files.createDirectories(stubs)
        Files.writeString(
            stubs.resolve("todos.component.kt.stub"),
            """
            package {{package}}

            import org.konaframework.wire.Component

            class Todo
            class {{class}} : Component() {
                override fun template() = "<div></div>"
            }
            """.trimIndent() + "\n",
        )
        assertEquals(0, run(dir, "Todos", "--lang=kotlin"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/wire/Todos.kt"))
        assertTrue(source.contains("class Todo"), source)
        assertTrue(source.contains("override fun template()"), source)
        assertFalse(source.contains("override fun render()"), source)
    }
}
