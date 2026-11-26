package com.litecal.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.litecal.app.data.local.BodyMetricEntity
import com.litecal.app.data.local.DailyExerciseSum
import com.litecal.app.data.local.ExerciseLogEntity
import com.litecal.app.data.local.FoodItemEntity
import com.litecal.app.data.local.MealComboWithItems
import com.litecal.app.data.local.MealLogEntity
import com.litecal.app.data.repository.LiteCalRepository
import com.litecal.app.domain.engine.MetabolismEngine
import com.litecal.app.domain.model.AdaptiveTdeeResult
import com.litecal.app.domain.model.AiConfig
import com.litecal.app.domain.model.AiMealEstimateResponse
import com.litecal.app.domain.model.MealType
import com.litecal.app.domain.model.UserProfile
import com.litecal.app.domain.model.WeeklyBankState
import com.litecal.app.worker.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeightTrendPoint(
    val date: LocalDate,
    val rawWeightKg: Float?,
    val emaWeightKg: Float
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val profile: UserProfile = UserProfile(),
    val consumedKcal: Int = 0,
    val dynamicTargetKcal: Int = 1800,
    val remainingKcal: Int = 1800,
    val proteinConsumedG: Int = 0,
    val proteinTargetG: Int = 105,
    val carbsConsumedG: Int = 0,
    val fatConsumedG: Int = 0,
    val waterMl: Int = 0,
    val isIncompleteDay: Boolean = false,
    val selectedDateWeightKg: Float? = null,
    val selectedDateEmaKg: Float? = null,
    val latestWeightKg: Float? = null,
    val latestEmaKg: Float? = null,
    val mealLogsByType: Map<MealType, List<MealLogEntity>> = emptyMap(),
    val exerciseLogs: List<ExerciseLogEntity> = emptyList(),
    val todayExerciseBurnKcal: Int = 0,
    val tdeeResult: AdaptiveTdeeResult? = null,
    val weeklyBank: WeeklyBankState? = null,
    val weightSeries: List<WeightTrendPoint> = emptyList(),
    val bodyMetricsHistory: List<BodyMetricEntity> = emptyList()
)

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val response: AiMealEstimateResponse) : AiUiState
    data class Error(val message: String) : AiUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiteCalViewModel(
    private val repository: LiteCalRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _quickSheetState = MutableStateFlow<QuickSheetRequest?>(null)
    val quickSheetState: StateFlow<QuickSheetRequest?> = _quickSheetState.asStateFlow()

    private val _aiUiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiUiState: StateFlow<AiUiState> = _aiUiState.asStateFlow()

    private val _aiTestMessage = MutableStateFlow<String?>(null)
    val aiTestMessage: StateFlow<String?> = _aiTestMessage.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val allFoodsFlow: StateFlow<List<FoodItemEntity>> = repository.allFoodsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val frequentFoodsFlow: StateFlow<List<FoodItemEntity>> = repository.frequentFoodsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mealCombosFlow: StateFlow<List<MealComboWithItems>> = repository.mealCombosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiConfigFlow: StateFlow<AiConfig> = repository.aiConfigFlow

    private val dateDependentBundle = _selectedDate.flatMapLatest { date ->
        val rangeStart = date.minusDays(28)
        val rangeEnd = date.with(DayOfWeek.MONDAY).plusDays(6).let { sun ->
            if (sun.isAfter(LocalDate.now())) sun else LocalDate.now()
        }
        val mealAndOverrideFlow = combine(
            repository.observeMealsForDate(date),
            repository.observeOverrideForDate(date),
            repository.observeSumsInRange(rangeStart, rangeEnd),
            repository.observeOverridesInRange(rangeStart, rangeEnd)
        ) { meals, override, sums, overrides ->
            DateWindowData(date, meals, override, sums, overrides, emptyList(), emptyList())
        }
        val exerciseFlow = combine(
            repository.observeExercisesForDate(date),
            repository.observeExerciseSumsInRange(rangeStart, rangeEnd)
        ) { exercises, exerciseSums ->
            exercises to exerciseSums
        }
        combine(mealAndOverrideFlow, exerciseFlow) { base, (exercises, exerciseSums) ->
            base.copy(exercises = exercises, exerciseSums = exerciseSums)
        }
    }

    val dashboardState: StateFlow<DashboardUiState> = combine(
        repository.userProfileFlow,
        dateDependentBundle,
        repository.allBodyMetricsFlow
    ) { profile, window, allMetrics ->
        val date = window.date
        val intakeMap = window.sums.associate { LocalDate.parse(it.dateIso) to it.totalKcal }
        val exerciseMap = window.exerciseSums.associate { LocalDate.parse(it.dateIso) to it.totalBurnKcal }
        val incompleteSet = window.overrides
            .filter { it.isIncompleteDay }
            .map { LocalDate.parse(it.dateIso) }
            .toSet()

        val rawWeights = allMetrics.mapNotNull { m ->
            m.weightKg?.let { LocalDate.parse(m.dateIso) to it }
        }.toMap()

        val emaSeries = MetabolismEngine.computeEmaSeries(rawWeights, upToDate = LocalDate.now())

        val tdeeRes = MetabolismEngine.estimateAdaptiveTdee(
            profile = profile,
            today = date,
            dailyIntakeMap = intakeMap,
            incompleteDates = incompleteSet,
            rawWeights = rawWeights,
            dailyExerciseMap = exerciseMap
        )

        val bankState = MetabolismEngine.computeWeeklyCalorieBank(
            today = date,
            profile = profile,
            estimatedTdee = tdeeRes.estimatedTdee,
            bmr = tdeeRes.bmr,
            weekIntakes = intakeMap,
            incompleteDates = incompleteSet,
            weekExercises = exerciseMap
        )

        val consumedKcal = window.meals.sumOf { it.totalKcal.toDouble() }.roundToInt()
        val consumedProtein = window.meals.sumOf { it.proteinG.toDouble() }.roundToInt()
        val consumedCarbs = window.meals.sumOf { it.carbsG.toDouble() }.roundToInt()
        val consumedFat = window.meals.sumOf { it.fatG.toDouble() }.roundToInt()
        val todayExerciseBurn = window.exercises.sumOf { it.burnKcal.toDouble() }.roundToInt()
        val proteinTarget = MetabolismEngine.calculateProteinTargetG(profile)

        val groupedMeals = MealType.entries.associateWith { type ->
            window.meals.filter { it.mealType == type.name }
        }

        val weightPoints = emaSeries.entries
            .sortedBy { it.key }
            .takeLast(30)
            .map { (d, ema) ->
                WeightTrendPoint(
                    date = d,
                    rawWeightKg = rawWeights[d],
                    emaWeightKg = ema
                )
            }

        val latestMeasuredWeight = rawWeights.maxByOrNull { it.key }?.value

        DashboardUiState(
            isLoading = false,
            selectedDate = date,
            profile = profile,
            consumedKcal = consumedKcal,
            dynamicTargetKcal = bankState.todayDynamicTarget,
            remainingKcal = bankState.todayDynamicTarget - consumedKcal,
            proteinConsumedG = consumedProtein,
            proteinTargetG = proteinTarget,
            carbsConsumedG = consumedCarbs,
            fatConsumedG = consumedFat,
            waterMl = window.override?.waterMl ?: 0,
            isIncompleteDay = window.override?.isIncompleteDay ?: false,
            selectedDateWeightKg = rawWeights[date],
            selectedDateEmaKg = emaSeries[date],
            latestWeightKg = latestMeasuredWeight,
            latestEmaKg = emaSeries[LocalDate.now()],
            mealLogsByType = groupedMeals,
            exerciseLogs = window.exercises,
            todayExerciseBurnKcal = todayExerciseBurn,
            tdeeResult = tdeeRes,
            weeklyBank = bankState,
            weightSeries = weightPoints,
            bodyMetricsHistory = allMetrics.sortedByDescending { it.dateIso }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardUiState(isLoading = true)
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun shiftDate(days: Long) {
        _selectedDate.value = _selectedDate.value.plusDays(days)
    }

    fun openQuickSheet(
        mealType: MealType = MealType.defaultForHour(LocalTime.now().hour),
        initialTab: Int = 0
    ) {
        _quickSheetState.value = QuickSheetRequest(mealType = mealType, initialTab = initialTab)
    }

    fun dismissQuickSheet() {
        _quickSheetState.value = null
        _aiUiState.value = AiUiState.Idle
    }

    fun completeOnboarding(profile: UserProfile) {
        viewModelScope.launch {
            val completed = profile.copy(onboardingCompleted = true)
            repository.saveProfile(completed)
            repository.recordWeight(LocalDate.now(), completed.startWeightKg)
            reminderScheduler.syncSchedules(completed)
            _snackbarEvents.emit("已开始记录")
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            reminderScheduler.syncSchedules(profile)
            _snackbarEvents.emit("已保存")
        }
    }

    fun saveAiConfig(config: AiConfig) {
        repository.saveAiConfig(config)
        viewModelScope.launch {
            _snackbarEvents.emit("已保存")
        }
    }

    fun testAiConnection(config: AiConfig) {
        _aiTestMessage.value = "测试中..."
        viewModelScope.launch {
            val res = repository.testAiConnection(config)
            _aiTestMessage.value = res.fold(
                onSuccess = { "✅ $it" },
                onFailure = { "❌ ${it.message ?: "连接失败"}" }
            )
        }
    }

    fun logLibraryFood(mealType: MealType, food: FoodItemEntity, grams: Float, portionLabel: String) {
        viewModelScope.launch {
            repository.logFoodFromLibrary(_selectedDate.value, mealType, food, grams, portionLabel)
            _snackbarEvents.emit("已记入${mealType.label}：${food.name}")
            dismissQuickSheet()
        }
    }

    fun logCombo(mealType: MealType, combo: MealComboWithItems) {
        viewModelScope.launch {
            repository.logMealCombo(_selectedDate.value, mealType, combo)
            _snackbarEvents.emit("已一键记入套餐：${combo.combo.name}")
            dismissQuickSheet()
        }
    }

    fun logManualEntry(
        mealType: MealType,
        title: String,
        kcal: Float,
        proteinG: Float
    ) {
        viewModelScope.launch {
            repository.logQuickCalories(
                date = _selectedDate.value,
                mealType = mealType,
                title = title,
                portionDesc = "手动填写",
                kcal = kcal,
                proteinG = proteinG,
                carbsG = 0f,
                fatG = 0f,
                saveAsTemplate = false
            )
            _snackbarEvents.emit("已记入${mealType.label}")
            dismissQuickSheet()
        }
    }

    fun runAiEstimation(text: String, imageUri: Uri?) {
        _aiUiState.value = AiUiState.Loading
        viewModelScope.launch {
            val result = repository.estimateMealWithAi(text, imageUri)
            _aiUiState.value = result.fold(
                onSuccess = { AiUiState.Success(it) },
                onFailure = { AiUiState.Error(it.message ?: "测算失败，请检查 AI 设置") }
            )
        }
    }

    fun resetAiEstimation() {
        _aiUiState.value = AiUiState.Idle
    }

    fun confirmAiMeal(
        mealType: MealType,
        response: AiMealEstimateResponse,
        multiplier: Float
    ) {
        viewModelScope.launch {
            repository.confirmAiMealItems(
                date = _selectedDate.value,
                mealType = mealType,
                items = response.items,
                portionMultiplier = multiplier,
                saveToTemplates = false,
                saveAsComboName = null
            )
            _snackbarEvents.emit("已记入${mealType.label}")
            dismissQuickSheet()
        }
    }

    fun deleteMealLog(log: MealLogEntity) {
        viewModelScope.launch {
            repository.deleteMealLog(log)
            _snackbarEvents.emit("已删除")
        }
    }

    fun copyYesterdayMeal(mealType: MealType) {
        viewModelScope.launch {
            val count = repository.copyPreviousDayMeal(_selectedDate.value, mealType)
            if (count > 0) {
                _snackbarEvents.emit("已复制昨日${mealType.label}")
            } else {
                _snackbarEvents.emit("昨日无记录")
            }
        }
    }

    fun addWater(deltaMl: Int) {
        viewModelScope.launch {
            repository.addWater(_selectedDate.value, deltaMl)
        }
    }

    fun toggleIncompleteDay(incomplete: Boolean) {
        viewModelScope.launch {
            repository.setIncompleteDay(_selectedDate.value, incomplete)
            _snackbarEvents.emit(if (incomplete) "已标为未记全" else "已恢复")
        }
    }

    fun recordWeight(weightKg: Float) {
        viewModelScope.launch {
            repository.recordWeight(_selectedDate.value, weightKg)
            _snackbarEvents.emit("已保存")
        }
    }

    fun recordBodyShape(waistCm: Float?, bodyFatPct: Float?) {
        viewModelScope.launch {
            repository.recordBodyShape(_selectedDate.value, waistCm, bodyFatPct)
            _snackbarEvents.emit("已保存")
        }
    }

    fun logExercise(name: String, burnKcal: Float) {
        viewModelScope.launch {
            repository.logExercise(_selectedDate.value, name, burnKcal)
            _snackbarEvents.emit("已记运动 +${burnKcal.roundToInt()} 大卡")
        }
    }

    fun deleteExercise(log: ExerciseLogEntity) {
        viewModelScope.launch {
            repository.deleteExercise(log)
            _snackbarEvents.emit("已删除")
        }
    }

    fun toggleFavorite(food: FoodItemEntity) {
        viewModelScope.launch {
            repository.toggleFoodFavorite(food)
        }
    }

    fun createCustomFood(
        name: String,
        unitName: String,
        unitGrams: Float,
        kcal: Float,
        protein: Float,
        carbs: Float,
        fat: Float
    ) {
        viewModelScope.launch {
            repository.saveCustomFood(name, unitName, unitGrams, kcal, protein, carbs, fat)
            _snackbarEvents.emit("已添加自定义常吃食物：$name")
        }
    }

    fun deleteCustomFood(food: FoodItemEntity) {
        viewModelScope.launch {
            repository.deleteCustomFood(food)
            _snackbarEvents.emit("已删除：${food.name}")
        }
    }

    fun deleteCombo(comboId: Long) {
        viewModelScope.launch {
            repository.deleteCombo(comboId)
            _snackbarEvents.emit("已删除套餐模板")
        }
    }

    companion object {
        fun factory(
            repository: LiteCalRepository,
            reminderScheduler: ReminderScheduler
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LiteCalViewModel(repository, reminderScheduler) as T
            }
        }
    }
}

data class QuickSheetRequest(
    val mealType: MealType,
    val initialTab: Int = 0
)

private data class DateWindowData(
    val date: LocalDate,
    val meals: List<MealLogEntity>,
    val override: com.litecal.app.data.local.DailyOverrideEntity?,
    val sums: List<com.litecal.app.data.local.DailyCalorieSum>,
    val overrides: List<com.litecal.app.data.local.DailyOverrideEntity>,
    val exercises: List<ExerciseLogEntity>,
    val exerciseSums: List<DailyExerciseSum>
)
