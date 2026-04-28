package com.example.basicnav

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class GoalType {
    WEIGHT_LOSS,
    WEIGHT_GAIN,
    MUSCLE_GAIN,
    OTHERS
}

data class GoalTarget(
    val goalType: GoalType = GoalType.WEIGHT_LOSS,
    /** Target body weight in kg, used for Weight Loss/Gain. */
    val targetWeightKg: Double? = null,
    /** If present, the requested change in weight (e.g. "lose 8 kg" => 8.0). */
    val weightDeltaKg: Double? = null,
    /** Free-text muscles focus for Muscle Gain (e.g., "Biceps, Triceps, Forearms"). */
    val muscleTargetText: String? = null,
    /** Free-text target for Others. */
    val otherTargetText: String? = null
)

class GoalTargetRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): GoalTarget? {
        val json = prefs.getString(KEY_TARGET, null) ?: return null
        return runCatching { gson.fromJson(json, GoalTarget::class.java) }.getOrNull()
    }

    fun save(target: GoalTarget?) {
        prefs.edit().apply {
            if (target == null) remove(KEY_TARGET) else putString(KEY_TARGET, gson.toJson(target))
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "goal_target_storage"
        private const val KEY_TARGET = "goal_target_json"
    }
}

object GoalTargetExtractor {
    private val targetHeaderRegex = Regex("""(?im)^\s*##\s*Target\s*$""")
    private val caloriesHeaderRegex = Regex("""(?im)^\s*##\s*Nutrition\s+Targets\s*$""")

    private val weightLossRegex = Regex("""(?i)\bweight\s*loss\b\s*[:：\-]?\s*([0-9]+(?:\.[0-9]+)?)\s*kg\b""")
    private val weightGainRegex = Regex("""(?i)\bweight\s*gain\b\s*[:：\-]?\s*([0-9]+(?:\.[0-9]+)?)\s*kg\b""")
    private val muscleGainRegex = Regex("""(?i)\bmuscle\s*gain\b""")
    private val muscleGainLineRegex = Regex("""(?im)^\s*[-•]?\s*muscle\s*gain\s*[:：]\s*(.+?)\s*$""")
    private val fatLossRegex = Regex("""(?i)\bfat\s*loss\b\s*[:：\-]?\s*([0-9]+(?:\.[0-9]+)?)\s*kg\b""")

    private val bulletLineRegex = Regex("""(?m)^\s*[-•]\s*(.+)$""")

    private val allowedMuscleGroups = listOf("Chest", "Shoulders", "Arms", "Back", "Abdominals", "Legs and Glutes")

    fun extract(rawAssistantText: String, profileWeightKg: Double?): GoalTarget? {
        val text = rawAssistantText
        val targetBlock = extractTargetBlock(text) ?: return null

        // Weight Loss / Gain: expected to be "Weight Loss: X kg" or "Weight Gain: X kg"
        fatLossRegex.find(targetBlock)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { deltaKg ->
            val targetKg = profileWeightKg?.let { (it - deltaKg).coerceAtLeast(0.0) }
            return GoalTarget(goalType = GoalType.WEIGHT_LOSS, targetWeightKg = targetKg, weightDeltaKg = deltaKg)
        }
        weightLossRegex.find(targetBlock)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { deltaKg ->
            val targetKg = profileWeightKg?.let { (it - deltaKg).coerceAtLeast(0.0) }
            return GoalTarget(goalType = GoalType.WEIGHT_LOSS, targetWeightKg = targetKg, weightDeltaKg = deltaKg)
        }
        weightGainRegex.find(targetBlock)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { deltaKg ->
            val targetKg = profileWeightKg?.let { it + deltaKg }
            return GoalTarget(goalType = GoalType.WEIGHT_GAIN, targetWeightKg = targetKg, weightDeltaKg = deltaKg)
        }

        // Muscle gain: prefer the explicit "Muscle Gain: ..." text if present; otherwise fall back to defaults.
        muscleGainLineRegex.find(targetBlock)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { focus ->
            return GoalTarget(goalType = GoalType.MUSCLE_GAIN, muscleTargetText = focus)
        }
        if (muscleGainRegex.containsMatchIn(targetBlock)) {
            // If user did not specify a focus, default to all supported groups as a readable string.
            return GoalTarget(goalType = GoalType.MUSCLE_GAIN, muscleTargetText = allowedMuscleGroups.joinToString())
        }

        // Others: store the first bullet if present, otherwise entire block trimmed.
        val firstBullet = bulletLineRegex.find(targetBlock)?.groupValues?.getOrNull(1)?.trim()
        val other = (firstBullet ?: targetBlock.trim())
            .removePrefix("Others:")
            .removePrefix("Other:")
            .trim()
            .takeIf { it.isNotBlank() }
        return GoalTarget(goalType = GoalType.OTHERS, otherTargetText = other)
    }

    private fun extractTargetBlock(text: String): String? {
        val lines = text.lines()
        val idx = lines.indexOfFirst { targetHeaderRegex.containsMatchIn(it) }
        if (idx == -1) return null
        val out = StringBuilder()
        for (i in idx + 1 until lines.size) {
            val line = lines[i]
            // Stop at next section heading.
            if (Regex("""^\s*##\s+""").containsMatchIn(line)) break
            out.appendLine(line)
        }
        return out.toString().trim().takeIf { it.isNotBlank() }
    }

    fun parseProfileWeightKgOrNull(weightText: String?): Double? {
        val s = weightText?.trim().orEmpty()
        if (s.isBlank()) return null
        val num = Regex("""(\d+(?:\.\d+)?)""").find(s)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        return if (s.lowercase().contains("lb")) num * 0.45359237 else num
    }
}

