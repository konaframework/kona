package org.konaframework.firefly.tables

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.support.FireflyException

class BulkActionsTest {
    @AfterEach
    fun reset() {
        BulkAction.configureUsing(null)
    }

    private fun rows(count: Int = 4): List<Map<String, Any?>> =
        (1..count).map { mapOf("id" to "$it", "name" to "Row $it", "team" to if (it % 2 == 0) "A" else "B", "locked" to (it == 3)) }

    private fun table(count: Int = 4) = Table.memory(rows(count)).columns(TextColumn.make("name"))

    @Test
    fun bulkActionsTurnOnSelectionAndRenderTheToolbar() {
        val plain = table()
        assertFalse(plain.isSelectable())
        assertFalse(plain.isBulkActionsConfigured())
        val plainHtml = plain.render()
        assertFalse(plainHtml.contains("data-ff-selectable"))
        assertFalse(plainHtml.contains("ff-table-select"))
        assertTrue(plainHtml.contains("ff-table-toolbar--empty"))

        val delete = DeleteBulkAction.make()
        val archive = BulkAction.make("archiveAll").icon("check").color("primary").tooltip("Archive them")
        val export = BulkAction.make("export").label("Export").button()
        val quiet = BulkAction.make("note_it").link()
        val gone = BulkAction.make("gone").hidden()
        val locked = BulkAction.make("locked").disabled()
        val bulk = table()
            .bulkActions(
                BulkActionGroup.make(delete, archive, gone).label("Mass").icon("bars").color("gray").tooltip("More"),
                export,
                quiet,
                gone,
                locked,
            )
            .select("1")
        assertTrue(bulk.isSelectable())
        assertTrue(bulk.isBulkActionsConfigured())
        assertEquals(5, bulk.bulkActionEntries().size)
        assertEquals("Archive all", archive.getLabel(Cell { null }))
        assertEquals("Note it", quiet.getLabel(Cell { null }))
        val html = bulk.render()
        assertTrue(html.contains("""data-ff-selectable data-ff-total="4" data-ff-deselect-on-filter="true""""), html)
        assertTrue(html.contains("""<div class="ff-table-toolbar"><div class="ff-table-toolbar-row"><div class="ff-table-bulk" data-ff-bulk>"""), html)
        assertTrue(html.contains("ff-bulk-trigger"))
        assertTrue(html.contains(">Mass<"))
        assertTrue(html.contains("""title="More""""))
        assertTrue(html.contains("""data-ff-bulk-run="delete""""))
        assertTrue(html.contains("""data-ff-color="danger""""))
        assertTrue(html.contains("""title="Archive them""""))
        assertTrue(html.contains("""class="ff-table-button ff-bulk-action" data-ff-bulk-run="export""""))
        assertTrue(html.contains("""class="ff-bulk-action ff-bulk-link" data-ff-bulk-run="note_it""""))
        assertFalse(html.contains("""data-ff-bulk-run="gone""""))
        assertTrue(html.contains("""data-ff-bulk-run="locked" data-ff-bulk-locked disabled"""))
        assertTrue(html.contains("""data-ff-select-page aria-label="Select all on this page""""), html)
        assertTrue(table().bulkActions(BulkAction.make("a")).select("1").select("2").select("3").select("4").render().contains("data-ff-select-page checked"))
        assertTrue(html.contains("""data-ff-select-record value="1" checked"""))
        assertTrue(html.contains("""<tr data-ff-record="1" class="ff-selected">"""))
        assertTrue(html.contains("""<tr data-ff-record="2">"""))
        assertTrue(html.contains("data-ff-select-all-matching hidden>Select all 4<"))
        assertTrue(html.contains("data-ff-deselect-all"))
        // Confirm dialog for delete; plain hidden form for export.
        assertTrue(html.contains("""data-ff-bulk-mount="delete"><div class="ff-table-button-dialog""""))
        assertTrue(html.contains("Delete selected"))
        assertTrue(html.contains("""data-ff-deselect="true""""))
        assertTrue(html.contains("""data-ff-bulk-mount="export"><form method="post" class="ff-bulk-form" data-ff-bulk-form data-ff-deselect="false" hidden>"""))

        // All group items hidden → no group trigger; no visible actions at all → no bulk area.
        val emptyGroup = table().bulkActions(BulkActionGroup.make(listOf(BulkAction.make("x").hidden())))
        val emptyHtml = emptyGroup.render()
        assertFalse(emptyHtml.contains("data-ff-bulk>"))
        assertTrue(emptyHtml.contains("ff-table-toolbar--empty"))
        assertTrue(emptyHtml.contains("data-ff-selectable"))

        // Toolbar with search + bulk shares one row; search alone keeps the original shape.
        val searchBulk = Table.memory(rows()).columns(TextColumn.make("name").searchable())
            .groupedBulkActions(BulkAction.make("archive"))
        assertTrue(searchBulk.render().contains("""</div></div><form class="ff-table-toolbar-form""""))
        val searchOnly = Table.memory(rows()).columns(TextColumn.make("name").searchable()).render()
        assertTrue(searchOnly.contains("""<div class="ff-table-toolbar"><form class="ff-table-toolbar-form""""))

        // bulkActions() with no entries opts out but still counts as configured.
        val cleared = table().bulkActions(BulkAction.make("a")).bulkActions()
        assertTrue(cleared.isBulkActionsConfigured())
        assertFalse(cleared.isSelectable())
    }

    @Test
    fun selectionOptionsShapeCheckboxesAndTheSelectionBar() {
        val after = table()
            .selectable(true)
            .checkboxPosition(RecordCheckboxPosition.AFTER_CELLS)
            .checkIfRecordIsSelectableUsing { it.get("locked") != true }
            .maxSelectableRecords(10)
            .deselectAllRecordsWhenFiltered(false)
        val html = after.render()
        assertTrue(html.contains("""data-ff-deselect-on-filter="false" data-ff-max-selectable="10""""))
        assertTrue(html.contains("""<th scope="col" data-ff-align="start">Name</th><th class="ff-table-select""""), html)
        assertTrue(html.contains("""data-ff-select-record value="3" disabled"""))
        assertTrue(html.contains("Select all 4"))
        assertFalse(html.contains("data-ff-bulk>"))
        assertTrue(table().selectable(true).checkboxPosition(RecordCheckboxPosition.BEFORE_CELLS).render()
            .contains("""<tr><th class="ff-table-select""""))

        // Max below the total hides "Select all".
        assertFalse(table().selectable(true).maxSelectableRecords(2).render().contains("data-ff-select-all-matching"))
        assertFalse(table().selectable(true).selectCurrentPageOnly().render().contains("data-ff-select-all-matching"))
        assertTrue(table().selectable(true).selectCurrentPageOnly(false).render().contains("data-ff-select-all-matching"))

        // No selectable keys on the page → disabled header checkbox.
        val none = table().selectable(true).checkIfRecordIsSelectableUsing { false }.render()
        assertTrue(none.contains("data-ff-select-page disabled"))
        assertTrue(Table.memory(emptyList<Map<String, Any?>>()).selectable(true).render().contains("data-ff-select-page disabled"))

        // Grouped rows carry data-ff-group and a group checkbox; group-only drops the page checkbox.
        val grouped = table().selectable(true).groups(TableGroup.make("team")).selectGroupsOnly()
            .checkIfRecordIsSelectableUsing { it.get("team") == "A" }
            .render()
        assertTrue(grouped.contains("""data-ff-select-groups-only="true""""))
        assertTrue(grouped.contains("""<th class="ff-table-select" scope="col"></th>"""))
        assertTrue(grouped.contains("""data-ff-select-group="A" aria-label="Select group A""""))
        assertTrue(grouped.contains("""data-ff-select-group="B" disabled"""))
        assertTrue(grouped.contains("""data-ff-group="A""""))
        assertFalse(grouped.contains("data-ff-select-all-matching"))
        val groupedOff = table().groups(TableGroup.make("team")).render()
        assertFalse(groupedOff.contains("data-ff-select-group"))
        assertTrue(table().selectable(true).groups(TableGroup.make("team")).selectGroupsOnly(false).render().contains("data-ff-select-page"))

        // Card layouts: checkbox in a div, selected card class, group attribute.
        val cards = table().selectable(true).select("2").layout(TableLayout.STACK).render()
        assertTrue(cards.contains("""<article class="ff-table-card ff-selected" data-ff-record="2">"""), cards)
        assertTrue(cards.contains("ff-table-card-select"))
        val groupedCards = table().selectable(true).layout(TableLayout.GRID).groups(TableGroup.make("team")).render()
        assertTrue(groupedCards.contains("""<h3 class="ff-group"><label"""))
        assertTrue(groupedCards.contains("""data-ff-group="B""""))
        val groupOnlyCards = table().selectable(true).layout(TableLayout.STACK).groups(TableGroup.make("team")).selectGroupsOnly().render()
        assertFalse(groupOnlyCards.contains("ff-table-card-select-page"), groupOnlyCards)
        assertTrue(groupOnlyCards.contains("data-ff-select-group"))
        val plainCards = table().layout(TableLayout.STACK).select("2").render()
        assertTrue(plainCards.contains("""<article class="ff-table-card" data-ff-record="2">"""))

        assertThrows(FireflyException::class.java) { table().maxSelectableRecords(0) }
    }

    @Test
    fun runBulkActionResolvesKeysAndGuardsTheRequest() {
        val seen = ArrayList<List<String>>()
        val t = table(6)
            .checkIfRecordIsSelectableUsing { it.get("locked") != true }
            .bulkActions(
                BulkAction.make("touch").action { seen.add(it.keys()) },
                BulkAction.make("hiddenOne").hidden(),
                BulkAction.make("disabledOne").disabled(),
            )
        assertThrows(FireflyException::class.java) { t.bulkAction("missing") }
        assertThrows(FireflyException::class.java) { t.runBulkAction("hiddenOne", listOf("1")) }
        assertThrows(FireflyException::class.java) { t.runBulkAction("disabledOne", listOf("1")) }
        assertThrows(FireflyException::class.java) { t.runBulkAction("touch", listOf("", " ")) }

        // Locked row 3 is dropped; missing keys stay (callback decides); duplicates collapse.
        val result = t.runBulkAction("touch", listOf("1", "3", "1", "99"))
        assertEquals(listOf("1", "99"), seen.last())
        assertEquals(2, result.totalCount)
        assertTrue(result.isSuccessful())
        assertNull(result.notificationTitle())

        // allMatching runs against the filtered query, ignoring the posted keys.
        t.search("Row")
        t.runBulkAction("touch", emptyList(), allMatching = true)
        assertEquals(listOf("1", "2", "4", "5", "6"), seen.last())

        val capped = table(6).maxSelectableRecords(2).bulkActions(BulkAction.make("touch"))
        assertThrows(FireflyException::class.java) { capped.runBulkAction("touch", listOf("1", "2", "3")) }
        assertEquals(2, capped.runBulkAction("touch", listOf("1", "2")).successCount)

        val groupsOnly = table(4).groups(TableGroup.make("team")).selectGroupsOnly().bulkActions(BulkAction.make("touch"))
        assertThrows(FireflyException::class.java) { groupsOnly.runBulkAction("touch", listOf("1", "2")) }
        assertEquals(2, groupsOnly.runBulkAction("touch", listOf("2", "4")).totalCount)
        // Group-only without a grouped column does not restrict.
        assertEquals(2, table(4).selectGroupsOnly().bulkActions(BulkAction.make("touch")).runBulkAction("touch", listOf("1", "2")).totalCount)
        // Records without a group value count as one "no group" bucket.
        val ungrouped = Table.memory(mapOf("id" to "1"), mapOf("id" to "2")).groups(TableGroup.make("team")).selectGroupsOnly()
            .bulkActions(BulkAction.make("touch"))
        assertEquals(2, ungrouped.runBulkAction("touch", listOf("1", "2")).totalCount)
        // Authorization sees missing records as empty rows.
        val gate = table(2).bulkActions(BulkAction.make("touch").authorizeIndividualRecords { it.get("name") != null })
        val gated = gate.runBulkAction("touch", listOf("1", "99"))
        assertEquals(1, gated.failureCount)
        // Grouping on without group-only does not restrict either.
        assertEquals(2, table(4).groups(TableGroup.make("team")).bulkActions(BulkAction.make("touch")).runBulkAction("touch", listOf("1", "2")).totalCount)
    }

    @Test
    fun deleteBulkActionDeletesAndReportsMissingRecords() {
        val t = table(5).bulkActions(BulkActionGroup.make(DeleteBulkAction.make()))
        val ok = t.runBulkAction("delete", listOf("1", "2"))
        assertEquals("Deleted", ok.notificationTitle())
        assertTrue(ok.deselectRecordsAfterCompletion)
        assertEquals("delete", ok.action)
        assertEquals(listOf("3", "4", "5"), t.load().records.map { it["id"] })

        val partial = t.runBulkAction("delete", listOf("3", "1"))
        assertFalse(partial.isSuccessful())
        assertEquals(1, partial.successCount)
        assertEquals(1, partial.failureCount)
        assertEquals("1 of 2 deleted", partial.notificationTitle())
        assertEquals(listOf("One selected record no longer exists."), partial.messages)

        val none = t.runBulkAction("delete", listOf("1", "2"))
        assertEquals("Nothing was deleted", none.notificationTitle())
        assertEquals(listOf("2 selected records no longer exist."), none.messages)

        val named = DeleteBulkAction.make("purge")
        assertEquals("purge", named.name)
    }

    @Test
    fun bulkActionContextAuthorizationChunksAndFailures() {
        val chunks = ArrayList<Int>()
        val captured = ArrayList<Any?>()
        val t = table(5).bulkActions(
            BulkAction.make("grade")
                .schema(Text.make("Pick a grade"))
                .authorizeIndividualRecords { it.get("team") == "A" }
                .chunkSelectedRecords(1)
                .action { context ->
                    captured.add(context.get("grade"))
                    captured.add(context.data())
                    captured.add(context.records().map { it["id"] })
                    context.eachChunk { chunks.add(it.size) }
                    context.reportBulkProcessingFailure("slow", "Too slow")
                    context.reportBulkProcessingFailure("slow", "Too slow")
                }
                .successNotificationTitle("Graded")
                .failureNotificationTitle("Some failed"),
            BulkAction.make("keysOnly")
                .fetchSelectedRecords(false)
                .authorizeIndividualRecords({ it.get("key") != "1" }) { counts -> "${counts.failureCount}/${counts.totalCount} denied" }
                .action { context ->
                    captured.add(context.records())
                    context.eachChunk { chunks.add(-1) }
                }
                .failureNotificationTitle { "${it.successCount} ok" },
            BulkAction.make("allChunk").action { context -> context.eachChunk { chunks.add(it.size * 10) } },
            BulkAction.make("noTitles"),
        )
        val graded = t.runBulkAction("grade", listOf("1", "2", "3", "4"), mapOf("grade" to "B+"))
        assertEquals("B+", captured[0])
        assertEquals(mapOf("grade" to "B+"), captured[1])
        assertEquals(listOf("2", "4"), captured[2])
        assertEquals(listOf(1, 1), chunks)
        // 2 unauthorized + 2 reported failures.
        assertEquals(4, graded.failureCount)
        assertEquals(0, graded.successCount)
        assertEquals("Some failed", graded.notificationTitle())
        assertEquals(listOf("2 selected records were not authorized.", "Too slow"), graded.messages)

        val oneDenied = t.runBulkAction("keysOnly", listOf("1", "2"))
        assertEquals(emptyList<Map<String, Any?>>(), captured.last())
        assertEquals(listOf(1, 1), chunks)
        assertEquals("1 ok", oneDenied.notificationTitle())
        assertEquals(listOf("1/2 denied"), oneDenied.messages)

        val single = table(2).bulkActions(BulkAction.make("a").authorizeIndividualRecords { false }).runBulkAction("a", listOf("1"))
        assertEquals(listOf("One selected record was not authorized."), single.messages)
        assertNull(single.notificationTitle())

        t.runBulkAction("allChunk", listOf("1", "2", "3"))
        assertEquals(30, chunks.last())
        val graded2 = table(2).bulkActions(BulkAction.make("g").successNotificationTitle("Done")).runBulkAction("g", listOf("1"))
        assertEquals("Done", graded2.notificationTitle())
        assertNull(t.runBulkAction("noTitles", listOf("1")).notificationTitle())
    }

    @Test
    fun bulkActionDialogsAndConfiguration() {
        BulkAction.configureUsing { it.color("warning") }
        val configured = BulkAction.make("tag")
        BulkAction.configureUsing(null)
        val cell = Cell { null }
        assertTrue(configured.renderTrigger(cell, menuItem = false).contains("""data-ff-color="warning""""))

        val schemaOnly = BulkAction.make("tag").schema(Text.make("Tag body")).url("/tags/bulk").label { "Tag ${it.get("x")}" }
        val mount = schemaOnly.renderMount(Cell { if (it == "x") "all" else null })
        assertTrue(mount.contains("""<form method="post" action="/tags/bulk""""))
        assertTrue(mount.contains("Tag body"))
        assertTrue(mount.contains(">Submit<"))
        assertTrue(mount.contains(">Cancel<"))
        assertTrue(mount.contains("Tag all"))
        assertFalse(mount.contains("ff-action-description"))

        val confirm = BulkAction.make("wipe").requiresConfirmation()
            .modalHeading("Wipe rows").modalDescription("Gone for good").modalSubmitActionLabel("Wipe").modalCancelActionLabel("Keep")
            .color("danger")
        val confirmHtml = confirm.renderMount(cell)
        assertTrue(confirmHtml.contains("Wipe rows"))
        assertTrue(confirmHtml.contains("Gone for good"))
        assertTrue(confirmHtml.contains("""<button type="submit" class="ff-action-submit" data-ff-color="danger">Wipe</button>"""))
        assertTrue(confirmHtml.contains(">Keep<"))
        val defaults = BulkAction.make("plain").requiresConfirmation(true).renderMount(cell)
        assertTrue(defaults.contains("Are you sure you would like to do this?"))
        assertTrue(defaults.contains(">Confirm<"))
        assertFalse(BulkAction.make("off").requiresConfirmation(false).renderMount(cell).contains("ff-table-button-dialog"))

        assertTrue(BulkAction.make("v").visible(false).isHidden(cell))
        assertTrue(BulkAction.make("v").visible { false }.isHidden(cell))
        assertFalse(BulkAction.make("v").visible(true).isHidden(cell))
        assertTrue(BulkAction.make("h").hidden { true }.isHidden(cell))
        assertFalse(BulkAction.make("h").hidden(false).isHidden(cell))
        assertTrue(BulkAction.make("d").disabled { true }.isDisabled(cell))
        assertFalse(BulkAction.make("d").disabled(false).isDisabled(cell))
        assertTrue(BulkAction.make("d").deselectRecordsAfterCompletion().deselectsRecordsAfterCompletion())
        assertFalse(BulkAction.make("d").deselectRecordsAfterCompletion(false).deselectsRecordsAfterCompletion())
        assertNull(BulkAction.make("c").chunkSize())
        assertTrue(BulkAction.make("c").fetchesSelectedRecords())
        assertEquals(listOf("only"), BulkAction.make("only").bulkActions().map { it.name })

        assertThrows(FireflyException::class.java) { BulkAction.make(" ") }
        assertThrows(FireflyException::class.java) { BulkAction.make("x").chunkSelectedRecords(0) }
        assertThrows(FireflyException::class.java) { BulkAction.make("x").url(" ") }
        assertThrows(FireflyException::class.java) { BulkAction.make("x").icon("nope") }
        assertThrows(FireflyException::class.java) { BulkActionGroup.make().icon("nope") }

        val counts = BulkCounts(1, 2, 3)
        assertEquals(listOf(1, 2, 3), listOf(counts.successCount, counts.failureCount, counts.totalCount))
        assertEquals("", BulkActionGroup.make().render(cell))
        assertTrue(BulkActionGroup.make(BulkAction.make("a")).render(cell).contains(">Bulk actions<"))
    }
}
