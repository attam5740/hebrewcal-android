package com.hebrewcal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ShabbatBannerTest {

    private fun at(h: Int, m: Int): Date = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 20, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun times(
        cl: Date? = at(16, 12),
        hav: Date? = at(17, 30),
        shabbat: Boolean = false,
        yomTov: Boolean = false,
        flame: Boolean = false
    ) = ShabbatTimesResult(
        isErevShabbat = cl != null && !shabbat, isShabbat = shabbat,
        isErevYomTov = false, isYomTov = yomTov,
        candleLighting = cl, havdalah = hav, havdalahRt = null,
        parshaName = null, yomTovName = null, fromExistingFlame = flame
    )

    private val f24 = ZmanimTimeFormat.TWENTY_FOUR_HOUR
    private val f12 = ZmanimTimeFormat.TWELVE_HOUR

    private fun text(t: ShabbatTimesResult?, now: Date, heb: Boolean = false, f: ZmanimTimeFormat = f24) =
        ShabbatBanner.text(t, now.time, heb, f, Locale.US)

    @Test fun nullTimesGivesNull() = assertNull(text(null, at(10, 0)))

    @Test fun beforeCandleLightingEnglish24h() = assertEquals(
        "🕯️🕯️ Candle Lighting: ⁦16:12⁩", text(times(), at(10, 0))
    )

    @Test fun beforeCandleLightingHebrew() = assertEquals(
        "🕯️🕯️ הדלקת נרות: ⁦16:12⁩", text(times(), at(10, 0), heb = true)
    )

    @Test fun beforeCandleLighting12h() {
        val expected = SimpleDateFormat("h:mm a", Locale.US).format(at(16, 12))
        assertEquals("🕯️🕯️ Candle Lighting: ⁦$expected⁩", text(times(), at(10, 0), f = f12))
    }

    @Test fun fromExistingFlameUsesArrowByDirection() {
        val t = times(flame = true)
        assertEquals("🕯️→🕯️ Candle Lighting: ⁦16:12⁩", text(t, at(10, 0)))
        assertEquals("🕯️←🕯️ הדלקת נרות: ⁦16:12⁩", text(t, at(10, 0), heb = true))
    }

    @Test fun betweenLightingAndHavdalahEnglish() = assertEquals(
        "Shabbat Shalom · Havdalah ⁦17:30⁩", text(times(), at(16, 30))
    )

    @Test fun betweenLightingAndHavdalahHebrew() = assertEquals(
        "שַׁבָּת שָׁלוֹם · הבדלה ⁦17:30⁩", text(times(), at(16, 30), heb = true)
    )

    @Test fun havdalah12h() {
        val expected = SimpleDateFormat("h:mm a", Locale.US).format(at(17, 30))
        assertEquals("Shabbat Shalom · Havdalah ⁦$expected⁩", text(times(), at(16, 30), f = f12))
    }

    @Test fun afterHavdalahGivesNull() = assertNull(text(times(), at(18, 0)))

    @Test fun shabbatWithNoHavdalahIsJustTitle() {
        val t = times(cl = null, hav = null, shabbat = true)
        assertEquals("Shabbat Shalom", text(t, at(12, 0)))
        assertEquals("שַׁבָּת שָׁלוֹם", text(t, at(12, 0), heb = true))
    }

    @Test fun yomTovFollowedByYomTovHasNoHavdalah() {
        val t = times(cl = at(16, 12), hav = null, yomTov = true)
        assertEquals("Shabbat Shalom", text(t, at(17, 0)))
    }

    @Test fun ordinaryDayGivesNull() = assertNull(text(times(cl = null, hav = null), at(12, 0)))
}
