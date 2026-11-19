package com.hebrewcal

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.hebrewcal.service.CalendarNotificationService
import com.hebrewcal.service.TextPrefetch

class HebrewCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start service immediately so the foreground notification appears.
        try { CalendarNotificationService.refresh(this) } catch (e: Exception) { /* non-fatal */ }
        // Defer job scheduling off the main-thread critical path.
        Handler(Looper.getMainLooper()).post {
            try { TextPrefetch.scheduleFromPrefs(this) } catch (e: Exception) { /* non-fatal */ }
        }
    }
}
