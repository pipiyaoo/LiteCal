package com.litecal.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "food_items",
    indices = [Index(value = ["name"]), Index(value = ["category"])]
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String = "常吃自定义",
    val name: String,
    val kcalPer100g: Float,
    val proteinPer100g: Float = 0f,
    val carbsPer100g: Float = 0f,
    val fatPer100g: Float = 0f,
    val defaultUnitName: String = "一份",
    val defaultUnitGrams: Float = 100f,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    val useCount: Int = 0
)

@Entity(tableName = "meal_combos")
data class MealComboEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultMealType: String = "BREAKFAST",
    val useCount: Int = 0
)

@Entity(
    tableName = "meal_combo_items",
    foreignKeys = [
        ForeignKey(
            entity = MealComboEntity::class,
            parentColumns = ["id"],
            childColumns = ["comboId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["comboId"])]
)
data class MealComboItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comboId: Long,
    val foodName: String,
    val portionDesc: String,
    val totalKcal: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float
)

data class MealComboWithItems(
    @Embedded val combo: MealComboEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "comboId"
    )
    val items: List<MealComboItemEntity>
) {
    val totalKcal: Float get() = items.sumOf { it.totalKcal.toDouble() }.toFloat()
    val totalProteinG: Float get() = items.sumOf { it.proteinG.toDouble() }.toFloat()
    val totalCarbsG: Float get() = items.sumOf { it.carbsG.toDouble() }.toFloat()
    val totalFatG: Float get() = items.sumOf { it.fatG.toDouble() }.toFloat()
}

@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["dateIso"]), Index(value = ["dateIso", "mealType"])]
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String, // YYYY-MM-DD
    val mealType: String, // BREAKFAST, LUNCH, DINNER, SNACK
    val foodName: String,
    val portionDesc: String,
    val totalKcal: Float,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val sourceType: String = "DB", // DB, TEMPLATE, AI, QUICK
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_overrides")
data class DailyOverrideEntity(
    @PrimaryKey val dateIso: String, // YYYY-MM-DD
    val isIncompleteDay: Boolean = false,
    val waterMl: Int = 0
)

@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey val dateIso: String, // YYYY-MM-DD
    val weightKg: Float? = null,
    val waistCm: Float? = null,
    val bodyFatPct: Float? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class DailyCalorieSum(
    val dateIso: String,
    val totalKcal: Float,
    val totalProteinG: Float
)
