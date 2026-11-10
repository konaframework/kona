package org.konaframework.firefly.tables

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.support.FireflyException
import java.util.function.Function

/** `Table.recordActions(Action…)` and bulk record handlers. */
class RecordActionsTableTest {
    @AfterEach
    fun reset() {
        Action.gate(null)
    }

    private fun rows(): List<Map<String, Any?>> = listOf(
        mapOf("id" to "1", "name" to "Draft entry", "state" to "draft"),
        mapOf("id" to "2", "name" to "Posted entry", "state" to "posted"),
    )

    private fun table() = Table.memory(rows()).columns(TextColumn.make("name"))

    @Test
    fun recordActionsRenderPerRowAndRespectRecordVisibility() {
        val post = Action.make("post").label("Post").visibleForRecord { record -> (record as Map<*, *>)["state"] == "draft" }
            .action { _ -> }
        val html = table().recordActions(post).render()
        // The post action shows on the draft row only.
        val draftRow = html.substringAfter("""data-ff-record="1"""").substringBefore("</tr>")
        val postedRow = html.substringAfter("""data-ff-record="2"""").substringBefore("</tr>")
        assertTrue(draftRow.contains("""data-ff-action="post""""))
        assertTrue(draftRow.contains("""wire:click="post""""))
        assertFalse(postedRow.contains("""data-ff-action="post""""))
        assertTrue(draftRow.contains("ff-row-actions"))
    }

    @Test
    fun ungroupedRowButtonStaysOutsideTheMenu() {
        val html = table().rowButtons(
            TableButton.make("open").label("Open").url("/open").ungrouped(),
            TableButton.make("edit").label("Edit").url("/edit"),
            TableButton.make("delete").label("Delete").url("/delete"),
        ).render()
        val row = html.substringAfter("""data-ff-record="1"""").substringBefore("</tr>")
        assertTrue(row.contains("""class="ff-table-button" href="/open""""))
        val menu = row.substringAfter("data-ff-menu-panel").substringBefore("</div>")
        assertTrue(menu.contains("""href="/edit""""))
        assertFalse(menu.contains("""href="/open""""))
    }

    @Test
    fun recordActionsPostToTheBoundUrlWithCsrfAndTheRowKey() {
        val post = Action.make("post").label("Post").action { _ -> }
        val view = Action.make("sign").label("Sign").requiresConfirmation().action { _ -> }
        val html = table()
            .recordActions(post, view)
            .recordActionsPostTo(Function { "/admin/entry/actions/${it.name}" }, """<input type="hidden" name="_token" value="t" />""")
            .render()
        val draftRow = html.substringAfter("""data-ff-record="1"""").substringBefore("</tr>")
        assertTrue(draftRow.contains("""<form method="post" action="/admin/entry/actions/post""""))
        assertTrue(draftRow.contains("""<form method="post" action="/admin/entry/actions/sign""""))
        assertTrue(draftRow.contains("""<input type="hidden" name="_token" value="t" />"""))
        assertTrue(draftRow.contains("""<input type="hidden" name="id" value="1" />"""))
        assertTrue(html.substringAfter("""data-ff-record="2"""").contains("""<input type="hidden" name="id" value="2" />"""))
        // Two actions share the ⋯ menu.
        assertTrue(draftRow.contains("ff-row-action-menu"))
    }

    @Test
    fun recordActionsSitBeforeRowButtonsAndCanStandAlone() {
        val post = Action.make("post").label("Post").action { _ -> }
        val view = TableButton.make("view").label("View").url("view")
        val grouped = table().recordActions(post).rowButtons(view).render()
        assertTrue(grouped.indexOf("data-ff-action=\"post\"") < grouped.indexOf("data-ff-action=\"view\""))
        assertTrue(grouped.contains("ff-row-action-menu"))
        val flat = table().recordActions(post).rowButtons(view).groupRowActions(false).render()
        assertFalse(flat.contains("ff-row-action-menu"))
        assertTrue(flat.contains("data-ff-action=\"post\""))
        assertTrue(flat.contains("data-ff-action=\"view\""))
        val alone = table().recordActions(post).render()
        assertFalse(alone.contains("ff-row-action-menu"))
        assertTrue(alone.contains("ff-row-actions"))
        assertFalse(table().render().contains("ff-row-actions"))
    }

    @Test
    fun recordActionsRenderOnCardLayoutsToo() {
        val post = Action.make("post").label("Post").action { _ -> }
        val html = table().layout(TableLayout.GRID).recordActions(post).render()
        assertTrue(html.contains("""<div class="ff-row-actions">"""))
        assertTrue(html.contains("""data-ff-action="post""""))
    }

    @Test
    fun recordActionsAreReplacedAndUnique() {
        val a = Action.make("a")
        val b = Action.make("b")
        val t = table().recordActions(a)
        assertEquals(listOf(a), t.recordActionList())
        t.recordActions(b)
        assertEquals(listOf(b), t.recordActionList())
        assertSame(b, t.findRecordAction("b"))
        assertNull(t.findRecordAction("a"))
        val failure = assertThrows(FireflyException::class.java) { table().recordActions(a, Action.make("a")) }
        assertTrue(failure.message!!.contains("Duplicate record action [a]"))
    }

    @Test
    fun recordLookupsFollowTheTableQuery() {
        val t = table().modifyQueryUsing { query -> query.where("state", "draft") }
        val draft = t.findRecord("1")
        assertEquals("Draft entry", draft!!["name"])
        assertNull(t.findRecord("2"))
        assertEquals("Draft entry", t.recordAttributes(draft)["name"])
    }

    @Test
    fun bulkActionOnRecordsReceivesTheSelectedRecords() {
        var seen: List<Any> = emptyList()
        val action = BulkAction.make("mark").actionOnRecords { records -> seen = records }
        val t = table().bulkActions(action)
        val result = t.runBulkAction("mark", listOf("1", "2", "99"))
        assertEquals(2, seen.size)
        assertEquals("Draft entry", (seen[0] as Map<*, *>)["name"])
        assertEquals(3, result.totalCount)
        // Keys can be selected without fetching records.
        var count = -1
        val lean = BulkAction.make("lean").fetchSelectedRecords(false).actionOnRecords { count = it.size }
        table().bulkActions(lean).runBulkAction("lean", listOf("1"))
        assertEquals(0, count)
    }

    @Test
    fun bulkRecordsOnlyIncludeAuthorizedRows() {
        var seen: List<Any> = emptyList()
        val action = BulkAction.make("mark")
            .authorizeIndividualRecords { cell -> cell.get("state") == "draft" }
            .actionOnRecords { seen = it }
        val result = table().bulkActions(action).runBulkAction("mark", listOf("1", "2"))
        assertEquals(1, seen.size)
        assertEquals(1, result.failureCount)
    }

    @Test
    fun recordActionsCanBeSwitchedOffAndStillBeFound() {
        val post = Action.make("post").label("Post").action { _ -> }
        val t = table().recordActions(post).recordActionsEnabled(false)
        assertFalse(t.render().contains("data-ff-action=\"post\""))
        assertSame(post, t.findRecordAction("post"))
        assertTrue(table().recordActions(post).recordActionsEnabled(true).render().contains("data-ff-action=\"post\""))
    }
}
