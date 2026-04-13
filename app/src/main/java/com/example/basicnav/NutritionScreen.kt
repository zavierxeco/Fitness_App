package com.example.basicnav

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlin.math.roundToInt

private fun parseWeightKgOrNull(weightText: String): Double? {
    val num = Regex("""(\d+(?:\.\d+)?)""").find(weightText)?.groupValues?.get(1)?.toDoubleOrNull()
    if (num == null) return null
    return when {
        weightText.lowercase().contains("lb") -> num * 0.45359237
        else -> num
    }
}

private fun formatLiters(v: Double): String =
    if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format("%.2f", v)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NutritionScreen(
    nutritionViewModel: NutritionViewModel,
    fitnessPlanViewModel: FitnessPlanViewModel
) {
    val plan by fitnessPlanViewModel.plan.collectAsStateWithLifecycle()
    val state by nutritionViewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        nutritionViewModel.ensureToday()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val weightText = remember { loadFromStorage(context, "weight", "70 kg") }
    val weightKg = parseWeightKgOrNull(weightText) ?: 70.0

    val targets = plan?.nutritionTargets
    val calorieTarget = targets?.caloriesKcal
    val proteinTarget = targets?.proteinGrams ?: targets?.proteinGPerKg?.let { (it * weightKg).roundToInt() }
    val waterTarget = targets?.waterLiters

    val totalCalories = state.foods.sumOf { it.caloriesKcal }
    val totalProtein = state.foods.sumOf { it.proteinGrams }
    val totalWater = state.waters.sumOf { it.liters }

    var showAddFood by rememberSaveable { mutableStateOf(false) }
    var showAddWater by rememberSaveable { mutableStateOf(false) }
    var editingFoodId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingWaterId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("Nutrition", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Progress rings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressRing(
                title = "Calories",
                valueText = "${totalCalories} kcal",
                progress = calorieTarget?.let { (totalCalories.toFloat() / it.toFloat()).coerceIn(0f, 1f) } ?: 0f,
                targetText = calorieTarget?.let { "Target: $it kcal" } ?: "No target yet",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            ProgressRing(
                title = "Protein",
                valueText = "${totalProtein} g",
                progress = proteinTarget?.let { (totalProtein.toFloat() / it.toFloat()).coerceIn(0f, 1f) } ?: 0f,
                targetText = proteinTarget?.let { "Target: $it g" } ?: "No target yet",
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            ProgressRing(
                title = "Water",
                valueText = "${formatLiters(totalWater)} L",
                progress = waterTarget?.let { (totalWater.toFloat() / it.toFloat()).coerceIn(0f, 1f) } ?: 0f,
                targetText = waterTarget?.let { "Target: ${formatLiters(it)} L" } ?: "No target yet",
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(18.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Today’s log", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Resets daily. (${LocalDate.now()})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showAddFood = true }, modifier = Modifier.weight(1f)) {
                        Text("Add food")
                    }
                    OutlinedButton(onClick = { showAddWater = true }, modifier = Modifier.weight(1f)) {
                        Text("Add water")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Food / dishes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (state.foods.isEmpty()) {
            Text("No food logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.foods.forEach { f ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { editingFoodId = f.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${f.caloriesKcal} kcal • ${f.proteinGrams} g protein",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Edit", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Water", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (state.waters.isEmpty()) {
            Text("No water logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.waters.forEach { w ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { editingWaterId = w.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${formatLiters(w.liters)} L", fontWeight = FontWeight.SemiBold)
                        Text("Edit", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Add food dialog
    if (showAddFood) {
        FoodDialog(
            title = "Add food / dish",
            initialName = "",
            initialCalories = "",
            initialProtein = "",
            onDismiss = { showAddFood = false },
            onSave = { name, cal, prot ->
                nutritionViewModel.addFood(name, cal, prot)
                showAddFood = false
            }
        )
    }

    // Add water dialog
    if (showAddWater) {
        WaterDialog(
            title = "Add water",
            initialLiters = "",
            onDismiss = { showAddWater = false },
            onSave = { liters ->
                nutritionViewModel.addWater(liters)
                showAddWater = false
            }
        )
    }

    // Edit food
    editingFoodId?.let { id ->
        val f = state.foods.firstOrNull { it.id == id }
        if (f == null) {
            editingFoodId = null
        } else {
            FoodDialog(
                title = "Edit food / dish",
                initialName = f.name,
                initialCalories = f.caloriesKcal.toString(),
                initialProtein = f.proteinGrams.toString(),
                onDismiss = { editingFoodId = null },
                onDelete = {
                    nutritionViewModel.deleteFood(id)
                    editingFoodId = null
                },
                onSave = { name, cal, prot ->
                    nutritionViewModel.updateFood(id, name, cal, prot)
                    editingFoodId = null
                }
            )
        }
    }

    // Edit water
    editingWaterId?.let { id ->
        val w = state.waters.firstOrNull { it.id == id }
        if (w == null) {
            editingWaterId = null
        } else {
            WaterDialog(
                title = "Edit water",
                initialLiters = formatLiters(w.liters),
                onDismiss = { editingWaterId = null },
                onDelete = {
                    nutritionViewModel.deleteWater(id)
                    editingWaterId = null
                },
                onSave = { liters ->
                    nutritionViewModel.updateWater(id, liters)
                    editingWaterId = null
                }
            )
        }
    }
}

@Composable
private fun ProgressRing(
    title: String,
    valueText: String,
    progress: Float,
    targetText: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = valueText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(targetText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FoodDialog(
    title: String,
    initialName: String,
    initialCalories: String,
    initialProtein: String,
    onDismiss: () -> Unit,
    onSave: (name: String, caloriesKcal: Int, proteinGrams: Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var calories by rememberSaveable { mutableStateOf(initialCalories) }
    var protein by rememberSaveable { mutableStateOf(initialProtein) }

    val caloriesInt = calories.trim().toIntOrNull()
    val proteinInt = protein.trim().toIntOrNull()
    val canSave = name.trim().isNotBlank() && (caloriesInt != null) && (proteinInt != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("Protein (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (!canSave) {
                    Text(
                        "Please enter name + numbers for calories and protein.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), caloriesInt ?: 0, proteinInt ?: 0) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun WaterDialog(
    title: String,
    initialLiters: String,
    onDismiss: () -> Unit,
    onSave: (liters: Double) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var litersText by rememberSaveable { mutableStateOf(initialLiters) }
    val liters = litersText.trim().toDoubleOrNull()
    val canSave = liters != null && liters > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("Water (liters)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (!canSave) {
                    Text(
                        "Please enter a number greater than 0.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(liters ?: 0.0) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

