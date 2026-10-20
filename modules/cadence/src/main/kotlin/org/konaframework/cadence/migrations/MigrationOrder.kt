package org.konaframework.cadence.migrations

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Timestamped migration identity.
 *
 * **Files** are `{yyyy_MM_dd_HHmmss}_{snake_name}` (UTC), e.g.
 * `2026_10_20_153950_add_status_to_users_table.kt`. **Classes** stay PascalCase
 * (`AddStatusToUsersTable`) — JVM types cannot start with a digit, and underscores
 * in a class name trip IDE inspections. [Migrator] records the file stem
 * ([MigrationName] on generated classes).
 *
 * Legacy `M{stamp}_{DescriptiveName}` class names are still recognized for apps
 * generated before this layout. Names without a stamp keep registration order
 * and run before timestamped ones.
 */
object MigrationOrder {
    /** Scaffold `create_users_table` file prefix so it always runs first among timestamped migrations. */
    const val DEFAULT_USERS_TIMESTAMP: String = "2000_01_01_000000"

    const val CLASS_PREFIX: String = "M"

    /** `2026_10_20_153950_add_status_to_users_table` (file stem / recorded name). */
    @JvmField
    val FILE_STEM: Regex = Regex("""^(\d{4}_\d{2}_\d{2}_\d{6})_(.+)$""")

    /** Legacy `M2026_10_20_153950_AddStatusToUsersTable`. */
    @JvmField
    val CLASS_NAME: Regex = Regex("""^M(\d{4}_\d{2}_\d{2}_\d{6})_(.+)$""")

    @JvmField
    val INPUT_PREFIX: Regex = Regex("""^(?:M)?(\d{4}_\d{2}_\d{2}_\d{6})_(.+)$""")

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss").withZone(ZoneOffset.UTC)

    @JvmStatic
    fun format(instant: Instant): String = FORMATTER.format(instant.truncatedTo(ChronoUnit.SECONDS))

    @JvmStatic
    fun parse(timestamp: String): Instant = Instant.from(FORMATTER.parse(timestamp))

    @JvmStatic
    @JvmOverloads
    fun format(clock: Clock = Clock.systemUTC()): String = format(clock.instant())

    /** Timestamped snake_case file stem (no class prefix). */
    @JvmStatic
    fun fileStem(timestamp: String, snake: String): String = "${timestamp}_$snake"

    /** Legacy JVM class name; prefer [fileStem] + a PascalCase type. */
    @JvmStatic
    fun className(timestamp: String, descriptive: String): String = "${CLASS_PREFIX}${timestamp}_$descriptive"

    /** Name [Migrator] stores: `@MigrationName` when present, otherwise the simple class name. */
    @JvmStatic
    fun recordedName(migration: Migration): String {
        val type = migration.javaClass
        val annotated = type.getAnnotation(MigrationName::class.java)
        if (annotated != null) {
            return annotated.value
        }
        return type.simpleName.substringAfterLast('$')
    }

    /** Timestamp from a file stem, legacy `M{stamp}_` class, or FQCN; null when untimestamped. */
    @JvmStatic
    fun timestampOf(name: String): String? {
        val simple = name.substringAfterLast('.').substringAfterLast('$')
        FILE_STEM.matchEntire(simple)?.let { return it.groupValues[1] }
        val legacy = CLASS_NAME.matchEntire(simple) ?: return null
        return legacy.groupValues[1]
    }

    /**
     * Stable sort: timestamped names by UTC stamp, untimestamped by original index
     * (all untimestamped sort as `0000_00_00_000000`, so they run first).
     */
    @JvmStatic
    fun sort(names: List<String>): List<String> =
        names.mapIndexed { index, name -> Triple(timestampOf(name) ?: "0000_00_00_000000", index, name) }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
}
