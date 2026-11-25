package com.litecal.app

import android.app.Application
import com.litecal.app.data.local.LiteCalDatabase
import com.litecal.app.data.local.SeedDataLoader
import com.litecal.app.data.local.UserPrefsStore
import com.litecal.app.data.remote.AiFoodEstimator
import com.litecal.app.data.repository.LiteCalRepository
import com.litecal.app.worker.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiteCalApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: LiteCalDatabase by lazy { LiteCalDatabase.getInstance(this) }
    val prefsStore: UserPrefsStore by lazy { UserPrefsStore(this) }
    val aiEstimator: AiFoodEstimator by lazy { AiFoodEstimator(this) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }

    val repository: LiteCalRepository by lazy {
        LiteCalRepository(
            appContext = this,
            dao = database.dao(),
            prefs = prefsStore,
            aiEstimator = aiEstimator
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            SeedDataLoader.ensureSeeded(this@LiteCalApplication, database.dao())
            val profile = prefsStore.getUserProfileOnce()
            if (profile.onboardingCompleted) {
                reminderScheduler.syncSchedules(profile)
            }
        }
    }
}
