package com.example.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class BanglaMotionDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        private const val DATABASE_NAME = "banglamotion_studio.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN subtitleCuesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE projects ADD COLUMN scenesJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE projects ADD COLUMN audioAssetJson TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: BanglaMotionDatabase? = null

        fun getInstance(context: Context): BanglaMotionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BanglaMotionDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
