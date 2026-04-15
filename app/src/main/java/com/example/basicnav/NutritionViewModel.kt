package com.example.basicnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = NutritionRepository(application)

    private val _state = MutableStateFlow<NutritionDayState>(
        repo.loadToday(LocalDate.now())
    )
    val state: StateFlow<NutritionDayState> = _state.asStateFlow()

    private fun persist() {
        repo.saveToday(_state.value)
    }

    fun ensureToday() {
        val today = LocalDate.now()
        val s = repo.loadToday(today)
        _state.value = s
    }

    fun addFood(name: String, caloriesKcal: Int, proteinGrams: Int) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val item = FoodLog(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            caloriesKcal = caloriesKcal.coerceAtLeast(0),
            proteinGrams = proteinGrams.coerceAtLeast(0)
        )
        _state.update { it.copy(foods = it.foods + item) }
        persist()
    }

    fun updateFood(id: String, name: String, caloriesKcal: Int, proteinGrams: Int) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _state.update { s ->
            s.copy(
                foods = s.foods.map { f ->
                    if (f.id == id) f.copy(
                        name = trimmed,
                        caloriesKcal = caloriesKcal.coerceAtLeast(0),
                        proteinGrams = proteinGrams.coerceAtLeast(0)
                    ) else f
                }
            )
        }
        persist()
    }

    fun deleteFood(id: String) {
        _state.update { it.copy(foods = it.foods.filterNot { f -> f.id == id }) }
        persist()
    }

    fun addWater(liters: Double) {
        val v = liters.coerceAtLeast(0.0)
        if (v <= 0.0) return
        val timeLabel = runCatching {
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("")
        val item = WaterLog(
            id = UUID.randomUUID().toString(),
            liters = v,
            timeLabel = timeLabel
        )
        _state.update { it.copy(waters = it.waters + item) }
        persist()
    }

    fun updateWater(id: String, liters: Double) {
        val v = liters.coerceAtLeast(0.0)
        _state.update { s ->
            s.copy(waters = s.waters.map { w -> if (w.id == id) w.copy(liters = v) else w })
        }
        persist()
    }

    fun deleteWater(id: String) {
        _state.update { it.copy(waters = it.waters.filterNot { w -> w.id == id }) }
        persist()
    }
}

