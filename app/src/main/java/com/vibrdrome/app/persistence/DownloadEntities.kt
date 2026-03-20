package com.vibrdrome.app.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloaded_songs")
data class DownloadedSong(
    @PrimaryKey
    val songId: String,
    val filePath: String,
    val fileSize: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val coverArt: String?,
    val downloadedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadedSong>>

    @Query("SELECT * FROM downloaded_songs WHERE songId = :songId")
    suspend fun getBySongId(songId: String): DownloadedSong?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: DownloadedSong)

    @Query("DELETE FROM downloaded_songs WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("SELECT SUM(fileSize) FROM downloaded_songs")
    suspend fun totalSize(): Long?
}
