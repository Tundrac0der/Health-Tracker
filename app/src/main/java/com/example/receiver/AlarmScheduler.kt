package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    fun scheduleReminder(
        context: Context,
        reminderId: Int,
        medicationName: String,
        dosage: String,
        scheduleType: String,
        hour: Int,
        minute: Int,
        reminderDate: Long? = null,
        isReschedule: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MedicationNotificationReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("medication_name", medicationName)
            putExtra("dosage", dosage)
            putExtra("schedule_type", scheduleType)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            if (scheduleType == "ONCE" && reminderDate != null) {
                timeInMillis = reminderDate
            } else {
                timeInMillis = System.currentTimeMillis()
            }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the scheduled time has already passed, schedule for the next period
        if (calendar.timeInMillis <= System.currentTimeMillis() || isReschedule) {
            when (scheduleType) {
                "DAILY" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> calendar.add(Calendar.MONTH, 1)
                "ONCE" -> {
                    // One-time reminder that has already passed - don't alarm again
                    return
                }
                else -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback for Android 12+ if SCHEDULE_EXACT_ALARM is not granted/allowed
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MedicationNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
