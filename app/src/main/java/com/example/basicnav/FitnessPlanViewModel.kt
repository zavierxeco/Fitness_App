package com.example.basicnav

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FitnessPlanViewModel : ViewModel() {

    private val _plan = MutableStateFlow<ParsedFitnessPlan?>(null)
    val plan: StateFlow<ParsedFitnessPlan?> = _plan.asStateFlow()

    /** Dates the user marked as having completed that day's objective. */
    private val _completedDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val completedDates: StateFlow<Set<LocalDate>> = _completedDates.asStateFlow()

    /**
     * Called when the AI returns a new message. If the text looks like a workout plan,
     * replaces the current plan and clears completion marks.
     */
    fun tryImportFromAssistantResponse(assistantText: String) {
        val parsed = FitnessPlanParser.parse(assistantText) ?: return
        _plan.value = parsed
        _completedDates.value = emptySet()
    }

    fun toggleDateCompleted(date: LocalDate) {
        _completedDates.update { current ->
            if (date in current) current - date else current + date
        }
    }

    fun isCompleted(date: LocalDate): Boolean = date in _completedDates.value

    fun clearPlan() {
        _plan.value = null
        _completedDates.value = emptySet()
    }
}
