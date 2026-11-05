package com.dub.calorietracker.data

import kotlinx.serialization.Serializable

@Serializable
enum class Sex { MALE, FEMALE }

@Serializable
enum class Units { US, METRIC }

/** Activity levels and multipliers (same scale used by common online calorie calculators). */
@Serializable
enum class ActivityLevel(val label: String, val description: String, val multiplier: Double) {
    BMR("Basal Metabolic Rate (BMR)", "Calories burned at complete rest", 1.0),
    SEDENTARY("Sedentary", "Little or no exercise", 1.2),
    LIGHT("Light", "Exercise 1-3 times/week", 1.375),
    MODERATE("Moderate", "Exercise 4-5 times/week", 1.465),
    ACTIVE("Active", "Daily exercise or intense exercise 3-4 times/week", 1.55),
    VERY_ACTIVE("Very Active", "Intense exercise 6-7 times/week", 1.725),
    EXTRA_ACTIVE("Extra Active", "Very intense exercise daily, or physical job", 1.9),
}

/** Weight goals. [deficit] is calories per day below maintenance. */
@Serializable
enum class Goal(val label: String, val rate: String, val deficit: Int) {
    MAINTAIN("Maintain weight", "", 0),
    MILD_LOSS("Mild weight loss", "0.5 lb/week", 250),
    LOSS("Weight loss", "1 lb/week", 500),
    EXTREME_LOSS("Extreme weight loss", "2 lb/week", 1000),
}

@Serializable
enum class ScheduleType(val label: String) {
    STANDARD("Standard"),
    ZIGZAG_1("Zigzag schedule 1"),
    ZIGZAG_2("Zigzag schedule 2"),
}

@Serializable
data class Profile(
    val age: Int,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    val units: Units = Units.US,
)

@Serializable
data class Plan(
    val profile: Profile,
    val maintenanceCalories: Int,
    val goal: Goal,
    val dailyTarget: Int,
    val schedule: ScheduleType,
    /** Calories for Sunday..Saturday (index 0 = Sunday). */
    val weekTemplate: List<Int>,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class FoodItem(
    val name: String,
    val quantity: String = "",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)

@Serializable
enum class EntrySource { PHOTO, TEXT, SAVED, MANUAL, BARCODE, PASTE }

@Serializable
data class FoodEntry(
    val id: String,
    /** ISO date, e.g. 2026-11-03 */
    val date: String,
    val time: Long,
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servings: Double = 1.0,
    val items: List<FoodItem> = emptyList(),
    val source: EntrySource = EntrySource.MANUAL,
)

/** A food/meal the app remembers so it can be logged again without asking the AI. */
@Serializable
data class SavedFood(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val items: List<FoodItem> = emptyList(),
    val timesUsed: Int = 1,
    val lastUsed: Long = System.currentTimeMillis(),
)

@Serializable
enum class AiProvider(val label: String, val defaultModel: String, val keyHint: String) {
    GEMINI("Google Gemini (free tier)", "gemini-flash-latest", "Get a free key at aistudio.google.com → Get API key"),
    CLAUDE("Anthropic Claude", "claude-opus-5-5", "Get a key at platform.claude.com (pay-as-you-go)"),
    OPENAI("OpenAI (ChatGPT API)", "gpt-5-mini", "Get a key at platform.openai.com (pay-as-you-go, separate from ChatGPT Plus)"),
}

@Serializable
data class AiSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKeys: Map<AiProvider, String> = emptyMap(),
    val models: Map<AiProvider, String> = emptyMap(),
    /** Epoch millis until which AI requests are paused because the provider rate-limited us. */
    val cooldownUntil: Long = 0,
    val cooldownMessage: String = "",
    /** Gemini only: let it look up branded packaged foods in Open Food Facts (free). */
    val webSearch: Boolean = true,
) {
    fun key(p: AiProvider = provider) = apiKeys[p].orEmpty()
    fun model(p: AiProvider = provider) = models[p]?.takeIf { it.isNotBlank() } ?: p.defaultModel
}

@Serializable
data class MacroSplit(val proteinPct: Int = 30, val carbsPct: Int = 40, val fatPct: Int = 30)

@Serializable
data class AppState(
    val plan: Plan? = null,
    val entries: List<FoodEntry> = emptyList(),
    val savedFoods: List<SavedFood> = emptyList(),
    /** Per-week calorie overrides after day swaps. Key = ISO date of that week's Sunday. */
    val weekOverrides: Map<String, List<Int>> = emptyMap(),
    /** Most recent automatic high-day shift message, keyed by ISO date it happened. */
    val shiftNotices: Map<String, String> = emptyMap(),
    val ai: AiSettings = AiSettings(),
    val macros: MacroSplit = MacroSplit(),
    val autoDetectHighDays: Boolean = true,
)
