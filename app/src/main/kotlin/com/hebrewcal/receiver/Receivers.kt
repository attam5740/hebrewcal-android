package com.hebrewcal.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hebrewcal.service.CalendarNotificationService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                CalendarNotificationService.start(context)
            }
        }
    }
}

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED -> {
                CalendarNotificationService.refresh(context)
            }
        }
    }
}

class ZmanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ZMAN_TRANSITION,
            ACTION_MIDNIGHT_REFRESH,
            ACTION_WIDGET_REFRESH,
            ACTION_SERVICE_RESTART -> {
                // refresh() routes through CalendarNotificationService, which redraws
                // both the lockscreen notification and the home-screen widget and
                // re-arms the next tzet-hakochavim widget refresh.
                CalendarNotificationService.refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_ZMAN_TRANSITION  = "com.hebrewcal.ZMAN_TRANSITION"
        const val ACTION_MIDNIGHT_REFRESH = "com.hebrewcal.MIDNIGHT_REFRESH"
        const val ACTION_WIDGET_REFRESH   = "com.hebrewcal.WIDGET_REFRESH"
        const val ACTION_SERVICE_RESTART  = "com.hebrewcal.SERVICE_RESTART"
        const val EXTRA_ZMAN_KEY = "zman_key"
    }
}
