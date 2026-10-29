package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.support.FireflyException

class PersistedQueryTest {
    private fun rows(): List<Map<String, Any?>> =
        (1..30).map { mapOf("id" to "$it", "name" to "Row $it", "team" to if (it % 2 == 0) "A" else "B") }

    @Test
    fun persistedParametersRideOnEverySortPageSearchAndFilterLink() {
        val table = Table.memory(rows())
            .columns(TextColumn.make("name").sortable().searchable())
            .filters(SelectFilter.make("team").options(mapOf("A" to "A", "B" to "B")))
            .persistQueryParameters(mapOf("id" to "7", "relation" to "tasks"))
        assertEquals(mapOf("id" to "7", "relation" to "tasks"), table.persistedQueryParameters())
        val html = table.render()
        assertTrue(html.contains("?id=7&amp;relation=tasks&amp;sort=name"), html)
        assertTrue(html.contains("?id=7&amp;relation=tasks&amp;page=2"))
        assertTrue(html.contains("""<input type="hidden" name="id" value="7" />"""))
        assertTrue(html.contains("""<input type="hidden" name="relation" value="tasks" />"""))
        table.persistQueryParameters(emptyMap())
        assertTrue(table.persistedQueryParameters().isEmpty())
        assertFalse(table.render().contains("""name="relation""""))
    }

    @Test
    fun bulkActionsOnlyReachRecordsInsideTheTableQuery() {
        var deleted = 0
        val table = Table.memory(rows())
            .columns(TextColumn.make("name"))
            .modifyQueryUsing { query -> query.where("team", "=", "A") }
            .bulkActions(DeleteBulkAction.make(), BulkAction.make("count").action { deleted = it.keys().size })
        val result = table.runBulkAction("delete", listOf("2", "3", "999"), emptyMap(), false)
        assertEquals(1, result.successCount, "row 3 is outside the query, 999 does not exist")
        assertEquals(2, result.failureCount)
        val plain = Table.memory(rows()).columns(TextColumn.make("name")).bulkActions(DeleteBulkAction.make())
        assertEquals(1, plain.runBulkAction("delete", listOf("3"), emptyMap(), false).successCount)
        table.runBulkAction("count", listOf("4"), emptyMap(), false)
        assertEquals(1, deleted)
    }

    @Test
    fun buttonModalHeadingDiffersFromTheTrigger() {
        val html = TableButton.make("edit").label("Edit").modalHeading("Edit task").body { "<p>form</p>" }.render(Cell { "1" }, true)
        assertTrue(html.contains("""<span class="ff-dialog-title">Edit task</span>"""), html)
        assertTrue(html.contains(">Edit</button>"))
        assertThrows(FireflyException::class.java) { TableButton.make("edit").modalHeading(" ") }
    }
}
