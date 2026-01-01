package com.mosu.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BeatmapEntity::class,
        SearchCacheEntity::class,
        RecentPlayEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun beatmapDao(): BeatmapDao
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun recentPlayDao(): RecentPlayDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mosu_database"
                )
                .fallbackToDestructiveMigration() // Reset DB since we changed schema drastically
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
