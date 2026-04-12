package com.example.basicnav

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDate

/**
 * Persists the committed fitness plan and per-item completion on device.
 */
class FitnessPlanRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateSerializer())
        .create()

    fun loadPlan(): ParsedFitnessPlan? {
        val json = prefs.getString(KEY_PLAN, null) ?: return null
        return try {
            gson.fromJson(json, ParsedFitnessPlan::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun loadCompletedItems(): Map<LocalDate, Set<String>> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Set<String>>>() {}.type
        return try {
            val raw = gson.fromJson<Map<String, Set<String>>>(json, type) ?: return emptyMap()
            raw.mapNotNull { (k, v) ->
                runCatching { LocalDate.parse(k) to v }.getOrNull()
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(plan: ParsedFitnessPlan?, completedItems: Map<LocalDate, Set<String>>) {
        prefs.edit().apply {
            if (plan == null) {
                remove(KEY_PLAN)
                remove(KEY_ITEMS)
            } else {
                putString(KEY_PLAN, gson.toJson(plan))
                val asStrings = completedItems.mapKeys { it.key.toString() }
                putString(KEY_ITEMS, gson.toJson(asStrings))
            }
            apply()
        }
    }

    private class LocalDateSerializer : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        override fun serialize(src: LocalDate, typeOfSrc: Type, context: JsonSerializationContext): JsonPrimitive =
            JsonPrimitive(src.toString())

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDate =
            LocalDate.parse(json.asString)
    }

    private companion object {
        const val PREFS_NAME = "fitness_plan_storage"
        const val KEY_PLAN = "plan_json"
        const val KEY_ITEMS = "completed_items_json"
    }
}
