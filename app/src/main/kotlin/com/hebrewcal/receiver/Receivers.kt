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
            Intent.ACTION_TIME_SET,
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
            ACTION_MIDNIGHT_REFRESH -> {
                CalendarNotificationService.refresh(context)
            }
        }
    }

    companion object {
        const val ACTION_ZMAN_TRANSITION = "com.hebrewcal.ZMAN_TRANSITION"
        const val ACTION_MIDNIGHT_REFRESH = "com.hebrewcal.MIDNIGHT_REFRESH"
        const val EXTRA_ZMAN_KEY = "zman_key"
    }
}
