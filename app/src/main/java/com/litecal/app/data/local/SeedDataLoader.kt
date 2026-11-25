package com.litecal.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SeedFoodDto(
    val category: String,
    val name: String,
    val kcalPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val defaultUnitName: String,
    val defaultUnitGrams: Float,
    val isFavorite: Boolean = false
)

object SeedDataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureSeeded(context: Context, dao: LiteCalDao) = withContext(Dispatchers.IO) {
        if (dao.getFoodItemCount() > 0) return@withContext
        runCatching {
            val rawJson = context.assets.open("builtin_foods.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val dtos = json.decodeFromString<List<SeedFoodDto>>(rawJson)
            val entities = dtos.map { dto ->
                FoodItemEntity(
                    category = dto.category,
                    name = dto.name,
                    kcalPer100g = dto.kcalPer100g,
                    proteinPer100g = dto.proteinPer100g,
                    carbsPer100g = dto.carbsPer100g,
                    fatPer100g = dto.fatPer100g,
                    defaultUnitName = dto.defaultUnitName,
                    defaultUnitGrams = dto.defaultUnitGrams,
                    isBuiltIn = true,
                    isFavorite = dto.isFavorite,
                    useCount = 0
                )
            }
            dao.insertFoodItems(entities)

            // 预置 2 个最典型的高蛋白减脂套餐模板，方便首次打开即体验一键整餐入账
            dao.createComboWithItems(
                combo = MealComboEntity(
                    name = "经典高蛋白快手早餐",
                    defaultMealType = "BREAKFAST",
                    useCount = 1
                ),
                items = listOf(
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = "水煮鸡蛋（全蛋）",
                        portionDesc = "2个 (110g)",
                        totalKcal = 158f,
                        proteinG = 14.6f,
                        carbsG = 3.1f,
                        fatG = 9.7f
                    ),
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = "纯牛奶（全脂）",
                        portionDesc = "一盒250ml",
                        totalKcal = 163f,
                        proteinG = 8.0f,
                        carbsG = 12.0f,
                        fatG = 9.0f
                    ),
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = "全麦吐司面包",
                        portionDesc = "一片 (45g)",
                        totalKcal = 111f,
                        proteinG = 4.4f,
                        carbsG = 19.9f,
                        fatG = 1.6f
                    )
                )
            )
            dao.createComboWithItems(
                combo = MealComboEntity(
                    name = "食堂控卡均衡午餐",
                    defaultMealType = "LUNCH",
                    useCount = 1
                ),
                items = listOf(
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = "米饭（熟白米饭）",
                        portionDesc = "小半碗 (120g)",
                        totalKcal = 139f,
                        proteinG = 3.1f,
                        carbsG = 31.1f,
                        fatG = 0.4f
                    ),
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = "去皮卤鸡腿 / 琵琶腿肉",
                        portionDesc = "一个去皮肉 (110g)",
                        totalKcal = 163f,
                        proteinG = 22.2f,
                        carbsG = 2.2f,
                        fatG = 7.2f
                    ),
                    MealComboItemEntity(
                        comboId = 0,
                        foodName = "蒜蓉清炒西兰花",
                        portionDesc = "一盘 (180g)",
                        totalKcal = 99f,
                        proteinG = 6.8f,
                        carbsG = 9.4f,
                        fatG = 4.3f
                    )
                )
            )
        }
    }
}
