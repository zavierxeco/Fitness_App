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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {
    private val logs = mutableListOf<String>()
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

    suspend fun saveLogsToFile(context: Context): File? {
        return withContext(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ring_debug_$timeStamp.txt"

                val file = File(context.getExternalFilesDir(null), fileName)
                val content = logs.joinToString("\n")
                file.writeText(content)

                addLog("Logs saved to: ${file.absolutePath}")
                file
            } catch (e: Exception) {
                addLog("Error saving logs: ${e.message}")
                null
            }
        }
    }
}

@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by remember { mutableStateOf(DebugLogger.getLogs()) }
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // Refresh logs periodically
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            // This will trigger recomposition when logs change
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

            IconButton(onClick = onBack) {
                Text("← Back")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { DebugLogger.clearLogs() },
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
                        DebugLogger.saveLogsToFile(context)
                        isSaving = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save to File")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logs Display
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No logs yet. Go back and use the ring app to generate logs.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.reversed()) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (log.contains("ERROR") || log.contains("error"))
                                MaterialTheme.colorScheme.errorContainer
                            else if (log.contains("BPM"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
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