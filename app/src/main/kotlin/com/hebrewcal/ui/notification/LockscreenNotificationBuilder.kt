package com.hebrewcal.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.hebrewcal.R
import com.hebrewcal.data.HebrewDateInfo
import com.hebrewcal.data.PillLabelFit
import com.hebrewcal.data.PillSpec
import com.hebrewcal.data.ZmanEntry
import com.hebrewcal.data.ZmanimData
import com.hebrewcal.ui.calendar.CalendarActivity
import com.hebrewcal.ui.reader.TextReaderActivity

object LockscreenNotificationBuilder {

    // Channel ID bumped to v3: OxygenOS requires IMPORTANCE_HIGH (4) for lockscreen display.
    // IMPORTANCE_DEFAULT (3) sets mShowBanner=false in OxygenOS and is silently excluded
    // from the lockscreen regardless of VISIBILITY_PUBLIC. Sound/vibration are suppressed
    // at the channel level so HIGH importance is silent-but-visible.
    const val CHANNEL_ID = "hebrew_calendar_lockscreen_v3"
    const val NOTIFICATION_ID = 1001
    const val TEHILLIM_ACTION_REQUEST_CODE = 3001
    const val PARSHA_ACTION_REQUEST_CODE = 3002
    const val CHITAS_ACTION_REQUEST_CODE = 3003
    /** First of the request codes for prayer actions; one per [PrayerButton] ordinal. */
    const val PRAYER_ACTION_REQUEST_CODE_BASE = 3010

    /**
     * Horizontal "chrome" the pill row cannot use, in dp. These are DELIBERATELY
     * CONSERVATIVE ESTIMATES: the notification card margin, the decorated-style
     * icon column and the card's end padding are not readable at runtime, and the
     * custom view's own padding is mirrored here by hand from the layout XML.
     * Over-estimating only trims a little pill text; under-estimating clips the
     * LAST pill (Chitas) off the edge. Calibrate on a device; change only here.
     *
     * Calibrated on a OnePlus Nord 2 (OxygenOS, Android 13, 360dp wide) from a
     * screenshot: the pill row starts 36.7dp from the screen edge and the date text
     * ends 36.7dp from the other edge. The custom view's own padding is 4dp a side
     * (collapsed), so the system contributes 32.7dp a side = 65.4dp in total.
     * There is no decorated icon column on this layout, so the content runs nearly
     * edge to edge. 72 adds ~6.7dp of deliberate safety: the earlier guess of 128
     * left ~74dp of the row unused and needlessly shortened the text.
     */
    private const val CARD_CHROME_DP = 72f
    /**
     * Widest screen width we trust, dp. In landscape, multi-window or on foldables
     * the card is NOT `widthPixels - chrome`, and we cannot read its real width,
     * so the width is capped at a phone-portrait maximum: those cases shorten
     * more rather than clip. Deliberate known limit.
     */
    private const val MAX_SCREEN_WIDTH_DP = 600f
    /** Root padding of notification_hebrew_date.xml (4dp each side). */
    private const val COLLAPSED_LAYOUT_PADDING_DP = 8f
    /** Root padding of notification_hebrew_date_expanded.xml (8dp each side). */
    private const val EXPANDED_LAYOUT_PADDING_DP = 16f
    /** Pill text sizes, sp (match textSize in the layouts). */
    private const val COLLAPSED_PILL_TEXT_SP = 9f
    private const val EXPANDED_PILL_TEXT_SP = 9.5f
    /** Pill horizontal padding (left + right) and inter-pill margin, dp (match layouts). */
    private const val COLLAPSED_PILL_PADDING_DP = 22f
    private const val EXPANDED_PILL_PADDING_DP = 24f
    private const val COLLAPSED_PILL_GAP_DP = 6f
    private const val EXPANDED_PILL_GAP_DP = 8f

    /** Display strings for one row of pills; null where a pill is absent. */
    private data class FittedPills(val tehillim: String?, val parsha: String?, val chitas: String?)

    private fun fitRow(
        context: Context,
        tehillim: PillSpec?,
        parsha: PillSpec?,
        chitas: String?,
        textSizeSp: Float,
        layoutPaddingDp: Float,
        pillPaddingDp: Float,
        gapDp: Float
    ): FittedPills {
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, metrics)
        }
        fun dp(v: Float) = v * metrics.density
        val specs = listOfNotNull(tehillim, parsha, chitas?.let { PillSpec(it, null) })
        val budget = minOf(metrics.widthPixels.toFloat(), dp(MAX_SCREEN_WIDTH_DP)) - dp(CARD_CHROME_DP + layoutPaddingDp)
        val out = PillLabelFit.fit(
            specs, budget, dp(pillPaddingDp), dp(gapDp),
            PillLabelFit.leadingMarginCount(tehillim != null, parsha != null, chitas != null)
        ) { paint.measureText(it) }
        var i = 0
        return FittedPills(
            tehillim = if (tehillim != null) out[i++] else null,
            parsha = if (parsha != null) out[i++] else null,
            chitas = if (chitas != null) out[i] else null
        )
    }

    /** A time-limited prayer shortcut shown as a standard notification action. */
    data class PrayerAction(val key: String, val label: String, val title: String, val ref: String, val requestCode: Int)

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hebrew Calendar",
            NotificationManager.IMPORTANCE_HIGH   // OxygenOS requires HIGH to set mShowBanner=true
        ).apply {
            description = "Hebrew date and zmanim on lockscreen"
            setShowBadge(false)
            setSound(null, null)          // silent — no alert sound despite HIGH importance
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)            // show through Bedtime/DND if user grants policy access
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        dateInfo: HebrewDateInfo,
        zmanimData: ZmanimData?,
        showZmanim: Boolean,
        showGregorian: Boolean,
        showParsha: Boolean,
        showOmer: Boolean = true,
        tehillimActionSpec: PillSpec? = null,
        tehillimActionRef: String? = null,
        parshaActionSpec: PillSpec? = null,
        diaspora: Boolean = true,
        languageName: String = "ENGLISH",
        elulRef: String? = null,
        shabbatTimes: com.hebrewcal.data.ShabbatTimesResult? = null,
        showShabbatBanner: Boolean = true,
        timeFormat: com.hebrewcal.data.ZmanimTimeFormat = com.hebrewcal.data.ZmanimTimeFormat.TWELVE_HOUR,
        /** Prayers whose time window is open now (see PrayerWindows). */
        prayerActions: List<PrayerAction> = emptyList(),
        /** Chitas pill label; null hides the pill. */
        chitasActionLabel: String? = null,
        /** Nightfall-advanced civil date (ISO-8601) the Chitas reader should show. */
        chitasAdvancedDate: String = ""
    ): Notification {
        val contentIntent = Intent(context, CalendarActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use custom RemoteViews for rich lockscreen layout
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_hebrew_date)
        val expandedViews = RemoteViews(context.packageName, R.layout.notification_hebrew_date_expanded)

        val omerText = if (showOmer && dateInfo.omerDay > 0) dateInfo.omerText else null

        val isHeb = languageName == "HEBREW"
        val nowMs = System.currentTimeMillis()
        val shabbatBannerText: String? = if (showShabbatBanner)
            com.hebrewcal.data.ShabbatBanner.text(shabbatTimes, nowMs, isHeb, timeFormat)
        else null

        val primaryHolidayText = shabbatBannerText
            ?: dateInfo.holidayName
            ?: if (dateInfo.isShabbat) "שַׁבָּת"
            else omerText ?: ""

        // --- Collapsed view ---
        remoteViews.setTextViewText(R.id.tv_hebrew_date, dateInfo.hebrewDateString)
        remoteViews.setTextViewText(R.id.tv_holiday, primaryHolidayText)
        if (showGregorian) {
            remoteViews.setTextViewText(R.id.tv_gregorian, dateInfo.gregorianDateString)
            remoteViews.setViewVisibility(R.id.tv_gregorian, android.view.View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.tv_gregorian, android.view.View.GONE)
        }

        // Highlight next zman in collapsed view
        val nextZman = zmanimData?.entries?.firstOrNull { it.isNext }
        if (showZmanim && nextZman != null) {
            val nextLine = if (isHeb) {
                "◀ ${com.hebrewcal.data.ZmanimKeys.hebrewName(nextZman.key)}  ${com.hebrewcal.data.ShabbatTimes.ltr(nextZman.timeString)}"
            } else {
                "▶ ${nextZman.displayName}  ${nextZman.timeString}"
            }
            remoteViews.setTextViewText(R.id.tv_next_zman, nextLine)
            remoteViews.setViewVisibility(R.id.tv_next_zman, android.view.View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.tv_next_zman, android.view.View.GONE)
        }

        // --- Expanded view ---
        expandedViews.setTextViewText(R.id.tv_hebrew_date_exp, dateInfo.hebrewDateString)
        expandedViews.setTextViewText(R.id.tv_holiday_exp, primaryHolidayText)
        if (showGregorian) {
            expandedViews.setTextViewText(R.id.tv_gregorian_exp, dateInfo.gregorianDateString)
            expandedViews.setViewVisibility(R.id.tv_gregorian_exp, android.view.View.VISIBLE)
        } else {
            expandedViews.setViewVisibility(R.id.tv_gregorian_exp, android.view.View.GONE)
        }

        if (showParsha && dateInfo.parshaName != null) {
            expandedViews.setTextViewText(R.id.tv_parsha_exp, dateInfo.parshaName)
            expandedViews.setViewVisibility(R.id.tv_parsha_exp, android.view.View.VISIBLE)
        } else {
            expandedViews.setViewVisibility(R.id.tv_parsha_exp, android.view.View.GONE)
        }

        if (omerText != null) {
            expandedViews.setTextViewText(R.id.tv_omer_exp, omerText)
            expandedViews.setViewVisibility(R.id.tv_omer_exp, android.view.View.VISIBLE)
        } else {
            expandedViews.setViewVisibility(R.id.tv_omer_exp, android.view.View.GONE)
        }

        // Zmanim list in expanded view
        if (showZmanim && zmanimData != null && zmanimData.entries.isNotEmpty()) {
            expandedViews.setViewVisibility(R.id.ll_zmanim_container, android.view.View.VISIBLE)
            expandedViews.removeAllViews(R.id.ll_zmanim_col0)
            expandedViews.removeAllViews(R.id.ll_zmanim_col1)
            zmanimData.entries.forEachIndexed { index, entry ->
                val rowView = RemoteViews(context.packageName, if (isHeb) R.layout.item_zman_row_rtl else R.layout.item_zman_row)
                val name = com.hebrewcal.data.ZmanimKeys.shortName(entry.key, isHeb)
                rowView.setTextViewText(R.id.tv_zman_name, name)
                rowView.setTextViewText(R.id.tv_zman_time, entry.timeString)

                // setAlpha via setFloat is blocked by OxygenOS RemoteViews policy;
                // use setTextColor with ARGB values to achieve the same dimming effect.
                val textColor = when {
                    entry.isNext -> 0xFFCCCCCC.toInt()
                    entry.isPast -> 0xFF525252.toInt()
                    else         -> 0xFF999999.toInt()
                }
                rowView.setInt(R.id.tv_zman_name, "setTextColor", textColor)
                rowView.setInt(R.id.tv_zman_time, "setTextColor", textColor)

                if (entry.isNext) {
                    rowView.setTextViewText(R.id.tv_zman_name, if (isHeb) "◀ $name" else "▶ $name")
                }
                // Hebrew reads right to left, so the first zman goes in the right-hand column.
                val firstCol = (index % 2 == 0) != isHeb
                expandedViews.addView(if (firstCol) R.id.ll_zmanim_col0 else R.id.ll_zmanim_col1, rowView)
            }
        } else {
            expandedViews.setViewVisibility(R.id.ll_zmanim_container, android.view.View.GONE)
        }

        // Reader buttons — drawn inside the custom layout with our own bright pills
        // (system addAction pills are OEM-recolored and ignore setColor).
        var anyReaderButton = false
        val tehillimShown = if (tehillimActionRef != null) tehillimActionSpec else null
        val collapsedPills = fitRow(
            context, tehillimShown, parshaActionSpec, chitasActionLabel,
            COLLAPSED_PILL_TEXT_SP, COLLAPSED_LAYOUT_PADDING_DP, COLLAPSED_PILL_PADDING_DP, COLLAPSED_PILL_GAP_DP
        )
        val expandedPills = fitRow(
            context, tehillimShown, parshaActionSpec, chitasActionLabel,
            EXPANDED_PILL_TEXT_SP, EXPANDED_LAYOUT_PADDING_DP, EXPANDED_PILL_PADDING_DP, EXPANDED_PILL_GAP_DP
        )
        if (tehillimActionSpec != null && tehillimActionRef != null) {
            val intent = Intent(context, TextReaderActivity::class.java).apply {
                putExtra(TextReaderActivity.EXTRA_MODE, "tehillim")
                putExtra(TextReaderActivity.EXTRA_REF, tehillimActionRef)
                putExtra(TextReaderActivity.EXTRA_TITLE, tehillimActionSpec.full)
                putExtra(TextReaderActivity.EXTRA_LANG, languageName)
                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                putExtra(TextReaderActivity.EXTRA_TEAMIM, false)
                putExtra(TextReaderActivity.EXTRA_ELUL_REF, elulRef ?: "")
                data = android.net.Uri.parse("hebrewcal://reader/tehillim")
            }
            val pi = PendingIntent.getActivity(
                context, TEHILLIM_ACTION_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            expandedViews.setTextViewText(R.id.tv_btn_tehillim, expandedPills.tehillim)
            expandedViews.setViewVisibility(R.id.tv_btn_tehillim, android.view.View.VISIBLE)
            expandedViews.setOnClickPendingIntent(R.id.tv_btn_tehillim, pi)
            remoteViews.setTextViewText(R.id.tv_btn_tehillim_c, collapsedPills.tehillim)
            remoteViews.setViewVisibility(R.id.tv_btn_tehillim_c, android.view.View.VISIBLE)
            remoteViews.setOnClickPendingIntent(R.id.tv_btn_tehillim_c, pi)
            anyReaderButton = true
        } else {
            expandedViews.setViewVisibility(R.id.tv_btn_tehillim, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.tv_btn_tehillim_c, android.view.View.GONE)
        }
        // Time-limited prayers (Havdalah, candle lighting, ...) are standard notification
        // actions below the content, so the reader pills stay as they are.
        val prayerNotificationActions = prayerActions.map { p ->
            val intent = Intent(context, TextReaderActivity::class.java).apply {
                putExtra(TextReaderActivity.EXTRA_MODE, "tefillah")
                putExtra(TextReaderActivity.EXTRA_REF, p.ref)
                putExtra(TextReaderActivity.EXTRA_TITLE, p.title)
                putExtra(TextReaderActivity.EXTRA_LANG, languageName)
                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                putExtra(TextReaderActivity.EXTRA_TEAMIM, false)
                data = android.net.Uri.parse("hebrewcal://reader/prayer/${p.key}")
            }
            val pi = PendingIntent.getActivity(
                context, p.requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(0, p.label, pi).build()
        }
        if (parshaActionSpec != null) {
            val intent = Intent(context, TextReaderActivity::class.java).apply {
                putExtra(TextReaderActivity.EXTRA_MODE, "parsha")
                putExtra(TextReaderActivity.EXTRA_REF, "")
                putExtra(TextReaderActivity.EXTRA_TITLE, parshaActionSpec.full)
                putExtra(TextReaderActivity.EXTRA_DIASPORA, diaspora)
                putExtra(TextReaderActivity.EXTRA_LANG, languageName)
                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                putExtra(TextReaderActivity.EXTRA_TEAMIM, true)
                data = android.net.Uri.parse("hebrewcal://reader/parsha")
            }
            val pi = PendingIntent.getActivity(
                context, PARSHA_ACTION_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            expandedViews.setTextViewText(R.id.tv_btn_parsha, expandedPills.parsha)
            expandedViews.setViewVisibility(R.id.tv_btn_parsha, android.view.View.VISIBLE)
            expandedViews.setOnClickPendingIntent(R.id.tv_btn_parsha, pi)
            remoteViews.setTextViewText(R.id.tv_btn_parsha_c, collapsedPills.parsha)
            remoteViews.setViewVisibility(R.id.tv_btn_parsha_c, android.view.View.VISIBLE)
            remoteViews.setOnClickPendingIntent(R.id.tv_btn_parsha_c, pi)
            anyReaderButton = true
        } else {
            expandedViews.setViewVisibility(R.id.tv_btn_parsha, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.tv_btn_parsha_c, android.view.View.GONE)
        }
        if (chitasActionLabel != null) {
            val intent = Intent(context, TextReaderActivity::class.java).apply {
                putExtra(TextReaderActivity.EXTRA_MODE, "chitas")
                putExtra(TextReaderActivity.EXTRA_REF, "")
                putExtra(TextReaderActivity.EXTRA_TITLE, chitasActionLabel)
                putExtra(TextReaderActivity.EXTRA_DIASPORA, diaspora)
                putExtra(TextReaderActivity.EXTRA_LANG, languageName)
                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                putExtra(TextReaderActivity.EXTRA_TEAMIM, false)
                putExtra(TextReaderActivity.EXTRA_ELUL_REF, "")
                putExtra(TextReaderActivity.EXTRA_ADVANCED_DATE, chitasAdvancedDate)
                data = android.net.Uri.parse("hebrewcal://reader/chitas")
            }
            val pi = PendingIntent.getActivity(
                context, CHITAS_ACTION_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            expandedViews.setTextViewText(R.id.tv_btn_chitas, expandedPills.chitas)
            expandedViews.setViewVisibility(R.id.tv_btn_chitas, android.view.View.VISIBLE)
            expandedViews.setOnClickPendingIntent(R.id.tv_btn_chitas, pi)
            remoteViews.setTextViewText(R.id.tv_btn_chitas_c, collapsedPills.chitas)
            remoteViews.setViewVisibility(R.id.tv_btn_chitas_c, android.view.View.VISIBLE)
            remoteViews.setOnClickPendingIntent(R.id.tv_btn_chitas_c, pi)
            anyReaderButton = true
        } else {
            expandedViews.setViewVisibility(R.id.tv_btn_chitas, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.tv_btn_chitas_c, android.view.View.GONE)
        }
        expandedViews.setViewVisibility(
            R.id.ll_reader_buttons,
            if (anyReaderButton) android.view.View.VISIBLE else android.view.View.GONE
        )
        remoteViews.setViewVisibility(
            R.id.ll_reader_buttons_c,
            if (anyReaderButton) android.view.View.VISIBLE else android.view.View.GONE
        )
        if (anyReaderButton) {
            // Collapsed height budget fits ~2 content rows; keep date + pills (+ holiday)
            // and defer the Gregorian and zman lines to the expanded view.
            remoteViews.setViewVisibility(R.id.tv_gregorian, android.view.View.GONE)
            remoteViews.setViewVisibility(R.id.tv_next_zman, android.view.View.GONE)
        }

        // Build a sub-text for standard lockscreen fallback (shown when custom view isn't rendered)
        val subText = listOfNotNull(
            shabbatBannerText ?: dateInfo.holidayName,
            dateInfo.parshaName
        ).joinToString(" · ").ifEmpty { dateInfo.gregorianDateString }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star_of_david)
            .setContentTitle(dateInfo.hebrewDateString.ifEmpty { "Hebrew Calendar" })
            .setContentText(subText)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Gold accent: OEM skins (incl. OxygenOS) tint action pills / small icon with
            // this color, giving the reader buttons real contrast against the shade.
            .setColor(0xFFD4AF37.toInt())
            .apply { prayerNotificationActions.forEach { addAction(it) } }
            .build()
    }
}
