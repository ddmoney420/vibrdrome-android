package com.vibrdrome.app.persistence

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "playback_state")
data class SavedPlaybackState(
    @PrimaryKey
    val id: Int = 0,
    @ColumnInfo(name = "queue_json")
    val queueJson: String,
    @ColumnInfo(name = "current_index")
    val currentIndex: Int,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long,
    @ColumnInfo(name = "repeat_mode")
    val repeatMode: Int,
    @ColumnInfo(name = "shuffle_enabled")
    val shuffleEnabled: Boolean,
)

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun get(): SavedPlaybackState?

    @Upsert
    suspend fun save(state: SavedPlaybackState)

    @Query("DELETE FROM playback_state")
    suspend fun clear()
}

@Database(
    entities = [SavedPlaybackState::class, DownloadedSong::class, PendingAction::class, ListeningSession::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun downloadDao(): DownloadDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun listeningStatsDao(): ListeningStatsDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS listening_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        song_id TEXT NOT NULL,
                        artist_name TEXT,
                        album_name TEXT,
                        genre TEXT,
                        started_at INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL DEFAULT 0,
                        track_duration_ms INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0,
                        skipped INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_listening_started ON listening_sessions(started_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_listening_song ON listening_sessions(song_id)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_actions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        paramsJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS downloaded_songs (
                        songId TEXT NOT NULL PRIMARY KEY,
                        filePath TEXT NOT NULL,
                        fileSize INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT,
                        album TEXT,
                        coverArt TEXT,
                        downloadedAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
