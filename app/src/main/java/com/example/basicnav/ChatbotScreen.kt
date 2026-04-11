package com.example.basicnav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Date = Date(),
    /** Local-only follow-up after a parsed fitness plan; not sent back to the API. */
    val isCommitPrompt: Boolean = false
)

class ChatbotService {
    /**
     * GLM-4.7 defaults to "thinking" mode. Non-streaming clients that only read [message.content]
     * often see blank text while the model fills [reasoning_content]. Disabling thinking restores
     * a single user-facing string in [content]. Long plans also need a longer read timeout than
     * OkHttp's default 10s.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val apiKey = "0a957e88a4844b7dbc8e73b6ee75b26a.xjHK0E5mvbkkTZIz" // Replace with your actual API key
    private val baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

    private fun includeInApiHistory(message: ChatMessage): Boolean {
        if (message.isCommitPrompt) return false
        if (message.text.isBlank()) return false
        if (!message.isUser) {
            val t = message.text.trimStart()
            if (t.startsWith("Error:") || t.startsWith("Network error", ignoreCase = true)) return false
        }
        return true
    }

    suspend fun sendMessage(conversation: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        try {
            val messagesJson = org.json.JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    }
                )
                conversation.filter(::includeInApiHistory).forEach { m ->
                    put(
                        JSONObject().apply {
                            put("role", if (m.isUser) "user" else "assistant")
                            put("content", m.text)
                        }
                    )
                }
            }

            val json = JSONObject().apply {
                put("model", "glm-4.7-flash")
                put("messages", messagesJson)
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("max_tokens", 8192)
                put("temperature", 0.7)
            }

            val mediaType = "application/json".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext "Error: no choices in response — $responseBody"
                }
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                if (message == null) {
                    return@withContext "Error: missing message — $responseBody"
                }
                val content = message.optString("content", "").trim()
                val reasoning = message.optString("reasoning_content", "").trim()
                when {
                    content.isNotEmpty() -> content
                    reasoning.isNotEmpty() -> reasoning
                    else -> {
                        val apiErr = jsonResponse.optJSONObject("error")?.optString("message")
                        if (!apiErr.isNullOrBlank()) {
                            "Error: $apiErr"
                        } else {
                            val reason = choice.optString("finish_reason", "unknown")
                            "Empty reply from model (finish_reason=$reason). Raw: ${responseBody.take(500)}"
                        }
                    }
                }
            } else {
                "Error: ${response.code} - $responseBody"
            }
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You are an AI fitness coach. Be encouraging and practical.
            For quick questions, keep answers brief.
            When the user asks for a workout or nutrition plan, a program, or goals over weeks/months,
            respond with a clear structured plan (sections/bullet points are fine). Do not refuse solely
            because the answer is longer. Remind users to consult a doctor for medical conditions.
            For weekly schedules, put each day on its own line with a clear label, e.g. "Mon/Wed/Fri: ...",
            or full names like "Monday: ...", "Thursday: ...". Mention the planned duration in weeks or months
            (e.g. "12 weeks", "in one month") or a target end date YYYY-MM-DD so the app can track it.
            Do not ask whether to save the plan to Goals; the app shows its own prompt after your reply.
        """.trimIndent()
    }
}

@Composable
fun ChatbotScreen(viewModel: ChatbotViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🤖", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "AI Fitness Coach",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Powered by GLM-4.7-Flash",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    onCommitYes = if (message.isCommitPrompt) {
                        { viewModel.onCommitPlanYes() }
                    } else null,
                    onCommitNo = if (message.isCommitPrompt) {
                        { viewModel.onCommitPlanNo() }
                    } else null
                )
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Typing...", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Input area
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::setInputText,
                    placeholder = { Text("Ask your fitness coach...") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    maxLines = 4,
                    minLines = 1,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Button(
                    onClick = { viewModel.sendMessage() },
                    enabled = !isLoading && inputText.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("➤", fontSize = 24.sp)
                }
            }
        }
    }
}

private val MarkdownHeadingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val MarkdownOrderedRegex = Regex("^(\\d+)\\.\\s+(.+)$")
private val MarkdownBulletPrefixRegex = Regex("^[-*+]\\s+")

/** Parses `**bold**` and single-`*italic*` (not list markers; those are handled per line). */
private fun parseInlineMarkdown(line: String, baseStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < line.length) {
        if (line.startsWith("**", i)) {
            val end = line.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(baseStyle.merge(SpanStyle(fontWeight = FontWeight.Bold))) {
                    append(line.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (line[i] == '*') {
            val end = line.indexOf('*', i + 1)
            if (end != -1 && end > i + 1) {
                withStyle(baseStyle.merge(SpanStyle(fontStyle = FontStyle.Italic))) {
                    append(line.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        withStyle(baseStyle) { append(line[i]) }
        i++
    }
}

@Composable
private fun AssistantMarkdownText(raw: String, color: Color) {
    val baseBody = SpanStyle(color = color, fontSize = 14.sp)
    val lines = raw.lines()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(2.dp))
                    i++
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        color = color.copy(alpha = 0.25f)
                    )
                    i++
                }
                else -> {
                    val heading = MarkdownHeadingRegex.matchEntire(trimmed)
                    if (heading != null) {
                        val level = heading.groupValues[1].length
                        val title = heading.groupValues[2]
                        val fontSize = when (level) {
                            1 -> 18.sp
                            2 -> 17.sp
                            3 -> 16.sp
                            4 -> 15.sp
                            else -> 14.sp
                        }
                        val weight = when (level) {
                            in 1..3 -> FontWeight.Bold
                            else -> FontWeight.SemiBold
                        }
                        val headStyle = baseBody.merge(SpanStyle(fontSize = fontSize, fontWeight = weight))
                        Text(
                            text = parseInlineMarkdown(title, headStyle),
                            modifier = Modifier.padding(top = if (i == 0) 0.dp else 2.dp, bottom = 2.dp)
                        )
                        i++
                    } else {
                        val ordered = MarkdownOrderedRegex.matchEntire(trimmed)
                        when {
                            ordered != null -> {
                                val num = ordered.groupValues[1]
                                val body = ordered.groupValues[2]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "$num.",
                                        fontSize = 14.sp,
                                        color = color,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(
                                        text = parseInlineMarkdown(body, baseBody),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                i++
                            }
                            trimmed.matches(Regex("^[-*+]\\s+.+")) -> {
                                val body = MarkdownBulletPrefixRegex.replaceFirst(trimmed, "")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        fontSize = 14.sp,
                                        color = color,
                                        modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                                    )
                                    Text(
                                        text = parseInlineMarkdown(body, baseBody),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                i++
                            }
                            else -> {
                                Text(text = parseInlineMarkdown(line, baseBody))
                                i++
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onCommitYes: (() -> Unit)? = null,
    onCommitNo: (() -> Unit)? = null
) {
    val isUser = message.isUser
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                val textColor = if (isUser)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant

                if (isUser) {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = textColor
                    )
                } else if (message.isCommitPrompt) {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = textColor
                    )
                    if (onCommitYes != null && onCommitNo != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onCommitYes,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Yes", fontSize = 14.sp)
                            }
                            OutlinedButton(
                                onClick = onCommitNo,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("No", fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    AssistantMarkdownText(
                        raw = message.text,
                        color = textColor
                    )
                }
                Text(
                    text = timeFormat.format(message.timestamp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}