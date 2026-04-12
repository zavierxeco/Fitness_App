package com.example.basicnav

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FitnessPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FitnessPlanRepository(application)

    private val _plan = MutableStateFlow<ParsedFitnessPlan?>(null)
    val plan: StateFlow<ParsedFitnessPlan?> = _plan.asStateFlow()

    /** Per-date set of completed workout item ids. */
    private val _completedItems = MutableStateFlow<Map<LocalDate, Set<String>>>(emptyMap())
    val completedItems: StateFlow<Map<LocalDate, Set<String>>> = _completedItems.asStateFlow()

    init {
        _plan.value = repository.loadPlan()
        _completedItems.value = repository.loadCompletedItems()
    }

    private fun persist() {
        repository.save(_plan.value, _completedItems.value)
    }

    /** @return true if a structured plan was saved */
    fun tryImportFromAssistantResponse(assistantText: String): Boolean {
        val parsed = FitnessPlanParser.parse(assistantText) ?: return false
        _plan.value = parsed
        _completedItems.value = emptyMap()
        persist()
        return true
    }

    fun toggleItemCompleted(date: LocalDate, itemId: String) {
        _completedItems.update { current ->
            val set = current[date].orEmpty()
            val next = if (itemId in set) set - itemId else set + itemId
            if (next.isEmpty()) current - date else current + (date to next)
        }
        persist()
    }

    fun isItemCompleted(date: LocalDate, itemId: String): Boolean =
        itemId in _completedItems.value[date].orEmpty()

    fun dayCompletionState(date: LocalDate): Boolean {
        val p = _plan.value ?: return false
        return p.allItemsCompleted(date, _completedItems.value[date].orEmpty())
    }

    fun clearPlan() {
        _plan.value = null
        _completedItems.value = emptyMap()
        persist()
    }

    /** Text injected into the chat system prompt when the user may want to adjust the saved plan. */
    fun savedPlanRawForApi(): String? = _plan.value?.rawSourceText?.takeIf { it.isNotBlank() }
}
