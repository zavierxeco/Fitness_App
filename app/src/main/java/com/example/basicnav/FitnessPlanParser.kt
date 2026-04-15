package com.example.basicnav

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class WorkoutItem(
    val id: String,
    val text: String
)

data class PlanPhase(
    val title: String,
    val weekIndex: Int,
    /** Keys: MONDAY, TUESDAY, … (strings for reliable JSON persistence). */
    val dayEntries: Map<String, List<WorkoutItem>>
)

/**
 * Parsed from AI free text. Supports multi-week phases (Week 1 / Week 2 / …) or legacy repeating templates.
 */
data class WeekdayObjectiveTemplate(
    val dayGroupLabel: String,
    val days: Set<DayOfWeek>,
    val objective: String
)

data class NutritionTargets(
    /** Daily kcal target (max of a range if provided). */
    val caloriesKcal: Int? = null,
    /** If present, protein target in g/kg/day (can be converted to grams using user weight). */
    val proteinGPerKg: Double? = null,
    /** Daily protein target in grams (if explicitly provided). */
    val proteinGrams: Int? = null,
    /** Daily water target in liters. */
    val waterLiters: Double? = null
)

data class ParsedFitnessPlan(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val phases: List<PlanPhase>,
    val templates: List<WeekdayObjectiveTemplate>,
    /** Full assistant message — sent back to the model when the user asks for adjustments. */
    val rawSourceText: String,
    val nutritionTargets: NutritionTargets? = null
) {
    private fun isRestDayText(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isBlank()) return false
        // Treat "Rest" / "Rest day" style headers as rest days.
        return Regex("""^\s*rest(?:\s+day)?\b""").containsMatchIn(t)
    }

    fun isRestDay(date: LocalDate): Boolean {
        val items = itemsForDate(date)
        // Heuristic: rest days are typically a single item such as "Rest" or "Rest Day".
        if (items.size != 1) return false
        return isRestDayText(items.first().text)
    }
    private fun weeksSpanFromTitle(title: String): Int? {
        // Examples we want to catch:
        // "Phase 1: ... (Weeks 1–2)", "Phase 2 ... (Week 3-4)"
        val rx = Regex("""\(\s*Weeks?\s*(\d{1,2})\s*[-–—]\s*(\d{1,2})\s*\)""", RegexOption.IGNORE_CASE)
        val m = rx.find(title) ?: return null
        val a = m.groupValues[1].toIntOrNull() ?: return null
        val b = m.groupValues[2].toIntOrNull() ?: return null
        if (a <= 0 || b <= 0 || b < a) return null
        val span = (b - a) + 1
        return span.takeIf { it in 1..52 }
    }

    private fun phaseDurationWeeks(phaseIndex: Int): Int =
        weeksSpanFromTitle(phases.getOrNull(phaseIndex)?.title.orEmpty()) ?: 1

    fun phaseIndexForDate(date: LocalDate): Int {
        if (phases.isEmpty()) return 0
        val days = ChronoUnit.DAYS.between(startDate, date).toInt().coerceAtLeast(0)
        val weekNumber = (days / 7) + 1 // 1-based
        var cursorWeek = 1
        for (i in phases.indices) {
            val len = phaseDurationWeeks(i)
            val endWeek = cursorWeek + len - 1
            if (weekNumber in cursorWeek..endWeek) return i
            cursorWeek += len
        }
        return phases.lastIndex
    }

    fun phaseForDate(date: LocalDate): PlanPhase? =
        phases.getOrNull(phaseIndexForDate(date))

    /** Inclusive first day of the phase week (aligned to [startDate]). */
    fun phaseStartDate(phaseIndex: Int): LocalDate =
        startDate.plusDays(
            (0 until phaseIndex).sumOf { phaseDurationWeeks(it) }.toLong() * 7L
        )

    /** Inclusive last day of the phase week, clamped to [endDate]. */
    fun phaseEndDate(phaseIndex: Int): LocalDate {
        val weekEnd = phaseStartDate(phaseIndex).plusDays((phaseDurationWeeks(phaseIndex) * 7L) - 1L)
        return if (weekEnd.isAfter(endDate)) endDate else weekEnd
    }

    fun itemsForDate(date: LocalDate): List<WorkoutItem> {
        if (phases.isNotEmpty()) {
            val phase = phaseForDate(date) ?: return emptyList()
            return phase.dayEntries[date.dayOfWeek.name].orEmpty()
        }
        val dow = date.dayOfWeek
        val obj = templates.firstOrNull { dow in it.days }?.objective?.trim().orEmpty()
        return if (obj.isNotBlank()) {
            listOf(WorkoutItem(id = "legacy_${dow.name}", text = obj))
        } else {
            emptyList()
        }
    }

    fun objectiveOn(date: LocalDate): String =
        itemsForDate(date).joinToString("\n") { it.text }.ifEmpty { "Rest or light activity" }

    fun allItemsCompleted(date: LocalDate, completedIds: Set<String>): Boolean {
        val items = itemsForDate(date)
        if (items.isEmpty()) return false
        // Rest days are treated as implicitly completed.
        if (isRestDay(date)) return true
        return items.all { it.id in completedIds }
    }
}

/**
 * Best-effort extraction of multi-phase weekly schedules + duration from GLM-style markdown answers.
 */
object FitnessPlanParser {

    private val isoDateInText = Regex("""(20\d{2}-\d{2}-\d{2})""")
    private val durationWeekCandidates = listOf(
        Regex("""in\s+about\s+(\d+)\s+weeks?""", RegexOption.IGNORE_CASE),
        Regex("""(?:reach|goal)\s+[^.\n]{0,40}?\s+in\s+about\s+(\d+)\s+weeks?""", RegexOption.IGNORE_CASE),
        Regex("""(?:in|within|for|over)\s+(\d+)\s+weeks?""", RegexOption.IGNORE_CASE),
        Regex("""about\s+(\d+)\s+weeks?""", RegexOption.IGNORE_CASE),
        // Matches titles like "The Plan: 4-Week Intensive Transformation"
        Regex("""(?:the\s+plan\s*[:\-–]?\s*)?(\d{1,2})\s*-\s*weeks?\b""", RegexOption.IGNORE_CASE)
    )
    private val weeksInParens = Regex("""\(\s*(\d+)\s+weeks?""", RegexOption.IGNORE_CASE)
    private val monthsPattern = Regex(
        """(?:in|about|over|within|for)\s+(\d+)\s*months?""",
        RegexOption.IGNORE_CASE
    )
    private val weekdayToken =
        """(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tues?|Wed|Thu|Thur|Fri|Sat|Sun)"""
    private val dayHeaderCore = Regex(
        "^($weekdayToken(?:\\s*[/,&]\\s*$weekdayToken)*)\\s*[:：]\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val phaseHeaderRegex = Regex(
        """^\s*(?:Week|Phase)\s*(\d+)\s*[:\-–.]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    /** Multiline: any line that looks like `Monday: ...` / `Thu: ...`. */
    private val dayHeaderAnyLine = Regex(
        """(?m)^\s*($weekdayToken(?:\s*[/,&]\s*$weekdayToken)*)\s*[:：].*""",
        RegexOption.IGNORE_CASE
    )
    private val weekOrPhaseMention = Regex("""\b(?:week|phase)\s*\d+""", RegexOption.IGNORE_CASE)
    private val dividerLineRegex = Regex("""^\s*(?:-{2,}|_{2,}|\*{3,})\s*$""")
    private val caloriesTargetRegex = Regex(
        """(?i)\b(?:calorie\s*target|calories)\b[^0-9\n]{0,40}(\d{1,3}(?:,\d{3})*)(?:\s*[–-]\s*(\d{1,3}(?:,\d{3})*))?\s*(?:kcal|cal(?:ories)?|calories)\b"""
    )
    private val proteinPerKgRegex = Regex("""(?i)\b(\d+(?:\.\d+)?)\s*g\s*(?:/|\s*per\s*)\s*kg\b""")
    private val proteinTargetGramsRegex = Regex("""(?i)\bprotein\b[^0-9\n]{0,40}(\d{2,4})\s*g\b""")
    private val waterTargetRegex = Regex("""(?i)\b(?:hydration|water)\b[^0-9\n]{0,40}(\d+(?:\.\d+)?)\s*(?:l|liters?|litres?)\b""")

    /**
     * Use for showing the "save to Goals?" prompt. Broader than [parse] so we still ask when the
     * model uses Week/Phase blocks or day lines that our importer doesn't fully structure yet.
     */
    fun looksLikeFitnessPlan(assistantText: String): Boolean {
        if (assistantText.isBlank()) return false
        val head = assistantText.trimStart()
        if (head.startsWith("Error:") || head.startsWith("Network error", ignoreCase = true)) return false
        if (parse(assistantText) != null) return true
        if (weekOrPhaseMention.containsMatchIn(assistantText)) return true
        if (dayHeaderAnyLine.containsMatchIn(assistantText)) return true
        val low = assistantText.lowercase()
        if (low.contains("workout schedule") &&
            (low.contains("monday") || low.contains("tuesday") || low.contains("wednesday"))
        ) {
            return true
        }
        return false
    }

    fun parse(assistantText: String, planStart: LocalDate = LocalDate.now()): ParsedFitnessPlan? {
        val normalized = stripMarkdownNoise(assistantText)
        val phaseBodies = splitPhaseBodies(normalized)
        var phases = if (phaseBodies.isNotEmpty()) {
            phaseBodies.mapIndexed { index, (title, body) ->
                parsePhase(title, index, body)
            }.filter { it.dayEntries.isNotEmpty() }
        } else {
            emptyList()
        }
        if (phaseBodies.isNotEmpty() && phases.all { it.dayEntries.isEmpty() }) {
            phases = emptyList()
        }

        val templates = if (phases.isEmpty()) extractTemplates(normalized) else emptyList()
        if (phases.isEmpty() && templates.isEmpty()) return null

        val nutritionTargets = parseNutritionTargets(normalized)

        val inferredEnd = inferEndDate(assistantText, planStart)
        var endDate = inferredEnd ?: planStart.plusWeeks(DEFAULT_WEEKS.toLong())

        if (phases.isNotEmpty()) {
            // Prefer the total duration described by the phase headers (supports "(Weeks 1-2)" etc.).
            val totalWeeksFromTitles = phases.sumOf { phase ->
                Regex("""\(\s*Weeks?\s*(\d{1,2})\s*[-–—]\s*(\d{1,2})\s*\)""", RegexOption.IGNORE_CASE)
                    .find(phase.title)
                    ?.let { m ->
                        val a = m.groupValues[1].toIntOrNull()
                        val b = m.groupValues[2].toIntOrNull()
                        if (a != null && b != null && a > 0 && b >= a) (b - a + 1) else null
                    }
                    ?: 1
            }.coerceAtLeast(1)

            val fromPhases = planStart.plusDays((totalWeeksFromTitles * 7L) - 1)
            endDate = when {
                inferredEnd == null -> fromPhases
                fromPhases.isAfter(endDate) -> fromPhases
                else -> endDate
            }
        }
        val safeEnd = if (endDate.isBefore(planStart)) planStart.plusWeeks(DEFAULT_WEEKS.toLong()) else endDate

        return ParsedFitnessPlan(
            startDate = planStart,
            endDate = safeEnd,
            phases = phases,
            templates = templates,
            rawSourceText = assistantText,
            nutritionTargets = nutritionTargets
        )
    }

    private const val DEFAULT_WEEKS = 12

    private fun parseNutritionTargets(normalized: String): NutritionTargets? {
        // Only scan the first part of the response to avoid picking up numbers from workouts.
        val head = normalized.lines().take(120).joinToString("\n")

        val calories = caloriesTargetRegex.find(head)?.let { m ->
            val a = m.groupValues[1].replace(",", "").toIntOrNull()
            val b = m.groupValues.getOrNull(2)?.replace(",", "")?.toIntOrNull()
            listOfNotNull(a, b).maxOrNull()
        }

        val proteinPerKg = proteinPerKgRegex.findAll(head).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .maxOrNull()

        val proteinGrams = proteinTargetGramsRegex.findAll(head).mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()

        val water = waterTargetRegex.findAll(head).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .maxOrNull()

        val out = NutritionTargets(
            caloriesKcal = calories,
            proteinGPerKg = proteinPerKg,
            proteinGrams = proteinGrams,
            waterLiters = water
        )
        return if (out.caloriesKcal != null || out.proteinGPerKg != null || out.proteinGrams != null || out.waterLiters != null) out else null
    }

    private fun stripMarkdownNoise(text: String): String {
        var s = text.replace(Regex("""#{1,6}\s*"""), "")
        // Keep list structure: line-start "* item" must not become plain text (or continuations won't match).
        s = s.replace(Regex("""(?m)^(\s*)\*\s+"""), "$1- ")
        s = s.replace("**", "")
        s = s.replace(Regex("""\*+"""), "")
        return s
    }

    /**
     * Returns list of (phase title line, body text). Preamble before first Week/Phase is merged into week 1.
     */
    private fun splitPhaseBodies(normalized: String): List<Pair<String, String>> {
        val lines = normalized.lines()
        val chunks = mutableListOf<Pair<String, StringBuilder>>()
        var preamble = StringBuilder()
        var currentTitle: String? = null
        var currentBody = StringBuilder()

        fun flushPhase() {
            val title = currentTitle ?: return
            val body = StringBuilder().apply {
                if (chunks.isEmpty() && preamble.isNotBlank()) {
                    append(preamble.toString())
                    if (!preamble.endsWith("\n")) append("\n")
                }
                append(currentBody.toString())
            }
            chunks.add(title to body)
            currentBody = StringBuilder()
        }

        for (line in lines) {
            val trimmed = line.trim()
            val trimmedForMatch = sanitizePlanLine(trimmed)
            val m = phaseHeaderRegex.find(trimmedForMatch)
            if (m != null) {
                if (currentTitle != null) flushPhase()
                currentTitle = trimmedForMatch
            } else {
                if (currentTitle == null) {
                    preamble.appendLine(line)
                } else {
                    currentBody.appendLine(line)
                }
            }
        }
        if (currentTitle != null) flushPhase()

        return chunks.map { (t, sb) -> t to sb.toString().trim() }
    }

    private fun extractVolumeFromHeader(header: String): String? {
        Regex("""(\d+)\s*[x×]\s*(\d+)""").find(header)?.let {
            return "${it.groupValues[1]} × ${it.groupValues[2]}"
        }
        Regex("""(\d+)\s*(?:min|mins|minutes)\b""", RegexOption.IGNORE_CASE).find(header)?.let {
            return "${it.groupValues[1]} min"
        }
        return null
    }

    private fun mergeBulletWithVolume(bullet: String, volume: String?): String {
        val b = bullet.trim()
        if (b.length < 2) return b
        if (Regex("""\d+\s*[x×]\s*\d+""").containsMatchIn(b)) return b
        if (Regex("""\d+\s*(?:min|mins|minutes|sec|secs|seconds)\b""", RegexOption.IGNORE_CASE).containsMatchIn(b)) {
            return b
        }
        if (Regex("""\d+\s*s\b""", RegexOption.IGNORE_CASE).containsMatchIn(b) &&
            Regex("""hold|plank""", RegexOption.IGNORE_CASE).containsMatchIn(b)
        ) {
            return b
        }
        if (Regex("""hold\s+for""", RegexOption.IGNORE_CASE).containsMatchIn(b)) return b
        return if (!volume.isNullOrBlank()) "$b — $volume" else b
    }

    private fun parsePhase(title: String, phaseIndex: Int, body: String): PlanPhase {
        val dayMap = mutableMapOf<DayOfWeek, MutableList<WorkoutItem>>()
        var slot = 0
        fun nextId(dow: DayOfWeek) = "p${phaseIndex}_${dow.name}_${slot++}"

        var anchorDays: Set<DayOfWeek> = emptySet()
        var pendingHeader: String? = null
        val pendingBullets = mutableListOf<String>()

        fun isDividerLine(raw: String): Boolean = dividerLineRegex.matches(raw.trim())

        fun flushDay() {
            val days = anchorDays
            val header = pendingHeader
            if (days.isEmpty() || (header.isNullOrBlank() && pendingBullets.isEmpty())) {
                pendingBullets.clear()
                pendingHeader = null
                return
            }
            val vol = extractVolumeFromHeader(header.orEmpty())
            if (pendingBullets.isNotEmpty()) {
                for (rawBullet in pendingBullets) {
                    val sanitized = sanitizePlanLine(rawBullet)
                    if (sanitized.length < 2) continue
                    if (isDividerLine(rawBullet) || isDividerLine(sanitized)) continue
                    if (looksLikeFooterText(sanitized)) continue
                    val text = mergeBulletWithVolume(sanitized, vol)
                    for (d in days) {
                        dayMap.getOrPut(d) { mutableListOf() }.add(WorkoutItem(nextId(d), text))
                    }
                }
            } else {
                val parts = splitObjectiveIntoItems(header.orEmpty())
                for (p in parts) {
                    if (p.length < 2) continue
                    if (isDividerLine(p) || looksLikeFooterText(p)) continue
                    for (d in days) {
                        dayMap.getOrPut(d) { mutableListOf() }.add(WorkoutItem(nextId(d), p))
                    }
                }
            }
            pendingBullets.clear()
            pendingHeader = null
        }

        for (raw in body.lines()) {
            val line = sanitizePlanLine(raw)
            if (line.isBlank()) continue
            if (isDividerLine(raw) || isDividerLine(line)) {
                // Divider separates the week's schedule from any trailing prose.
                flushDay()
                anchorDays = emptySet()
                pendingHeader = null
                pendingBullets.clear()
                continue
            }
            if (looksLikeFooterText(line)) {
                // Do not treat closing encouragement as a workout item.
                continue
            }

            val dm = dayHeaderCore.find(line)
            if (dm != null) {
                flushDay()
                val grp = parseDayGroup(dm.groupValues[1].trim()) ?: continue
                anchorDays = grp
                val rest = dm.groupValues[2].trim()
                // Allow bare `Monday:` (no rest) — sub-lines will carry the real content.
                pendingHeader = rest
                continue
            }

            if (anchorDays.isNotEmpty() && pendingHeader != null && isExerciseSubLine(raw, line)) {
                pendingBullets.add(raw)
            }
        }
        flushDay()

        val asStrings = dayMap.mapKeys { it.key.name }.mapValues { it.value.toList() }
        return PlanPhase(title = title, weekIndex = phaseIndex, dayEntries = asStrings)
    }

    private fun splitObjectiveIntoItems(text: String): List<String> {
        val byBullet = text.split(Regex("""\s*[•·]\s*""")).map { it.trim() }.filter { it.length > 2 }
        if (byBullet.size >= 2) return byBullet
        return listOf(text.trim())
    }

    private fun isContinuationLine(raw: String): Boolean {
        val t = raw.trim()
        if (t.startsWith("•") || t.startsWith("·") || t.startsWith("-") || t.startsWith("*")) return true
        return Regex("""^\d+\.""").containsMatchIn(t)
    }

    /**
     * True for markdown list lines and plain lines that belong under the current [pendingHeader] day
     * (e.g. exercise names after stripping `*` list markers).
     */
    private fun isExerciseSubLine(raw: String, sanitizedLine: String): Boolean {
        if (isContinuationLine(raw)) return true
        val t = sanitizedLine.trim()
        if (t.length < 3) return false
        if (dividerLineRegex.matches(t)) return false
        if (looksLikeFooterText(t)) return false
        if (dayHeaderCore.find(t) != null) return false
        if (phaseHeaderRegex.find(t.trim()) != null) return false
        return true
    }

    private fun looksLikeFooterText(sanitizedLine: String): Boolean {
        val t = sanitizedLine.trim()
        if (t.length < 10) return false
        val low = t.lowercase()
        val footerPhrases = listOf(
            "good luck",
            "stick to",
            "you will see",
            "you'll see",
            "let me know",
            "feel free",
            "reach out",
            "if you need help",
            "if you have questions"
        )
        if (footerPhrases.any { low.contains(it) }) return true
        // A pure prose line with no obvious workout signal (numbers, time units, sets×reps).
        val hasWorkoutSignal = Regex("""\b(\d+)\b""").containsMatchIn(low) ||
            Regex("""\b(min|mins|minute|minutes|sec|secs|second|seconds|km|mile|miles)\b""")
                .containsMatchIn(low) ||
            Regex("""\d+\s*[x×]\s*\d+""").containsMatchIn(low)
        return !hasWorkoutSignal && (low.endsWith(".") || low.endsWith("!"))
    }

    private fun extractTemplates(normalizedFlat: String): List<WeekdayObjectiveTemplate> {
        val lines = normalizedFlat.lines().map { sanitizePlanLine(it) }.filter { it.isNotBlank() }
        val out = mutableListOf<WeekdayObjectiveTemplate>()
        val seen = mutableSetOf<Set<DayOfWeek>>()
        for (line in lines) {
            val m = dayHeaderCore.find(line) ?: continue
            val group = m.groupValues[1].trim()
            val objective = m.groupValues[2].trim()
            if (objective.length < 4) continue
            val days = parseDayGroup(group) ?: continue
            if (days.isEmpty() || days in seen) continue
            seen.add(days)
            out += WeekdayObjectiveTemplate(
                dayGroupLabel = group,
                days = days,
                objective = objective
            )
        }
        return out
    }

    private fun sanitizePlanLine(raw: String): String {
        var s = raw.trim()
        s = s.removePrefix("-").trim()
        s = s.removePrefix("•").trim()
        s = s.removePrefix("·").trim()
        s = s.removePrefix("*").trim()
        s = Regex("""^\d+\.\s*""").replace(s, "")
        return s.trim()
    }

    private fun parseDayGroup(group: String): Set<DayOfWeek>? {
        val parts = group.split(Regex("""\s*[/,&]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableSetOf<DayOfWeek>()
        for (p in parts) {
            result.add(mapDayToken(p) ?: return null)
        }
        return result
    }

    private fun mapDayToken(token: String): DayOfWeek? {
        val t = token.lowercase().trim()
        return when {
            t.startsWith("mon") -> DayOfWeek.MONDAY
            t.startsWith("tue") || t.startsWith("tues") -> DayOfWeek.TUESDAY
            t.startsWith("wed") -> DayOfWeek.WEDNESDAY
            t.startsWith("thu") || t.startsWith("thur") -> DayOfWeek.THURSDAY
            t.startsWith("fri") -> DayOfWeek.FRIDAY
            t.startsWith("sat") -> DayOfWeek.SATURDAY
            t.startsWith("sun") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun inferEndDate(raw: String, start: LocalDate): LocalDate? {
        isoDateInText.find(raw)?.groupValues?.get(1)?.let { s ->
            try {
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: DateTimeParseException) {
            }
        }
        for (rx in durationWeekCandidates) {
            val w = rx.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (w in 1..104) return start.plusWeeks(w.toLong())
        }
        weeksInParens.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { w ->
            if (w in 1..104) return start.plusWeeks(w.toLong())
        }
        monthsPattern.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { m ->
            if (m in 1..24) return start.plusWeeks((m * 4L).coerceAtMost(104))
        }
        if (Regex("""(?:in|for|within|over|during)\s+(?:one|a|the|1)\s+month\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) {
            return start.plusWeeks(4L)
        }
        Regex("""\b(\d+)\s*months?\b""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { m ->
            if (m in 1..12) return start.plusWeeks((m * 4L).coerceAtMost(52))
        }
        Regex("""(\d+)months?\b""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { m ->
            if (m in 1..12) return start.plusWeeks((m * 4L).coerceAtMost(52))
        }
        return null
    }
}
