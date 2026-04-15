package com.example.basicnav

//import androidx.compose.material3.Scaffold


import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
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

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GoalsScreen(fitnessPlanViewModel: FitnessPlanViewModel) {
    val plan by fitnessPlanViewModel.plan.collectAsStateWithLifecycle()
    /** Snapshot so the compiler can smart-cast (delegated `plan` cannot). */
    val activePlan = plan
    val completedItems by fitnessPlanViewModel.completedItems.collectAsStateWithLifecycle()

    var selectedWorkoutDateStr by rememberSaveable { mutableStateOf("") }

    // Target state
    var targetType by rememberSaveable { mutableStateOf("Weight Loss") }
    var targetWeight by rememberSaveable { mutableStateOf("") }
    var targetDateText by rememberSaveable { mutableStateOf("") } // format: yyyy-MM-dd
    var isTargetDialogOpen by rememberSaveable { mutableStateOf(false) }

    // Helpers for date/calendar
    val dateFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val today = remember { LocalDate.now() }
    val scroll = rememberScrollState()

    val parsedTargetDate: LocalDate? = remember(targetDateText) {
        if (targetDateText.isBlank()) null
        else {
            try {
                LocalDate.parse(targetDateText, dateFormatter)
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }

    val selectedWorkoutDate: LocalDate = remember(selectedWorkoutDateStr) {
        if (selectedWorkoutDateStr.isBlank()) today
        else {
            try {
                LocalDate.parse(selectedWorkoutDateStr, dateFormatter)
            } catch (_: DateTimeParseException) {
                today
            }
        }
    }

    LaunchedEffect(activePlan?.rawSourceText) {
        val p = activePlan ?: return@LaunchedEffect
        val now = LocalDate.now()
        selectedWorkoutDateStr = now.coerceIn(p.startDate, p.endDate).format(dateFormatter)
    }

    LaunchedEffect(activePlan?.startDate, activePlan?.endDate, selectedWorkoutDateStr) {
        val p = activePlan ?: return@LaunchedEffect
        if (selectedWorkoutDateStr.isBlank()) {
            selectedWorkoutDateStr =
                LocalDate.now().coerceIn(p.startDate, p.endDate).format(dateFormatter)
            return@LaunchedEffect
        }
        val d = runCatching { LocalDate.parse(selectedWorkoutDateStr, dateFormatter) }.getOrNull()
            ?: return@LaunchedEffect
        val c = d.coerceIn(p.startDate, p.endDate)
        if (c != d) selectedWorkoutDateStr = c.format(dateFormatter)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        // Screen title (also appears in top bar, but emphasized here as requested)
        Text(
            text = "Goals",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Target section (acts like a card/column that opens a dialog)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { isTargetDialogOpen = true }
                .padding(16.dp)
        ) {
            Text(
                text = "Target",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Goal: $targetType",
                fontSize = 16.sp
            )

            if (targetWeight.isNotBlank()) {
                Text(
                    text = "Target weight: $targetWeight",
                    fontSize = 16.sp
                )
            }

            if (parsedTargetDate != null) {
                Text(
                    text = "Target date: ${parsedTargetDate.format(dateFormatter)}",
                    fontSize = 16.sp
                )
            } else if (targetDateText.isNotBlank()) {
                // Invalid date hint
                Text(
                    text = "Target date: invalid (use YYYY-MM-DD)",
                    fontSize = 14.sp,
                    color = Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap to adjust your goal",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Target editing dialog
        if (isTargetDialogOpen) {
            AlertDialog(
                onDismissRequest = { isTargetDialogOpen = false },
                confirmButton = {
                    TextButton(onClick = { isTargetDialogOpen = false }) {
                        Text("Done")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isTargetDialogOpen = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Edit Target") },
                text = {
                    Column {
                        // Selection bar for Weight Loss / Muscle Gain
                        Text(
                            text = "Goal type",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GoalTypeChip(
                                text = "Weight Loss",
                                isSelected = targetType == "Weight Loss",
                                onClick = { targetType = "Weight Loss" }
                            )
                            GoalTypeChip(
                                text = "Muscle Gain",
                                isSelected = targetType == "Muscle Gain",
                                onClick = { targetType = "Muscle Gain" }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target weight input
                        Text(
                            text = "Target weight",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = targetWeight,
                            onValueChange = { targetWeight = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. 70 kg") }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target date input
                        Text(
                            text = "Target completion date",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = targetDateText,
                            onValueChange = { targetDateText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("YYYY-MM-DD") }
                        )

                        if (targetDateText.isNotBlank() && parsedTargetDate == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please enter a valid date in format YYYY-MM-DD.",
                                fontSize = 12.sp,
                                color = Color.Red
                            )
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val effectiveWorkoutDate = remember(activePlan, selectedWorkoutDate) {
            if (activePlan == null) selectedWorkoutDate
            else selectedWorkoutDate.coerceIn(activePlan.startDate, activePlan.endDate)
        }

        // Selected day checklist — directly under Target, before the plan summary
        Text(
            text = when {
                activePlan == null -> "Workout checklist"
                effectiveWorkoutDate == LocalDate.now() -> "Today's workout"
                else -> "Workout for ${effectiveWorkoutDate.format(dateFormatter)}"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (activePlan == null) {
            Text(
                text = "Set a goal above, then chat with AI Coach for a personalized plan.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (effectiveWorkoutDate >= activePlan.startDate && effectiveWorkoutDate <= activePlan.endDate) {
            val itemsForDay = activePlan.itemsForDate(effectiveWorkoutDate)
            val doneSet = completedItems[effectiveWorkoutDate].orEmpty()
            if (itemsForDay.isEmpty()) {
                Text(
                    text = "No specific items for this day — ${activePlan.objectiveOn(effectiveWorkoutDate)}",
                    fontSize = 14.sp
                )
            } else {
                val isRestDay = activePlan.isRestDay(effectiveWorkoutDate)
                itemsForDay.forEach { item ->
                    val checked = (item.id in doneSet) || isRestDay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isRestDay) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    fitnessPlanViewModel.toggleItemCompleted(effectiveWorkoutDate, item.id)
                                }
                            )
                        } else {
                            // Hide checkbox for rest days but still show as completed.
                            Text(
                                text = "✓",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp, end = 12.dp)
                            )
                        }
                        Text(
                            text = item.text,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (itemsForDay.isNotEmpty() && (isRestDay || itemsForDay.all { it.id in doneSet })) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nice work — you logged everything for this day.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { fitnessPlanViewModel.clearPlan() }) {
                Text("Clear imported plan", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan card: phase that contains the selected calendar day, with phase date range
        if (activePlan != null) {
            val phaseIdxForSelected = remember(activePlan, effectiveWorkoutDate) {
                activePlan.phaseIndexForDate(effectiveWorkoutDate)
            }
            val planAccent = phaseAccentColor(phaseIdxForSelected, MaterialTheme.colorScheme)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = planAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "Plan from AI Coach",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Overall: ${activePlan.startDate.format(dateFormatter)} → ${activePlan.endDate.format(dateFormatter)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                val phase = activePlan.phaseForDate(effectiveWorkoutDate)
                if (phase != null) {
                    val phaseIdx = activePlan.phaseIndexForDate(effectiveWorkoutDate)
                    val phaseStart = activePlan.phaseStartDate(phaseIdx)
                    val phaseEnd = activePlan.phaseEndDate(phaseIdx)
                    Text(
                        text = "Phase for selected day",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = phase.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "${phaseStart.format(dateFormatter)} → ${phaseEnd.format(dateFormatter)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val dayOrder = listOf(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                    )
                    for (dow in dayOrder) {
                        val items = phase.dayEntries[dow.name].orEmpty()
                        if (items.isEmpty()) continue
                        Text(
                            text = dow.name.lowercase().replaceFirstChar { it.titlecase() },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        items.forEach { w ->
                            Text(
                                text = "• ${w.text}",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else if (activePlan.templates.isNotEmpty()) {
                    Text(
                        text = "Weekly schedule (imported as repeating template)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    activePlan.templates.forEach { t ->
                        Text(
                            text = t.dayGroupLabel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = t.objective,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Text(
                text = "Ask the AI Coach for a workout plan; your weekly objectives and schedule will appear here.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Calendar section
        Text(
            text = "Calendar",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Highlighted days follow your AI plan (or your target date if no plan). Tap a day to update the checklist above and the phase shown in the plan.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ScrollableCalendar(
            today = today,
            manualHighlightFrom = today,
            manualHighlightUntil = parsedTargetDate,
            planStart = activePlan?.startDate,
            planEnd = activePlan?.endDate,
            selectedDate = activePlan?.let { effectiveWorkoutDate },
            phaseIndexForDate = activePlan
                ?.takeIf { it.phases.isNotEmpty() }
                ?.let { p -> { d: LocalDate -> p.phaseIndexForDate(d) } },
            dayAllItemsDone = { d ->
                activePlan?.allItemsCompleted(d, completedItems[d].orEmpty()) == true
            },
            onDayClick = { d -> selectedWorkoutDateStr = d.format(dateFormatter) },
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        )
    }
}

@Composable
private fun GoalTypeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

private fun phaseAccentColor(phaseIndex: Int, scheme: androidx.compose.material3.ColorScheme): Color {
    val palette = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.error,
        scheme.primaryContainer,
        scheme.secondaryContainer,
        scheme.tertiaryContainer,
        scheme.inversePrimary
    )
    return palette[(phaseIndex % palette.size).coerceAtLeast(0)]
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun CalendarMonthView(
    month: YearMonth,
    manualHighlightFrom: LocalDate?,
    manualHighlightUntil: LocalDate?,
    planStart: LocalDate?,
    planEnd: LocalDate?,
    selectedDate: LocalDate?,
    phaseIndexForDate: ((LocalDate) -> Int?)?,
    dayAllItemsDone: (LocalDate) -> Boolean,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()

    // Convert DayOfWeek (MON..SUN) to column index (0..6) with Monday as first
    val firstDayColumnIndex = (firstOfMonth.dayOfWeek.value - 1) // Monday = 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // Month header
        Text(
            text = month.month.name.lowercase()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } +
                    " ${month.year}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Weekday labels
        val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekDays.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        val totalCells = firstDayColumnIndex + daysInMonth
        val rows = (totalCells + 6) / 7
        var dayCounter = 1

        repeat(rows) { rowIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0 until 7) {
                    val cellIndex = rowIndex * 7 + col
                    if (cellIndex < firstDayColumnIndex || dayCounter > daysInMonth) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                        )
                    } else {
                        val currentDate = month.atDay(dayCounter)
                        val inPlanRange = planStart != null && planEnd != null &&
                            !currentDate.isBefore(planStart) && !currentDate.isAfter(planEnd)
                        val inManualRange = manualHighlightFrom != null && manualHighlightUntil != null &&
                            !manualHighlightUntil.isBefore(manualHighlightFrom) &&
                            !currentDate.isBefore(manualHighlightFrom) &&
                            !currentDate.isAfter(manualHighlightUntil)
                        val isHighlighted = when {
                            planStart != null && planEnd != null -> inPlanRange
                            else -> inManualRange
                        }
                        val isDone = dayAllItemsDone(currentDate)
                        val clickable = isHighlighted
                        val isSelected = selectedDate != null && currentDate == selectedDate
                        val phaseIdx = if (inPlanRange) phaseIndexForDate?.invoke(currentDate) else null
                        val accent = phaseIdx?.let { phaseAccentColor(it, MaterialTheme.colorScheme) }
                            ?: MaterialTheme.colorScheme.primary
                        val borderColor = when {
                            isSelected && phaseIdx != null -> accent
                            isSelected -> MaterialTheme.colorScheme.tertiary
                            else -> Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .background(
                                    color = when {
                                        isDone && isHighlighted ->
                                            accent.copy(alpha = 0.42f)
                                        isHighlighted ->
                                            accent.copy(alpha = 0.22f)
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable(
                                    enabled = clickable,
                                    onClick = { onDayClick(currentDate) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayCounter.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHighlighted)
                                    accent
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }

                        dayCounter++
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ScrollableCalendar(
    today: LocalDate,
    manualHighlightFrom: LocalDate?,
    manualHighlightUntil: LocalDate?,
    planStart: LocalDate?,
    planEnd: LocalDate?,
    selectedDate: LocalDate?,
    phaseIndexForDate: ((LocalDate) -> Int?)?,
    dayAllItemsDone: (LocalDate) -> Boolean,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val (startMonth, endMonth) = remember(today, planStart, planEnd) {
        if (planStart == null || planEnd == null) {
            // No plan saved: show only the current month.
            YearMonth.from(today) to YearMonth.from(today)
        } else {
            val minDate = listOf(today, planStart, planEnd).minOrNull() ?: today
            val maxDate = listOf(today, planStart, planEnd).maxOrNull() ?: today
            YearMonth.from(minDate) to YearMonth.from(maxDate)
        }
    }

    val months = remember(startMonth, endMonth) { buildMonthList(startMonth, endMonth) }
    val initialIndex = remember(months, today, selectedDate) {
        val targetMonth = YearMonth.from(selectedDate ?: today)
        months.indexOf(targetMonth).coerceAtLeast(0)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(months) { month ->
            CalendarMonthView(
                month = month,
                manualHighlightFrom = manualHighlightFrom,
                manualHighlightUntil = manualHighlightUntil,
                planStart = planStart,
                planEnd = planEnd,
                selectedDate = selectedDate,
                phaseIndexForDate = phaseIndexForDate,
                dayAllItemsDone = dayAllItemsDone,
                onDayClick = onDayClick
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun buildMonthList(start: YearMonth, end: YearMonth): List<YearMonth> {
    val result = ArrayList<YearMonth>()
    var cursor = start
    while (!cursor.isAfter(end)) {
        result.add(cursor)
        cursor = cursor.plusMonths(1)
    }
    return result
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