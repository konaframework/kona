package org.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.support.date.KonaDate;
import org.konaframework.support.lang.Translator;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaDefaultsTest {
    @Test
    void konaDateDefaultOverloads() {
        assertNotNull(KonaDate.now());
        assertNotNull(KonaDate.now(ZoneOffset.UTC));
        assertNotNull(KonaDate.now(ZoneOffset.UTC, Clock.systemUTC()));
        assertNotNull(KonaDate.today());
        assertNotNull(KonaDate.today(ZoneOffset.UTC));
        assertNotNull(KonaDate.ofInstant(java.time.Instant.EPOCH));
        assertNotNull(KonaDate.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC));
        assertNotNull(KonaDate.parse("2024-06-01T00:00:00Z"));
        assertNotNull(KonaDate.parse("2024-06-01T00:00:00Z", ZoneOffset.UTC));
        assertNotNull(KonaDate.parse("2024-06-01 00:00:00"));
        assertNotNull(KonaDate.parse("2024-06-01 00:00:00", ZoneOffset.ofHours(1)));
        assertNotNull(KonaDate.parse("2024-06-01T00:00:00+02:00"));
        assertNotNull(KonaDate.parse("2024-06-01"));
        assertEquals("2024-06-01T00:00:00Z", KonaDate.parse("2024-06-01T00:00:00Z").toIso());
        assertEquals(ZoneOffset.UTC, KonaDate.parse("2024-06-01T00:00:00Z").timezone());
        assertNotNull(KonaDate.ofLocalDateTime(java.time.LocalDateTime.of(2024, 6, 1, 0, 0)));
        assertNotNull(KonaDate.ofLocalDateTime(java.time.LocalDateTime.of(2024, 6, 1, 0, 0), ZoneOffset.UTC));
        assertNotNull(KonaDate.ofLocalDate(java.time.LocalDate.of(2024, 6, 1)));
        assertNotNull(KonaDate.ofLocalDate(java.time.LocalDate.of(2024, 6, 1), ZoneOffset.UTC));
        assertNotNull(KonaDate.ofZonedDateTime(java.time.ZonedDateTime.parse("2024-06-01T00:00:00Z")));
        assertNotNull(KonaDate.ofOffsetDateTime(java.time.OffsetDateTime.parse("2024-06-01T00:00:00Z")));

        assertNotNull(KonaDate.yesterday());
        assertNotNull(KonaDate.yesterday(ZoneOffset.UTC));
        assertNotNull(KonaDate.tomorrow());
        assertNotNull(KonaDate.tomorrow(ZoneOffset.UTC));
        assertNotNull(KonaDate.create(2024, 6, 1));
        assertNotNull(KonaDate.create(2024, 6, 1, 12));
        assertNotNull(KonaDate.create(2024, 6, 1, 12, 30));
        assertNotNull(KonaDate.create(2024, 6, 1, 12, 30, 0));
        assertNotNull(KonaDate.create(2024, 6, 1, 12, 30, 0, ZoneOffset.UTC));
        assertNotNull(KonaDate.createFromDate(2024, 6, 1));
        assertNotNull(KonaDate.createFromDate(2024, 6, 1, ZoneOffset.UTC));
        assertNotNull(KonaDate.createFromTime(12, 0));
        assertNotNull(KonaDate.createFromTime(12, 0, 0));
        assertNotNull(KonaDate.createFromTime(12, 0, 0, ZoneOffset.UTC));
        assertNotNull(KonaDate.createFromTimestamp(0L));
        assertNotNull(KonaDate.createFromTimestamp(0L, ZoneOffset.UTC));
        assertNotNull(KonaDate.createFromTimestampMs(0L));
        assertNotNull(KonaDate.createFromTimestampMs(0L, ZoneOffset.UTC));
        assertNotNull(KonaDate.min(KonaDate.today(), KonaDate.tomorrow()));
        assertNotNull(KonaDate.max(KonaDate.today(), KonaDate.tomorrow()));

        KonaDate sample = KonaDate.parse("2024-06-15T12:00:00Z");
        KonaDate later = sample.addHours(2);
        assertEquals(2L * 60 * 60 * 1000, later.diffInMilliseconds(sample));
        assertEquals(2L * 60 * 60, later.diffInSeconds(sample));
        assertEquals(120L, later.diffInMinutes(sample));
        assertEquals(2L, later.diffInHours(sample));
        assertEquals(1L, sample.addDays(1).diffInDays(sample));
        assertEquals(1L, sample.addWeeks(1).diffInWeeks(sample));
        assertEquals(1L, sample.addMonths(1).diffInMonths(sample));
        assertEquals(1L, sample.addYears(1).diffInYears(sample));
        assertTrue(sample.between(sample.subDay(), sample.addDay()));
        assertTrue(sample.isBetween(sample.subDay(), sample.addDay()));
        assertNotNull(sample.diffForHumans());
        assertNotNull(sample.diffForHumans(later));
        assertNotNull(sample.diffForHumans(later, true));
        assertNotNull(sample.ago());
        assertNotNull(sample.fromNow());
        sample.isPast();
        sample.isFuture();
        sample.isToday();
        sample.isYesterday();
        sample.isTomorrow();
        sample.isCurrentYear();
        sample.isCurrentMonth();
        sample.isCurrentWeek();
        assertTrue(sample.age() >= 0);

        Translator t = new Translator("en");
        t.addLines("en", Map.of("solo", "only"));
        assertEquals("only", t.trans("solo"));
        assertEquals("only", t.get("solo"));
    }
}
