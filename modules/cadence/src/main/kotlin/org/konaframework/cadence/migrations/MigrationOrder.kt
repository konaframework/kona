package org.konaframework.cadence.migrations

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Timestamped migration identity.
 *
 * JVM class names cannot start with a digit, so generated classes are
 * `M{yyyy_MM_dd_HHmmss}_{DescriptiveName}` (UTC). [Migrator] records that
 * simple name. Discovery and the registry sort by the timestamp so run order
 * does not depend on how lines happen to be listed in `META-INF/kona/migrations`.
 *
 * Classes without a timestamp keep their registration order and run before
 * timestamped ones (so existing `CreateUsersTable` scaffolds stay first).
 */
object MigrationOrder {
    const val CLASS_PREFIX: String = "M"

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

    @JvmStatic
    fun className(timestamp: String, descriptive: String): String = "${CLASS_PREFIX}${timestamp}_$descriptive"

    /** Timestamp from a simple name or FQCN, or null when the class is untimestamped. */
    @JvmStatic
    fun timestampOf(name: String): String? {
        val simple = name.substringAfterLast('.').substringAfterLast('$')
        val match = CLASS_NAME.matchEntire(simple) ?: return null
        return match.groupValues[1]
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
