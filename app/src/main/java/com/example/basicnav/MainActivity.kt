package com.example.basicnav

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Star
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    2001
                )
            }
        }
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
    val fitnessPlanViewModel: FitnessPlanViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as Application
                return FitnessPlanViewModel(app) as T
            }
        }
    )
    val chatViewModel: ChatbotViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatbotViewModel(fitnessPlanViewModel) as T
            }
        }
    )
    val nutritionViewModel: NutritionViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext as Application
                return NutritionViewModel(app) as T
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
                2 -> "Nutrition"
                3 -> "Profile"
                4 -> "Debug"
                5 -> "AI Coach"
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
                1 -> GoalsScreen(fitnessPlanViewModel = fitnessPlanViewModel)
                2 -> NutritionScreen(nutritionViewModel = nutritionViewModel, fitnessPlanViewModel = fitnessPlanViewModel)
                3 -> ProfileScreen()
                4 -> DebugScreen()
                5 -> ChatbotScreen(viewModel = chatViewModel)
            }
        }

        // Bottom navigation (simple row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray)
                .navigationBarsPadding()
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
                    Icons.Default.Star,
                    contentDescription = "Nutrition",
                    tint = if (currentScreen == 2) Color.Blue else Color.Gray
                )
                Text(
                    "Nutrition",
                    color = if (currentScreen == 2) Color.Blue else Color.Gray
                )
            }
            // Debug button
            Column(
                modifier = Modifier.clickable { currentScreen = 3 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AccountBox,
                    contentDescription = "Profile",
                    tint = if (currentScreen == 3) Color.Blue else Color.Gray
                )
                Text(
                    "Profile",
                    color = if (currentScreen == 3) Color.Blue else Color.Gray
                )
            }
            // Add Chatbot button
            Column(
                modifier = Modifier.clickable { currentScreen = 4 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Debug",
                    tint = if (currentScreen == 4) Color.Blue else Color.Gray
                )
                Text(
                    "Debug",
                    color = if (currentScreen == 4) Color.Blue else Color.Gray
                )
            }
            Column(
                modifier = Modifier.clickable { currentScreen = 5 },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Chatbot",
                    tint = if (currentScreen == 5) Color.Blue else Color.Gray
                )
                Text(
                    "Chatbot",
                    color = if (currentScreen == 5) Color.Blue else Color.Gray
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