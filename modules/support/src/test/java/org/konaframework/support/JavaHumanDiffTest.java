package org.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.konaframework.support.date.KonaDate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaHumanDiffTest {
    @AfterEach
    void reset() {
        KonaDate.resetHumanLocale();
    }

    @Test
    void localeOverloadsFromJava() {
        KonaDate base = KonaDate.parse("2024-06-15T12:00:00Z");
        Clock clock = Clock.fixed(base.toInstant(), ZoneOffset.UTC);
        KonaDate past = base.subHours(3);
        assertEquals("hace 3 horas", past.diffForHumans(null, false, clock, Locale.forLanguageTag("es")));
        assertEquals(Locale.ENGLISH, KonaDate.humanLocale());
        KonaDate.setHumanLocale(Locale.GERMAN);
        assertEquals(Locale.GERMAN, KonaDate.humanLocale());
        assertEquals("vor 3 Stunden", past.diffForHumans(null, false, clock));
        KonaDate.resetHumanLocale();
        assertEquals(Locale.ENGLISH, KonaDate.humanLocale());
        assertEquals("3 hours ago", past.ago(clock));
    }
}
