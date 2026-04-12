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

data class ParsedFitnessPlan(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val phases: List<PlanPhase>,
    val templates: List<WeekdayObjectiveTemplate>,
    /** Full assistant message — sent back to the model when the user asks for adjustments. */
    val rawSourceText: String
) {
    fun phaseIndexForDate(date: LocalDate): Int {
        if (phases.isEmpty()) return 0
        val days = ChronoUnit.DAYS.between(startDate, date).toInt().coerceAtLeast(0)
        val idx = days / 7
        return idx.coerceIn(0, phases.lastIndex)
    }

    fun phaseForDate(date: LocalDate): PlanPhase? =
        phases.getOrNull(phaseIndexForDate(date))

    /** Inclusive first day of the phase week (aligned to [startDate]). */
    fun phaseStartDate(phaseIndex: Int): LocalDate =
        startDate.plusDays(phaseIndex * 7L)

    /** Inclusive last day of the phase week, clamped to [endDate]. */
    fun phaseEndDate(phaseIndex: Int): LocalDate {
        val weekEnd = phaseStartDate(phaseIndex).plusDays(6)
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
        Regex("""about\s+(\d+)\s+weeks?""", RegexOption.IGNORE_CASE)
    )
    private val weeksInParens = Regex("""\(\s*(\d+)\s+weeks?""", RegexOption.IGNORE_CASE)
    private val monthsPattern = Regex(
        """(?:in|about|over|within|for)\s+(\d+)\s*months?""",
        RegexOption.IGNORE_CASE
    )
    private val weekdayToken =
        """(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tues?|Wed|Thu|Thur|Fri|Sat|Sun)"""
    private val dayHeaderCore = Regex(
        "^($weekdayToken(?:\\s*[/,&]\\s*$weekdayToken)*)\\s*[:：]\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val phaseHeaderRegex = Regex(
        """^\s*(?:Week|Phase)\s*(\d+)\s*[:\-–.]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    /** Multiline: any line that looks like `Monday: ...` / `Thu: ...`. */
    private val dayHeaderAnyLine = Regex(
        """(?m)^\s*($weekdayToken(?:\s*[/,&]\s*$weekdayToken)*)\s*[:：]\s*.+""",
        RegexOption.IGNORE_CASE
    )
    private val weekOrPhaseMention = Regex("""\b(?:week|phase)\s*\d+""", RegexOption.IGNORE_CASE)

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

        var endDate = inferEndDate(assistantText, planStart) ?: planStart.plusWeeks(DEFAULT_WEEKS.toLong())
        if (phases.isNotEmpty()) {
            val fromPhases = planStart.plusDays((phases.size * 7L) - 1)
            if (fromPhases.isAfter(endDate)) endDate = fromPhases
        }
        val safeEnd = if (endDate.isBefore(planStart)) planStart.plusWeeks(DEFAULT_WEEKS.toLong()) else endDate

        return ParsedFitnessPlan(
            startDate = planStart,
            endDate = safeEnd,
            phases = phases,
            templates = templates,
            rawSourceText = assistantText
        )
    }

    private const val DEFAULT_WEEKS = 12

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
            val m = phaseHeaderRegex.find(trimmed)
            if (m != null) {
                if (currentTitle != null) flushPhase()
                currentTitle = trimmed
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

        fun flushDay() {
            val days = anchorDays
            val header = pendingHeader
            if (header.isNullOrBlank() || days.isEmpty()) {
                pendingBullets.clear()
                pendingHeader = null
                return
            }
            val vol = extractVolumeFromHeader(header)
            if (pendingBullets.isNotEmpty()) {
                for (rawBullet in pendingBullets) {
                    val sanitized = sanitizePlanLine(rawBullet)
                    if (sanitized.length < 2) continue
                    val text = mergeBulletWithVolume(sanitized, vol)
                    for (d in days) {
                        dayMap.getOrPut(d) { mutableListOf() }.add(WorkoutItem(nextId(d), text))
                    }
                }
            } else {
                val parts = splitObjectiveIntoItems(header)
                for (p in parts) {
                    if (p.length < 2) continue
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

            val dm = dayHeaderCore.find(line)
            if (dm != null) {
                flushDay()
                val grp = parseDayGroup(dm.groupValues[1].trim()) ?: continue
                anchorDays = grp
                val rest = dm.groupValues[2].trim()
                pendingHeader = rest.ifBlank { null }
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
        if (dayHeaderCore.find(t) != null) return false
        if (phaseHeaderRegex.find(t.trim()) != null) return false
        return true
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
