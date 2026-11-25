package com.litecal.app.data.repository

import android.content.Context
import android.net.Uri
import com.litecal.app.data.local.BodyMetricEntity
import com.litecal.app.data.local.DailyOverrideEntity
import com.litecal.app.data.local.FoodItemEntity
import com.litecal.app.data.local.LiteCalDao
import com.litecal.app.data.local.MealComboEntity
import com.litecal.app.data.local.MealComboItemEntity
import com.litecal.app.data.local.MealComboWithItems
import com.litecal.app.data.local.MealLogEntity
import com.litecal.app.data.local.UserPrefsStore
import com.litecal.app.data.remote.AiFoodEstimator
import com.litecal.app.domain.engine.MetabolismEngine
import com.litecal.app.domain.model.AdaptiveTdeeResult
import com.litecal.app.domain.model.AiConfig
import com.litecal.app.domain.model.AiFoodItemEstimate
import com.litecal.app.domain.model.AiMealEstimateResponse
import com.litecal.app.domain.model.MealType
import com.litecal.app.domain.model.UserProfile
import com.litecal.app.domain.model.WeeklyBankState
import com.litecal.app.widget.LiteCalWidgetUpdater
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

data class WidgetSnapshot(
    val remainingKcal: Int,
    val consumedKcal: Int,
    val targetKcal: Int,
    val proteinConsumedG: Int,
    val proteinTargetG: Int,
    val waterMl: Int,
    val waterTargetMl: Int
)

class LiteCalRepository(
    private val appContext: Context,
    val dao: LiteCalDao,
    val prefs: UserPrefsStore,
    val aiEstimator: AiFoodEstimator
) {

    val userProfileFlow: Flow<UserProfile> = prefs.userProfileFlow
    val aiConfigFlow = prefs.aiConfigFlow
    val allFoodsFlow: Flow<List<FoodItemEntity>> = dao.observeAllFoods()
    val frequentFoodsFlow: Flow<List<FoodItemEntity>> = dao.observeFrequentFoods()
    val mealCombosFlow: Flow<List<MealComboWithItems>> = dao.observeMealCombos()
    val allBodyMetricsFlow: Flow<List<BodyMetricEntity>> = dao.observeAllBodyMetrics()

    fun observeMealsForDate(date: LocalDate): Flow<List<MealLogEntity>> =
        dao.observeMealLogsForDate(date.toString())

    fun observeOverrideForDate(date: LocalDate): Flow<DailyOverrideEntity?> =
        dao.observeDailyOverride(date.toString())

    fun observeSumsInRange(start: LocalDate, end: LocalDate) =
        dao.observeDailySumsInRange(start.toString(), end.toString())

    fun observeOverridesInRange(start: LocalDate, end: LocalDate) =
        dao.observeOverridesInRange(start.toString(), end.toString())

    suspend fun saveProfile(profile: UserProfile) {
        prefs.saveUserProfile(profile)
        refreshWidget()
    }

    fun saveAiConfig(config: AiConfig) {
        prefs.saveAiConfig(config)
    }

    suspend fun logFoodFromLibrary(
        date: LocalDate,
        mealType: MealType,
        food: FoodItemEntity,
        grams: Float,
        portionLabel: String
    ) {
        val factor = (grams / 100f).coerceAtLeast(0.01f)
        dao.insertMealLog(
            MealLogEntity(
                dateIso = date.toString(),
                mealType = mealType.name,
                foodName = food.name,
                portionDesc = portionLabel,
                totalKcal = food.kcalPer100g * factor,
                proteinG = food.proteinPer100g * factor,
                carbsG = food.carbsPer100g * factor,
                fatG = food.fatPer100g * factor,
                sourceType = "DB"
            )
        )
        dao.incrementFoodUseCount(food.id)
        refreshWidget()
    }

    suspend fun logMealCombo(
        date: LocalDate,
        mealType: MealType,
        comboWithItems: MealComboWithItems
    ) {
        val now = System.currentTimeMillis()
        val logs = comboWithItems.items.mapIndexed { idx, item ->
            MealLogEntity(
                dateIso = date.toString(),
                mealType = mealType.name,
                foodName = item.foodName,
                portionDesc = item.portionDesc,
                totalKcal = item.totalKcal,
                proteinG = item.proteinG,
                carbsG = item.carbsG,
                fatG = item.fatG,
                sourceType = "TEMPLATE",
                createdAt = now + idx
            )
        }
        dao.insertMealLogs(logs)
        dao.incrementComboUseCount(comboWithItems.combo.id)
        refreshWidget()
    }

    suspend fun logQuickCalories(
        date: LocalDate,
        mealType: MealType,
        title: String,
        portionDesc: String,
        kcal: Float,
        proteinG: Float = 0f,
        carbsG: Float = 0f,
        fatG: Float = 0f,
        saveAsTemplate: Boolean = false
    ) {
        val cleanName = title.trim().ifBlank { "${mealType.label}快记" }
        dao.insertMealLog(
            MealLogEntity(
                dateIso = date.toString(),
                mealType = mealType.name,
                foodName = cleanName,
                portionDesc = portionDesc.ifBlank { "1份" },
                totalKcal = kcal.coerceAtLeast(0f),
                proteinG = proteinG.coerceAtLeast(0f),
                carbsG = carbsG.coerceAtLeast(0f),
                fatG = fatG.coerceAtLeast(0f),
                sourceType = "QUICK"
            )
        )
        if (saveAsTemplate && kcal > 0f) {
            dao.insertFoodItem(
                FoodItemEntity(
                    category = "常吃自定义",
                    name = cleanName,
                    kcalPer100g = kcal,
                    proteinPer100g = proteinG,
                    carbsPer100g = carbsG,
                    fatPer100g = fatG,
                    defaultUnitName = portionDesc.ifBlank { "一份" },
                    defaultUnitGrams = 100f,
                    isBuiltIn = false,
                    isFavorite = true,
                    useCount = 1
                )
            )
        }
        refreshWidget()
    }

    suspend fun confirmAiMealItems(
        date: LocalDate,
        mealType: MealType,
        items: List<AiFoodItemEstimate>,
        portionMultiplier: Float,
        saveToTemplates: Boolean,
        saveAsComboName: String? = null
    ) {
        val mult = portionMultiplier.coerceIn(0.2f, 3.0f)
        val now = System.currentTimeMillis()
        val logs = items.mapIndexed { idx, item ->
            val scaledPortion = if (mult == 1.0f) {
                item.portionDesc
            } else {
                "${item.portionDesc} ×${String.format("%.1f", mult)}"
            }
            MealLogEntity(
                dateIso = date.toString(),
                mealType = mealType.name,
                foodName = item.name,
                portionDesc = scaledPortion,
                totalKcal = item.kcal * mult,
                proteinG = item.proteinG * mult,
                carbsG = item.carbsG * mult,
                fatG = item.fatG * mult,
                sourceType = "AI",
                createdAt = now + idx
            )
        }
        dao.insertMealLogs(logs)

        if (saveToTemplates) {
            items.forEach { item ->
                val grams = item.estimatedGrams.takeIf { it > 1f } ?: 100f
                val per100Factor = 100f / grams
                dao.insertFoodItem(
                    FoodItemEntity(
                        category = "AI沉淀常吃",
                        name = item.name,
                        kcalPer100g = (item.kcal * mult) * per100Factor,
                        proteinPer100g = (item.proteinG * mult) * per100Factor,
                        carbsPer100g = (item.carbsG * mult) * per100Factor,
                        fatPer100g = (item.fatG * mult) * per100Factor,
                        defaultUnitName = item.portionDesc.ifBlank { "一份" },
                        defaultUnitGrams = grams * mult,
                        isBuiltIn = false,
                        isFavorite = true,
                        useCount = 1
                    )
                )
            }
        }

        if (!saveAsComboName.isNullOrBlank() && items.isNotEmpty()) {
            dao.createComboWithItems(
                combo = MealComboEntity(
                    name = saveAsComboName.trim(),
                    defaultMealType = mealType.name,
                    useCount = 1
                ),
                items = items.map { item ->
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = item.name,
                        portionDesc = item.portionDesc,
                        totalKcal = item.kcal * mult,
                        proteinG = item.proteinG * mult,
                        carbsG = item.carbsG * mult,
                        fatG = item.fatG * mult
                    )
                }
            )
        }

        refreshWidget()
    }

    suspend fun deleteMealLog(log: MealLogEntity) {
        dao.deleteMealLog(log)
        refreshWidget()
    }

    suspend fun copyPreviousDayMeal(targetDate: LocalDate, mealType: MealType): Int {
        val prevDate = targetDate.minusDays(1)
        val prevLogs = dao.getMealLogsForDateAndTypeOnce(prevDate.toString(), mealType.name)
        if (prevLogs.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val copied = prevLogs.mapIndexed { idx, old ->
            old.copy(
                id = 0,
                dateIso = targetDate.toString(),
                createdAt = now + idx
            )
        }
        dao.insertMealLogs(copied)
        refreshWidget()
        return copied.size
    }

    suspend fun addWater(date: LocalDate, deltaMl: Int) {
        val existing = dao.getDailyOverrideOnce(date.toString())
        val updatedWater = ((existing?.waterMl ?: 0) + deltaMl).coerceIn(0, 8000)
        dao.upsertDailyOverride(
            DailyOverrideEntity(
                dateIso = date.toString(),
                isIncompleteDay = existing?.isIncompleteDay ?: false,
                waterMl = updatedWater
            )
        )
        refreshWidget()
    }

    suspend fun setIncompleteDay(date: LocalDate, incomplete: Boolean) {
        val existing = dao.getDailyOverrideOnce(date.toString())
        dao.upsertDailyOverride(
            DailyOverrideEntity(
                dateIso = date.toString(),
                isIncompleteDay = incomplete,
                waterMl = existing?.waterMl ?: 0
            )
        )
        refreshWidget()
    }

    suspend fun recordWeight(date: LocalDate, weightKg: Float) {
        val existing = dao.getBodyMetricForDateOnce(date.toString())
        dao.upsertBodyMetric(
            BodyMetricEntity(
                dateIso = date.toString(),
                weightKg = weightKg,
                waistCm = existing?.waistCm,
                bodyFatPct = existing?.bodyFatPct,
                updatedAt = System.currentTimeMillis()
            )
        )
        refreshWidget()
    }

    suspend fun recordBodyShape(date: LocalDate, waistCm: Float?, bodyFatPct: Float?) {
        val existing = dao.getBodyMetricForDateOnce(date.toString())
        dao.upsertBodyMetric(
            BodyMetricEntity(
                dateIso = date.toString(),
                weightKg = existing?.weightKg,
                waistCm = waistCm ?: existing?.waistCm,
                bodyFatPct = bodyFatPct ?: existing?.bodyFatPct,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun toggleFoodFavorite(food: FoodItemEntity) {
        dao.setFoodFavorite(food.id, !food.isFavorite)
    }

    suspend fun saveCustomFood(
        name: String,
        unitName: String,
        unitGrams: Float,
        kcalPerUnit: Float,
        proteinPerUnit: Float,
        carbsPerUnit: Float,
        fatPerUnit: Float
    ) {
        val safeGrams = unitGrams.coerceAtLeast(1f)
        val scaleTo100 = 100f / safeGrams
        dao.insertFoodItem(
            FoodItemEntity(
                category = "常吃自定义",
                name = name.trim(),
                kcalPer100g = kcalPerUnit * scaleTo100,
                proteinPer100g = proteinPerUnit * scaleTo100,
                carbsPer100g = carbsPerUnit * scaleTo100,
                fatPer100g = fatPerUnit * scaleTo100,
                defaultUnitName = unitName.trim().ifBlank { "一份" },
                defaultUnitGrams = safeGrams,
                isBuiltIn = false,
                isFavorite = true,
                useCount = 1
            )
        )
    }

    suspend fun saveTodayMealAsCombo(date: LocalDate, mealType: MealType, comboName: String): Boolean {
        val logs = dao.getMealLogsForDateAndTypeOnce(date.toString(), mealType.name)
        if (logs.isEmpty()) return false
        dao.createComboWithItems(
            combo = MealComboEntity(
                name = comboName.trim(),
                defaultMealType = mealType.name,
                useCount = 1
            ),
            items = logs.map { log ->
                MealComboItemEntity(
                    comboId = 0,
                    foodName = log.foodName,
                    portionDesc = log.portionDesc,
                    totalKcal = log.totalKcal,
                    proteinG = log.proteinG,
                    carbsG = log.carbsG,
                    fatG = log.fatG
                )
            }
        )
        return true
    }

    suspend fun deleteCombo(comboId: Long) {
        dao.deleteMealCombo(comboId)
    }

    suspend fun deleteCustomFood(food: FoodItemEntity) {
        dao.deleteFoodItem(food)
    }

    suspend fun estimateMealWithAi(text: String, imageUri: Uri?): Result<AiMealEstimateResponse> {
        return aiEstimator.estimateMeal(prefs.aiConfigFlow.value, text, imageUri)
    }

    suspend fun testAiConnection(config: AiConfig): Result<String> {
        return aiEstimator.testConnection(config)
    }

    /**
     * 供桌面小组件与后台通知一次性读取今日实时快照
     */
    suspend fun computeTodaySnapshotOnce(today: LocalDate = LocalDate.now()): WidgetSnapshot {
        val profile = prefs.getUserProfileOnce()
        val startRange = today.minusDays(21)
        val sunday = today.with(DayOfWeek.MONDAY).plusDays(6)
        val sums = dao.getDailySumsInRangeOnce(startRange.toString(), sunday.toString())
        val overrides = dao.getOverridesInRangeOnce(startRange.toString(), sunday.toString())
        val metrics = dao.getAllBodyMetricsOnce()

        val intakeMap = sums.associate { LocalDate.parse(it.dateIso) to it.totalKcal }
        val incompleteSet = overrides.filter { it.isIncompleteDay }
            .map { LocalDate.parse(it.dateIso) }
            .toSet()
        val weightMap = metrics.mapNotNull { m ->
            m.weightKg?.let { LocalDate.parse(m.dateIso) to it }
        }.toMap()

        val tdeeRes: AdaptiveTdeeResult = MetabolismEngine.estimateAdaptiveTdee(
            profile = profile,
            today = today,
            dailyIntakeMap = intakeMap,
            incompleteDates = incompleteSet,
            rawWeights = weightMap
        )
        val bank: WeeklyBankState = MetabolismEngine.computeWeeklyCalorieBank(
            today = today,
            profile = profile,
            estimatedTdee = tdeeRes.estimatedTdee,
            bmr = tdeeRes.bmr,
            weekIntakes = intakeMap,
            incompleteDates = incompleteSet
        )

        val todaySum = sums.find { it.dateIso == today.toString() }
        val consumedKcal = (todaySum?.totalKcal ?: 0f).roundToInt()
        val consumedProt = (todaySum?.totalProteinG ?: 0f).roundToInt()
        val protTarget = MetabolismEngine.calculateProteinTargetG(profile)
        val todayWater = overrides.find { it.dateIso == today.toString() }?.waterMl ?: 0

        return WidgetSnapshot(
            remainingKcal = bank.todayDynamicTarget - consumedKcal,
            consumedKcal = consumedKcal,
            targetKcal = bank.todayDynamicTarget,
            proteinConsumedG = consumedProt,
            proteinTargetG = protTarget,
            waterMl = todayWater,
            waterTargetMl = profile.waterTargetMl
        )
    }

    suspend fun refreshWidget() {
        runCatching {
            LiteCalWidgetUpdater.updateAll(appContext)
        }
    }
}
