package com.vibrdrome.app.ui.player

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ToolbarAction(
    val id: String,
    val label: String,
    val visible: Boolean = true,
    val order: Int,
)

object ToolbarActionId {
    const val SPEED = "speed"
    const val SLEEP = "sleep"
    const val BOOKMARK = "bookmark"
    const val FAVORITE = "favorite"
    const val PLAYLIST = "playlist"
    const val RATING = "rating"
}

object NowPlayingToolbarConfig {
    private const val PREFS_NAME = "vibrdrome_prefs"
    private const val KEY = "np_toolbar_config"

    val defaultActions = listOf(
        ToolbarAction(ToolbarActionId.SPEED, "Speed", true, 0),
        ToolbarAction(ToolbarActionId.SLEEP, "Sleep Timer", true, 1),
        ToolbarAction(ToolbarActionId.BOOKMARK, "Bookmark", true, 2),
        ToolbarAction(ToolbarActionId.FAVORITE, "Favorite", true, 3),
        ToolbarAction(ToolbarActionId.PLAYLIST, "Add to Playlist", true, 4),
        ToolbarAction(ToolbarActionId.RATING, "Rating", true, 5),
    )

    fun load(context: Context): List<ToolbarAction> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return defaultActions
        return try {
            Json.decodeFromString<List<ToolbarAction>>(json)
        } catch (_: Throwable) {
            defaultActions
        }
    }

    fun save(context: Context, actions: List<ToolbarAction>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, Json.encodeToString(actions)).apply()
    }
}
