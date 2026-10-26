package org.konaframework.wire

import org.konaframework.wire.console.MakeFormCommand
import org.konaframework.grind.CommandExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MakeFormCommandTest {
    private fun run(dir: Path, vararg args: String): Int =
        CommandExecutor(dir).run(MakeFormCommand(), args.toList() + "--base=$dir")

    @Test
    fun `scaffolds kotlin and java form objects`(@TempDir dir: Path) {
        assertEquals(1, run(dir, "post.form"))
        assertEquals(1, run(dir, "PostForm", "--lang=ruby"))
        assertEquals(0, run(dir, "post-form"))
        val kotlin = dir.resolve("src/main/kotlin/app/wire/forms/PostForm.kt")
        val source = Files.readString(kotlin)
        assertTrue(source.contains("package app.wire.forms"))
        assertTrue(source.contains("class PostForm : Form()"))
        assertTrue(source.contains("@Validate(\"required\")"))
        assertEquals(1, run(dir, "PostForm"))
        assertEquals(0, run(dir, "PostForm", "--force"))
        assertTrue(Files.readString(kotlin).contains("class PostForm"))

        assertEquals(0, run(dir, "CommentForm", "--lang=java"))
        val java = Files.readString(dir.resolve("src/main/java/app/wire/forms/CommentForm.java"))
        assertTrue(java.contains("public class CommentForm extends Form"))
        assertTrue(java.contains("@Validate({\"required\"})"))

        val detected = dir.resolve("detected")
        Files.createDirectories(detected.resolve("src/main/java/app"))
        Files.writeString(detected.resolve("src/main/java/app/App.java"), "class App {}\n")
        assertEquals(0, run(detected, "DetectedForm"))
        assertTrue(Files.exists(detected.resolve("src/main/java/app/wire/forms/DetectedForm.java")))

        val emptyJava = dir.resolve("empty-java")
        Files.createDirectories(emptyJava.resolve("src/main/java"))
        assertEquals(0, run(emptyJava, "StillKotlin"))
        assertTrue(Files.exists(emptyJava.resolve("src/main/kotlin/app/wire/forms/StillKotlin.kt")))
    }
}
