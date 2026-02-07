package com.example.basicnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.basicnav.ui.theme.BasicnavTheme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp  // For font size

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

@PreviewScreenSizes
@Composable
fun BasicnavApp() {
    // State to track which screen to show
    var currentScreen by rememberSaveable {
        mutableStateOf<Screen>(Screen.Profile)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            // Home tab
            item(
                icon = { Icon(Icons.Default.Info, contentDescription = "Tracking") },
                label = { Text("Tracking") },
                selected = currentScreen is Screen.Home,
                onClick = {
                    currentScreen = Screen.Home
                    showHomeScreen() // Call function
                }
            )
            // Goals tab
            item(
                icon = { Icon(Icons.Default.Build, contentDescription = "Goals") },
                label = { Text("Goals") },
                selected = currentScreen is Screen.Goals,
                onClick = {
                    currentScreen = Screen.Goals
                    showGoalsScreen() // Call function
                }
            )
            // Profile tab
            item(
                icon = { Icon(Icons.Default.AccountBox, contentDescription = "Profile") },
                label = { Text("Profile") },
                selected = currentScreen is Screen.Profile,
                onClick = {
                    currentScreen = Screen.Profile
                    showProfileScreen() // Call function
                }
            )
        }
    ) {
        // Display the current screen based on state
        when (currentScreen) {
            is Screen.Home -> HomeScreen()
            is Screen.Goals -> GoalsScreen()
            is Screen.Profile -> ProfileScreen()
        }
    }
}

// Sealed class for screen types
sealed class Screen {
    object Home : Screen()
    object Goals : Screen()
    object Profile : Screen()
}

// Navigation functions (can be called from anywhere)
fun showHomeScreen() {
    println("Home screen requested")
    // You could add analytics, logging, or other logic here
}

fun showGoalsScreen() {
    println("Goals screen requested")
}

fun showProfileScreen() {
    println("Profile screen requested")
}

// Screen composables
@Composable
fun HomeScreen() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Greeting(
            name = "Home Screen",
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun GoalsScreen() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Goals Screen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileScreen() {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Profile Screen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    TRACKING("Tracking", Icons.Default.Info),
    GOALS("Goals", Icons.Default.Build),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // First Button
        Text(
            text = "WEIGHT LOSS",
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    println("Weight loss button clicked!")
                }
                .background(
                    color = Color.Blue,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Space between buttons
        Spacer(modifier = Modifier.height(16.dp))

        // Second Button
        Text(
            text = "MUSCLE GROWTH",
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    println("Muscle growth button clicked!")
                }
                .background(
                    color = Color.Green,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    BasicnavTheme {
//        Greeting("Android")
//    }
//}