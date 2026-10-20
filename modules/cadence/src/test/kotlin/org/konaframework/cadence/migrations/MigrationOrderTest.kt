package org.konaframework.cadence.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MigrationOrderTest {
    @Test
    fun formatsParsesAndNamesClasses() {
        val instant = Instant.parse("2026-10-20T14:15:30Z")
        assertEquals("2026_10_20_141530", MigrationOrder.format(instant))
        assertEquals("2026_10_20_141530", MigrationOrder.format(Clock.fixed(instant, ZoneOffset.UTC)))
        assertTrue(MigrationOrder.format().matches(Regex("""\d{4}_\d{2}_\d{2}_\d{6}""")))
        assertEquals(instant, MigrationOrder.parse("2026_10_20_141530"))
        assertEquals(
            "M2026_10_20_141530_CreateFlightsTable",
            MigrationOrder.className("2026_10_20_141530", "CreateFlightsTable"),
        )
        assertEquals("M", MigrationOrder.CLASS_PREFIX)
    }

    @Test
    fun timestampOfReadsSimpleAndFqcn() {
        assertEquals(
            "2026_10_20_141530",
            MigrationOrder.timestampOf("M2026_10_20_141530_CreateFlightsTable"),
        )
        assertEquals(
            "2026_01_02_030405",
            MigrationOrder.timestampOf("database.migrations.M2026_01_02_030405_AddGateToFlightsTable"),
        )
        assertNull(MigrationOrder.timestampOf("CreateUsersTable"))
        assertNull(MigrationOrder.timestampOf("database.migrations.CreateUsersTable"))
        assertNull(MigrationOrder.timestampOf("2026_add_flag"))
        assertEquals(
            "2026_10_20_110000",
            MigrationOrder.timestampOf("pkg.Outer\$M2026_10_20_110000_Earlier"),
        )
    }

    @Test
    fun sortPutsUntimestampedFirstThenStampOrder() {
        val names = listOf(
            "M2026_10_20_120000_CreatePostsTable",
            "CreateUsersTable",
            "M2026_10_20_110000_CreateFlightsTable",
            "2026_add_flag",
        )
        assertEquals(
            listOf(
                "CreateUsersTable",
                "2026_add_flag",
                "M2026_10_20_110000_CreateFlightsTable",
                "M2026_10_20_120000_CreatePostsTable",
            ),
            MigrationOrder.sort(names),
        )
        val unstamped = names.filter { MigrationOrder.timestampOf(it) == null }
        assertEquals(unstamped, MigrationOrder.sort(unstamped))
    }
}
