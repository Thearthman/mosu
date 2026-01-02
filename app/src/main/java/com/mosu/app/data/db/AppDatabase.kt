package com.mosu.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

@Database(
    entities = [
        BeatmapEntity::class,
        SearchCacheEntity::class,
        RecentPlayEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PreservedBeatmapSetIdEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun beatmapDao(): BeatmapDao
    abstract fun searchCacheDao(): SearchCacheDao
    abstract fun recentPlayDao(): RecentPlayDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun preservedBeatmapSetIdDao(): PreservedBeatmapSetIdDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 8 to 9: Add preserved_beatmap_set_ids table
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS preserved_beatmap_set_ids (
                        beatmapSetId INTEGER PRIMARY KEY NOT NULL,
                        preservedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        // Migration from version 9 to 10: Clear only beatmap data but preserve everything else
        // This is used when we need to reset beatmap data due to schema changes
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if isDownloaded column exists before adding it (idempotent migration)
                val cursor = db.query("PRAGMA table_info(playlist_tracks)")
                var columnExists = false
                cursor.use {
                    while (it.moveToNext()) {
                        val columnName = it.getString(it.getColumnIndex("name"))
                        if (columnName == "isDownloaded") {
                            columnExists = true
                            break
                        }
                    }
                }

                if (!columnExists) {
                    // Add isDownloaded column to playlist_tracks table (with default true)
                    db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN isDownloaded INTEGER DEFAULT 1")
                }

                // Clear only beatmap data
                db.execSQL("DELETE FROM beatmaps")

                // Mark all playlist tracks as undownloaded since beatmaps were cleared
                db.execSQL("UPDATE playlist_tracks SET isDownloaded = 0")

                // Note: preserved_beatmap_set_ids, playlists, search_cache, and recent_plays are preserved
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mosu_database"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration() // Only as last resort for major schema incompatibilities
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clears only beatmap data while preserving playlists, preserved IDs, and other user data.
         * This should be called when beatmap schema changes require clearing the data.
         */
        suspend fun clearBeatmapDataOnly(db: AppDatabase) {
            withContext(Dispatchers.IO) {
                // Mark all playlist tracks as undownloaded before clearing beatmaps
                db.playlistDao().markAllTracksAsUndownloaded()

                // Clear beatmap data from database
                // Note: We can't use DELETE FROM beatmaps here because it would trigger foreign key constraints
                // Instead, we'll delete each beatmap individually
                val allBeatmaps = db.beatmapDao().getAllBeatmaps().firstOrNull() ?: emptyList()
                allBeatmaps.forEach { beatmap ->
                    db.beatmapDao().deleteBeatmap(beatmap)
                }

                // Clean up files on disk (optional - could be done during restore instead)
                // filePaths.forEach { paths ->
                //     try { java.io.File(paths.audioPath).delete() } catch (e: Exception) { }
                //     try { java.io.File(paths.coverPath).delete() } catch (e: Exception) { }
                // }
            }
        }
    }
}
