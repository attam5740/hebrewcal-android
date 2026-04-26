package com.hebrewcal.service

import android.content.Context
import androidx.work.*
import com.hebrewcal.service.CalendarNotificationService
import java.util.concurrent.TimeUnit
import java.util.Calendar

class MidnightUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        CalendarNotificationService.refresh(context)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "midnight_hebrew_cal_update"

        fun schedule(context: Context) {
            // Calculate initial delay to next midnight
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DATE, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
            }
            val delayMs = midnight.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<MidnightUpdateWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
