package org.konaframework.support.date

import java.util.Locale

/**
 * Relative-time phrases for [KonaDate.diffForHumans].
 *
 * Unknown locales use English. `zh-CN` / `zh-TW` (and Hans/Hant) select the Chinese packs;
 * `pt-BR` uses Portuguese; `nb` / `nn` / `no` use Norwegian Bokmål.
 */
internal object HumanDiff {
    fun phrase(
        count: Long,
        unit: String,
        absolute: Boolean,
        relativeToNow: Boolean,
        ahead: Boolean,
        locale: Locale,
    ): String {
        val pack = resolve(locale)
        val amount = amount(pack, unit, count)
        if (absolute) {
            return amount
        }
        val pattern = if (relativeToNow) {
            if (ahead) pack.fromNow else pack.ago
        } else {
            if (ahead) pack.after else pack.before
        }
        return pattern.replace("{0}", amount)
    }

    fun resolve(locale: Locale): HumanDiffPack {
        val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
        byTag[tag]?.let { return it }
        val language = locale.language.lowercase(Locale.ROOT)
        return byTag[language] ?: ENGLISH
    }

    private fun amount(pack: HumanDiffPack, unit: String, count: Long): String {
        val forms = pack.units.getValue(unit)
        val category = pluralCategory(pack.rule, count)
        val word = when (category) {
            "one" -> forms[0]
            "few" -> forms[1]
            "many" -> forms[2]
            else -> forms.last()
        }
        return if (pack.spaced) "$count $word" else "$count$word"
    }

    internal fun pluralCategory(rule: String, count: Long): String =
        when (rule) {
            "one-other" -> if (count == 1L) "one" else "other"
            "french" -> if (count == 0L || count == 1L) "one" else "other"
            "russian" -> {
                val mod10 = (count % 10).toInt()
                val mod100 = (count % 100).toInt()
                when {
                    mod10 == 1 && mod100 != 11 -> "one"
                    mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "few"
                    else -> "many"
                }
            }
            else -> "other"
        }

    private val ENGLISH = pack(
        "one-other",
        true,
        listOf("second", "seconds"),
        listOf("minute", "minutes"),
        listOf("hour", "hours"),
        listOf("day", "days"),
        listOf("week", "weeks"),
        listOf("month", "months"),
        listOf("year", "years"),
        "{0} ago",
        "{0} from now",
        "{0} before",
        "{0} after",
    )

    private val byTag: Map<String, HumanDiffPack> = mapOf(
        "en" to ENGLISH,
        "es" to pack(
            "one-other", true,
            listOf("segundo", "segundos"),
            listOf("minuto", "minutos"),
            listOf("hora", "horas"),
            listOf("día", "días"),
            listOf("semana", "semanas"),
            listOf("mes", "meses"),
            listOf("año", "años"),
            "hace {0}", "dentro de {0}", "{0} antes", "{0} después",
        ),
        "fr" to pack(
            "french", true,
            listOf("seconde", "secondes"),
            listOf("minute", "minutes"),
            listOf("heure", "heures"),
            listOf("jour", "jours"),
            listOf("semaine", "semaines"),
            listOf("mois", "mois"),
            listOf("an", "ans"),
            "il y a {0}", "dans {0}", "{0} avant", "{0} après",
        ),
        "de" to pack(
            "one-other", true,
            listOf("Sekunde", "Sekunden"),
            listOf("Minute", "Minuten"),
            listOf("Stunde", "Stunden"),
            listOf("Tag", "Tage"),
            listOf("Woche", "Wochen"),
            listOf("Monat", "Monate"),
            listOf("Jahr", "Jahre"),
            "vor {0}", "in {0}", "{0} vorher", "{0} danach",
        ),
        "it" to pack(
            "one-other", true,
            listOf("secondo", "secondi"),
            listOf("minuto", "minuti"),
            listOf("ora", "ore"),
            listOf("giorno", "giorni"),
            listOf("settimana", "settimane"),
            listOf("mese", "mesi"),
            listOf("anno", "anni"),
            "{0} fa", "tra {0}", "{0} prima", "{0} dopo",
        ),
        "pt" to pack(
            "one-other", true,
            listOf("segundo", "segundos"),
            listOf("minuto", "minutos"),
            listOf("hora", "horas"),
            listOf("dia", "dias"),
            listOf("semana", "semanas"),
            listOf("mês", "meses"),
            listOf("ano", "anos"),
            "há {0}", "em {0}", "{0} antes", "{0} depois",
        ),
        "pt-br" to pack(
            "one-other", true,
            listOf("segundo", "segundos"),
            listOf("minuto", "minutos"),
            listOf("hora", "horas"),
            listOf("dia", "dias"),
            listOf("semana", "semanas"),
            listOf("mês", "meses"),
            listOf("ano", "anos"),
            "há {0}", "em {0}", "{0} antes", "{0} depois",
        ),
        "nl" to pack(
            "one-other", true,
            listOf("seconde", "seconden"),
            listOf("minuut", "minuten"),
            listOf("uur", "uur"),
            listOf("dag", "dagen"),
            listOf("week", "weken"),
            listOf("maand", "maanden"),
            listOf("jaar", "jaar"),
            "{0} geleden", "over {0}", "{0} eerder", "{0} later",
        ),
        "sv" to pack(
            "one-other", true,
            listOf("sekund", "sekunder"),
            listOf("minut", "minuter"),
            listOf("timme", "timmar"),
            listOf("dag", "dagar"),
            listOf("vecka", "veckor"),
            listOf("månad", "månader"),
            listOf("år", "år"),
            "för {0} sedan", "om {0}", "{0} innan", "{0} senare",
        ),
        "da" to pack(
            "one-other", true,
            listOf("sekund", "sekunder"),
            listOf("minut", "minutter"),
            listOf("time", "timer"),
            listOf("dag", "dage"),
            listOf("uge", "uger"),
            listOf("måned", "måneder"),
            listOf("år", "år"),
            "for {0} siden", "om {0}", "{0} før", "{0} efter",
        ),
        "nb" to pack(
            "one-other", true,
            listOf("sekund", "sekunder"),
            listOf("minutt", "minutter"),
            listOf("time", "timer"),
            listOf("dag", "dager"),
            listOf("uke", "uker"),
            listOf("måned", "måneder"),
            listOf("år", "år"),
            "for {0} siden", "om {0}", "{0} før", "{0} etter",
        ),
        "nn" to pack(
            "one-other", true,
            listOf("sekund", "sekunder"),
            listOf("minutt", "minutter"),
            listOf("time", "timar"),
            listOf("dag", "dagar"),
            listOf("veke", "veker"),
            listOf("månad", "månader"),
            listOf("år", "år"),
            "for {0} sidan", "om {0}", "{0} før", "{0} etter",
        ),
        "no" to pack(
            "one-other", true,
            listOf("sekund", "sekunder"),
            listOf("minutt", "minutter"),
            listOf("time", "timer"),
            listOf("dag", "dager"),
            listOf("uke", "uker"),
            listOf("måned", "måneder"),
            listOf("år", "år"),
            "for {0} siden", "om {0}", "{0} før", "{0} etter",
        ),
        "zh" to cjk("秒", "分钟", "小时", "天", "周", "个月", "年", "{0}前", "{0}后", "{0}前", "{0}后"),
        "zh-cn" to cjk("秒", "分钟", "小时", "天", "周", "个月", "年", "{0}前", "{0}后", "{0}前", "{0}后"),
        "zh-hans" to cjk("秒", "分钟", "小时", "天", "周", "个月", "年", "{0}前", "{0}后", "{0}前", "{0}后"),
        "zh-tw" to cjk("秒", "分鐘", "小時", "天", "週", "個月", "年", "{0}前", "{0}後", "{0}前", "{0}後"),
        "zh-hant" to cjk("秒", "分鐘", "小時", "天", "週", "個月", "年", "{0}前", "{0}後", "{0}前", "{0}後"),
        "ja" to cjk("秒", "分", "時間", "日", "週間", "か月", "年", "{0}前", "{0}後", "{0}前", "{0}後"),
        "ko" to cjk("초", "분", "시간", "일", "주", "개월", "년", "{0} 전", "{0} 후", "{0} 전", "{0} 후"),
        "id" to pack(
            "other", true,
            listOf("detik"),
            listOf("menit"),
            listOf("jam"),
            listOf("hari"),
            listOf("minggu"),
            listOf("bulan"),
            listOf("tahun"),
            "{0} yang lalu", "dalam {0}", "{0} sebelumnya", "{0} kemudian",
        ),
        "tr" to pack(
            "other", true,
            listOf("saniye"),
            listOf("dakika"),
            listOf("saat"),
            listOf("gün"),
            listOf("hafta"),
            listOf("ay"),
            listOf("yıl"),
            "{0} önce", "{0} sonra", "{0} önce", "{0} sonra",
        ),
        "ru" to pack(
            "russian", true,
            listOf("секунда", "секунды", "секунд"),
            listOf("минута", "минуты", "минут"),
            listOf("час", "часа", "часов"),
            listOf("день", "дня", "дней"),
            listOf("неделя", "недели", "недель"),
            listOf("месяц", "месяца", "месяцев"),
            listOf("год", "года", "лет"),
            "{0} назад", "через {0}", "{0} до", "{0} спустя",
        ),
    )

    private fun pack(
        rule: String,
        spaced: Boolean,
        second: List<String>,
        minute: List<String>,
        hour: List<String>,
        day: List<String>,
        week: List<String>,
        month: List<String>,
        year: List<String>,
        ago: String,
        fromNow: String,
        before: String,
        after: String,
    ): HumanDiffPack = HumanDiffPack(
        rule,
        spaced,
        mapOf(
            "second" to second,
            "minute" to minute,
            "hour" to hour,
            "day" to day,
            "week" to week,
            "month" to month,
            "year" to year,
        ),
        ago,
        fromNow,
        before,
        after,
    )

    private fun cjk(
        second: String,
        minute: String,
        hour: String,
        day: String,
        week: String,
        month: String,
        year: String,
        ago: String,
        fromNow: String,
        before: String,
        after: String,
    ): HumanDiffPack = pack(
        "other",
        false,
        listOf(second),
        listOf(minute),
        listOf(hour),
        listOf(day),
        listOf(week),
        listOf(month),
        listOf(year),
        ago,
        fromNow,
        before,
        after,
    )
}

internal class HumanDiffPack(
    val rule: String,
    val spaced: Boolean,
    val units: Map<String, List<String>>,
    val ago: String,
    val fromNow: String,
    val before: String,
    val after: String,
)
