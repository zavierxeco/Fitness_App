package com.example.basicnav

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider

import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.basicnav.ui.theme.BasicnavTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.compose.material.icons.filled.Chat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BasicnavTheme {
                BasicnavApp()
            }
        }
    }
}

@Composable
fun BasicnavApp() {
    val context = LocalContext.current
    val ringViewModel: RingViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RingViewModel(context) as T
            }
        }
    )
    var currentScreen by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Text(
            text = when (currentScreen) {
                0 -> "Tracking"
                1 -> "Goals"
                2 -> "Profile"
                3 -> "Debug"
                4 -> "AI Coach"
                else -> "App"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            when (currentScreen) {
                0 -> TrackingScreen(ringViewModel)
                1 -> GoalsScreen()
                2 -> ProfileScreen()
                3 -> DebugScreen()
                4 -> ChatbotScreen()
            }
        }

        // Bottom navigation (simple row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Tracking button
            Column(
                modifier = Modifier.clickable { currentScreen = 0 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Tracking",
                    tint = if (currentScreen == 0) Color.Blue else Color.Gray
                )
                Text(
                    "Tracking",
                    color = if (currentScreen == 0) Color.Blue else Color.Gray
                )
            }

            // Goals button
            Column(
                modifier = Modifier.clickable { currentScreen = 1 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = "Goals",
                    tint = if (currentScreen == 1) Color.Blue else Color.Gray
                )
                Text(
                    "Goals",
                    color = if (currentScreen == 1) Color.Blue else Color.Gray
                )
            }

            // Profile button
            Column(
                modifier = Modifier.clickable { currentScreen = 2 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AccountBox,
                    contentDescription = "Profile",
                    tint = if (currentScreen == 2) Color.Blue else Color.Gray
                )
                Text(
                    "Profile",
                    color = if (currentScreen == 2) Color.Blue else Color.Gray
                )
            }
            // Debug button
            Column(
                modifier = Modifier.clickable { currentScreen = 3 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Debug",
                    tint = if (currentScreen == 3) Color.Blue else Color.Gray
                )
                Text(
                    "Debug",
                    color = if (currentScreen == 3) Color.Blue else Color.Gray
                )
            }
            // Add Chatbot button
            Column(
                modifier = Modifier.clickable { currentScreen = 4 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Chatbot",
                    tint = if (currentScreen == 4) Color.Blue else Color.Gray
                )
                Text(
                    "Chatbot",
                    color = if (currentScreen == 4) Color.Blue else Color.Gray
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBasicnavApp() {
    BasicnavTheme {
        BasicnavApp()
    }
}

fun saveToStorage(context: Context, key: String, value: String) {
    val prefs = context.getSharedPreferences("profile_data", Context.MODE_PRIVATE)
    prefs.edit().putString(key, value).apply()
}

fun loadFromStorage(context: Context, key: String, defaultValue: String): String {
    val prefs = context.getSharedPreferences("profile_data", Context.MODE_PRIVATE)
    return prefs.getString(key, defaultValue) ?: defaultValue
}