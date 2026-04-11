package com.example.basicnav

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parsed from AI free text. Maps repeating weekday labels (e.g. Mon/Wed/Fri) to objectives.
 */
data class WeekdayObjectiveTemplate(
    val dayGroupLabel: String,
    val days: Set<DayOfWeek>,
    val objective: String
)

data class ParsedFitnessPlan(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val templates: List<WeekdayObjectiveTemplate>
) {
    fun objectiveOn(date: LocalDate): String {
        val dow = date.dayOfWeek
        return templates.firstOrNull { dow in it.days }?.objective?.trim().orEmpty()
            .ifEmpty { "Rest or light activity" }
    }
}

/**
 * Best-effort extraction of weekly schedule + duration from GLM-style markdown answers.
 */
object FitnessPlanParser {

    private val isoDateInText = Regex("""(20\d{2}-\d{2}-\d{2})""")
    /** Phrases like "in about 12 weeks" — avoids "pounds per week" (no leading duration phrase). */
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
    /**
     * Matches full or short English weekday names (e.g. `Thursday: HIIT`, `Mon/Wed/Fri:`).
     * Longer names are listed first so `Thursday` is not parsed as `Thu` + junk.
     */
    private val weekdayToken =
        """(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tues?|Wed|Thu|Thur|Fri|Sat|Sun)"""
    private val dayHeaderCore = Regex(
        "^($weekdayToken(?:\\s*[/,&]\\s*$weekdayToken)*)\\s*[:：]\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )

    fun parse(assistantText: String, planStart: LocalDate = LocalDate.now()): ParsedFitnessPlan? {
        val normalized = stripMarkdownNoise(assistantText)
        val templates = extractTemplates(normalized)
        if (templates.isEmpty()) return null

        val endDate = inferEndDate(assistantText, planStart) ?: planStart.plusWeeks(DEFAULT_WEEKS.toLong())
        val safeEnd = if (endDate.isBefore(planStart)) planStart.plusWeeks(DEFAULT_WEEKS.toLong()) else endDate

        return ParsedFitnessPlan(
            startDate = planStart,
            endDate = safeEnd,
            templates = templates
        )
    }

    private const val DEFAULT_WEEKS = 12

    private fun stripMarkdownNoise(text: String): String =
        text.replace(Regex("""\*+"""), "")
            .replace(Regex("""#{1,6}\s*"""), "")

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
        // "in one month", "for a month" (avoid matching unrelated phrases like "once a month")
        if (Regex("""(?:in|for|within|over|during)\s+(?:one|a|the|1)\s+month\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)) {
            return start.plusWeeks(4L)
        }
        // "1 month" / "3 months" (e.g. "3kg in 1 month"); also "1month" without space
        Regex("""\b(\d+)\s*months?\b""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { m ->
            if (m in 1..12) return start.plusWeeks((m * 4L).coerceAtMost(52))
        }
        Regex("""(\d+)months?\b""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { m ->
            if (m in 1..12) return start.plusWeeks((m * 4L).coerceAtMost(52))
        }
        return null
    }
}
