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

/**
 * Goals. [deficit] is calories per day below maintenance ([RECOMP] uses 5% of maintenance instead).
 * [proteinPerKg] is daily protein per kg of body weight: research puts the muscle-building benefit at
 * ~1.6 g/kg (Morton 2018, upper range 2.2 g/kg), and more protein helps keep muscle when calories are lower,
 * so it rises with the deficit. Recomp (lose fat + build muscle at once) uses the top of the range.
 */
@Serializable
enum class Goal(val label: String, val rate: String, val deficit: Int, val proteinPerKg: Double) {
    MAINTAIN("Maintain weight", "", 0, 1.6),
    RECOMP("Body recomposition", "Lose fat and build muscle at the same time (needs weight training)", 0, 2.2),
    MILD_LOSS("Mild weight loss", "0.5 lb/week", 250, 1.8),
    LOSS("Weight loss", "1 lb/week", 500, 2.0),
    EXTREME_LOSS("Extreme weight loss", "2 lb/week", 1000, 2.2),
}

/** How the calories left after protein are split between fat and carbs. [fatPct] = share of calories from fat. */
@Serializable
enum class MacroStyle(val label: String, val description: String, val fatPct: Double) {
    BALANCED("Balanced (recommended)", "25% of calories from fat, the rest from carbs. Plenty of carbs to fuel workouts.", 0.25),
    LOWER_CARB("Lower carb", "35% fat. Fewer carbs, if you feel better with more fat.", 0.35),
    HIGHER_CARB("Higher carb", "20% fat. Most carbs, for lots of cardio or sports.", 0.20),
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
    /** Null for a custom plan typed in without the calculator. */
    val profile: Profile?,
    val maintenanceCalories: Int,
    val goal: Goal,
    /** Average calories per day (the week averages to this). */
    val dailyTarget: Int,
    val schedule: ScheduleType,
    /** Calories for Sunday..Saturday (index 0 = Sunday). */
    val weekTemplate: List<Int>,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Protein and fat in grams, the same every day; carbs fill the rest of each day's calories, so on
     * zigzag high days the extra calories come from carbs and protein stays put. 0 = older plan (use [MacroSplit]).
     */
    val proteinG: Int = 0,
    val fatG: Int = 0,
    val macroStyle: MacroStyle = MacroStyle.BALANCED,
    /** Typed in by hand instead of calculated. */
    val custom: Boolean = false,
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
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Accent colors: [hue]/[sat] for buttons, tabs and macro bars; [calorieHue] (a neighbor) for calorie bars. */
@Serializable
enum class Accent(val label: String, val hue: Float, val sat: Float, val calorieHue: Float) {
    RED("Red", 355f, 0.72f, 335f),
    ORANGE("Orange", 24f, 0.85f, 42f),
    YELLOW("Yellow", 46f, 0.9f, 80f),
    GREEN("Green", 135f, 0.5f, 165f),
    TEAL("Teal", 172f, 0.6f, 195f),
    BLUE("Blue", 205f, 0.78f, 170f),
    INDIGO("Indigo", 232f, 0.62f, 205f),
    PURPLE("Purple", 270f, 0.55f, 300f),
    PINK("Pink", 330f, 0.7f, 290f),
}

@Serializable
data class ThemeSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val accent: Accent = Accent.BLUE)

/** Daily check-in. Weight and waist are stored in kg / cm; waist is measured about once a week. */
@Serializable
data class DayLog(
    val weightKg: Double? = null,
    val gym: Boolean = false,
    val cardio: Boolean = false,
    val waistCm: Double? = null,
) {
    fun isEmpty() = weightKg == null && !gym && !cardio && waistCm == null
}

/** Old percentage-based macros, only used for plans saved before protein/fat grams existed. */
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
    val theme: ThemeSettings = ThemeSettings(),
    /** Daily check-ins keyed by ISO date. */
    val dayLogs: Map<String, DayLog> = emptyMap(),
)
