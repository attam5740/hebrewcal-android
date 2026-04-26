package com.hebrewcal.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.hebrewcal.data.*
import com.hebrewcal.receiver.ZmanAlarmReceiver
import com.hebrewcal.ui.notification.LockscreenNotificationBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

class CalendarNotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val calRepo = HebrewCalendarRepository()
    private val zmanimRepo = ZmanimRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LockscreenNotificationBuilder.createNotificationChannel(this)
        // Start as foreground immediately with a placeholder
        startForeground(
            LockscreenNotificationBuilder.NOTIFICATION_ID,
            LockscreenNotificationBuilder.build(
                this,
                HebrewDateInfo("", "", null, null, false, 0, "", 0),
                null, false, true, true
            )
        )
        refreshAndSchedule()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshAndSchedule()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshAndSchedule() {
        scope.launch {
            val prefsRepo = UserPreferencesRepository(applicationContext)
            val prefs = prefsRepo.preferences.first()

            // Resolve location for zmanim
            val (lat, lng) = resolveLocation(prefs)

            // Build date info
            val dateInfo = calRepo.getDateInfo(
                date     = Date(),
                language = prefs.language,
                location = prefs.location
            )

            // Build zmanim if enabled and we have location
            val zmanimData = if (prefs.showZmanim && lat != 0.0 && lng != 0.0) {
                zmanimRepo.getZmanim(
                    latitude       = lat,
                    longitude      = lng,
                    timeZone       = TimeZone.getDefault(),
                    date           = Date(),
                    selectedZmanim = prefs.selectedZmanim,
                    timeFormat     = prefs.zmanimTimeFormat
                )
            } else null

            // Post notification
            val notification = LockscreenNotificationBuilder.build(
                context      = applicationContext,
                dateInfo     = dateInfo,
                zmanimData   = zmanimData,
                showZmanim   = prefs.showZmanim,
                showGregorian = prefs.showGregorianDate,
                showParsha   = prefs.showParsha
            )
            NotificationManagerCompat.from(applicationContext)
                .notify(LockscreenNotificationBuilder.NOTIFICATION_ID, notification)

            // Schedule zman transition alarms
            if (prefs.showZmanim && lat != 0.0 && lng != 0.0) {
                scheduleZmanAlarms(lat, lng, prefs)
            }

            // Schedule midnight refresh
            scheduleMidnightAlarm()
        }
    }

    private suspend fun resolveLocation(prefs: UserPreferences): Pair<Double, Double> {
        return when (prefs.zmanimLocationSource) {
            ZmanimLocationSource.GPS -> {
                if (ActivityCompat.checkSelfPermission(
                        applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        val loc = LocationHelper(applicationContext).getCurrentLocation()
                        Pair(loc.latitude, loc.longitude)
                    } catch (e: Exception) {
                        Pair(prefs.zmanimManualLat, prefs.zmanimManualLng)
                    }
                } else {
                    Pair(prefs.zmanimManualLat, prefs.zmanimManualLng)
                }
            }
            ZmanimLocationSource.MANUAL -> Pair(prefs.zmanimManualLat, prefs.zmanimManualLng)
        }
    }

    private fun scheduleZmanAlarms(lat: Double, lng: Double, prefs: UserPreferences) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel any existing zman alarms
        cancelZmanAlarms(alarmManager)

        val futureTimes = zmanimRepo.getFutureZmanimTimes(
            latitude       = lat,
            longitude      = lng,
            timeZone       = TimeZone.getDefault(),
            date           = Date(),
            selectedZmanim = prefs.selectedZmanim
        )

        futureTimes.forEachIndexed { index, (key, time) ->
            val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
                action = ZmanAlarmReceiver.ACTION_ZMAN_TRANSITION
                putExtra(ZmanAlarmReceiver.EXTRA_ZMAN_KEY, key)
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                ZMAN_ALARM_BASE_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time.time,
                    pi
                )
            } catch (e: SecurityException) {
                // SCHEDULE_EXACT_ALARM not granted — fall back to inexact
                alarmManager.set(AlarmManager.RTC_WAKEUP, time.time, pi)
            }
        }
    }

    private fun cancelZmanAlarms(alarmManager: AlarmManager) {
        for (i in 0 until 20) {
            val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
                action = ZmanAlarmReceiver.ACTION_ZMAN_TRANSITION
            }
            val pi = PendingIntent.getBroadcast(
                applicationContext,
                ZMAN_ALARM_BASE_REQUEST_CODE + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleMidnightAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(applicationContext, ZmanAlarmReceiver::class.java).apply {
            action = ZmanAlarmReceiver.ACTION_MIDNIGHT_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext,
            MIDNIGHT_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pi)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pi)
        }
    }

    companion object {
        const val ZMAN_ALARM_BASE_REQUEST_CODE = 2000
        const val MIDNIGHT_ALARM_REQUEST_CODE = 2100

        fun start(context: Context) {
            val intent = Intent(context, CalendarNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, CalendarNotificationService::class.java)
            context.startForegroundService(intent)
        }
    }
}
