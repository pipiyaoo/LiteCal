package com.litecal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LiteCalDao {

    // --- Food Library & Frequent Templates ---
    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun getFoodItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItemEntity): Long

    @Update
    suspend fun updateFoodItem(item: FoodItemEntity)

    @Delete
    suspend fun deleteFoodItem(item: FoodItemEntity)

    @Query("SELECT * FROM food_items ORDER BY isFavorite DESC, useCount DESC, id ASC")
    fun observeAllFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE isFavorite = 1 OR useCount > 0 OR isBuiltIn = 0 ORDER BY isFavorite DESC, useCount DESC, id DESC LIMIT 40")
    fun observeFrequentFoods(): Flow<List<FoodItemEntity>>

    @Query("UPDATE food_items SET useCount = useCount + 1 WHERE id = :foodId")
    suspend fun incrementFoodUseCount(foodId: Long)

    @Query("UPDATE food_items SET isFavorite = :favorite WHERE id = :foodId")
    suspend fun setFoodFavorite(foodId: Long, favorite: Boolean)

    // --- Meal Combos ---
    @Transaction
    @Query("SELECT * FROM meal_combos ORDER BY useCount DESC, id DESC")
    fun observeMealCombos(): Flow<List<MealComboWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealCombo(combo: MealComboEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealComboItems(items: List<MealComboItemEntity>)

    @Query("DELETE FROM meal_combos WHERE id = :comboId")
    suspend fun deleteMealCombo(comboId: Long)

    @Query("UPDATE meal_combos SET useCount = useCount + 1 WHERE id = :comboId")
    suspend fun incrementComboUseCount(comboId: Long)

    @Transaction
    suspend fun createComboWithItems(combo: MealComboEntity, items: List<MealComboItemEntity>): Long {
        val comboId = insertMealCombo(combo)
        insertMealComboItems(items.map { it.copy(comboId = comboId) })
        return comboId
    }

    // --- Meal Logs ---
    @Query("SELECT * FROM meal_logs WHERE dateIso = :dateIso ORDER BY createdAt ASC")
    fun observeMealLogsForDate(dateIso: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE dateIso = :dateIso ORDER BY createdAt ASC")
    suspend fun getMealLogsForDateOnce(dateIso: String): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs WHERE dateIso = :dateIso AND mealType = :mealType ORDER BY createdAt ASC")
    suspend fun getMealLogsForDateAndTypeOnce(dateIso: String, mealType: String): List<MealLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLog(log: MealLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLogs(logs: List<MealLogEntity>)

    @Delete
    suspend fun deleteMealLog(log: MealLogEntity)

    @Query(
        """
        SELECT dateIso, SUM(totalKcal) AS totalKcal, SUM(proteinG) AS totalProteinG
        FROM meal_logs
        WHERE dateIso >= :startDateIso AND dateIso <= :endDateIso
        GROUP BY dateIso
        """
    )
    fun observeDailySumsInRange(startDateIso: String, endDateIso: String): Flow<List<DailyCalorieSum>>

    @Query(
        """
        SELECT dateIso, SUM(totalKcal) AS totalKcal, SUM(proteinG) AS totalProteinG
        FROM meal_logs
        WHERE dateIso >= :startDateIso AND dateIso <= :endDateIso
        GROUP BY dateIso
        """
    )
    suspend fun getDailySumsInRangeOnce(startDateIso: String, endDateIso: String): List<DailyCalorieSum>

    // --- Daily Overrides (Incomplete Flag & Water) ---
    @Query("SELECT * FROM daily_overrides WHERE dateIso = :dateIso")
    fun observeDailyOverride(dateIso: String): Flow<DailyOverrideEntity?>

    @Query("SELECT * FROM daily_overrides WHERE dateIso = :dateIso")
    suspend fun getDailyOverrideOnce(dateIso: String): DailyOverrideEntity?

    @Query("SELECT * FROM daily_overrides WHERE dateIso >= :startDateIso AND dateIso <= :endDateIso")
    fun observeOverridesInRange(startDateIso: String, endDateIso: String): Flow<List<DailyOverrideEntity>>

    @Query("SELECT * FROM daily_overrides WHERE dateIso >= :startDateIso AND dateIso <= :endDateIso")
    suspend fun getOverridesInRangeOnce(startDateIso: String, endDateIso: String): List<DailyOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyOverride(overrideEntity: DailyOverrideEntity)

    // --- Body Metrics (Weight, Waist, Body Fat) ---
    @Query("SELECT * FROM body_metrics ORDER BY dateIso ASC")
    fun observeAllBodyMetrics(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY dateIso ASC")
    suspend fun getAllBodyMetricsOnce(): List<BodyMetricEntity>

    @Query("SELECT * FROM body_metrics WHERE dateIso = :dateIso")
    suspend fun getBodyMetricForDateOnce(dateIso: String): BodyMetricEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBodyMetric(metric: BodyMetricEntity)
}
