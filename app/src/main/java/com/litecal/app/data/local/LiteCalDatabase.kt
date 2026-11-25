package com.litecal.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodItemEntity::class,
        MealComboEntity::class,
        MealComboItemEntity::class,
        MealLogEntity::class,
        DailyOverrideEntity::class,
        BodyMetricEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LiteCalDatabase : RoomDatabase() {

    abstract fun dao(): LiteCalDao

    companion object {
        @Volatile
        private var INSTANCE: LiteCalDatabase? = null

        fun getInstance(context: Context): LiteCalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiteCalDatabase::class.java,
                    "litecal_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
