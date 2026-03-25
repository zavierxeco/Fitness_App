package com.example.basicnav

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

object DebugLogger {
    private var logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun addLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        logs.add(logEntry)
        println(logEntry) // Also print to Logcat
    }

    fun getLogs(): List<String> = logs.toList()

    fun clearLogs() {
        logs.clear()
        addLog("Logs cleared")
    }

    suspend fun shareLogs(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ring_debug_$timeStamp.txt"

                // Create file in cache
                val cacheFile = File(context.cacheDir, fileName)
                val content = logs.joinToString("\n")
                cacheFile.writeText(content)

                // Get URI for sharing
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )

                // Create share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Start share menu
                context.startActivity(Intent.createChooser(shareIntent, "Share Debug Logs"))

                addLog("Logs shared via Android share menu")
            } catch (e: Exception) {
                addLog("Error sharing logs: ${e.message}")
            }
        }
    }
}

@Composable
fun DebugScreen() {
    val context = LocalContext.current
    var allLogs by remember { mutableStateOf(DebugLogger.getLogs()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Filter logs based on search query
    val filteredLogs = remember(searchQuery, allLogs) {
        if (searchQuery.isBlank()) {
            allLogs
        } else {
            allLogs.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Refresh logs periodically
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            allLogs = DebugLogger.getLogs()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Debug Logs",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filteredLogs.size} / ${allLogs.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logs...", color = Color.Gray) },
            leadingIcon = { Text("🔍") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.LightGray,
                unfocusedPlaceholderColor = Color.LightGray,
                cursorColor = Color.White,
                focusedContainerColor = Color.DarkGray,
                unfocusedContainerColor = Color.DarkGray
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    DebugLogger.clearLogs()
                    allLogs = DebugLogger.getLogs()
                    searchQuery = ""
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear Logs")
            }
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        DebugLogger.shareLogs(context)
                        isSaving = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Sharing..." else "Share Logs")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logs Display
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty())
                        "No logs match '$searchQuery'"
                    else
                        "No logs yet. Go back and use the ring app to generate logs.",
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                state = listState
            ) {
                items(filteredLogs.reversed()) { log ->
                    val lowerLog = log.lowercase()
                    val lowerQuery = searchQuery.lowercase()

                    // Determine if this log matches the search
                    val matchesSearch = searchQuery.isNotEmpty() && lowerLog.contains(lowerQuery)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                matchesSearch -> MaterialTheme.colorScheme.tertiaryContainer
                                log.contains("ERROR") || log.contains("error") -> MaterialTheme.colorScheme.errorContainer
                                log.contains("BPM") -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(
                            text = log,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}