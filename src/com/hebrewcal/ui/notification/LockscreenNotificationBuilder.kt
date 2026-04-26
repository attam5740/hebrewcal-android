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

object LockscreenNotificationBuilder {

    const val CHANNEL_ID = "hebrew_calendar_lockscreen"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hebrew Calendar",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Hebrew date and zmanim on lockscreen"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
        showParsha: Boolean
    ): Notification {
        val settingsIntent = Intent(context, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use custom RemoteViews for rich lockscreen layout
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_hebrew_date)
        val expandedViews = RemoteViews(context.packageName, R.layout.notification_hebrew_date_expanded)

        // --- Collapsed view ---
        remoteViews.setTextViewText(R.id.tv_hebrew_date, dateInfo.hebrewDateString)
        remoteViews.setTextViewText(
            R.id.tv_holiday,
            dateInfo.holidayName ?: if (dateInfo.isShabbat) "שַׁבָּת" else ""
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

        // Zmanim list in expanded view
        if (showZmanim && zmanimData != null && zmanimData.entries.isNotEmpty()) {
            expandedViews.setViewVisibility(R.id.ll_zmanim_container, android.view.View.VISIBLE)
            expandedViews.removeAllViews(R.id.ll_zmanim_container)
            zmanimData.entries.forEach { entry ->
                val rowView = RemoteViews(context.packageName, R.layout.item_zman_row)
                rowView.setTextViewText(R.id.tv_zman_name, entry.displayName)
                rowView.setTextViewText(R.id.tv_zman_time, entry.timeString)

                val alpha = when {
                    entry.isNext -> 1.0f
                    entry.isPast -> 0.4f
                    else         -> 0.75f
                }
                rowView.setFloat(R.id.tv_zman_name, "setAlpha", alpha)
                rowView.setFloat(R.id.tv_zman_time, "setAlpha", alpha)

                if (entry.isNext) {
                    rowView.setTextViewText(R.id.tv_zman_name, "▶ ${entry.displayName}")
                }
                expandedViews.addView(R.id.ll_zmanim_container, rowView)
            }
        } else {
            expandedViews.setViewVisibility(R.id.ll_zmanim_container, android.view.View.GONE)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star_of_david)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(expandedViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }
}
