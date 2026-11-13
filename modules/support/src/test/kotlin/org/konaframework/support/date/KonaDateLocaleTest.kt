package org.konaframework.support.date

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.Locale

class KonaDateLocaleTest {
    @AfterEach
    fun resetLocale() {
        KonaDate.resetHumanLocale()
    }

    @Test
    fun phrasePacksForEachLocale() {
        val base = KonaDate.parse("2024-06-15T12:00:00Z")
        val clock = Clock.fixed(base.toInstant(), ZoneOffset.UTC)
        val past = base.subHours(3)
        val expected = mapOf(
            "en" to "3 hours ago",
            "es" to "hace 3 horas",
            "es-MX" to "hace 3 horas",
            "fr" to "il y a 3 heures",
            "de" to "vor 3 Stunden",
            "it" to "3 ore fa",
            "pt" to "há 3 horas",
            "pt-BR" to "há 3 horas",
            "nl" to "3 uur geleden",
            "sv" to "för 3 timmar sedan",
            "da" to "for 3 timer siden",
            "nb" to "for 3 timer siden",
            "nn" to "for 3 timar sidan",
            "no" to "for 3 timer siden",
            "zh" to "3小时前",
            "zh-CN" to "3小时前",
            "zh-Hans" to "3小时前",
            "zh-TW" to "3小時前",
            "zh-Hant" to "3小時前",
            "ja" to "3時間前",
            "ko" to "3시간 전",
            "id" to "3 jam yang lalu",
            "tr" to "3 saat önce",
            "ru" to "3 часа назад",
            "xx" to "3 hours ago",
        )
        for ((tag, phrase) in expected) {
            assertEquals(phrase, past.diffForHumans(null, false, clock, Locale.forLanguageTag(tag)), tag)
        }
        assertEquals("dentro de 3 horas", base.addHours(3).diffForHumans(null, false, clock, Locale.forLanguageTag("es")))
        assertEquals("3 horas antes", past.diffForHumans(base, false, clock, Locale.forLanguageTag("es")))
        assertEquals("3 horas después", base.addHours(3).diffForHumans(base, false, clock, Locale.forLanguageTag("es")))
        assertEquals("3 horas", past.diffForHumans(base, true, clock, Locale.forLanguageTag("es")))
        assertEquals("0 seconde", base.diffForHumans(base, true, clock, Locale.FRENCH))
        assertEquals("dans 1 seconde", base.addSecond().diffForHumans(null, false, clock, Locale.FRENCH))
        assertEquals("dans 2 secondes", base.addSeconds(2).diffForHumans(null, false, clock, Locale.FRENCH))
        assertEquals("1 год назад", base.subYears(1).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("2 года назад", base.subYears(2).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("4 года назад", base.subYears(4).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("5 лет назад", base.subYears(5).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("11 лет назад", base.subYears(11).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("12 лет назад", base.subYears(12).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("14 лет назад", base.subYears(14).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("21 год назад", base.subYears(21).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("22 года назад", base.subYears(22).diffForHumans(null, false, clock, Locale.forLanguageTag("ru")))
        assertEquals("one", HumanDiff.pluralCategory("one-other", 1))
        assertEquals("other", HumanDiff.pluralCategory("one-other", 0))
        assertEquals("other", HumanDiff.pluralCategory("cjk", 3))
        assertTrue(base.diffForHumans(Locale.GERMAN).isNotEmpty())

        KonaDate.setHumanLocale(Locale.FRENCH)
        assertEquals(Locale.FRENCH, KonaDate.humanLocale())
        assertEquals("il y a 3 heures", past.diffForHumans(null, false, clock))
        KonaDate.resetHumanLocale()
        assertEquals(Locale.ENGLISH, KonaDate.humanLocale())
        assertEquals("3 hours ago", past.diffForHumans(null, false, clock))
    }
}
