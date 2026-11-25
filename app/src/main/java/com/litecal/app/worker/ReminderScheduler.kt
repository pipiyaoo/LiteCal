package com.litecal.app.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.litecal.app.domain.model.UserProfile
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun syncSchedules(profile: UserProfile) {
        if (profile.morningReminderEnabled) {
            scheduleMorningWeight(profile.morningReminderHour, profile.morningReminderMinute)
        } else {
            workManager.cancelUniqueWork(WORK_MORNING)
        }

        if (profile.eveningReminderEnabled) {
            scheduleEveningMealCheck(profile.eveningReminderHour, profile.eveningReminderMinute)
        } else {
            workManager.cancelUniqueWork(WORK_EVENING)
        }
    }

    fun scheduleMorningWeight(hour: Int, minute: Int) {
        enqueueNextAt(
            uniqueName = WORK_MORNING,
            type = SmartReminderWorker.TYPE_MORNING_WEIGHT,
            hour = hour,
            minute = minute
        )
    }

    fun scheduleEveningMealCheck(hour: Int, minute: Int) {
        enqueueNextAt(
            uniqueName = WORK_EVENING,
            type = SmartReminderWorker.TYPE_EVENING_MEAL_CHECK,
            hour = hour,
            minute = minute
        )
    }

    private fun enqueueNextAt(uniqueName: String, type: String, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var target = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now.plusMinutes(1))) {
            target = target.plusDays(1)
        }
        val delayMillis = Duration.between(now, target).toMillis().coerceAtLeast(60_000L)

        val request = OneTimeWorkRequestBuilder<SmartReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(SmartReminderWorker.KEY_REMINDER_TYPE, type)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val WORK_MORNING = "litecal_morning_weight_reminder"
        private const val WORK_EVENING = "litecal_evening_meal_reminder"
    }
}
