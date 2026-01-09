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
    version = 15,
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

        // Migration from version 10 to 11: Placeholder migration
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // This migration may have been skipped or handled elsewhere
                // Keeping as placeholder for proper version progression
            }
        }

        // Migration from version 11 to 12: Placeholder migration
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Placeholder migration for proper version progression
            }
        }

        // Migration from version 12 to 13: Add title and artist columns to playlist_tracks
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if title column exists before adding it
                val cursor = db.query("PRAGMA table_info(playlist_tracks)")
                var titleExists = false
                var artistExists = false
                cursor.use {
                    while (it.moveToNext()) {
                        val columnName = it.getString(it.getColumnIndex("name"))
                        when (columnName) {
                            "title" -> titleExists = true
                            "artist" -> artistExists = true
                        }
                    }
                }

                if (!titleExists) {
                    db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                }
                if (!artistExists) {
                    db.execSQL("ALTER TABLE playlist_tracks ADD COLUMN artist TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        // Migration from version 13 to 14: Add difficultyName to playlist_tracks and update primary key
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the new table with the new schema (including difficultyName in primary key)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks_new (
                        playlistId INTEGER NOT NULL,
                        beatmapSetId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        difficultyName TEXT NOT NULL DEFAULT '',
                        addedAt INTEGER NOT NULL,
                        isDownloaded INTEGER,
                        PRIMARY KEY(playlistId, beatmapSetId, difficultyName)
                    )
                """)

                // 2. Copy data from the old table to the new one
                // Check if difficultyName already exists in old table (it shouldn't, but let's be safe)
                val cursor = db.query("PRAGMA table_info(playlist_tracks)")
                var difficultyNameExists = false
                cursor.use {
                    while (it.moveToNext()) {
                        val columnName = it.getString(it.getColumnIndex("name"))
                        if (columnName == "difficultyName") {
                            difficultyNameExists = true
                            break
                        }
                    }
                }

                if (difficultyNameExists) {
                    db.execSQL("""
                        INSERT INTO playlist_tracks_new (playlistId, beatmapSetId, title, artist, difficultyName, addedAt, isDownloaded)
                        SELECT playlistId, beatmapSetId, title, artist, difficultyName, addedAt, isDownloaded FROM playlist_tracks
                    """)
                } else {
                    db.execSQL("""
                        INSERT INTO playlist_tracks_new (playlistId, beatmapSetId, title, artist, difficultyName, addedAt, isDownloaded)
                        SELECT playlistId, beatmapSetId, title, artist, '', addedAt, isDownloaded FROM playlist_tracks
                    """)
                }

                // 3. Remove the old table
                db.execSQL("DROP TABLE playlist_tracks")

                // 4. Rename the new table to the original name
                db.execSQL("ALTER TABLE playlist_tracks_new RENAME TO playlist_tracks")
                
                // 5. Recreate indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks (playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_beatmapSetId ON playlist_tracks (beatmapSetId)")
            }
        }

        // Migration from version 14 to 15: Add isAlbum to beatmaps
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if isAlbum column exists before adding it
                val cursor = db.query("PRAGMA table_info(beatmaps)")
                var isAlbumExists = false
                cursor.use {
                    while (it.moveToNext()) {
                        val columnName = it.getString(it.getColumnIndex("name"))
                        if (columnName == "isAlbum") {
                            isAlbumExists = true
                            break
                        }
                    }
                }

                if (!isAlbumExists) {
                    db.execSQL("ALTER TABLE beatmaps ADD COLUMN isAlbum INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mosu_database"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
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
