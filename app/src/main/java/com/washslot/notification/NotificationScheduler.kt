package com.washslot.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.washslot.domain.model.LaundryRequest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a", java.util.Locale.US)

    fun scheduleReminder(request: LaundryRequest) {
        val requestId = request.id ?: return
        
        try {
            val dateTimeString = "${request.preferredDate} ${request.preferredStartTime}"
            val requestDateTime = LocalDateTime.parse(dateTimeString, dateFormatter)
            val reminderTime = requestDateTime.minusMinutes(15)
            
            val triggerAtMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            if (triggerAtMillis <= System.currentTimeMillis()) {
                Log.d("NotificationScheduler", "Reminder time for $requestId is in the past, skipping.")
                return
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("request_id", requestId)
                putExtra("start_time", request.preferredStartTime)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            
            Log.d("NotificationScheduler", "Scheduled reminder for $requestId at $reminderTime")
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to schedule reminder for $requestId", e)
        }
    }

    fun cancelReminder(requestId: String) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d("NotificationScheduler", "Cancelled reminder for $requestId")
        }
    }
}
