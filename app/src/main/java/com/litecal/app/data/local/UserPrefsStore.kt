package com.litecal.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.litecal.app.domain.model.ActivityLevel
import com.litecal.app.domain.model.AiConfig
import com.litecal.app.domain.model.AiProviderProtocol
import com.litecal.app.domain.model.Gender
import com.litecal.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "litecal_user_prefs")

class UserPrefsStore(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val GENDER = stringPreferencesKey("gender")
        val AGE = intPreferencesKey("age")
        val HEIGHT_CM = floatPreferencesKey("height_cm")
        val START_WEIGHT_KG = floatPreferencesKey("start_weight_kg")
        val TARGET_WEIGHT_KG = floatPreferencesKey("target_weight_kg")
        val ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val WEEKLY_LOSS_KG = floatPreferencesKey("weekly_loss_kg")
        val CUSTOM_PROTEIN_G = intPreferencesKey("custom_protein_g")
        val WATER_TARGET_ML = intPreferencesKey("water_target_ml")
        val WEEKEND_RESERVE = booleanPreferencesKey("weekend_reserve")
        val WEEKEND_SAVE_KCAL = intPreferencesKey("weekend_save_kcal")
        val MORNING_REMINDER = booleanPreferencesKey("morning_reminder")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val MORNING_MINUTE = intPreferencesKey("morning_minute")
        val EVENING_REMINDER = booleanPreferencesKey("evening_reminder")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val EVENING_MINUTE = intPreferencesKey("evening_minute")
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "litecal_ai_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences("litecal_ai_secrets_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _aiConfigFlow = MutableStateFlow(loadAiConfig())
    val aiConfigFlow: StateFlow<AiConfig> = _aiConfigFlow.asStateFlow()

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        prefs.toUserProfile()
    }

    suspend fun getUserProfileOnce(): UserProfile {
        return context.dataStore.data.first().toUserProfile()
    }

    private fun Preferences.toUserProfile(): UserProfile {
        val gender = runCatching {
            Gender.valueOf(this[Keys.GENDER] ?: Gender.MALE.name)
        }.getOrDefault(Gender.MALE)

        val activity = runCatching {
            ActivityLevel.valueOf(this[Keys.ACTIVITY_LEVEL] ?: ActivityLevel.LIGHT.name)
        }.getOrDefault(ActivityLevel.LIGHT)

        val customProt = this[Keys.CUSTOM_PROTEIN_G]?.takeIf { it > 0 }

        return UserProfile(
            onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: false,
            gender = gender,
            age = this[Keys.AGE] ?: 28,
            heightCm = this[Keys.HEIGHT_CM] ?: 172f,
            startWeightKg = this[Keys.START_WEIGHT_KG] ?: 72f,
            targetWeightKg = this[Keys.TARGET_WEIGHT_KG] ?: 64f,
            activityLevel = activity,
            weeklyLossRateKg = this[Keys.WEEKLY_LOSS_KG] ?: 0.5f,
            customProteinTargetG = customProt,
            waterTargetMl = this[Keys.WATER_TARGET_ML] ?: 2000,
            weekendReserveEnabled = this[Keys.WEEKEND_RESERVE] ?: true,
            weekendDailySaveKcal = this[Keys.WEEKEND_SAVE_KCAL] ?: 120,
            morningReminderEnabled = this[Keys.MORNING_REMINDER] ?: true,
            morningReminderHour = this[Keys.MORNING_HOUR] ?: 8,
            morningReminderMinute = this[Keys.MORNING_MINUTE] ?: 0,
            eveningReminderEnabled = this[Keys.EVENING_REMINDER] ?: true,
            eveningReminderHour = this[Keys.EVENING_HOUR] ?: 20,
            eveningReminderMinute = this[Keys.EVENING_MINUTE] ?: 30
        )
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = profile.onboardingCompleted
            prefs[Keys.GENDER] = profile.gender.name
            prefs[Keys.AGE] = profile.age
            prefs[Keys.HEIGHT_CM] = profile.heightCm
            prefs[Keys.START_WEIGHT_KG] = profile.startWeightKg
            prefs[Keys.TARGET_WEIGHT_KG] = profile.targetWeightKg
            prefs[Keys.ACTIVITY_LEVEL] = profile.activityLevel.name
            prefs[Keys.WEEKLY_LOSS_KG] = profile.weeklyLossRateKg
            prefs[Keys.CUSTOM_PROTEIN_G] = profile.customProteinTargetG ?: 0
            prefs[Keys.WATER_TARGET_ML] = profile.waterTargetMl
            prefs[Keys.WEEKEND_RESERVE] = profile.weekendReserveEnabled
            prefs[Keys.WEEKEND_SAVE_KCAL] = profile.weekendDailySaveKcal
            prefs[Keys.MORNING_REMINDER] = profile.morningReminderEnabled
            prefs[Keys.MORNING_HOUR] = profile.morningReminderHour
            prefs[Keys.MORNING_MINUTE] = profile.morningReminderMinute
            prefs[Keys.EVENING_REMINDER] = profile.eveningReminderEnabled
            prefs[Keys.EVENING_HOUR] = profile.eveningReminderHour
            prefs[Keys.EVENING_MINUTE] = profile.eveningReminderMinute
        }
    }

    private fun loadAiConfig(): AiConfig {
        val protocolName = encryptedPrefs.getString("protocol", AiProviderProtocol.OPENAI_COMPATIBLE.name)
        val protocol = runCatching {
            AiProviderProtocol.valueOf(protocolName ?: AiProviderProtocol.OPENAI_COMPATIBLE.name)
        }.getOrDefault(AiProviderProtocol.OPENAI_COMPATIBLE)

        val defaultBaseUrl = if (protocol == AiProviderProtocol.GEMINI_NATIVE) {
            "https://generativelanguage.googleapis.com"
        } else {
            "https://dashscope.aliyuncs.com/compatible-mode/v1"
        }
        val defaultModel = if (protocol == AiProviderProtocol.GEMINI_NATIVE) {
            "gemini-2.5-flash"
        } else {
            "qwen-vl-max"
        }

        return AiConfig(
            protocol = protocol,
            baseUrl = encryptedPrefs.getString("base_url", defaultBaseUrl) ?: defaultBaseUrl,
            apiKey = encryptedPrefs.getString("api_key", "") ?: "",
            modelName = encryptedPrefs.getString("model_name", defaultModel) ?: defaultModel
        )
    }

    fun saveAiConfig(config: AiConfig) {
        val cleaned = config.copy(
            baseUrl = config.baseUrl.trim(),
            apiKey = config.apiKey.trim(),
            modelName = config.modelName.trim()
        )
        encryptedPrefs.edit()
            .putString("protocol", cleaned.protocol.name)
            .putString("base_url", cleaned.baseUrl)
            .putString("api_key", cleaned.apiKey)
            .putString("model_name", cleaned.modelName)
            .apply()
        _aiConfigFlow.value = cleaned
    }
}
