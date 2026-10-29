package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FireflyRelationManagerCommandTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun kotlinManagerWithAssociateAndSoftDeletes() {
        val base = temp.resolve("kt")
        Files.createDirectories(base)
        val args = listOf("ProjectResource", "tasks", "title", "--associate", "--soft-deletes", "--base=$base", "--lang=kotlin")
        assertEquals(0, MakeFireflyRelationManagerCommand().run(args))
        val source = Files.readString(base.resolve("src/main/kotlin/app/panels/admin/resources/project/relations/TasksRelationManager.kt"))
        assertTrue(source.contains("package app.panels.admin.resources.project.relations"))
        assertTrue(source.contains("class TasksRelationManager : RelationManager(\"tasks\")"))
        assertTrue(source.contains("recordTitleAttribute(\"title\")"))
        assertTrue(source.contains("TextInput.make(\"title\").required()"))
        assertTrue(source.contains("headerActions(RelationAction.create(), RelationAction.associate())"))
        assertTrue(
            source.contains(
                "actions(RelationAction.view(), RelationAction.edit(), RelationAction.dissociate(), RelationAction.delete(), " +
                    "RelationAction.restore(), RelationAction.forceDelete())",
            ),
        )
        assertTrue(
            source.contains(
                "BulkActionGroup.make(DissociateBulkAction.make(), RelationBulkAction.delete(), RestoreBulkAction.make(), ForceDeleteBulkAction.make())",
            ),
        )
        assertTrue(source.contains(".filters(TrashedFilter.make())"))
        assertTrue(source.contains("import org.konaframework.firefly.tables.TrashedFilter"))
        assertFalse(source.contains("attach"))
        assertThrows(GrindException::class.java) { MakeFireflyRelationManagerCommand().run(args) }
    }

    @Test
    fun javaManagerWithAttachAndPlainDefaults() {
        val base = temp.resolve("java")
        Files.createDirectories(base)
        assertEquals(
            0,
            MakeFireflyRelationManagerCommand().run(listOf("Project", "members", "name", "--attach", "--panel=desk", "--base=$base", "--lang=java")),
        )
        val source = Files.readString(base.resolve("src/main/java/app/panels/desk/resources/project/relations/MembersRelationManager.java"))
        assertTrue(source.contains("package app.panels.desk.resources.project.relations;"))
        assertTrue(source.contains("public class MembersRelationManager extends RelationManager {"))
        assertTrue(source.contains("super(\"members\");"))
        assertTrue(source.contains("headerActions(RelationAction.create(), RelationAction.attach());"))
        assertTrue(source.contains("RelationAction.detach()"))
        assertTrue(source.contains("BulkActionGroup.make(DetachBulkAction.make(), RelationBulkAction.delete())"))
        assertTrue(source.contains("import org.konaframework.firefly.DetachBulkAction;"))
        assertFalse(source.contains("TrashedFilter"))

        assertEquals(0, MakeFireflyRelationManagerCommand().run(listOf("Post", "comments", "body", "--base=$base", "--lang=kotlin")))
        val plain = Files.readString(base.resolve("src/main/kotlin/app/panels/admin/resources/post/relations/CommentsRelationManager.kt"))
        assertTrue(plain.contains("headerActions(RelationAction.create())"))
        assertTrue(plain.contains("actions(RelationAction.view(), RelationAction.edit(), RelationAction.delete())"))
        assertTrue(plain.contains("BulkActionGroup.make(RelationBulkAction.delete())"))
    }

    @Test
    fun rejectsBadArgumentsAndIsListed() {
        val base = temp.resolve("bad")
        Files.createDirectories(base)
        val command = MakeFireflyRelationManagerCommand()
        assertThrows(GrindException::class.java) { command.run(listOf("Project", "tasks", "--base=$base")) }
        assertThrows(GrindException::class.java) { command.run(listOf("bad-name", "tasks", "title", "--base=$base")) }
        assertThrows(GrindException::class.java) { command.run(listOf("Project", "Tasks", "title", "--base=$base")) }
        assertThrows(GrindException::class.java) { command.run(listOf("Project", "tasks", "Title-x", "--base=$base")) }
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-relation-manager" })
        assertEquals("make:firefly-relation-manager", BuiltinCommandSpecs["make:firefly-relation-manager"]!!.name)
        assertEquals(
            0,
            GrindApplication().run(arrayOf("make:firefly-relation-manager", "Project", "tasks", "title", "--base=$base", "--lang=kotlin")),
        )
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/panels/admin/resources/project/relations/TasksRelationManager.kt")))
    }
}
