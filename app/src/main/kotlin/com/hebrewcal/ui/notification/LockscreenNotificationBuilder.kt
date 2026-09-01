package com.hebrewcal.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.hebrewcal.R
import com.hebrewcal.data.HebrewDateInfo
import com.hebrewcal.data.ZmanEntry
import com.hebrewcal.data.ZmanimData
import com.hebrewcal.ui.settings.SettingsActivity
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
        tehillimActionLabel: String? = null,
        tehillimActionRef: String? = null,
        parshaActionLabel: String? = null,
        diaspora: Boolean = true,
        languageName: String = "ENGLISH"
    ): Notification {
        val settingsIntent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use custom RemoteViews for rich lockscreen layout
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_hebrew_date)
        val expandedViews = RemoteViews(context.packageName, R.layout.notification_hebrew_date_expanded)

        val omerText = if (showOmer && dateInfo.omerDay > 0) dateInfo.omerText else null

        // --- Collapsed view ---
        remoteViews.setTextViewText(R.id.tv_hebrew_date, dateInfo.hebrewDateString)
        remoteViews.setTextViewText(
            R.id.tv_holiday,
            dateInfo.holidayName
                ?: if (dateInfo.isShabbat) "שַׁבָּת"
                else omerText ?: ""
        )
        if (showGregorian) {
            remoteViews.setTextViewText(R.id.tv_gregorian, dateInfo.gregorianDateString)
            remoteViews.setViewVisibility(R.id.tv_gregorian, android.view.View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.tv_gregorian, android.view.View.GONE)
        }

        // Highlight next zman in collapsed view
        val nextZman = zmanimData?.entries?.firstOrNull { it.isNext }
        if (showZmanim && nextZman != null) {
            remoteViews.setTextViewText(R.id.tv_next_zman, "▶ ${nextZman.displayName}  ${nextZman.timeString}")
            remoteViews.setViewVisibility(R.id.tv_next_zman, android.view.View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.tv_next_zman, android.view.View.GONE)
        }

        // --- Expanded view ---
        expandedViews.setTextViewText(R.id.tv_hebrew_date_exp, dateInfo.hebrewDateString)
        expandedViews.setTextViewText(
            R.id.tv_holiday_exp,
            dateInfo.holidayName ?: if (dateInfo.isShabbat) "שַׁבָּת" else ""
        )
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
            expandedViews.removeAllViews(R.id.ll_zmanim_container)
            zmanimData.entries.forEach { entry ->
                val rowView = RemoteViews(context.packageName, R.layout.item_zman_row)
                rowView.setTextViewText(R.id.tv_zman_name, entry.displayName)
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
                    rowView.setTextViewText(R.id.tv_zman_name, "▶ ${entry.displayName}")
                }
                expandedViews.addView(R.id.ll_zmanim_container, rowView)
            }
        } else {
            expandedViews.setViewVisibility(R.id.ll_zmanim_container, android.view.View.GONE)
        }

        // Reader buttons — drawn inside the custom layout with our own bright pills
        // (system addAction pills are OEM-recolored and ignore setColor).
        var anyReaderButton = false
        if (tehillimActionLabel != null && tehillimActionRef != null) {
            val intent = Intent(context, TextReaderActivity::class.java).apply {
                putExtra(TextReaderActivity.EXTRA_MODE, "tehillim")
                putExtra(TextReaderActivity.EXTRA_REF, tehillimActionRef)
                putExtra(TextReaderActivity.EXTRA_TITLE, tehillimActionLabel)
                putExtra(TextReaderActivity.EXTRA_LANG, languageName)
                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                putExtra(TextReaderActivity.EXTRA_TEAMIM, false)
                data = android.net.Uri.parse("hebrewcal://reader/tehillim")
            }
            expandedViews.setTextViewText(R.id.tv_btn_tehillim, tehillimActionLabel)
            expandedViews.setViewVisibility(R.id.tv_btn_tehillim, android.view.View.VISIBLE)
            expandedViews.setOnClickPendingIntent(R.id.tv_btn_tehillim, PendingIntent.getActivity(
                context, TEHILLIM_ACTION_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            anyReaderButton = true
        } else {
            expandedViews.setViewVisibility(R.id.tv_btn_tehillim, android.view.View.GONE)
        }
        if (parshaActionLabel != null) {
            val intent = Intent(context, TextReaderActivity::class.java).apply {
                putExtra(TextReaderActivity.EXTRA_MODE, "parsha")
                putExtra(TextReaderActivity.EXTRA_REF, "")
                putExtra(TextReaderActivity.EXTRA_TITLE, parshaActionLabel)
                putExtra(TextReaderActivity.EXTRA_DIASPORA, diaspora)
                putExtra(TextReaderActivity.EXTRA_LANG, languageName)
                putExtra(TextReaderActivity.EXTRA_NIKKUD, true)
                putExtra(TextReaderActivity.EXTRA_TEAMIM, true)
                data = android.net.Uri.parse("hebrewcal://reader/parsha")
            }
            expandedViews.setTextViewText(R.id.tv_btn_parsha, parshaActionLabel)
            expandedViews.setViewVisibility(R.id.tv_btn_parsha, android.view.View.VISIBLE)
            expandedViews.setOnClickPendingIntent(R.id.tv_btn_parsha, PendingIntent.getActivity(
                context, PARSHA_ACTION_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            anyReaderButton = true
        } else {
            expandedViews.setViewVisibility(R.id.tv_btn_parsha, android.view.View.GONE)
        }
        expandedViews.setViewVisibility(
            R.id.ll_reader_buttons,
            if (anyReaderButton) android.view.View.VISIBLE else android.view.View.GONE
        )

        // Build a sub-text for standard lockscreen fallback (shown when custom view isn't rendered)
        val subText = listOfNotNull(
            dateInfo.holidayName,
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
            .build()
    }
}
