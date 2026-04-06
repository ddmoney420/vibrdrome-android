package com.vibrdrome.app.persistence

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Records each playback session for a track.
 */
@Entity(tableName = "listening_sessions")
data class ListeningSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "artist_name") val artistName: String?,
    @ColumnInfo(name = "album_name") val albumName: String?,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long, // epoch ms
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "track_duration_ms") val trackDurationMs: Long = 0,
    @ColumnInfo(name = "completed") val completed: Boolean = false,
    @ColumnInfo(name = "skipped") val skipped: Boolean = false,
)

data class ArtistPlayCount(
    val artistName: String,
    val playCount: Int,
    val totalDurationMs: Long,
)

data class AlbumPlayCount(
    val albumName: String,
    val artistName: String?,
    val playCount: Int,
)

data class GenrePlayCount(
    val genre: String,
    val playCount: Int,
)

data class DayActivity(
    val dayOfWeek: Int, // 1=Sun, 7=Sat
    val hourOfDay: Int, // 0-23
    val sessionCount: Int,
)

@Dao
interface ListeningStatsDao {

    @Insert
    suspend fun insert(session: ListeningSession): Long

    @Update
    suspend fun update(session: ListeningSession)

    @Query("SELECT * FROM listening_sessions WHERE id = :id")
    suspend fun getById(id: Long): ListeningSession?

    // --- Aggregate queries ---

    @Query("""
        SELECT artist_name AS artistName, COUNT(*) AS playCount, SUM(duration_ms) AS totalDurationMs
        FROM listening_sessions
        WHERE artist_name IS NOT NULL AND started_at >= :sinceMs
        GROUP BY artist_name
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun topArtists(sinceMs: Long, limit: Int = 10): List<ArtistPlayCount>

    @Query("""
        SELECT album_name AS albumName, artist_name AS artistName, COUNT(*) AS playCount
        FROM listening_sessions
        WHERE album_name IS NOT NULL AND started_at >= :sinceMs
        GROUP BY album_name, artist_name
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun topAlbums(sinceMs: Long, limit: Int = 10): List<AlbumPlayCount>

    @Query("""
        SELECT genre AS genre, COUNT(*) AS playCount
        FROM listening_sessions
        WHERE genre IS NOT NULL AND genre != '' AND started_at >= :sinceMs
        GROUP BY genre
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun topGenres(sinceMs: Long, limit: Int = 5): List<GenrePlayCount>

    @Query("SELECT SUM(duration_ms) FROM listening_sessions WHERE started_at >= :sinceMs")
    suspend fun totalListeningMs(sinceMs: Long): Long?

    @Query("SELECT COUNT(DISTINCT song_id) FROM listening_sessions WHERE started_at >= :sinceMs")
    suspend fun uniqueTracksPlayed(sinceMs: Long): Int

    @Query("""
        SELECT COUNT(DISTINCT date(started_at / 1000, 'unixepoch', 'localtime'))
        FROM listening_sessions
        WHERE started_at >= :sinceMs
    """)
    suspend fun activeDays(sinceMs: Long): Int

    @Query("""
        SELECT song_id, artist_name AS artistName,
               COUNT(*) as skipCount
        FROM listening_sessions
        WHERE skipped = 1 AND started_at >= :sinceMs
        GROUP BY song_id
        ORDER BY skipCount DESC
        LIMIT :limit
    """)
    suspend fun mostSkippedSongIds(sinceMs: Long, limit: Int = 10): List<SkippedSong>

    @Query("""
        SELECT COUNT(*)
        FROM listening_sessions
        WHERE started_at >= :sinceMs
    """)
    suspend fun totalSessions(sinceMs: Long): Int

    /** Count of distinct days with listening activity, for streak calculation */
    @Query("""
        SELECT DISTINCT date(started_at / 1000, 'unixepoch', 'localtime') as day
        FROM listening_sessions
        ORDER BY day DESC
    """)
    suspend fun allActiveDays(): List<String>

    /** Skip rate per artist: skips / total plays */
    @Query("""
        SELECT artist_name AS artistName,
               SUM(CASE WHEN skipped = 1 THEN 1 ELSE 0 END) AS skips,
               COUNT(*) AS total
        FROM listening_sessions
        WHERE artist_name IS NOT NULL AND started_at >= :sinceMs
        GROUP BY artist_name
        HAVING total >= 5
        ORDER BY (CAST(skips AS REAL) / total) DESC
        LIMIT :limit
    """)
    suspend fun skipRateByArtist(sinceMs: Long, limit: Int = 10): List<ArtistSkipRate>

    /** Heatmap data: session count by day of week and hour */
    @Query("""
        SELECT CAST(strftime('%w', started_at / 1000, 'unixepoch', 'localtime') AS INTEGER) + 1 AS dayOfWeek,
               CAST(strftime('%H', started_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hourOfDay,
               COUNT(*) AS sessionCount
        FROM listening_sessions
        WHERE started_at >= :sinceMs
        GROUP BY dayOfWeek, hourOfDay
    """)
    suspend fun activityHeatmap(sinceMs: Long): List<DayActivity>

    /** For smart download suggestions: most-played songs on cellular */
    @Query("""
        SELECT song_id AS songId, artist_name AS artistName, album_name AS albumName, COUNT(*) AS playCount
        FROM listening_sessions
        WHERE started_at >= :sinceMs
        GROUP BY song_id
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun frequentlyPlayedSongs(sinceMs: Long, limit: Int = 30): List<FrequentSong>
}

data class ArtistSkipRate(
    val artistName: String,
    val skips: Int,
    val total: Int,
) {
    val rate: Float get() = if (total > 0) skips.toFloat() / total else 0f
}

data class SkippedSong(
    val song_id: String,
    val artistName: String?,
    val skipCount: Int,
)

data class FrequentSong(
    val songId: String,
    val artistName: String?,
    val albumName: String?,
    val playCount: Int,
)
