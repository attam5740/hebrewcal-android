package com.hebrewcal.data

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The candle-lighting / "Shabbat Shalom · Havdalah" banner shared by the lockscreen
 * notification and the home-screen widget, so both always agree.
 */
object ShabbatBanner {

    /**
     * Fixed times: candle lighting until it is lit, then Shabbat Shalom until Havdalah,
     * then nothing. Returns null when no banner is relevant.
     */
    fun text(
        times: ShabbatTimesResult?,
        nowMs: Long,
        hebrew: Boolean,
        timeFormat: ZmanimTimeFormat,
        locale: Locale = Locale.getDefault()
    ): String? {
        if (times == null) return null
        val timeFmt = SimpleDateFormat(
            if (timeFormat == ZmanimTimeFormat.TWELVE_HOUR) "h:mm a" else "HH:mm",
            locale
        )
        val cl = times.candleLighting
        val hav = times.havdalah
        return when {
            cl != null && nowMs < cl.time ->
                ShabbatTimes.candles(hebrew, times.fromExistingFlame) + " " +
                    ShabbatTimes.formatCandleLighting(hebrew, timeFmt.format(cl))
            hav != null && nowMs >= hav.time -> null
            cl != null || times.isShabbat || times.isYomTov -> {
                val shabbatTitle = if (hebrew) "שַׁבָּת שָׁלוֹם" else "Shabbat Shalom"
                val havStr = hav?.let { ShabbatTimes.ltr(timeFmt.format(it)) }
                if (havStr == null) shabbatTitle
                else if (hebrew) "$shabbatTitle · הבדלה $havStr" else "$shabbatTitle · Havdalah $havStr"
            }
            else -> null
        }
    }
}
