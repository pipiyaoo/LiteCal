package com.litecal.app.domain.engine

import com.litecal.app.domain.model.ActivityLevel
import com.litecal.app.domain.model.Gender
import com.litecal.app.domain.model.UserProfile
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetabolismEngineTest {

    private val sampleProfile = UserProfile(
        onboardingCompleted = true,
        gender = Gender.MALE,
        age = 28,
        heightCm = 175f,
        startWeightKg = 75f,
        targetWeightKg = 67f,
        activityLevel = ActivityLevel.LIGHT,
        weeklyLossRateKg = 0.5f,
        weekendReserveEnabled = true,
        weekendDailySaveKcal = 120
    )

    @Test
    fun `calculateBmr and InitialTdee follow Mifflin-St Jeor`() {
        // 10*75 + 6.25*175 - 5*28 + 5 = 750 + 1093.75 - 140 + 5 = 1708.75 -> 1709
        val bmr = MetabolismEngine.calculateBmr(sampleProfile, 75f)
        assertEquals(1709, bmr)

        // 1709 * 1.375 = 2349.875 -> 2350
        val tdee = MetabolismEngine.calculateInitialTdee(sampleProfile, 75f)
        assertEquals(2350, tdee)

        // Protein: 67 * 1.6 = 107.2 -> 107g
        assertEquals(107, MetabolismEngine.calculateProteinTargetG(sampleProfile))
    }

    @Test
    fun `kjToKcal and nutritionTableToKcal convert packaged food accurately`() {
        // 2092 kJ per 100g = 500 kcal per 100g; eating 60g = 300 kcal
        val kcal = MetabolismEngine.nutritionTableToKcal(
            energyPer100g = 2092f,
            isKj = true,
            weightGrams = 60f
        )
        assertEquals(300f, kcal, 0.5f)
    }

    @Test
    fun `computeEmaSeries smooths single day water spike`() {
        val d1 = LocalDate.of(2026, 11, 1)
        val raw = mapOf(
            d1 to 75.0f,
            d1.plusDays(1) to 76.2f, // 聚餐高盐水肿突增 1.2kg
            d1.plusDays(2) to 74.9f
        )
        val ema = MetabolismEngine.computeEmaSeries(raw)
        // Day 2 EMA should only rise by 0.25 * 1.2 = +0.3kg instead of +1.2kg!
        assertEquals(75.0f, ema.getValue(d1), 0.01f)
        assertEquals(75.3f, ema.getValue(d1.plusDays(1)), 0.01f)
        assertTrue(ema.getValue(d1.plusDays(2)) < 75.3f)
    }

    @Test
    fun `estimateAdaptiveTdee ignores incomplete days and calibrates upward when losing faster than expected`() {
        val today = LocalDate.of(2026, 11, 20)
        val intakes = mutableMapOf<LocalDate, Float>()
        val weights = mutableMapOf<LocalDate, Float>()
        val incomplete = setOf(today.minusDays(5))

        // 连续14天每天吃 1900 kcal (其中1天标记未记全只记了 300 kcal)，平滑体重14天下降了 1.4kg (日均缺口 ~770 kcal -> 真实TDEE ~2670)
        for (i in 16 downTo 1) {
            val d = today.minusDays(i.toLong())
            intakes[d] = if (d in incomplete) 300f else 1900f
            weights[d] = 75.0f - (16 - i) * 0.1f
        }

        val res = MetabolismEngine.estimateAdaptiveTdee(
            profile = sampleProfile,
            today = today,
            dailyIntakeMap = intakes,
            incompleteDates = incomplete,
            rawWeights = weights
        )

        assertTrue(res.isCalibrated)
        assertEquals(13, res.validDaysCount) // 14天窗口剔除了1天未记全
        assertEquals(1900, res.avgIntake14d)
        assertTrue("Adaptive TDEE should calibrate higher than formula TDEE", res.estimatedTdee > res.formulaTdee)
    }

    @Test
    fun `computeWeeklyCalorieBank handles weekend reserve, unlogged neutral days, and safety floor`() {
        // 2026-11-25 is Wednesday
        val wednesday = LocalDate.of(2026, 11, 25)
        val monday = LocalDate.of(2026, 11, 23)
        val tuesday = LocalDate.of(2026, 11, 24)

        // Monday unlogged (0 kcal -> should NOT inflate budget!), Tuesday overspent by 500 kcal
        val bmr = 1709
        val tdee = 2350
        // Base daily = 2350 - 550 = 1800; Weekday planned = 1800 - 120 = 1680; Weekend planned = 1800 + 300 = 2100
        val intakes = mapOf(
            monday to 0f,
            tuesday to 2180f // overspent by 500 kcal vs 1680
        )

        val state = MetabolismEngine.computeWeeklyCalorieBank(
            today = wednesday,
            profile = sampleProfile,
            estimatedTdee = tdee,
            bmr = bmr,
            weekIntakes = intakes,
            incompleteDates = emptySet()
        )

        assertEquals(-500, state.bankBalanceBeforeToday)
        // Remaining days Wed..Sun = 5 days -> -500 / 5 = -100 kcal adjustment -> 1680 - 100 = 1580
        assertEquals(1680, state.todayPlannedBase)
        assertEquals(1580, state.todayDynamicTarget)
        assertTrue(state.todayDynamicTarget >= state.safetyFloorKcal)

        // Saturday planned should include +300 weekend bonus
        val saturdayBudget = state.days.first { it.dayLabel == "周六" }.plannedKcal
        assertEquals(2100, saturdayBudget)

        // Extreme overspend should never drop dynamic target below safetyFloorKcal
        val extremeState = MetabolismEngine.computeWeeklyCalorieBank(
            today = wednesday,
            profile = sampleProfile,
            estimatedTdee = tdee,
            bmr = bmr,
            weekIntakes = mapOf(tuesday to 5000f),
            incompleteDates = emptySet()
        )
        assertEquals(extremeState.safetyFloorKcal, extremeState.todayDynamicTarget)
        assertFalse(extremeState.todayDynamicTarget < 1200)
    }

    @Test
    fun `computeWeeklyCalorieBank adds manual exercise burn 1-to-1 to today budget`() {
        val wednesday = LocalDate.of(2026, 11, 25)
        val bmr = 1709
        val tdee = 2350

        val baseState = MetabolismEngine.computeWeeklyCalorieBank(
            today = wednesday,
            profile = sampleProfile,
            estimatedTdee = tdee,
            bmr = bmr,
            weekIntakes = emptyMap(),
            incompleteDates = emptySet(),
            weekExercises = emptyMap()
        )
        val withWorkoutState = MetabolismEngine.computeWeeklyCalorieBank(
            today = wednesday,
            profile = sampleProfile,
            estimatedTdee = tdee,
            bmr = bmr,
            weekIntakes = emptyMap(),
            incompleteDates = emptySet(),
            weekExercises = mapOf(wednesday to 350f)
        )

        assertEquals(baseState.todayDynamicTarget + 350, withWorkoutState.todayDynamicTarget)
    }
}
