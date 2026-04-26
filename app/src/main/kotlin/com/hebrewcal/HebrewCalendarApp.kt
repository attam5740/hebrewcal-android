package com.hebrewcal

import android.app.Application
import com.hebrewcal.service.CalendarNotificationService
import com.hebrewcal.service.MidnightUpdateWorker

class HebrewCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try { CalendarNotificationService.start(this) } catch (e: Exception) { /* non-fatal */ }
        try { MidnightUpdateWorker.schedule(this) }    catch (e: Exception) { /* non-fatal */ }
    }
}
