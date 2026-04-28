package com.example.basicnav
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun ProfileScreen(startInEditMode: Boolean = false) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Load saved data from storage
    var name by remember { mutableStateOf(loadFromStorage(context, "name", "Enter Name")) }
    var email by remember { mutableStateOf(loadFromStorage(context, "email", "Enter Email")) }
    var gender by remember { mutableStateOf(loadFromStorage(context, "gender", "Male")) }
    var age by remember { mutableStateOf(loadFromStorage(context, "age", "25")) }
    var weight by remember { mutableStateOf(loadFromStorage(context, "weight", "70 kg")) }
    var height by remember { mutableStateOf(loadFromStorage(context, "height", "180 cm")) }
    var selectedRace by remember { mutableStateOf(loadFromStorage(context, "race", "Asian")) }

    // Profile image URI
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }

    fun loadProfileImage() {
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
    // Load saved image from permanent storage
    LaunchedEffect(Unit) {
        loadProfileImage()
    }

    var isEditing by remember { mutableStateOf(startInEditMode) }

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

                    loadProfileImage()

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
                        saveToStorage(context, "gender", gender)
                        saveToStorage(context, "age", age)
                        saveToStorage(context, "weight", weight)
                        saveToStorage(context, "height", height)
                        saveToStorage(context, "race", selectedRace)
                        saveToStorage(context, "profile_created", "true")
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

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Gender",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val genders = listOf("Male", "Female")
            genders.forEach { g ->
                GenderChip(
                    gender = g,
                    isSelected = gender == g,
                    isEditing = isEditing,
                    onClick = { if (isEditing) gender = g }
                )
            }
        }

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

@Composable
fun GenderChip(
    gender: String,
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
            text = gender,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}