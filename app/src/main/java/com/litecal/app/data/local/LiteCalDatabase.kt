package com.litecal.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodItemEntity::class,
        MealComboEntity::class,
        MealComboItemEntity::class,
        MealLogEntity::class,
        DailyOverrideEntity::class,
        BodyMetricEntity::class,
        ExerciseLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LiteCalDatabase : RoomDatabase() {

    abstract fun dao(): LiteCalDao

    companion object {
        @Volatile
        private var INSTANCE: LiteCalDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exercise_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `burnKcal` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_logs_dateIso` ON `exercise_logs` (`dateIso`)"
                )
            }
        }

        fun getInstance(context: Context): LiteCalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiteCalDatabase::class.java,
                    "litecal_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
