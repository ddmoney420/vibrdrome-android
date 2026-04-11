package com.vibrdrome.app.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

object SettingsBackup {

    private val PREF_FILES = listOf(
        "vibrdrome_prefs",
        "playback_prefs",
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportSettings(context: Context): String {
        val allPrefs = mutableMapOf<String, JsonObject>()
        for (name in PREF_FILES) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val entries = mutableMapOf<String, JsonElement>()
            prefs.all.forEach { (key, value) ->
                val element: JsonElement = when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Int -> JsonPrimitive(value)
                    is Long -> JsonPrimitive(value)
                    is Float -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    else -> JsonPrimitive(value.toString())
                }
                entries[key] = element
            }
            allPrefs[name] = JsonObject(entries)
        }
        return json.encodeToString(JsonObject(allPrefs.mapValues { it.value as JsonElement }))
    }

    fun importSettings(context: Context, jsonString: String) {
        val root = json.parseToJsonElement(jsonString).jsonObject
        for ((prefName, prefValues) in root) {
            if (prefName !in PREF_FILES) continue
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val values = prefValues.jsonObject
            for ((key, element) in values) {
                val prim = element.jsonPrimitive
                when {
                    prim.booleanOrNull != null -> editor.putBoolean(key, prim.boolean)
                    prim.intOrNull != null -> editor.putInt(key, prim.int)
                    prim.longOrNull != null -> editor.putLong(key, prim.long)
                    prim.floatOrNull != null -> editor.putFloat(key, prim.float)
                    prim.isString -> editor.putString(key, prim.content)
                }
            }
            editor.apply()
        }
    }
}
