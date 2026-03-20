package com.vibrdrome.app.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "pending_actions")
data class PendingAction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val paramsJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
)

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    fun getAll(): Flow<List<PendingAction>>

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int

    @Insert
    suspend fun insert(action: PendingAction)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_actions SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM pending_actions WHERE retryCount > 5")
    suspend fun deleteStale()

    @Query("DELETE FROM pending_actions")
    suspend fun clear()
}

class OfflineActionQueue(
    private val dao: PendingActionDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val pendingActions: Flow<List<PendingAction>> = dao.getAll()

    suspend fun pendingCount(): Int = dao.count()

    suspend fun enqueue(type: String, params: Map<String, String>) {
        dao.insert(
            PendingAction(
                type = type,
                paramsJson = json.encodeToString(params),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun processAll(client: SubsonicClient) {
        dao.deleteStale()
        val actions = mutableListOf<PendingAction>()
        // Collect current actions (one-shot)
        dao.getAll().collect { actions.addAll(it); return@collect }

        for (action in actions) {
            try {
                execute(action, client)
                dao.delete(action.id)
            } catch (_: Throwable) {
                dao.incrementRetry(action.id)
            }
        }
    }

    private suspend fun execute(action: PendingAction, client: SubsonicClient) {
        val params: Map<String, String> = json.decodeFromString(action.paramsJson)
        when (action.type) {
            "star" -> client.star(id = params["id"], albumId = params["albumId"], artistId = params["artistId"])
            "unstar" -> client.unstar(id = params["id"], albumId = params["albumId"], artistId = params["artistId"])
            "scrobble" -> client.scrobble(params["id"]!!)
            "setRating" -> client.setRating(params["id"]!!, params["rating"]!!.toInt())
        }
    }

    suspend fun clear() = dao.clear()
}
