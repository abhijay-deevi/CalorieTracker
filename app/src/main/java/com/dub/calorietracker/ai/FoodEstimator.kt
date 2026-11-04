package com.dub.calorietracker.ai

import com.dub.calorietracker.data.AiProvider
import com.dub.calorietracker.data.AiSettings
import com.dub.calorietracker.data.FoodItem
import com.dub.calorietracker.data.SavedFood
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Estimate(
    val name: String,
    val items: List<FoodItem>,
    val confidence: String,
    val notes: String,
) {
    val calories get() = items.sumOf { it.calories }
    val protein get() = items.sumOf { it.protein }
    val carbs get() = items.sumOf { it.carbs }
    val fat get() = items.sumOf { it.fat }
}

/** The provider said "too many requests" — try again after [retryAfterSeconds]. */
class AiRateLimitException(val retryAfterSeconds: Long, message: String) : Exception(message)

/** Any other AI failure with a user-readable message. */
class AiException(message: String) : Exception(message)

interface FoodEstimator {
    /** Blocking call — run off the main thread. */
    /** [images]: 0..n JPEGs of the same meal (different angles). */
    fun estimate(text: String?, images: List<ByteArray>, known: List<SavedFood>): Estimate

    companion object {
        fun create(settings: AiSettings): FoodEstimator {
            val key = settings.key().trim()
            if (key.isEmpty()) throw AiException("Add your ${settings.provider.label} API key in Settings first.")
            val model = settings.model()
            return when (settings.provider) {
                AiProvider.GEMINI -> GeminiEstimator(key, model)
                AiProvider.OPENAI -> OpenAiEstimator(key, model)
                AiProvider.CLAUDE -> ClaudeEstimator(key, model)
            }
        }
    }
}

internal object Prompt {
    val system = """
You are a nutrition estimator inside a calorie tracking app. The user sends a photo of food and/or a
text description of what they ate. Estimate calories and macronutrients as accurately as you can,
using typical US portion sizes when amounts are not given. If a photo is included, estimate portion
sizes from visual cues (plate size, utensils, packaging). Break the meal into its main items.

The user has foods saved from earlier. If the user refers to one of them (even loosely, e.g. "pb&j"
for "peanut butter jelly"), reuse its saved values exactly (scaled if they say a different amount).

Reply with ONLY a JSON object, no markdown, in this exact shape:
{
  "name": "short everyday name for this meal, lowercase, 1-5 words, e.g. peanut butter jelly",
  "items": [
    {"name": "white bread", "quantity": "2 slices", "calories": 160, "protein_g": 5, "carbs_g": 30, "fat_g": 2}
  ],
  "confidence": "low | medium | high",
  "notes": "one short sentence about your assumptions"
}
If the image or text is not food, return "items": [] and explain in "notes".
""".trimIndent()

    fun user(text: String?, imageCount: Int, known: List<SavedFood>): String = buildString {
        val hasImage = imageCount > 0
        if (known.isNotEmpty()) {
            appendLine("Saved foods (name: calories / protein g / carbs g / fat g):")
            known.take(60).forEach {
                appendLine("- ${it.name}: ${it.calories.roundToInt()} / ${it.protein.roundToInt()} / ${it.carbs.roundToInt()} / ${it.fat.roundToInt()}" +
                    if (it.description.isNotBlank()) " (${it.description.take(120)})" else "")
            }
            appendLine()
        }
        if (imageCount == 1) appendLine("A photo of the food is attached.")
        if (imageCount > 1) appendLine(
            "$imageCount photos are attached. They all show the SAME single meal from different angles — " +
                "do not count any food twice. Use the extra angles to judge portion size, depth and hidden items."
        )
        if (!text.isNullOrBlank()) appendLine("User's description: $text")
        else if (hasImage) appendLine("No description given — estimate from the photo.")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Pulls the JSON object out of the model's reply, tolerating code fences or extra text. */
    fun parse(reply: String): Estimate {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("The AI reply didn't contain an estimate. Try again.")
        val obj = try {
            json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject
        } catch (e: Exception) {
            throw AiException("Couldn't read the AI reply. Try again.")
        }
        fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun JsonObject.num(vararg keys: String) =
            keys.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.doubleOrNull } ?: 0.0
        val items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            FoodItem(
                name = o.str("name"),
                quantity = o.str("quantity"),
                calories = o.num("calories", "kcal"),
                protein = o.num("protein_g", "protein"),
                carbs = o.num("carbs_g", "carbs"),
                fat = o.num("fat_g", "fat"),
            )
        }
        val notes = obj.str("notes")
        if (items.isEmpty()) throw AiException(notes.ifBlank { "No food found in that." })
        return Estimate(
            name = obj.str("name").ifBlank { items.joinToString(", ") { it.name } }.trim(),
            items = items,
            confidence = obj.str("confidence"),
            notes = notes,
        )
    }
}

/** Minimal HTTP POST returning (status, headers, body). */
internal fun postJson(url: String, headers: Map<String, String>, body: String): Triple<Int, Map<String, String>, String> {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 20_000
        readTimeout = 120_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val hdrs = conn.headerFields.filterKeys { it != null }
            .mapKeys { it.key.lowercase() }.mapValues { it.value.firstOrNull().orEmpty() }
        return Triple(code, hdrs, text)
    } catch (e: IOException) {
        throw AiException("Network error: ${e.message ?: "check your connection"}")
    } finally {
        conn.disconnect()
    }
}

/** Parses durations like "33s", "1.5s", "6m0s", "1h2m3s" into seconds. */
internal fun parseDurationSeconds(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    s.trim().toDoubleOrNull()?.let { return kotlin.math.ceil(it).toLong() }
    val re = Regex("""(\d+(?:\.\d+)?)(ms|h|m|s)""")
    var total = 0.0
    var found = false
    re.findAll(s).forEach {
        found = true
        val v = it.groupValues[1].toDouble()
        total += when (it.groupValues[2]) { "h" -> v * 3600; "m" -> v * 60; "ms" -> v / 1000; else -> v }
    }
    return if (found) kotlin.math.ceil(total).toLong() else null
}

internal fun errorMessage(body: String): String = try {
    val o = Json.parseToJsonElement(body).jsonObject
    val err = o["error"]
    when (err) {
        is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull
        else -> err?.jsonPrimitive?.contentOrNull
    } ?: body.take(300)
} catch (e: Exception) {
    body.take(300)
}

internal fun JsonObject.arr(k: String): List<JsonObject> =
    (this[k] as? JsonArray)?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
