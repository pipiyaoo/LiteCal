package com.litecal.app.domain.engine

import com.litecal.app.domain.model.AdaptiveTdeeResult
import com.litecal.app.domain.model.Gender
import com.litecal.app.domain.model.UserProfile
import com.litecal.app.domain.model.WeekDayBudget
import com.litecal.app.domain.model.WeeklyBankState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * 核心减脂与代谢计算引擎：
 * 1. Mifflin-St Jeor 基础代谢 (BMR) 与初始消耗估算
 * 2. 7 日指数移动平均体重 (7-Day EMA，过滤水分/糖原短期波动)
 * 3. 自适应代谢校准 (Adaptive TDEE，基于有效记录日摄入与平滑体重差反推真实代谢)
 * 4. 周热量银行 (Weekly Calorie Bank，支持周末聚餐预留、超支温和分摊与安全下限护栏)
 */
object MetabolismEngine {

    private const val KCAL_PER_KG_BODY_FAT = 7700f
    private const val KJ_PER_KCAL = 4.184f
    private const val MIN_VALID_DAYS_FOR_CALIBRATION = 8

    fun calculateBmr(profile: UserProfile, currentWeightKg: Float = profile.startWeightKg): Int {
        val genderOffset = when (profile.gender) {
            Gender.MALE -> 5f
            Gender.FEMALE -> -161f
        }
        val raw = 10f * currentWeightKg + 6.25f * profile.heightCm - 5f * profile.age + genderOffset
        return raw.roundToInt().coerceAtLeast(900)
    }

    fun calculateInitialTdee(profile: UserProfile, currentWeightKg: Float = profile.startWeightKg): Int {
        val bmr = calculateBmr(profile, currentWeightKg)
        return (bmr * profile.activityLevel.factor).roundToInt()
    }

    fun calculateProteinTargetG(profile: UserProfile): Int {
        profile.customProteinTargetG?.let { if (it > 0) return it }
        return (profile.targetWeightKg * 1.6f).roundToInt().coerceAtLeast(55)
    }

    fun calculateDailyDeficitTarget(weeklyLossRateKg: Float): Int {
        return ((weeklyLossRateKg.coerceIn(0f, 1.2f) * KCAL_PER_KG_BODY_FAT) / 7f).roundToInt()
    }

    fun kjToKcal(kj: Float): Float = (kj / KJ_PER_KCAL).coerceAtLeast(0f)

    fun nutritionTableToKcal(energyPer100g: Float, isKj: Boolean, weightGrams: Float): Float {
        val kcalPer100g = if (isKj) kjToKcal(energyPer100g) else energyPer100g
        return (kcalPer100g * (weightGrams / 100f)).coerceAtLeast(0f)
    }

    /**
     * 计算连续日历维度上的 7 日 EMA 趋势体重。
     * 若某几天未称重，在相邻两次称重间进行日历线性插值后再滚动更新 EMA，确保时间常数恒定为 7 天。
     */
    fun computeEmaSeries(
        rawWeights: Map<LocalDate, Float>,
        upToDate: LocalDate? = null,
        alpha: Float = 0.25f // 2 / (7 + 1)
    ): Map<LocalDate, Float> {
        if (rawWeights.isEmpty()) return emptyMap()
        val sortedDates = rawWeights.keys.sorted()
        val firstDate = sortedDates.first()
        val lastMeasuredDate = sortedDates.last()
        val endDate = if (upToDate != null && upToDate.isAfter(lastMeasuredDate)) upToDate else lastMeasuredDate

        val dailyInterpolated = LinkedHashMap<LocalDate, Float>()
        for (i in sortedDates.indices) {
            val d1 = sortedDates[i]
            val w1 = rawWeights.getValue(d1)
            dailyInterpolated[d1] = w1
            if (i < sortedDates.lastIndex) {
                val d2 = sortedDates[i + 1]
                val w2 = rawWeights.getValue(d2)
                val gapDays = ChronoUnit.DAYS.between(d1, d2).toInt()
                for (step in 1 until gapDays) {
                    val ratio = step.toFloat() / gapDays.toFloat()
                    dailyInterpolated[d1.plusDays(step.toLong())] = w1 + (w2 - w1) * ratio
                }
            }
        }

        val result = LinkedHashMap<LocalDate, Float>()
        var currentEma = rawWeights.getValue(firstDate)
        var cursor = firstDate
        while (!cursor.isAfter(endDate)) {
            val observedOrInterp = dailyInterpolated[cursor]
            if (cursor == firstDate) {
                currentEma = observedOrInterp ?: currentEma
            } else if (observedOrInterp != null) {
                currentEma = alpha * observedOrInterp + (1f - alpha) * currentEma
            }
            // 若在最后一次称重之后，保持最新平滑体重水平
            result[cursor] = currentEma
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * 自适应代谢校准（滚动过去 14 天已完成记录窗口，排除今日与未记全日）。
     */
    fun estimateAdaptiveTdee(
        profile: UserProfile,
        today: LocalDate,
        dailyIntakeMap: Map<LocalDate, Float>,
        incompleteDates: Set<LocalDate>,
        rawWeights: Map<LocalDate, Float>
    ): AdaptiveTdeeResult {
        val latestWeight = rawWeights
            .filterKeys { !it.isAfter(today) }
            .maxByOrNull { it.key }
            ?.value ?: profile.startWeightKg

        val bmr = calculateBmr(profile, latestWeight)
        val formulaTdee = calculateInitialTdee(profile, latestWeight)
        val minValidIntake = bmr * 0.5f

        val windowEnd = today.minusDays(1)
        val windowStart = today.minusDays(14)

        val validDaysInWindow = (0L until 14L)
            .map { windowStart.plusDays(it) }
            .filter { date ->
                date !in incompleteDates && (dailyIntakeMap[date] ?: 0f) >= minValidIntake
            }

        val emaSeries = computeEmaSeries(rawWeights, upToDate = today)
        val hasWeightSpan = rawWeights.keys.count { !it.isAfter(today) } >= 3 &&
            emaSeries.containsKey(windowStart) &&
            emaSeries.containsKey(windowEnd)

        if (validDaysInWindow.size < MIN_VALID_DAYS_FOR_CALIBRATION || !hasWeightSpan) {
            return AdaptiveTdeeResult(
                estimatedTdee = formulaTdee,
                formulaTdee = formulaTdee,
                bmr = bmr,
                isCalibrated = false,
                validDaysCount = validDaysInWindow.size,
                minRequiredDays = MIN_VALID_DAYS_FOR_CALIBRATION,
                avgIntake14d = validDaysInWindow
                    .takeIf { it.isNotEmpty() }
                    ?.map { dailyIntakeMap[it] ?: 0f }
                    ?.average()
                    ?.roundToInt(),
                weightDelta14dKg = if (emaSeries.containsKey(windowStart) && emaSeries.containsKey(windowEnd)) {
                    emaSeries.getValue(windowEnd) - emaSeries.getValue(windowStart)
                } else null
            )
        }

        val avgIntake = validDaysInWindow.map { dailyIntakeMap.getValue(it) }.average().toFloat()
        val emaStart = emaSeries.getValue(windowStart)
        val emaEnd = emaSeries.getValue(windowEnd)
        val weightDelta14d = emaEnd - emaStart
        val dailyWeightDelta = weightDelta14d / 14f

        // 能量守恒: 真实消耗 = 平均摄入 - (每日平滑体重变化 * 7700)
        val rawTdee = (avgIntake - dailyWeightDelta * KCAL_PER_KG_BODY_FAT)
            .coerceIn(bmr * 1.05f, bmr * 2.4f)

        // 按有效天数平滑提升自适应权重 (8天 -> 0.35, 14天 -> 0.70)，并限制偏离幅度防剧烈跳变
        val adaptiveWeight = (0.35f + (validDaysInWindow.size - MIN_VALID_DAYS_FOR_CALIBRATION) * (0.35f / 6f))
            .coerceIn(0.35f, 0.70f)
        val blended = (1f - adaptiveWeight) * formulaTdee + adaptiveWeight * rawTdee
        val dampedTdee = blended.roundToInt()
            .coerceIn(formulaTdee - 350, formulaTdee + 350)
            .coerceAtLeast(bmr + 100)

        return AdaptiveTdeeResult(
            estimatedTdee = dampedTdee,
            formulaTdee = formulaTdee,
            bmr = bmr,
            isCalibrated = true,
            validDaysCount = validDaysInWindow.size,
            minRequiredDays = MIN_VALID_DAYS_FOR_CALIBRATION,
            avgIntake14d = avgIntake.roundToInt(),
            weightDelta14dKg = weightDelta14d
        )
    }

    /**
     * 周热量银行计算（自然周 周一~周日）：
     * - 支持工作日预留热量给周末聚餐
     * - 已完成日的超支/结余温和分摊到本周剩余天数
     * - 空白未记日或标记“未记全”的历史日按中性 (0 偏差) 处理，防止漏记导致次日额度暴涨
     * - 严格执行单日安全保底下限 max(0.85 * BMR, 1200 kcal)
     */
    fun computeWeeklyCalorieBank(
        today: LocalDate,
        profile: UserProfile,
        estimatedTdee: Int,
        bmr: Int,
        weekIntakes: Map<LocalDate, Float>,
        incompleteDates: Set<LocalDate>
    ): WeeklyBankState {
        val monday = today.with(DayOfWeek.MONDAY)
        val safetyFloor = maxOf((bmr * 0.85f).roundToInt(), 1200)
        val deficitTarget = calculateDailyDeficitTarget(profile.weeklyLossRateKg)
        val baseDailyBudget = maxOf(estimatedTdee - deficitTarget, safetyFloor)

        val weekdaySave = if (profile.weekendReserveEnabled) {
            profile.weekendDailySaveKcal.coerceIn(0, (baseDailyBudget - safetyFloor).coerceAtLeast(0))
        } else {
            0
        }
        val weekendBonus = ((weekdaySave * 5) / 2f).roundToInt()

        fun plannedFor(date: LocalDate): Int {
            return when (date.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> baseDailyBudget + weekendBonus
                else -> baseDailyBudget - weekdaySave
            }
        }

        val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        var bankBeforeToday = 0

        val days = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val planned = plannedFor(date)
            val intake = (weekIntakes[date] ?: 0f).roundToInt()
            val incomplete = date in incompleteDates

            if (date.isBefore(today) && intake > 0 && !incomplete) {
                bankBeforeToday += (planned - intake)
            }

            WeekDayBudget(
                date = date,
                dayLabel = dayLabels[offset.toInt()],
                plannedKcal = planned,
                intakeKcal = intake,
                isIncomplete = incomplete,
                isToday = date == today,
                isFuture = date.isAfter(today)
            )
        }

        val sunday = monday.plusDays(6)
        val remainingDaysCount = (ChronoUnit.DAYS.between(today, sunday).toInt() + 1).coerceIn(1, 7)
        val todayPlanned = plannedFor(today)
        val amortizedDelta = (bankBeforeToday.toFloat() / remainingDaysCount.toFloat()).roundToInt()

        val todayDynamicTarget = (todayPlanned + amortizedDelta)
            .coerceIn(safetyFloor, todayPlanned + 450)

        val weeklyTotalBudget = days.sumOf { it.plannedKcal }

        return WeeklyBankState(
            weeklyTotalBudget = weeklyTotalBudget,
            todayPlannedBase = todayPlanned,
            todayDynamicTarget = todayDynamicTarget,
            bankBalanceBeforeToday = bankBeforeToday,
            safetyFloorKcal = safetyFloor,
            days = days
        )
    }
}
