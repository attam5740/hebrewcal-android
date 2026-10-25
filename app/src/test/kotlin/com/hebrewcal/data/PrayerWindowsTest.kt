package com.hebrewcal.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class PrayerWindowsTest {
    private val tz = TimeZone.getTimeZone("America/New_York")
    private val lat = 40.7128
    private val lng = -74.0060
    private val all = PrayerButton.values().toSet()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Date =
        Calendar.getInstance(tz).apply { clear(); set(y, m, d, h, min) }.time

    private fun status(now: Date, enabled: Set<PrayerButton> = all) =
        PrayerWindows.status(now, lat, lng, tz, false, 18, HavdalahOpinion.STANDARD_8_5, enabled)

    private fun activeAt(now: Date) = status(now).active.map { it.button }.toSet()

    private fun cal(day: Date) =
        ComplexZmanimCalendar(GeoLocation("t", lat, lng, 0.0, tz)).apply { calendar.time = day }

    @Test
    fun havdalahOpensAtHavdalahAndClosesThreeHoursLater() {
        // Motzei Shabbat Oct 24, 2026
        val havdalah = ShabbatTimes.calculate(at(2026, Calendar.OCTOBER, 24, 12), lat, lng, timeZone = tz).havdalah!!
        assertFalse(PrayerButton.HAVDALAH in activeAt(Date(havdalah.time - 60_000)))
        assertEquals(havdalah, status(Date(havdalah.time - 60_000), setOf(PrayerButton.HAVDALAH)).nextChange)
        assertTrue(PrayerButton.HAVDALAH in activeAt(Date(havdalah.time + 3_600_000)))
        assertFalse(PrayerButton.HAVDALAH in activeAt(Date(havdalah.time + PrayerWindows.HAVDALAH_LENGTH_MS)))
        assertFalse(PrayerButton.HAVDALAH in activeAt(at(2026, Calendar.OCTOBER, 26, 20)))   // Monday
    }

    @Test
    fun havdalahSkipsShabbatFollowedByYomTov() {
        val day = Calendar.getInstance(tz).apply { time = at(2026, Calendar.OCTOBER, 24, 12) }
        var saturday: Date? = null
        repeat(160) {
            if (saturday == null && JewishCalendar(day.time).apply { inIsrael = false }.isTomorrowShabbosOrYomTov) saturday = day.time
            day.add(Calendar.DATE, 7)
        }
        val evening = Calendar.getInstance(tz).apply { time = saturday!!; set(Calendar.HOUR_OF_DAY, 21) }.time
        assertFalse(PrayerButton.HAVDALAH in activeAt(evening))
    }

    @Test
    fun candleLightingIsOneHourEitherSide() {
        val friday = at(2026, Calendar.OCTOBER, 23, 12)
        val cl = ShabbatTimes.calculate(friday, lat, lng, timeZone = tz).candleLighting!!
        assertTrue(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time - 59 * 60_000)))
        assertTrue(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time + 59 * 60_000)))
        assertFalse(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time - 61 * 60_000)))
        assertFalse(PrayerButton.CANDLE_LIGHTING in activeAt(Date(cl.time + 61 * 60_000)))
        val w = status(Date(cl.time)).active.first { it.button == PrayerButton.CANDLE_LIGHTING }
        assertEquals(CandleKind.SHABBAT, w.candleKind)
        assertFalse(PrayerButton.CANDLE_LIGHTING in activeAt(at(2026, Calendar.OCTOBER, 21, 17)))  // Wednesday
    }

    @Test
    fun birchotHashacharRunsFromDawnToSofZmanShema() {
        val day = at(2026, Calendar.OCTOBER, 21, 12)
        val c = cal(day)
        assertTrue(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.alosHashachar.time + 60_000)))
        assertTrue(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.sofZmanShmaGRA.time - 60_000)))
        assertFalse(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.alosHashachar.time - 60_000)))
        assertFalse(PrayerButton.BIRCHOT_HASHACHAR in activeAt(Date(c.sofZmanShmaGRA.time + 60_000)))
    }

    @Test
    fun shemaAlHamitaRunsFromNightfallPastMidnightToDawn() {
        val c = cal(at(2026, Calendar.OCTOBER, 21, 12))
        val nextDawn = cal(at(2026, Calendar.OCTOBER, 22, 12)).alosHashachar
        assertFalse(PrayerButton.SHEMA_AL_HAMITA in activeAt(Date(c.tzais.time - 60_000)))
        assertTrue(PrayerButton.SHEMA_AL_HAMITA in activeAt(Date(c.tzais.time + 60_000)))
        assertTrue("after midnight", PrayerButton.SHEMA_AL_HAMITA in activeAt(at(2026, Calendar.OCTOBER, 22, 2)))
        assertFalse(PrayerButton.SHEMA_AL_HAMITA in activeAt(Date(nextDawn.time + 60_000)))
    }

    @Test
    fun disabledButtonsNeverShow() {
        val night = at(2026, Calendar.OCTOBER, 21, 23)
        assertTrue(PrayerButton.SHEMA_AL_HAMITA in activeAt(night))
        assertTrue(PrayerButton.ARVIT in activeAt(night))
        assertTrue(status(night, all - PrayerButton.SHEMA_AL_HAMITA).active.none { it.button == PrayerButton.SHEMA_AL_HAMITA })
        assertTrue(status(night, emptySet()).active.isEmpty())
        assertNotNull(status(night).nextChange)
    }

    @Test
    fun everyButtonHasRefsFromItsOwnCustom() {
        for (n in Nusach.values()) for (b in PrayerButton.values()) for (k in CandleKind.values()) {
            val refs = Tefillot.refsFor(b, n, k)
            assertTrue("$n $b", refs.isNotEmpty() && refs.none { it.contains(Tefillot.REF_SEPARATOR) })
        }
        assertTrue(Tefillot.refsFor(PrayerButton.HAVDALAH, Nusach.SEFARD, null).all { it.startsWith("Siddur Sefard") })
        assertTrue(Tefillot.refsFor(PrayerButton.SHEMA_AL_HAMITA, Nusach.EDOT_HAMIZRACH, null).all { it.startsWith("Siddur Edot HaMizrach") })
        // Morning blessings must reach Birchot HaTorah in every custom (Edot HaMizrach's whole
        // "Preparatory Prayers" group ends with its Torah Blessings section).
        for (n in Nusach.values()) {
            val refs = Tefillot.refsFor(PrayerButton.BIRCHOT_HASHACHAR, n, null)
            assertTrue("$n includes the Torah blessings",
                refs.any { it.contains("Torah") } || refs.last() == SiddurIndex.ALL_UNDER + "Siddur Edot HaMizrach, Preparatory Prayers")
        }
    }

    @Test
    fun servicesUseTheWidestTimes() {
        val c = cal(at(2026, Calendar.OCTOBER, 21, 12))     // Wednesday
        val nextDawn = cal(at(2026, Calendar.OCTOBER, 22, 12)).alosHashachar
        fun on(b: PrayerButton, t: Date) = b in activeAt(t)
        val min = 60_000L
        // Shacharit: alot hashachar .. chatzot
        assertTrue(on(PrayerButton.SHACHARIT, Date(c.alosHashachar.time + min)))
        assertTrue(on(PrayerButton.SHACHARIT, Date(c.chatzos.time - min)))
        assertFalse(on(PrayerButton.SHACHARIT, Date(c.chatzos.time + min)))
        // Mincha: mincha gedola .. tzet
        assertFalse(on(PrayerButton.MINCHA, Date(c.minchaGedola.time - min)))
        assertTrue(on(PrayerButton.MINCHA, Date(c.minchaGedola.time + min)))
        assertFalse(on(PrayerButton.MINCHA, Date(c.tzais.time + min)))
        // Arvit: plag .. next alot, across midnight
        assertFalse(on(PrayerButton.ARVIT, Date(c.plagHamincha.time - min)))
        assertTrue(on(PrayerButton.ARVIT, Date(c.plagHamincha.time + min)))
        assertTrue(on(PrayerButton.ARVIT, at(2026, Calendar.OCTOBER, 22, 3)))
        assertFalse(on(PrayerButton.ARVIT, Date(nextDawn.time + min)))
    }

    @Test
    fun shabbatPicksTheRightServices() {
        val friday = cal(at(2026, Calendar.OCTOBER, 23, 12))
        val saturday = cal(at(2026, Calendar.OCTOBER, 24, 12))
        val min = 60_000L
        fun window(b: PrayerButton, t: Date) = status(t).active.first { it.button == b }
        // Friday after plag: Arvit is the Shabbat service; Friday Mincha is Erev Shabbat.
        assertEquals(ServiceDay.SHABBAT, window(PrayerButton.ARVIT, Date(friday.plagHamincha.time + min)).serviceDay)
        assertEquals(ServiceDay.EREV_SHABBAT, window(PrayerButton.MINCHA, Date(friday.plagHamincha.time + min)).serviceDay)
        // Shabbat day: Shacharit and Mincha are the Shabbat services.
        assertEquals(ServiceDay.SHABBAT, window(PrayerButton.SHACHARIT, Date(saturday.chatzos.time - 2 * 3_600_000)).serviceDay)
        assertEquals(ServiceDay.SHABBAT, window(PrayerButton.MINCHA, Date(saturday.plagHamincha.time + min)).serviceDay)
        // Saturday: no weekday Arvit before nightfall; after it, the weekday service.
        assertFalse(PrayerButton.ARVIT in activeAt(Date(saturday.plagHamincha.time + min)))
        assertEquals(ServiceDay.WEEKDAY, window(PrayerButton.ARVIT, Date(saturday.tzais.time + min)).serviceDay)
    }

    @Test
    fun serviceRefsComeFromTheChosenSiddur() {
        for (n in Nusach.values()) for (b in listOf(PrayerButton.SHACHARIT, PrayerButton.MINCHA, PrayerButton.ARVIT))
            for (d in ServiceDay.values()) {
                val refs = Tefillot.refsFor(b, n, null, d)
                // Each part is a whole-service spec or a plain (possibly partial) section ref.
                assertTrue("$n $b $d", refs.isNotEmpty() && refs.all { it.startsWith(SiddurIndex.ALL_UNDER) || it.startsWith("Siddur ") })
            }
        assertTrue(Tefillot.refsFor(PrayerButton.ARVIT, Nusach.ASHKENAZ, null, ServiceDay.SHABBAT).any { it.contains("Kabbalat Shabbat") })
        assertTrue(Tefillot.refsFor(PrayerButton.MINCHA, Nusach.SEFARD, null, ServiceDay.EREV_SHABBAT).single().contains("Shabbat Eve Mincha"))
    }

    @Test
    fun indexLeavesKeepOrderAndPath() {
        val json = """{"schema":{"title":"Siddur X","nodes":[
            {"title":"Weekday","nodes":[
                {"title":"Shacharit","nodes":[{"title":"Modeh Ani"},{"title":"Amidah","nodes":[{"title":""},{"title":"Kedusha"}]}]},
                {"title":"Maariv","nodes":[{"title":"Shema"}]}]},
            {"title":"Shabbat Candle Lighting"}]}}"""
        assertEquals(
            listOf("Siddur X, Weekday, Shacharit, Modeh Ani", "Siddur X, Weekday, Shacharit, Amidah",
                "Siddur X, Weekday, Shacharit, Amidah, Kedusha"),
            SiddurIndex.leaves(json, "Siddur X, Weekday, Shacharit")
        )
        assertEquals(listOf("Siddur X, Shabbat Candle Lighting"), SiddurIndex.leaves(json, "Siddur X, Shabbat Candle Lighting"))
        assertTrue("prefix must match whole titles", SiddurIndex.leaves(json, "Siddur X, Week").isEmpty())
    }

    @Test
    fun morningBlessingsRunFromModehAniThroughBirkatKohanim() {
        val ash = Tefillot.refsFor(PrayerButton.BIRCHOT_HASHACHAR, Nusach.ASHKENAZ, null)
        assertTrue(ash.first().endsWith("Modeh Ani"))
        assertEquals("Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study 1", ash.last())
        // Shacharit picks up exactly where that left off: Eilu Devarim, then the rest of the service.
        val sh = Tefillot.refsFor(PrayerButton.SHACHARIT, Nusach.ASHKENAZ, null, ServiceDay.WEEKDAY)
        assertEquals("Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study 2-3", sh.first())
        assertTrue(sh[1].startsWith(SiddurIndex.ALL_UNDER) && sh[1].contains(SiddurIndex.AFTER + "Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study"))
        val sef = Tefillot.refsFor(PrayerButton.BIRCHOT_HASHACHAR, Nusach.SEFARD, null)
        assertTrue(sef.first().contains("Upon Arising") && sef.last().endsWith("Blessings on Torah 1-6"))
        assertEquals("Siddur Sefard, Weekday Shacharit, Blessings on Torah 7-26",
            Tefillot.refsFor(PrayerButton.SHACHARIT, Nusach.SEFARD, null, ServiceDay.WEEKDAY).first())
        for (n in Nusach.values()) {
            val specs = Tefillot.allPrayerSpecs(n)
            assertTrue("$n has prefetch specs", specs.size >= 10)
            assertEquals("no duplicates", specs.size, specs.distinct().size)
        }
    }

    @Test
    fun specsSelectSectionsAfterALeaf() {
        val json = """{"schema":{"title":"S","nodes":[{"title":"Weekday","nodes":[{"title":"A"},{"title":"B"},{"title":"C"}]}]}}"""
        assertEquals(listOf("S, Weekday, C"), SiddurIndex.leaves(json, "S, Weekday", after = "S, Weekday, B"))
        assertEquals(listOf("S, Weekday, A", "S, Weekday, B", "S, Weekday, C"), SiddurIndex.leaves(json, "S, Weekday", after = "S, Weekday, Nope"))
        assertEquals("S, Weekday" to "S, Weekday, B", SiddurIndex.parseSpec("@S, Weekday>>S, Weekday, B"))
        assertEquals("S, Weekday" to null, SiddurIndex.parseSpec("@S, Weekday"))
    }

    @Test
    fun sectionTitlesDropParagraphRanges() {
        assertEquals(SectionTitle("Torah Study", "לימוד תורה"),
            SefariaRefs.sectionTitle("Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Torah Study 2-3", "סידור אשכנז, ימי חול, תפילת שחרית, הכנה לתפילה, לימוד תורה ב׳-ג׳"))
        assertEquals(SectionTitle("Modeh Ani", "מודה אני"),
            SefariaRefs.sectionTitle("Siddur Ashkenaz, Weekday, Shacharit, Preparatory Prayers, Modeh Ani", "סידור אשכנז, ימי חול, תפילת שחרית, הכנה לתפילה, מודה אני"))
        assertEquals(SectionTitle("Havdalah", ""), SefariaRefs.sectionTitle("Siddur Ashkenaz, Shabbat, Havdalah", ""))
    }

    @Test
    fun ashkenazSectionsMergeIntoTheServiceParts() {
        val p = "Siddur Ashkenaz, Weekday, Shacharit, Pesukei Dezimra, "
        val refs = listOf(
            "Other, Unknown A",                       // outside any part: stays alone
            p + "Introductory Psalm", p + "Ashrei", p + "Psalm 146", p + "Psalm 150",
            "Siddur Ashkenaz, Weekday, Shacharit, Blessings of the Shema, Barchu",
            "Siddur Ashkenaz, Weekday, Shacharit, Blessings of the Shema, Shema"
        )
        val parts = PrayerSections.group(refs)
        assertEquals(listOf(0..0, 1..4, 5..6), parts.map { it.indices })
        assertEquals(null, parts[0].title)
        assertEquals("פסוקי דזמרה", parts[1].title?.he)
        assertEquals("Shema and Its Blessings", parts[2].title?.en)
        assertTrue(PrayerSections.group(emptyList()).isEmpty())
    }

    @Test
    fun specsCanStopBeforeALeaf() {
        val json = """{"schema":{"title":"S","nodes":[{"title":"M","nodes":[{"title":"A"},{"title":"B"},{"title":"C"}]}]}}"""
        val s = SiddurIndex.parse("@S, M<<S, M, B")
        assertEquals(SiddurIndex.Spec("S, M", null, "S, M, B"), s)
        assertEquals(listOf("S, M, A"), SiddurIndex.leaves(json, s.path, s.after, s.before))
        assertTrue(Tefillot.refsFor(PrayerButton.ARVIT, Nusach.ASHKENAZ, null, ServiceDay.WEEKDAY).single().endsWith("Birkat HaLevana"))
    }
}
