package com.example.basicnav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Holds chat state for the lifetime of the activity so switching tabs does not clear history.
 * Cleared when the app process is killed or the activity is destroyed.
 */
class ChatbotViewModel(
    private val fitnessPlanViewModel: FitnessPlanViewModel
) : ViewModel() {

    private val chatbotService = ChatbotService()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    init {
        ensureWelcomeMessage()
    }

    private fun ensureWelcomeMessage() {
        _messages.update { current ->
            if (current.isEmpty()) {
                listOf(
                    ChatMessage(
                        text = "Hi! I'm your AI fitness coach. Ask me anything about workouts, nutrition, or your health goals!",
                        isUser = false
                    )
                )
            } else {
                current
            }
        }
    }

    fun setInputText(value: String) {
        _inputText.value = value
    }

    fun sendMessage() {
        val trimmed = _inputText.value.trim()
        if (trimmed.isBlank() || _isLoading.value) return

        _inputText.value = ""
        pendingPlanText = null
        _messages.update {
            it.filterNot { m -> m.isCommitPrompt } + ChatMessage(text = trimmed, isUser = true)
        }
        val historySnapshot = _messages.value
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val apiContext = ChatApiContext(
                    todayDayOfWeek = today.dayOfWeek,
                    todayIsoDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    savedPlanRaw = fitnessPlanViewModel.savedPlanRawForApi()
                )
                val response = chatbotService.sendMessage(historySnapshot, apiContext)
                _messages.update { it + ChatMessage(text = response, isUser = false) }
                if (FitnessPlanParser.looksLikeFitnessPlan(response)) {
                    pendingPlanText = response
                    _messages.update {
                        it + ChatMessage(
                            text = COMMIT_PROMPT_TEXT,
                            isUser = false,
                            isCommitPrompt = true
                        )
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Raw assistant text of the last detected plan, until the user commits or dismisses. */
    private var pendingPlanText: String? = null

    fun onCommitPlanYes() {
        val plan = pendingPlanText ?: return
        pendingPlanText = null
        val saved = fitnessPlanViewModel.tryImportFromAssistantResponse(plan)
        _messages.update { list ->
            val withoutPrompt = list.filterNot { it.isCommitPrompt }
            withoutPrompt + ChatMessage(
                text = if (saved) {
                    "Done — this plan is saved to your Goals tab."
                } else {
                    "That message didn't contain a plan I could import yet. Ask for a clear week-by-week schedule with lines like \"Monday: …\" for each day, then try saving again."
                },
                isUser = false
            )
        }
    }

    fun onCommitPlanNo() {
        pendingPlanText = null
        _messages.update { list -> list.filterNot { it.isCommitPrompt } }
    }

    private companion object {
        const val COMMIT_PROMPT_TEXT =
            "Would you like to commit this fitness plan to your Goals screen? You can open the Goals tab to review it and log your workouts."
    }
}
