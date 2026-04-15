package com.example.basicnav

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

data class FoodLog(
    val id: String,
    val name: String,
    val caloriesKcal: Int,
    val proteinGrams: Int
)

data class WaterLog(
    val id: String,
    val liters: Double,
    /** Display label for the time the log was created (e.g., "14:05"). */
    val timeLabel: String? = null
)

data class NutritionDayState(
    val dayIso: String,
    val foods: List<FoodLog>,
    val waters: List<WaterLog>
)

class NutritionRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadToday(today: LocalDate): NutritionDayState {
        val todayIso = today.toString()
        val storedDay = prefs.getString(KEY_DAY, null)
        if (storedDay == null || storedDay != todayIso) {
            // Reset daily
            saveToday(NutritionDayState(todayIso, emptyList(), emptyList()))
        }

        val foods = runCatching {
            val json = prefs.getString(KEY_FOODS, null) ?: return@runCatching emptyList()
            val type = object : TypeToken<List<FoodLog>>() {}.type
            gson.fromJson<List<FoodLog>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())

        val waters = runCatching {
            val json = prefs.getString(KEY_WATERS, null) ?: return@runCatching emptyList()
            val type = object : TypeToken<List<WaterLog>>() {}.type
            gson.fromJson<List<WaterLog>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())

        return NutritionDayState(todayIso, foods, waters)
    }

    fun saveToday(state: NutritionDayState) {
        prefs.edit().apply {
            putString(KEY_DAY, state.dayIso)
            putString(KEY_FOODS, gson.toJson(state.foods))
            putString(KEY_WATERS, gson.toJson(state.waters))
            apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "nutrition_storage"
        const val KEY_DAY = "day_iso"
        const val KEY_FOODS = "foods_json"
        const val KEY_WATERS = "waters_json"
    }
}

