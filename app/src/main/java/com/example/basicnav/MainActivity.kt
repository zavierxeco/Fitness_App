package com.example.basicnav

//import androidx.compose.material3.Scaffold


import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.basicnav.ui.theme.BasicnavTheme
import java.io.File
import java.io.FileOutputStream

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
    var currentScreen by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Text(
            text = when (currentScreen) {
                0 -> "Tracking"
                1 -> "Goals"
                2 -> "Profile"
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
                0 -> TrackingScreen()
                1 -> GoalsScreen()
                2 -> ProfileScreen()
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
        }
    }
}

@Composable
fun TrackingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tracking Screen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GoalsScreen() {
    var selectedGoal by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Goals", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(24.dp))

        // Weight Loss Goal
        GoalCard(
            title = "Weight Loss",
            icon = Icons.Default.Info,
            backgroundColor = Color(0xFF1976D2),
            isSelected = selectedGoal == "weight_loss",
            onClick = {
                selectedGoal = if (selectedGoal == "weight_loss") null else "weight_loss"
                saveToStorage(context, "selected_goal", selectedGoal ?: "")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Muscle Growth Goal
        GoalCard(
            title = "Muscle Growth",
            icon = Icons.Default.Build,
            backgroundColor = Color(0xFF388E3C),
            isSelected = selectedGoal == "muscle_growth",
            onClick = {
                selectedGoal = if (selectedGoal == "muscle_growth") null else "muscle_growth"
                saveToStorage(context, "selected_goal", selectedGoal ?: "")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // General Fitness Goal
        GoalCard(
            title = "General Fitness",
            icon = Icons.Default.AccountBox,
            backgroundColor = Color(0xFFF57C00),
            isSelected = selectedGoal == "fitness",
            onClick = {
                selectedGoal = if (selectedGoal == "fitness") null else "fitness"
                saveToStorage(context, "selected_goal", selectedGoal ?: "")
            }
        )

        if (selectedGoal != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { selectedGoal = null }) {
                Text("Clear Selection")
            }
        }
    }
}

@Composable
fun GoalCard(
    title: String,
    icon: ImageVector,
    backgroundColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor.copy(alpha = if (isSelected) 1f else 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Load saved data from storage
    var name by remember { mutableStateOf(loadFromStorage(context, "name", "Enter Name")) }
    var email by remember { mutableStateOf(loadFromStorage(context, "email", "Enter Email")) }
    var age by remember { mutableStateOf(loadFromStorage(context, "age", "25")) }
    var weight by remember { mutableStateOf(loadFromStorage(context, "weight", "70 kg")) }
    var height by remember { mutableStateOf(loadFromStorage(context, "height", "180 cm")) }
    var selectedRace by remember { mutableStateOf(loadFromStorage(context, "race", "Asian")) }

    // Profile image URI
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }

    // Load saved image from permanent storage
    LaunchedEffect(Unit) {
        // Try to load from permanent file first
        val file = File(context.filesDir, "profile_pic.jpg")
        if (file.exists()) {
            profileImageUri = Uri.fromFile(file)
        } else {
            // Fallback to old saved URI (for backward compatibility)
            val savedImageUriString = loadFromStorage(context, "profile_image", "")
            if (savedImageUriString.isNotEmpty()) {
                profileImageUri = Uri.parse(savedImageUriString)
            }
        }
    }

    var isEditing by remember { mutableStateOf(false) }

    // Create an ActivityResultLauncher for picking images
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { selectedUri: Uri ->
                try {
                    // DELETE OLD FILE FIRST
                    val oldFile = File(context.filesDir, "profile_pic.jpg")
                    if (oldFile.exists()) {
                        oldFile.delete()
                    }
                    val file = File(context.filesDir, "profile_pic.jpg")
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Use the permanent file URI
                    val permanentUri = Uri.fromFile(file)
                    profileImageUri = permanentUri
                    saveToStorage(context, "profile_image", permanentUri.toString())

                } catch (e: Exception) {
                    // Fallback to original (temporary)
                    profileImageUri = selectedUri
                    saveToStorage(context, "profile_image", selectedUri.toString())
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile header with edit button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    if (isEditing) {
                        // Save all data when clicking Save
                        saveToStorage(context, "name", name)
                        saveToStorage(context, "email", email)
                        saveToStorage(context, "age", age)
                        saveToStorage(context, "weight", weight)
                        saveToStorage(context, "height", height)
                        saveToStorage(context, "race", selectedRace)
                    }
                    isEditing = !isEditing
                }
            ) {
                Text(if (isEditing) "Save" else "Edit")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile picture with upload functionality
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (profileImageUri != null) {
                // Show selected image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(profileImageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                // Show default avatar
                Text("👤", fontSize = 48.sp)
            }

            if (isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            imagePicker.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (profileImageUri != null) "Change Photo" else "Add Photo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Profile fields
        ProfileField(
            label = "Name",
            value = name,
            isEditing = isEditing,
            onValueChange = { name = it }
        )

        ProfileField(
            label = "Email",
            value = email,
            isEditing = isEditing,
            onValueChange = { email = it }
        )

        ProfileField(
            label = "Age",
            value = age,
            isEditing = isEditing,
            onValueChange = { age = it }
        )

        ProfileField(
            label = "Weight",
            value = weight,
            isEditing = isEditing,
            onValueChange = { weight = it }
        )

        ProfileField(
            label = "Height",
            value = height,
            isEditing = isEditing,
            onValueChange = { height = it }
        )

        // Race selection
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Race",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val races = listOf("Asian", "White", "Black", "Hispanic", "Other")
            races.forEach { race ->
                RaceChip(
                    race = race,
                    isSelected = race == selectedRace,
                    isEditing = isEditing,
                    onClick = { if (isEditing) selectedRace = race }
                )
            }
        }
    }
}

// Storage helper functions
private fun saveToStorage(context: Context, key: String, value: String) {
    val prefs = context.getSharedPreferences("profile_data", Context.MODE_PRIVATE)
    prefs.edit().putString(key, value).apply()
}

private fun loadFromStorage(context: Context, key: String, defaultValue: String): String {
    val prefs = context.getSharedPreferences("profile_data", Context.MODE_PRIVATE)
    return prefs.getString(key, defaultValue) ?: defaultValue
}

@Composable
fun ProfileField(
    label: String,
    value: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isEditing) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RaceChip(
    race: String,
    isSelected: Boolean,
    isEditing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(all = 4.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(size = 16.dp)
            )
            .clickable(
                enabled = isEditing,
                onClick = onClick
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(size = 16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = race,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBasicnavApp() {
    BasicnavTheme {
        BasicnavApp()
    }
}