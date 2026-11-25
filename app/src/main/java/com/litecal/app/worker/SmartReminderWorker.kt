package com.litecal.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.litecal.app.LiteCalApplication
import com.litecal.app.MainActivity
import com.litecal.app.domain.engine.MetabolismEngine
import java.time.LocalDate

class SmartReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LiteCalApplication
        val repository = app.repository
        val profile = repository.prefs.getUserProfileOnce()
        val type = inputData.getString(KEY_REMINDER_TYPE) ?: return Result.success()
        val today = LocalDate.now()

        when (type) {
            TYPE_MORNING_WEIGHT -> {
                if (profile.morningReminderEnabled) {
                    val metric = repository.dao.getBodyMetricForDateOnce(today.toString())
                    if (metric?.weightKg == null) {
                        postGentleNotification(
                            id = 1001,
                            title = "早安 · 记得空腹记录晨重",
                            body = "只需 3 秒录入晨重，即可更新 7 日平滑趋势线并精准校准代谢"
                        )
                    }
                    app.reminderScheduler.scheduleMorningWeight(
                        hour = profile.morningReminderHour,
                        minute = profile.morningReminderMinute
                    )
                }
            }

            TYPE_EVENING_MEAL_CHECK -> {
                if (profile.eveningReminderEnabled) {
                    val override = repository.dao.getDailyOverrideOnce(today.toString())
                    if (override?.isIncompleteDay != true) {
                        val logs = repository.dao.getMealLogsForDateOnce(today.toString())
                        val totalKcal = logs.sumOf { it.totalKcal.toDouble() }.toFloat()
                        val bmr = MetabolismEngine.calculateBmr(profile)
                        if (totalKcal < bmr * 0.5f) {
                            postGentleNotification(
                                id = 1002,
                                title = "今日饮食似乎还没记全？",
                                body = "补记晚餐或开启「未记全」保护开关，避免拉偏自适应 TDEE 校准"
                            )
                        }
                    }
                    app.reminderScheduler.scheduleEveningMealCheck(
                        hour = profile.eveningReminderHour,
                        minute = profile.eveningReminderMinute
                    )
                }
            }
        }
        return Result.success()
    }

    private fun postGentleNotification(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "轻刻防漏记温和提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "仅在晨重未记或晚间疑似漏记时提醒，已记自动静默"
        }
        manager.createNotificationChannel(channel)

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(id, notif)
    }

    companion object {
        const val CHANNEL_ID = "litecal_smart_reminders"
        const val KEY_REMINDER_TYPE = "reminder_type"
        const val TYPE_MORNING_WEIGHT = "MORNING_WEIGHT"
        const val TYPE_EVENING_MEAL_CHECK = "EVENING_MEAL_CHECK"
    }
}
