package org.konaframework.support.number;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import kotlin.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaNumberTest {
    @AfterEach
    void reset() {
        Number.resetDefaults();
    }

    @Test
    void formattingOverloads() {
        assertEquals("1,234.568", Number.format(1234.5678));
        assertEquals("1,234.57", Number.format(1234.5678, 2));
        assertEquals("1,234.6", Number.format(1234.5678, null, 1));
        assertEquals("1.234,57", Number.format(1234.5678, 2, null, "de"));
        assertEquals("12,345,678,901,234,567.89", Number.format(new BigDecimal("12345678901234567.89"), 2));

        assertEquals("10%", Number.percentage(10));
        assertEquals("10.50%", Number.percentage(10.5, 2));
        assertEquals("10.5%", Number.percentage(10.5, 0, 2));
        assertEquals("10,50\u00A0%", Number.percentage(10.5, 2, null, "de"));
    }

    @Test
    void currencyOverloads() {
        BigDecimal total = new BigDecimal("1234.5");
        assertEquals("$1,234.50", Number.currency(total));
        assertEquals("KES1,234.50", Number.currency(total, "KES"));
        assertEquals("Ksh1,234.50", Number.currency(total, "KES", "en_KE"));
        assertEquals("Ksh1,235", Number.currency(new BigDecimal("1234.6"), "KES", "en_KE", 0));

        Number.useCurrency("KES");
        assertEquals("KES", Number.defaultCurrency());
        assertEquals("KES1,234.50", Number.currency(total));
        assertEquals("€1,234.50", Number.withCurrency("EUR", () -> Number.currency(total)));
    }

    @Test
    void wordsAndSizes() {
        assertEquals("forty-two", Number.spell(42));
        assertEquals("forty-two", Number.spell(42, "en"));
        assertEquals("42", Number.spell(42, "en", 50));
        assertEquals("forty-two", Number.spell(42, "en", 10));
        assertEquals("42", Number.spell(42, null, null, 42));
        assertEquals("42nd", Number.ordinal(42));
        assertEquals("1.001st", Number.ordinal(1001, "de"));
        assertEquals("forty-second", Number.spellOrdinal(42));
        assertEquals("forty-second", Number.spellOrdinal(42, "en"));

        assertEquals("1 KB", Number.fileSize(1024));
        assertEquals("1.50 KB", Number.fileSize(1536, 2));
        assertEquals("1.5 KB", Number.fileSize(1536, 0, 2));
        assertEquals("1M", Number.abbreviate(1_000_000));
        assertEquals("1.2M", Number.abbreviate(1_200_000, 1));
        assertEquals("1.25M", Number.abbreviate(1_250_000, 0, 2));
        assertEquals("1 million", Number.forHumans(1_000_000));
        assertEquals("1.2 million", Number.forHumans(1_200_000, 1));
        assertEquals("1.25 million", Number.forHumans(1_250_000, 0, 2));
        assertEquals("1.25M", Number.forHumans(1_250_000, 0, 2, true));
    }

    @Test
    void helpers() {
        assertEquals(3, Number.clamp(5, 1, 3));
        List<Pair<Long, Long>> pages = Number.pairs(25, 10);
        assertEquals(3, pages.size());
        assertEquals(Long.valueOf(9), pages.get(0).getSecond());
        assertEquals(2, Number.pairs(25, 10, 5).size());
        assertEquals(Long.valueOf(10), Number.pairs(25, 10, 0, 0).get(0).getSecond());
        assertEquals(3, Number.pairs(new BigDecimal("25"), BigDecimal.TEN).size());
        assertEquals(new BigDecimal("12.3"), Number.trim(12.30));

        assertEquals(new BigDecimal("1234.5"), Number.parse("1,234.5"));
        assertEquals(new BigDecimal("1234.5"), Number.parse("1.234,5", "de"));
        assertEquals(Integer.valueOf(1234), Number.parseInt("1,234"));
        assertEquals(Double.valueOf(1234.5), Number.parseFloat("1,234.5"));
        assertNull(Number.parseInt("nope", "en"));
        assertNull(Number.parseFloat("nope", "en"));
    }

    @Test
    void localeScopes() {
        Number.useLocale("de");
        assertEquals("de", Number.defaultLocale());
        assertEquals("1.234,5", Number.format(1234.5));
        assertEquals("1,234.5", Number.withLocale("en", () -> Number.format(1234.5)));
        assertEquals("de", Number.defaultLocale());
    }
}
