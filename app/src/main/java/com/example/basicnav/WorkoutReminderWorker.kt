package com.example.basicnav

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class WorkoutReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = FitnessPlanRepository(applicationContext)
        val plan = repo.loadPlan()
        if (plan == null) {
            // No plan, no reminders. Ensure we don't keep scheduling.
            WorkoutReminder.cancel(applicationContext)
            return Result.success()
        }

        val completed = repo.loadCompletedItems()
        val today = LocalDate.now()

        val inRange = !today.isBefore(plan.startDate) && !today.isAfter(plan.endDate)
        if (inRange) {
            val done = plan.allItemsCompleted(today, completed[today].orEmpty())
            if (!done) {
                WorkoutReminder.postNotification(
                    context = applicationContext,
                    title = "Workout reminder",
                    body = "You’re close to finishing today strong. Open Fitness App and complete today’s workout — you’ve got this."
                )
            }
        }

        // Schedule again for the next 22:00.
        WorkoutReminder.scheduleNext22(applicationContext)
        return Result.success()
    }
}

