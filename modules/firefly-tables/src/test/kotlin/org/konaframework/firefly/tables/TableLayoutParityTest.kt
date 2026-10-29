package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.layout.Grid
import org.konaframework.firefly.tables.layout.Split
import org.konaframework.firefly.tables.layout.Stack

class TableLayoutParityTest {
    private val rows = listOf(
        mapOf("id" to "1", "name" to "Ada", "email" to "ada@x.test", "phone" to "111", "job" to "Engineer"),
        mapOf("id" to "2", "name" to "Bea", "email" to "bea@x.test", "phone" to "222", "job" to "Designer"),
    )

    @Test
    fun stackedOnMobileDefaultsTrueAndEmitsDataLabels() {
        val html =
            Table.memory(rows)
                .columns(
                    TextColumn.make("name").label("Name").sortable(),
                    TextColumn.make("email").label("Email"),
                )
                .rowButtons(TableButton.make("view").label("View").url("view"))
                .selectable(true)
                .render()
        assertTrue(html.contains("""data-ff-stacked-mobile="true""""), html)
        assertTrue(html.contains("""data-label="Name""""), html)
        assertTrue(html.contains("ff-table-mobile-sort"), html)
        assertTrue(html.contains("data-ff-mobile-sort"), html)
        assertTrue(html.contains("Name · Asc"), html)
        assertTrue(html.contains("data-ff-select-record"), html)

        val off =
            Table.memory(rows)
                .columns(TextColumn.make("name").sortable())
                .stackedOnMobile(false)
                .render()
        assertTrue(off.contains("""data-ff-stacked-mobile="false""""), off)
        assertFalse(off.contains("ff-table-mobile-sort"), off)

        val defaultCall =
            Table.memory(rows)
                .columns(TextColumn.make("name"))
                .stackedOnMobile()
                .render()
        assertTrue(defaultCall.contains("""data-ff-stacked-mobile="true""""), defaultCall)
    }

    @Test
    fun contentGridUsesBreakpointTracksAndCardChrome() {
        val html =
            Table.memory(rows)
                .columns(TextColumn.make("name").label("Name"), TextColumn.make("email").label("Email"))
                .contentGrid(mapOf("md" to 2, "xl" to 3))
                .selectable(true)
                .rowButtons(TableButton.make("edit").label("Edit").url("edit"))
                .render()
        assertTrue(html.contains("""data-ff-layout="grid""""), html)
        assertTrue(html.contains("""data-ff-content-grid="true""""), html)
        assertTrue(html.contains("ff-table-grid"), html)
        assertTrue(html.contains("--ff-cols-md: 2"), html)
        assertTrue(html.contains("--ff-cols-xl: 3"), html)
        assertTrue(html.contains("ff-table-card"), html)
        assertTrue(html.contains("ff-stack-label"), html)
        assertTrue(html.contains("ff-table-card-main"), html)
        assertTrue(html.contains("""<div class="ff-row-actions">"""), html)
        assertTrue(html.contains("data-ff-select-page"), html)
        assertTrue(html.contains("""<div class="ff-table-select ff-table-card-select">"""), html)

        val cleared =
            Table.memory(rows)
                .columns(TextColumn.make("name"))
                .contentGrid(mapOf("md" to 2))
                .layout(TableLayout.ROWS)
                .render()
        assertFalse(cleared.contains("data-ff-content-grid"), cleared)
    }

    @Test
    fun splitStackAndGridComposeInsideCards() {
        val html =
            Table.memory(rows)
                .columns(
                    Split.make(
                        TextColumn.make("name").label("Name").searchable().sortable().grow(false),
                        Stack.make(
                            TextColumn.make("phone").label("Phone"),
                            TextColumn.make("email").label("Email"),
                        ).space(2).alignment(Align.END).visibleFrom("md"),
                        Grid.make(2).schema(
                            TextColumn.make("job").label("Job"),
                        ),
                    ).from("md"),
                )
                .render()
        assertTrue(html.contains("""data-ff-composed="true""""), html)
        assertTrue(html.contains("ff-layout-split"), html)
        assertTrue(html.contains("""data-ff-from="md""""), html)
        assertTrue(html.contains("ff-layout-stack"), html)
        assertTrue(html.contains("""data-ff-space="2""""), html)
        assertTrue(html.contains("""data-ff-align="end""""), html)
        assertTrue(html.contains("""data-ff-visible-from="md""""), html)
        assertTrue(html.contains("ff-layout-grid"), html)
        assertTrue(html.contains("""data-ff-grow="false""""), html)
        assertTrue(html.contains("ff-table-stack") || html.contains("ff-table-grid"), html)
        assertTrue(html.contains("ff-table-mobile-sort"), html)

        val intGrid =
            Table.memory(rows)
                .columns(Grid.make(2).schema(TextColumn.make("name"), TextColumn.make("email")))
                .render()
        assertTrue(intGrid.contains("ff-layout-grid"), intGrid)
        assertTrue(intGrid.contains("data-ff-columns"), intGrid)
    }

    @Test
    fun layoutRejectsBadInputsAndCoversEmptyCards() {
        assertThrows(FireflyException::class.java) {
            Table.memory(rows).columns("not-a-column")
        }
        assertThrows(FireflyException::class.java) {
            Split.make("bad")
        }
        assertThrows(FireflyException::class.java) {
            Split.make(TextColumn.make("name")).from("xx")
        }
        assertThrows(FireflyException::class.java) {
            Stack.make(TextColumn.make("name")).space(9)
        }
        assertThrows(FireflyException::class.java) {
            Stack.make(TextColumn.make("name")).visibleFrom("nope")
        }
        val emptyCards =
            Table.memory(emptyList<Map<String, Any?>>())
                .columns(TextColumn.make("name"))
                .layout(TableLayout.STACK)
                .empty("Nothing here")
                .render()
        assertTrue(emptyCards.contains("ff-table-card--empty"), emptyCards)
        assertTrue(emptyCards.contains("Nothing here"), emptyCards)

        val hiddenInLayout =
            Table.memory(rows)
                .columns(
                    Split.make(
                        TextColumn.make("name"),
                        TextColumn.make("email").hidden(true),
                    ),
                )
                .render()
        assertTrue(hiddenInLayout.contains("ff-layout-split"), hiddenInLayout)
        assertFalse(hiddenInLayout.contains("data-ff-path=\"email\""), hiddenInLayout)

        val mixed =
            Table.memory(rows)
                .columns(
                    TextColumn.make("name").label("Name"),
                    Stack.make(TextColumn.make("email")).space(0).alignment(Align.CENTER),
                )
                .layout(TableLayout.GRID)
                .render()
        assertTrue(mixed.contains("ff-stack-field"), mixed)
        assertTrue(mixed.contains("ff-layout-stack"), mixed)

        val stackSpace1 =
            Table.memory(rows)
                .columns(Stack.make(TextColumn.make("name")).space(1))
                .render()
        assertTrue(stackSpace1.contains("""data-ff-space="1""""), stackSpace1)

        val stackSpace3 =
            Table.memory(rows)
                .columns(Stack.make(TextColumn.make("name")).space(3))
                .render()
        assertTrue(stackSpace3.contains("""data-ff-space="3""""), stackSpace3)

        val growDefault =
            Table.memory(rows)
                .columns(Split.make(TextColumn.make("name").grow()))
                .render()
        assertFalse(growDefault.contains("""data-ff-grow="false""""), growDefault)

        val noFrom =
            Table.memory(rows)
                .columns(Split.make(TextColumn.make("name"), TextColumn.make("email")))
                .render()
        assertTrue(noFrom.contains("ff-layout-split"), noFrom)
        assertFalse(noFrom.contains("data-ff-from="), noFrom)

        val groupedCards =
            Table.memory(rows)
                .columns(TextColumn.make("name"), TextColumn.make("job"))
                .groups(TableGroup.make("job").label("Job"))
                .groupedBy("job")
                .layout(TableLayout.STACK)
                .selectable(true)
                .render()
        assertTrue(groupedCards.contains("ff-group"), groupedCards)
        assertTrue(groupedCards.contains("ff-table-card-select-page"), groupedCards)
    }

    @Test
    fun mobileSortOmitsWhenNoSortableColumnsEvenIfStacked() {
        val html =
            Table.memory(rows)
                .columns(TextColumn.make("name").sortable(false))
                .stackedOnMobile(true)
                .render()
        assertFalse(html.contains("ff-table-mobile-sort"), html)
    }

    @Test
    fun selectedSortOptionMarkedInMobileDropdown() {
        val html =
            Table.memory(rows)
                .columns(TextColumn.make("name").sortable())
                .sort("name", "desc")
                .render()
        assertTrue(html.contains("""value="name:desc" selected"""), html)
        assertTrue(html.contains("""value="name:asc""""), html)

        val ascSelected =
            Table.memory(rows)
                .columns(TextColumn.make("name").sortable())
                .sort("name", "asc")
                .render()
        assertTrue(ascSelected.contains("""value="name:asc" selected"""), ascSelected)
    }

    @Test
    fun defensiveLayoutBranchesRejectUnknownChildren() {
        val split = Split.make(TextColumn.make("name"))
        split.addRawChildForCoverage("nope")
        assertThrows(FireflyException::class.java) { split.flattenColumns() }
        val ctxRows = mapOf("id" to "1", "name" to "Ada")
        val ctx =
            org.konaframework.firefly.tables.layout.LayoutRenderContext(
                values = ctxRows,
                key = "1",
                tableCell = Cell { null },
                rowCellFor = { Cell { null } },
                shown = { true },
            )
        assertThrows(FireflyException::class.java) { split.render(ctx) }

        val table = Table.memory(rows).columns(Split.make(TextColumn.make("name")))
        val field = Table::class.java.getDeclaredField("columnDefs")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(table) as java.util.ArrayList<Any>).add("bad-def")
        assertThrows(FireflyException::class.java) { table.render() }

        val stackOnly =
            Table.memory(rows)
                .columns(TextColumn.make("name").sortable())
                .stackedOnMobile(false)
                .layout(TableLayout.STACK)
                .render()
        assertTrue(stackOnly.contains("ff-table-mobile-sort"), stackOnly)

        val gridSort =
            Table.memory(rows)
                .columns(TextColumn.make("name").sortable())
                .stackedOnMobile(false)
                .contentGrid(mapOf("md" to 2))
                .render()
        assertTrue(gridSort.contains("ff-table-mobile-sort"), gridSort)

        val composedRows =
            Table.memory(rows)
                .columns(
                    TextColumn.make("secret").hidden(true),
                    Split.make(TextColumn.make("name").sortable()),
                )
                .stackedOnMobile(false)
                .render()
        assertTrue(composedRows.contains("ff-layout-split"), composedRows)
        assertFalse(composedRows.contains("data-ff-path=\"secret\""), composedRows)
        assertTrue(composedRows.contains("ff-table-mobile-sort"), composedRows)

        val emptySelectable =
            Table.memory(emptyList<Map<String, Any?>>())
                .columns(TextColumn.make("name"))
                .layout(TableLayout.STACK)
                .selectable(true)
                .render()
        assertTrue(emptySelectable.contains("ff-table-card--empty"), emptySelectable)
        assertFalse(emptySelectable.contains("ff-table-card-select-page"), emptySelectable)

        val cardsNoSelect =
            Table.memory(rows)
                .columns(TextColumn.make("name"))
                .layout(TableLayout.STACK)
                .selectable(false)
                .render()
        assertFalse(cardsNoSelect.contains("ff-table-card-select-page"), cardsNoSelect)

        Stack.make(TextColumn.make("name")).space(0)
    }
}
