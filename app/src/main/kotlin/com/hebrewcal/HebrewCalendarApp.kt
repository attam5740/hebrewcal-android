package com.hebrewcal

import android.app.Application
import com.hebrewcal.service.CalendarNotificationService
import com.hebrewcal.service.MidnightUpdateWorker

class HebrewCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start persistent lockscreen notification service
        CalendarNotificationService.start(this)
        // Schedule daily WorkManager backup refresh
        MidnightUpdateWorker.schedule(this)
    }
}
