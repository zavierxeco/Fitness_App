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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
//import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.basicnav.ui.theme.BasicnavTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Goals Screen", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        // Weight Loss Button
        Text(
            text = "WEIGHT LOSS",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { println("Weight loss clicked") }
                .background(Color.Blue, RoundedCornerShape(8.dp))
                .padding(16.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Muscle Growth Button
        Text(
            text = "MUSCLE GROWTH",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { println("Muscle growth clicked") }
                .background(Color.Green, RoundedCornerShape(8.dp))
                .padding(16.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )
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