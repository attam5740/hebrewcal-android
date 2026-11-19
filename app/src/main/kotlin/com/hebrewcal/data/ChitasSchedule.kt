package com.hebrewcal.data

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.TimeZone

/** The day's Chitas: one Chumash aliyah, the day's Tehillim, and the day's Tanya. */
data class ChitasPortion(
    val chumashRef: String,
    val chumashParsha: String,
    val chumashParshaHe: String,
    /** 1-based, 1..7 (Sunday..Shabbat). */
    val aliyahIndex: Int,
    val tehillimRef: String,
    val tehillimFirst: Int,
    val tehillimLast: Int,
    val tanyaRef: String,
    val tanyaLabel: String,
    val tanyaLabelHe: String
)

/** Pure computation: every date passed in is already nightfall-advanced by the caller. */
object ChitasSchedule {

    /**
     * The Shabbat whose parsha this Chitas day belongs to. [advanced] is already
     * nightfall-advanced, so on Saturday evening it is Sunday and this correctly
     * resolves to the coming Shabbat.
     */
    fun parshaDateFor(advanced: LocalDate): LocalDate =
        advanced.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    /**
     * Tanya Yomi is a per-day calendar item, while the aliyot belong to the Shabbat's
     * response. Takes the aliyot/parsha from [weekly] and the Tanya fields from [daily]
     * (the response for the Chitas day itself).
     */
    fun withDailyTanya(weekly: ParshaRef, daily: ParshaRef): ParshaRef =
        weekly.copy(tanyaRef = daily.tanyaRef, tanyaLabel = daily.tanyaLabel, tanyaLabelHe = daily.tanyaLabelHe)

    fun portionFor(advanced: LocalDate, hebrewDay: Int, daysInMonth: Int, parsha: ParshaRef): ChitasPortion {
        // Sunday = 1 .. Saturday = 7; maftir (8th entry) is never used.
        val index = advanced.dayOfWeek.value % 7 + 1
        val ref = parsha.aliyot.take(7).getOrNull(index - 1) ?: ""
        val tehillim = TehillimSchedule.portionFor(hebrewDay, daysInMonth)
        return ChitasPortion(
            chumashRef = ref,
            chumashParsha = parsha.nameEn,
            chumashParshaHe = parsha.nameHe,
            aliyahIndex = index,
            tehillimRef = tehillim.sefariaRef,
            tehillimFirst = tehillim.firstChapter,
            tehillimLast = tehillim.lastChapter,
            tanyaRef = parsha.tanyaRef,
            tanyaLabel = parsha.tanyaLabel,
            tanyaLabelHe = parsha.tanyaLabelHe
        )
    }

    /** Headings for the three legs, in Chumash / Tehillim / Tanya order. */
    fun legTitles(p: ChitasPortion, hebrew: Boolean): List<String> {
        val fmt = HebrewDateFormatter()
        fun n(x: Int) = if (hebrew) fmt.formatHebrewNumber(x) else x.toString()
        val aliyah = AliyotPartitioner.aliyahName(p.aliyahIndex - 1, 7, hebrew)
        val range = if (p.tehillimFirst == p.tehillimLast) n(p.tehillimFirst)
        else "${n(p.tehillimFirst)}–${n(p.tehillimLast)}"
        return if (hebrew) listOf(
            "חומש — ${p.chumashParshaHe}, $aliyah",
            "תהילים $range",
            "תניא — ${p.tanyaLabelHe.ifBlank { p.tanyaLabel }}"
        ) else listOf(
            "Chumash — ${p.chumashParsha}, $aliyah",
            "Tehillim $range",
            "Tanya — ${p.tanyaLabel}"
        )
    }
}

/**
 * The civil date whose Chitas the reader should show: from tzet hakochavim onward the Hebrew
 * day is tomorrow's (same rule as getDateInfo's advanceAfter, hence `>=`, not strict `after`).
 * A null [tzet] (no location configured) means civil-midnight rollover, i.e. no advance.
 * Returns ISO-8601 (yyyy-MM-dd), which the reader parses with LocalDate.parse.
 */
fun chitasAdvancedDate(now: Date, tz: TimeZone, tzet: Date?): String =
    now.toInstant().atZone(tz.toZoneId()).toLocalDate()
        .let { if (tzet != null && !now.before(tzet)) it.plusDays(1) else it }
        .toString()
