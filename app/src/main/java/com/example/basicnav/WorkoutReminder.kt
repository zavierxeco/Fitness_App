package com.example.basicnav

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkoutReminder {
    const val CHANNEL_ID = "workout_reminders"
    const val UNIQUE_WORK_NAME = "daily_workout_reminder_22"
    const val NOTIFICATION_ID = 2200

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminders to complete today's workout."
        }
        mgr.createNotificationChannel(channel)
    }

    fun scheduleNext22(context: Context) {
        ensureChannel(context)

        val now = LocalDateTime.now()
        val targetToday = LocalDateTime.of(now.toLocalDate(), LocalTime.of(22, 0))
        val target = if (now.isBefore(targetToday)) targetToday else targetToday.plusDays(1)
        val delayMs = Duration.between(now, target).toMillis().coerceAtLeast(0)

        val req = OneTimeWorkRequestBuilder<WorkoutReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun postNotification(context: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }
}

