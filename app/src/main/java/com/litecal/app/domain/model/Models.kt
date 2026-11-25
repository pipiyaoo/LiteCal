package com.litecal.app.domain.model

import java.time.LocalDate
import kotlinx.serialization.Serializable

enum class Gender(val label: String) {
    MALE("男"),
    FEMALE("女")
}

enum class ActivityLevel(val factor: Float, val title: String, val subtitle: String) {
    SEDENTARY(1.2f, "久坐办公", "日常几乎不运动，上下班通勤为主"),
    LIGHT(1.375f, "轻度活动", "每周运动 1~3 次，或日均步数 6000~9000"),
    MODERATE(1.55f, "中度活动", "每周规律运动 3~5 次，训练强度适中"),
    ACTIVE(1.725f, "高频训练", "每周高强度训练 6~7 次或体力工作")
}

enum class MealType(val label: String, val emoji: String, val sortOrder: Int) {
    BREAKFAST("早餐", "🌅", 0),
    LUNCH("午餐", "☀️", 1),
    DINNER("晚餐", "🌙", 2),
    SNACK("加餐", "🍎", 3);

    companion object {
        fun defaultForHour(hour: Int): MealType = when (hour) {
            in 5..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
    }
}

enum class AiProviderProtocol(val label: String) {
    OPENAI_COMPATIBLE("OpenAI 兼容协议 (通义千问/豆包/硅基流动等)"),
    GEMINI_NATIVE("Google Gemini 原生协议")
}

data class UserProfile(
    val onboardingCompleted: Boolean = false,
    val gender: Gender = Gender.MALE,
    val age: Int = 28,
    val heightCm: Float = 172f,
    val startWeightKg: Float = 72f,
    val targetWeightKg: Float = 64f,
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val weeklyLossRateKg: Float = 0.5f,
    val customProteinTargetG: Int? = null,
    val waterTargetMl: Int = 2000,
    val weekendReserveEnabled: Boolean = true,
    val weekendDailySaveKcal: Int = 120,
    val morningReminderEnabled: Boolean = true,
    val morningReminderHour: Int = 8,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = true,
    val eveningReminderHour: Int = 20,
    val eveningReminderMinute: Int = 30
)

data class AiConfig(
    val protocol: AiProviderProtocol = AiProviderProtocol.OPENAI_COMPATIBLE,
    val baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    val apiKey: String = "",
    val modelName: String = "qwen-vl-max"
)

@Serializable
data class AiFoodItemEstimate(
    val name: String,
    val portionDesc: String,
    val estimatedGrams: Float = 100f,
    val kcal: Float,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f
)

@Serializable
data class AiMealEstimateResponse(
    val summary: String = "",
    val items: List<AiFoodItemEstimate> = emptyList(),
    val confidenceNote: String = ""
)

data class AdaptiveTdeeResult(
    val estimatedTdee: Int,
    val formulaTdee: Int,
    val bmr: Int,
    val isCalibrated: Boolean,
    val validDaysCount: Int,
    val minRequiredDays: Int = 8,
    val avgIntake14d: Int?,
    val weightDelta14dKg: Float?
)

data class WeekDayBudget(
    val date: LocalDate,
    val dayLabel: String,
    val plannedKcal: Int,
    val intakeKcal: Int,
    val isIncomplete: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean
)

data class WeeklyBankState(
    val weeklyTotalBudget: Int,
    val todayPlannedBase: Int,
    val todayDynamicTarget: Int,
    val bankBalanceBeforeToday: Int,
    val safetyFloorKcal: Int,
    val days: List<WeekDayBudget>
)
