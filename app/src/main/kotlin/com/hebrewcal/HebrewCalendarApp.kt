package com.hebrewcal

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.hebrewcal.service.CalendarNotificationService
import com.hebrewcal.service.MidnightUpdateWorker

class HebrewCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start service immediately so the foreground notification appears.
        try { CalendarNotificationService.start(this) } catch (e: Exception) { /* non-fatal */ }
        // Defer WorkManager scheduling off the main-thread critical path.
        Handler(Looper.getMainLooper()).post {
            try { MidnightUpdateWorker.schedule(this) } catch (e: Exception) { /* non-fatal */ }
        }
    }
}
