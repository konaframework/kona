package org.konaframework.brew

import org.konaframework.brew.console.MakeBrewCommand
import org.konaframework.grind.CommandExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MakeBrewCommandTest {
    private fun run(dir: Path, vararg args: String): Int =
        CommandExecutor(dir).run(MakeBrewCommand(), args.toList() + "--base=$dir")

    @Test
    fun `kotlin single-file component`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "counter"))
        val file = dir.resolve("src/main/kotlin/app/brew/Counter.kt")
        val source = Files.readString(file)
        assertTrue(source.contains("package app.brew"))
        assertTrue(source.contains("override fun template() = \"\"\""))
        assertEquals(1, run(dir, "counter"))
        assertEquals(0, run(dir, "counter", "--force"))
    }

    @Test
    fun `kotlin class component with view and test`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "posts.create-post", "--class", "--test"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/brew/posts/CreatePost.kt"))
        assertTrue(source.contains("override fun render() = view(\"brew.posts.create-post\")"), source)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/posts/create-post.crema.html")))
        val test = Files.readString(dir.resolve("src/test/kotlin/app/brew/posts/CreatePostTest.kt"))
        assertTrue(test.contains("Brew.test(\"posts.create-post\")"))
    }

    @Test
    fun `java components and namespaces`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "admin-area::UserList", "--lang=java", "--test"))
        val source = Files.readString(dir.resolve("src/main/java/app/brew/admin_area/UserList.java"))
        assertTrue(source.contains("public String template()"))
        assertTrue(source.contains("\"\"\""))
        assertTrue(Files.exists(dir.resolve("src/test/java/app/brew/admin_area/UserListTest.java")))
        assertEquals(0, run(dir, "Dashboard", "--lang=java", "--class"))
        val dash = Files.readString(dir.resolve("src/main/java/app/brew/Dashboard.java"))
        assertTrue(dash.contains("return view(\"brew.dashboard\");"), dash)
    }

    @Test
    fun `language detection and validation`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("src/main/java/app"))
        assertEquals(0, run(dir, "empty-java"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/brew/EmptyJava.kt")))
        Files.writeString(dir.resolve("src/main/java/app/App.java"), "class App {}")
        assertEquals(0, run(dir, "ShowPosts"))
        assertTrue(Files.exists(dir.resolve("src/main/java/app/brew/ShowPosts.java")))
        assertEquals(1, run(dir, "bad name!"))
        assertEquals(1, run(dir, "ok", "--lang=scala"))
        assertFalse(Files.exists(dir.resolve("src/main/scala")))
    }

    @Test
    fun `todos-shaped names get todos stubs`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "Todos", "--test"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/brew/Todos.kt"))
        assertTrue(source.contains("var draft"), source)
        assertTrue(source.contains("fun remaining()"), source)
        assertTrue(source.contains("override fun render() = view(\"brew.todos\")"), source)
        assertFalse(source.contains("var count"), source)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/todos.crema.html")))
        val view = Files.readString(dir.resolve("src/main/resources/views/brew/todos.crema.html"))
        assertTrue(view.contains("wire:submit=\"add\""), view)
        val test = Files.readString(dir.resolve("src/test/kotlin/app/brew/TodosTest.kt"))
        assertTrue(test.contains("call(\"add\")"), test)

        assertEquals(0, run(dir, "todo-list", "--lang=java", "--force", "--test"))
        val java = Files.readString(dir.resolve("src/main/java/app/brew/TodoList.java"))
        assertTrue(java.contains("class Todo"), java)
        assertTrue(java.contains("remaining()"), java)
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/todo-list.crema.html")))
        assertTrue(Files.exists(dir.resolve("src/test/java/app/brew/TodoListTest.java")))

        assertEquals(0, run(dir, "todo", "--lang=kotlin", "--force"))
        assertTrue(Files.readString(dir.resolve("src/main/kotlin/app/brew/Todo.kt")).contains("var draft"))
        assertEquals(0, run(dir, "todolist", "--lang=kotlin", "--force"))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/todolist.crema.html")))

        // --mfc keeps MFC type even for todos-shaped names (skips the forced class path).
        assertEquals(0, run(dir, "Todos", "--lang=kotlin", "--mfc", "--force"))
        assertTrue(Files.isDirectory(dir.resolve("src/main/kotlin/app/brew/todos")))
    }

    @Test
    fun `slash path names normalize like dotted`(@TempDir dir: Path) {
        assertEquals(0, run(dir, "posts/create-post", "--class"))
        assertTrue(Files.exists(dir.resolve("src/main/kotlin/app/brew/posts/CreatePost.kt")))
        assertTrue(Files.exists(dir.resolve("src/main/resources/views/brew/posts/create-post.crema.html")))
    }

    @Test
    fun `mismatched custom stub falls back to generated default`(@TempDir dir: Path) {
        val stubs = dir.resolve("stubs/brew")
        Files.createDirectories(stubs)
        Files.writeString(stubs.resolve("component.kt.stub"), "package {{package}}\n\nclass {{class}}\n")
        assertEquals(0, run(dir, "counter"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/brew/Counter.kt"))
        assertTrue(source.contains("override fun template()"), source)
        assertTrue(source.contains("var count"), source)
    }

    @Test
    fun `todos custom stub without render still accepted via Todo marker`(@TempDir dir: Path) {
        val stubs = dir.resolve("stubs/brew")
        Files.createDirectories(stubs)
        Files.writeString(
            stubs.resolve("todos.component.kt.stub"),
            """
            package {{package}}

            import org.konaframework.brew.Component

            class Todo
            class {{class}} : Component() {
                override fun template() = "<div></div>"
            }
            """.trimIndent() + "\n",
        )
        assertEquals(0, run(dir, "Todos", "--lang=kotlin"))
        val source = Files.readString(dir.resolve("src/main/kotlin/app/brew/Todos.kt"))
        assertTrue(source.contains("class Todo"), source)
        assertTrue(source.contains("override fun template()"), source)
        assertFalse(source.contains("override fun render()"), source)
    }
}
